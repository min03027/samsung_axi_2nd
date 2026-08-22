package com.ssa.lms.auth;

import com.ssa.lms.completion.entity.Completion;
import com.ssa.lms.completion.entity.CompletionResult;
import com.ssa.lms.completion.entity.ConfirmStatus;
import com.ssa.lms.completion.repository.CompletionRepository;
import com.ssa.lms.completion.service.CompletionService;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.Enrollment;
import com.ssa.lms.course.entity.EnrollmentStatus;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.EnrollmentRepository;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증/보안 감사 실증 테스트 (docs/auth-audit-2026-07-28.md 의 재현 근거).
 *
 * <p>"코드가 그렇게 보인다"가 아니라 실제 요청/DB 로 각 항목을 실증한다. 체크리스트 대응:
 * <ol>
 *   <li>인증 기본 흐름 — 상태별 로그인 차단·실패 메시지 비구분·로그아웃 세션 무효화</li>
 *   <li>세션/CSRF — CSRF 없는 POST 거부 (세션ID 교체·HttpOnly 는 실기동 curl 로 별도 실증)</li>
 *   <li>권한 경계 — 역할×구역 매트릭스(미인증/훈련생/강사)</li>
 *   <li>IDOR — 남의 수강신청 취소·이수증 다운로드 차단</li>
 *   <li>시험 응시 본인인증 — IdentityVerificationService 계약</li>
 *   <li>비밀번호/개인정보 — bcrypt·AES 암호문 실측, 현재 비밀번호 검증, 로그 원문 미노출</li>
 *   <li>가입 검증 — AuthFlowTest 에서 커버(중복/동의/불일치)</li>
 * </ol>
 *
 * 시드 계정(admin/instructor1/trainee1, pw 1234)에 더해 감사용 픽스처를 멱등 생성한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthSecurityAuditTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CompletionRepository completionRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired IdentityVerificationService identityVerificationService;
    @Autowired CompletionService completionService;
    @Autowired JdbcTemplate jdbc;

    private User trainee1;
    private User trainee2;      // IDOR 피해자
    private Long victimEnrollmentId;
    private Long victimCompletionId;

    @BeforeEach
    void setUp() {
        trainee1 = userRepository.findByLoginId("trainee1").orElseThrow();
        Course course = courseRepository.findAll().get(0);
        LocalDateTime now = LocalDateTime.now();

        trainee2 = userRepository.findByLoginId("audit_trainee2").orElseGet(() ->
                userRepository.save(User.builder()
                        .loginId("audit_trainee2").password(passwordEncoder.encode("1234"))
                        .name("감사훈련2").role(Role.TRAINEE).status(UserStatus.ACTIVE)
                        .email("audit.t2@ssa.local").phone("010-9999-0002").birthDate("2000-02-02")
                        .privacyConsentAt(now).thirdPartyConsentAt(now).build()));

        userRepository.findByLoginId("audit_suspended").orElseGet(() ->
                userRepository.save(User.builder()
                        .loginId("audit_suspended").password(passwordEncoder.encode("1234"))
                        .name("정지회원").role(Role.TRAINEE).status(UserStatus.SUSPENDED)
                        .email("audit.susp@ssa.local").phone("010-9999-0003").birthDate("2000-03-03")
                        .privacyConsentAt(now).thirdPartyConsentAt(now).build()));

        victimEnrollmentId = enrollmentRepository.findByTraineeIdOrderByAppliedAtDesc(trainee2.getId()).stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.APPLIED)
                .map(Enrollment::getId).findFirst()
                .orElseGet(() -> enrollmentRepository.save(Enrollment.builder()
                        .trainee(trainee2).course(course)
                        .status(EnrollmentStatus.APPLIED).appliedAt(now).build()).getId());

        victimCompletionId = completionRepository.findByTraineeId(trainee2.getId()).stream()
                .map(Completion::getId).findFirst()
                .orElseGet(() -> completionRepository.save(Completion.builder()
                        .course(course).trainee(trainee2).progressRate(90).attendanceRate(90)
                        .gradesConfirmed(true).result(CompletionResult.PASS)
                        .confirmStatus(ConfirmStatus.CONFIRMED).evaluatedAt(now).build()).getId());
    }

    /** @AuthenticationPrincipal LoginUser 로 주입되도록 실제 LoginUser principal 로 인증. */
    private RequestPostProcessor as(User u) {
        return user(new LoginUser(u));
    }

    // ============================================================
    // 체크리스트 2 — CSRF
    // ============================================================
    @Nested
    @DisplayName("CSRF")
    class Csrf {
        @Test
        @DisplayName("CSRF 토큰 없는 로그인 POST 는 403")
        void 로그인_POST_csrf없으면_403() throws Exception {
            mvc.perform(post("/login").param("username", "trainee1").param("password", "1234"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("CSRF 토큰 없는 가입 POST 는 403")
        void 가입_POST_csrf없으면_403() throws Exception {
            mvc.perform(post("/signup/trainee")
                            .param("loginId", "csrfless").param("password", "password123")
                            .param("passwordConfirm", "password123").param("name", "무토큰")
                            .param("email", "x@x.com").param("phone", "010-0000-0000")
                            .param("birthDate", "2000-01-01")
                            .param("privacyConsent", "true").param("thirdPartyConsent", "true"))
                    .andExpect(status().isForbidden());
            assertThat(userRepository.findByLoginId("csrfless")).isEmpty();
        }

        @Test
        @DisplayName("CSRF 토큰 없는 진도 저장 POST(REST) 는 403")
        void 진도저장_POST_csrf없으면_403() throws Exception {
            mvc.perform(post("/trainee/contents/1/progress").with(as(trainee1))
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    // ============================================================
    // 체크리스트 3 — 권한 경계 매트릭스 (역할 × 구역)
    // ============================================================
    @Nested
    @DisplayName("권한 경계")
    class RoleBoundary {

        // 관리자 전용(hasRole ADMIN)
        private final List<String> adminOnly = List.of(
                "/admin", "/admin/user", "/admin/courses", "/admin/attendance", "/admin/completion-management");
        // 강사 이상(hasAnyRole ADMIN,INSTRUCTOR)
        private final List<String> instructorArea = List.of(
                "/instructor", "/instructor/courses", "/instructor/proctor");

        @Test
        @DisplayName("미인증 상태로 보호 구역 접근 → /login 리다이렉트")
        void 미인증은_로그인으로_리다이렉트() throws Exception {
            for (String url : List.of("/admin", "/admin/user", "/instructor", "/instructor/courses",
                    "/trainee", "/trainee/my-course", "/trainee/attendance")) {
                mvc.perform(get(url))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrlPattern("**/login"));
            }
        }

        @Test
        @DisplayName("훈련생으로 관리자/강사 구역 5개 이상 → 403")
        void 훈련생은_상위구역_403() throws Exception {
            int count = 0;
            for (String url : concat(adminOnly, instructorArea)) {
                mvc.perform(get(url).with(as(trainee1))).andExpect(status().isForbidden());
                count++;
            }
            assertThat(count).isGreaterThanOrEqualTo(5);
        }

        @Test
        @DisplayName("강사로 관리자 전용 구역 5개 이상 → 403, 강사 구역은 허용")
        void 강사는_관리자구역_403이고_강사구역은_허용() throws Exception {
            User instructor = userRepository.findByLoginId("instructor1").orElseThrow();
            for (String url : adminOnly) {   // 5개
                mvc.perform(get(url).with(as(instructor))).andExpect(status().isForbidden());
            }
            // 양성 대조군: 강사 구역은 403 이 아니어야 한다 (핸들러 유무와 무관하게 인가는 통과)
            for (String url : instructorArea) {
                mvc.perform(get(url).with(as(instructor)))
                        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
            }
        }

        private List<String> concat(List<String> a, List<String> b) {
            return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
        }
    }

    // ============================================================
    // 체크리스트 1 — 상태별 로그인 차단 & 실패 메시지 비구분
    // ============================================================
    @Nested
    @DisplayName("상태별 로그인 차단")
    class LoginStatus {
        @Test
        @DisplayName("PENDING 계정 → /login?pending (승인 전 차단)")
        void PENDING은_pending으로() throws Exception {
            mvc.perform(post("/login").with(csrf())
                            .param("username", "trainee_pending").param("password", "1234"))
                    .andExpect(redirectedUrl("/login?pending"));
        }

        @Test
        @DisplayName("SUSPENDED 계정 → /login?error (정지 사실을 노출하지 않음 · 승인대기 오분류 방지)")
        void SUSPENDED는_error로() throws Exception {
            mvc.perform(post("/login").with(csrf())
                            .param("username", "audit_suspended").param("password", "1234"))
                    .andExpect(redirectedUrl("/login?error"));
        }

        @Test
        @DisplayName("없는 계정과 틀린 비밀번호는 동일하게 /login?error (계정 존재여부 비구분)")
        void 없는계정과_틀린비번은_동일메시지() throws Exception {
            mvc.perform(post("/login").with(csrf())
                            .param("username", "no_such_user_zzz").param("password", "whatever"))
                    .andExpect(redirectedUrl("/login?error"));
            mvc.perform(post("/login").with(csrf())
                            .param("username", "trainee1").param("password", "wrong-password"))
                    .andExpect(redirectedUrl("/login?error"));
        }
    }

    // ============================================================
    // 체크리스트 1 — 로그아웃 세션 무효화
    // ============================================================
    @Test
    @DisplayName("로그아웃 후 같은 세션으로 보호 페이지 접근 불가")
    void 로그아웃하면_세션이_무효화된다() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", "trainee1").param("password", "1234"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession(false);

        // 로그인 세션으로 보호 페이지 정상 접근
        mvc.perform(get("/trainee/my-course").session(session))
                .andExpect(status().isOk());

        // 로그아웃
        mvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(redirectedUrl("/login?logout"));

        // 같은 세션 재사용 → 미인증으로 로그인 리다이렉트
        mvc.perform(get("/trainee/my-course").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ============================================================
    // 체크리스트 4 — IDOR
    // ============================================================
    @Nested
    @DisplayName("IDOR")
    class Idor {
        @Test
        @DisplayName("남의 수강신청은 취소되지 않는다 (본인 검증)")
        void 남의_수강신청_취소_차단() throws Exception {
            mvc.perform(post("/trainee/enrollments/" + victimEnrollmentId + "/cancel")
                            .with(as(trainee1)).with(csrf()))
                    .andExpect(status().is3xxRedirection());
            // 피해자 신청은 여전히 APPLIED (취소되지 않음)
            Enrollment after = enrollmentRepository.findById(victimEnrollmentId).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(EnrollmentStatus.APPLIED);
        }

        @Test
        @DisplayName("남의 이수증 다운로드는 404 (존재여부 미노출)")
        void 남의_이수증_다운로드_404() throws Exception {
            mvc.perform(get("/trainee/completion-management/" + victimCompletionId + "/certificate")
                            .with(as(trainee1)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("이수증 소유 경계: 본인만 true (서비스 실측)")
        void 이수증_소유_경계() {
            assertThat(completionService.isOwnedByTrainee(victimCompletionId, trainee2.getId())).isTrue();
            assertThat(completionService.isOwnedByTrainee(victimCompletionId, trainee1.getId())).isFalse();
        }

        @Test
        @DisplayName("본인 리소스 화면은 principal id 로만 조회 (URL 로 타인 id 주입 불가)")
        void 본인리소스는_principal로만() throws Exception {
            // /trainee/attendance·/trainee/my-course 는 경로에 사용자 id 파라미터가 없다 → 조작 지점 자체가 없음
            mvc.perform(get("/trainee/attendance").with(as(trainee1))).andExpect(status().isOk());
            mvc.perform(get("/trainee/my-course").with(as(trainee1))).andExpect(status().isOk());
        }
    }

    // ============================================================
    // 체크리스트 5 — 시험 응시 본인인증 (A 계약)
    // ============================================================
    @Nested
    @DisplayName("본인인증 계약")
    class Identity {
        @Test
        @DisplayName("비밀번호 일치 시 인증 성공 + 인증 이력 기록")
        void 인증성공_이력기록() {
            assertThat(identityVerificationService.lastVerifiedAt(trainee2.getId())).isEmpty();
            String method = identityVerificationService.verify(trainee2.getId(), VerifyRequest.password("1234"));
            assertThat(method).isEqualTo(VerifyRequest.METHOD_PASSWORD);
            assertThat(identityVerificationService.lastVerifiedAt(trainee2.getId())).isPresent();
        }

        @Test
        @DisplayName("비밀번호 불일치 시 인증 실패(예외)")
        void 비번틀리면_실패() {
            assertThatThrownBy(() -> identityVerificationService.verify(trainee1.getId(),
                    VerifyRequest.password("nope-nope")))
                    .isInstanceOf(IdentityVerificationException.class);
        }

        @Test
        @DisplayName("지원하지 않는 수단은 거부")
        void 미지원수단_거부() {
            assertThatThrownBy(() -> identityVerificationService.verify(trainee1.getId(),
                    new VerifyRequest("SMS", "1234")))
                    .isInstanceOf(IdentityVerificationException.class);
        }
    }

    // ============================================================
    // 체크리스트 6 — 비밀번호/개인정보
    // ============================================================
    @Nested
    @DisplayName("비밀번호/개인정보 취급")
    class Secrets {
        @Test
        @DisplayName("DB 저장값: 비밀번호는 bcrypt, 개인정보는 암호문")
        void DB저장은_해시와_암호문() {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT password, email, phone, birth_date FROM users WHERE login_id = 'trainee1'");
            String pw = (String) row.get("password");
            String email = (String) row.get("email");
            String phone = (String) row.get("phone");
            String birth = (String) row.get("birth_date");

            assertThat(pw).startsWith("$2");                 // bcrypt
            assertThat(pw).isNotEqualTo("1234");
            assertThat(email).isNotEqualTo("trainee1@ssa.local");   // 암호문
            assertThat(phone).isNotEqualTo("010-2222-2222");
            assertThat(birth).isNotEqualTo("1999-07-07");

            // 라운드트립: 엔티티 조회 시 평문 복호화
            User u = userRepository.findByLoginId("trainee1").orElseThrow();
            assertThat(u.getEmail()).isEqualTo("trainee1@ssa.local");
            assertThat(u.getPhone()).isEqualTo("010-2222-2222");
        }

        @Test
        @DisplayName("비밀번호 변경: 현재 비밀번호가 틀리면 거부")
        void 비번변경_현재비번_틀리면_거부() throws Exception {
            mvc.perform(post("/trainee/my-info/password").with(as(trainee2)).with(csrf())
                            .param("currentPassword", "wrong-current")
                            .param("newPassword", "brandNew123")
                            .param("confirmPassword", "brandNew123"))
                    .andExpect(status().is3xxRedirection());
            // 비밀번호는 그대로여야 한다
            User after = userRepository.findByLoginId("audit_trainee2").orElseThrow();
            assertThat(passwordEncoder.matches("1234", after.getPassword())).isTrue();
            assertThat(passwordEncoder.matches("brandNew123", after.getPassword())).isFalse();
        }

        @Test
        @DisplayName("로그인 실패 로그에 비밀번호 원문이 저장되지 않는다")
        void 실패로그에_비번원문_없음() throws Exception {
            String secret = "S3cretProbe_9x";
            mvc.perform(post("/login").with(csrf())
                    .param("username", "audit_probe_user").param("password", secret));

            List<Map<String, Object>> logs = jdbc.queryForList(
                    "SELECT login_id, ip_address, user_agent FROM access_log");
            boolean leaked = logs.stream().flatMap(m -> m.values().stream())
                    .anyMatch(v -> v != null && v.toString().contains(secret));
            assertThat(leaked).as("access_log 어느 컬럼에도 비밀번호 원문이 없어야 함").isFalse();
        }
    }
}
