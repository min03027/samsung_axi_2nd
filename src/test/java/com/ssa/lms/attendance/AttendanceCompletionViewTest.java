package com.ssa.lms.attendance;

import com.ssa.lms.completion.entity.Completion;
import com.ssa.lms.completion.entity.CompletionResult;
import com.ssa.lms.completion.entity.ConfirmStatus;
import com.ssa.lms.completion.repository.CompletionRepository;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.Enrollment;
import com.ssa.lms.course.entity.EnrollmentStatus;
import com.ssa.lms.course.entity.CourseStatus;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.EnrollmentRepository;
import com.ssa.lms.grading.entity.Grade;
import com.ssa.lms.grading.repository.GradeRepository;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 강사·훈련생 출결/이수 화면의 렌더링과 권한 경계 검증.
 *
 * <p>컨트롤러가 {@code @AuthenticationPrincipal LoginUser} 를 쓰므로 시드 계정(instructor1/trainee1)으로
 * 인증한다(local 프로필 데모 시더가 COURSE-2026-001 에 출결·이수·이수증까지 심어 둔다).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AttendanceCompletionViewTest {

    private static final String DEMO_COURSE = "COURSE-2026-001";

    @Autowired MockMvc mvc;
    @Autowired CourseRepository courseRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired GradeRepository gradeRepository;
    @Autowired UserRepository userRepository;
    @Autowired CompletionRepository completionRepository;

    /* ===== 렌더링 ===== */

    @Test
    @DisplayName("강사 출결현황/이수 관리 화면 렌더링")
    @WithUserDetails("instructor1")
    void instructorViewsRender() throws Exception {
        mvc.perform(get("/instructor/attendance"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("</html>")));
        mvc.perform(get("/instructor/graduate"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("</html>")));
    }

    @Test
    @DisplayName("훈련생 출결현황/이수관리 화면 렌더링")
    @WithUserDetails("trainee1")
    void traineeViewsRender() throws Exception {
        mvc.perform(get("/trainee/attendance"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("</html>")));
        mvc.perform(get("/trainee/completion-management"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이수증 보기")))
                .andExpect(content().string(containsString("data-certificate-preview")))
                .andExpect(content().string(containsString("certificatePreviewFrame")))
                .andExpect(content().string(containsString("</html>")));
    }

    @Test
    @DisplayName("관리자는 훈련생에게 실제 과정 이수를 부여하고 즉시 이수증을 발급한다")
    @WithUserDetails("admin")
    void adminGrantsManualCompletionAndCertificate() throws Exception {
        Course course = courseRepository.save(Course.builder()
                .courseCode("COURSE-MANUAL-CERT-A3")
                .courseName("관리자 직접 이수 부여 과정")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 8, 20))
                .capacity(20)
                .status(CourseStatus.COMPLETED)
                .completionProgressRate(80)
                .build());
        User trainee = userRepository.save(User.builder()
                .loginId("manual-certificate-trainee-a3")
                .password("x")
                .name("직접이수훈련생")
                .role(Role.TRAINEE)
                .status(UserStatus.ACTIVE)
                .birthDate("2000-01-01")
                .build());

        mvc.perform(post("/admin/completion/manual-grant")
                        .with(csrf())
                        .param("courseId", course.getId().toString())
                        .param("traineeId", trainee.getId().toString())
                        .param("progressRate", "96")
                        .param("attendanceRate", "94")
                        .param("averageScore", "91")
                        .param("gradesConfirmed", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/completion?courseId=" + course.getId()))
                .andExpect(flash().attribute("message", containsString("이수증을 발급")));

        Completion granted = completionRepository
                .findByCourseIdAndTraineeId(course.getId(), trainee.getId())
                .orElseThrow();
        assertThat(granted.getProgressRate()).isEqualTo(96);
        assertThat(granted.getAttendanceRate()).isEqualTo(94);
        assertThat(granted.getAverageScore()).isEqualTo(91.0);
        assertThat(granted.getResult()).isEqualTo(CompletionResult.PASS);
        assertThat(granted.getConfirmStatus()).isEqualTo(ConfirmStatus.CONFIRMED);
        assertThat(granted.isCertificateIssuable()).isTrue();

        mvc.perform(get("/admin/completion/{id}/certificate", granted.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("관리자가 설정한 진도·출석·테스트 기준 충족 시 자동 이수와 이수증 발급까지 처리한다")
    @WithUserDetails("admin")
    void adminCriteriaAutomaticallyCompletesTraineeAndIssuesCertificate() throws Exception {
        Course course = courseRepository.save(Course.builder()
                .courseCode("COURSE-AUTO-CERT-A3")
                .courseName("자동 이수 기준 과정")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 8, 20))
                .capacity(20)
                .status(CourseStatus.COMPLETED)
                .completionProgressRate(0)
                .build());
        User trainee = userRepository.save(User.builder()
                .loginId("auto-certificate-trainee-a3")
                .password("x")
                .name("자동이수훈련생")
                .role(Role.TRAINEE)
                .status(UserStatus.ACTIVE)
                .birthDate("2001-03-14")
                .build());
        Enrollment enrollment = enrollmentRepository.save(Enrollment.builder()
                .trainee(trainee)
                .course(course)
                .status(EnrollmentStatus.APPROVED)
                .appliedAt(LocalDateTime.now().minusMonths(3))
                .build());
        User admin = userRepository.findByLoginId("admin").orElseThrow();
        Grade grade = Grade.builder()
                .user(trainee)
                .course(course)
                .evalType(Grade.EvalType.EXAM)
                .evalRefId(991_001L)
                .status(Grade.GradeStatus.UNGRADED)
                .build();
        grade.applyScore(85, null, 60, admin, LocalDateTime.now());
        grade.confirm(admin, LocalDateTime.now());
        gradeRepository.save(grade);

        mvc.perform(post("/admin/completion/criteria")
                        .with(csrf())
                        .param("courseId", course.getId().toString())
                        .param("minProgressRate", "0")
                        .param("minAttendanceRate", "0")
                        .param("minAverageScore", "90")
                        .param("requireGradePass", "true")
                        .param("note", "자동 이수 통합 테스트"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/admin/completion/evaluate")
                        .with(csrf())
                        .param("courseId", course.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", containsString("이수 확정 및 이수증 발급")));

        Completion belowThreshold = completionRepository
                .findByCourseIdAndTraineeId(course.getId(), trainee.getId())
                .orElseThrow();
        assertThat(belowThreshold.getAverageScore()).isEqualTo(85.0);
        assertThat(belowThreshold.getResult()).isEqualTo(CompletionResult.FAIL);
        assertThat(belowThreshold.getConfirmStatus()).isEqualTo(ConfirmStatus.EXPECTED);
        assertThat(enrollmentRepository.findById(enrollment.getId()).orElseThrow().getStatus())
                .isEqualTo(EnrollmentStatus.APPROVED);

        mvc.perform(post("/admin/completion/criteria")
                        .with(csrf())
                        .param("courseId", course.getId().toString())
                        .param("minProgressRate", "0")
                        .param("minAttendanceRate", "0")
                        .param("minAverageScore", "80")
                        .param("requireGradePass", "true"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/completion/evaluate")
                        .with(csrf())
                        .param("courseId", course.getId().toString()))
                .andExpect(status().is3xxRedirection());

        Completion completion = completionRepository
                .findByCourseIdAndTraineeId(course.getId(), trainee.getId())
                .orElseThrow();
        assertThat(completion.getAverageScore()).isEqualTo(85.0);
        assertThat(completion.getGradesConfirmed()).isTrue();
        assertThat(completion.getResult()).isEqualTo(CompletionResult.PASS);
        assertThat(completion.getConfirmStatus()).isEqualTo(ConfirmStatus.CONFIRMED);
        assertThat(completion.isCertificateIssuable()).isTrue();
        assertThat(enrollmentRepository.findById(enrollment.getId()).orElseThrow().getStatus())
                .isEqualTo(EnrollmentStatus.COMPLETED);

        mvc.perform(get("/admin/completion").param("courseId", course.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("최소 테스트 평균")))
                .andExpect(content().string(containsString("기준 적용·자동 이수 처리")))
                .andExpect(content().string(containsString("자동이수훈련생")));
    }

    @Test
    @DisplayName("승인된 과정 수강생은 자동 판정 전에도 관리자 이수 관리 명단에 표시된다")
    @WithUserDetails("admin")
    void approvedTraineeAppearsBeforeCompletionEvaluation() throws Exception {
        Course course = courseRepository.save(Course.builder()
                .courseCode("COURSE-PENDING-COMPLETION-A3")
                .courseName("미판정 수강생 표시 과정")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 8, 20))
                .capacity(20)
                .status(CourseStatus.IN_PROGRESS)
                .completionProgressRate(80)
                .build());
        User trainee = userRepository.save(User.builder()
                .loginId("pending-completion-trainee-a3")
                .password("x")
                .name("승인미판정훈련생")
                .role(Role.TRAINEE)
                .status(UserStatus.ACTIVE)
                .birthDate("2002-05-03")
                .build());
        enrollmentRepository.save(Enrollment.builder()
                .trainee(trainee)
                .course(course)
                .status(EnrollmentStatus.APPROVED)
                .appliedAt(LocalDateTime.now().minusDays(2))
                .build());

        assertThat(completionRepository.findByCourseIdAndTraineeId(course.getId(), trainee.getId())).isEmpty();

        mvc.perform(get("/admin/completion").param("courseId", course.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-pending-trainee=\"승인미판정훈련생\"")))
                .andExpect(content().string(containsString("자동 판정 전")));

        // 단순 화면 조회는 공식 이수 기록을 만들지 않는다.
        assertThat(completionRepository.findByCourseIdAndTraineeId(course.getId(), trainee.getId())).isEmpty();
    }

    @Test
    @DisplayName("관리자는 과정별 이수증 템플릿 3종과 A4 미리보기 화면을 연다")
    @WithUserDetails("admin")
    void adminCertificateEditorRendersForSelectedCourse() throws Exception {
        Course course = courseRepository.save(Course.builder()
                .courseCode("COURSE-CERT-EDITOR-A3")
                .courseName("AI 실무 이수증 시연 과정")
                .cohort("3기")
                .category("AI")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 8, 20))
                .capacity(20)
                .status(CourseStatus.IN_PROGRESS)
                .completionProgressRate(80)
                .build());

        mvc.perform(get("/admin/completion/certificate-editor")
                        .param("courseId", course.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI 실무 이수증 시연 과정")))
                .andExpect(content().string(containsString("공식형")))
                .andExpect(content().string(containsString("테크형")))
                .andExpect(content().string(containsString("크리에이티브형")))
                .andExpect(content().string(containsString("A4 미리보기")))
                .andExpect(content().string(containsString("id=\"certificatePaper\"")))
                .andExpect(content().string(containsString("실제 발급 PDF와 서버 데이터에는 아직 반영되지 않습니다")));

        mvc.perform(get("/admin/completion").param("courseId", course.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/completion/certificate-editor?courseId=" + course.getId())))
                .andExpect(content().string(containsString("이수증 디자인 편집")));
    }

    /* ===== 권한 경계 ===== */

    @Test
    @DisplayName("강사가 담당하지 않는 과정 출결/이수를 조회하면 403")
    @WithUserDetails("instructor1")
    void instructorCannotAccessForeignCourse() throws Exception {
        Long foreignCourseId = courseRepository.save(Course.builder()
                .courseCode("COURSE-FOREIGN-A3").courseName("타 강사 과정")
                .startDate(LocalDate.of(2026, 2, 1)).endDate(LocalDate.of(2026, 7, 1))
                .capacity(20).status(CourseStatus.DRAFT).completionProgressRate(80).build()).getId();

        mvc.perform(get("/instructor/attendance").param("courseId", foreignCourseId.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/instructor/graduate").param("courseId", foreignCourseId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("훈련생 본인 이수증은 내려받고, 남의 이수증 다운로드는 404")
    @WithUserDetails("trainee1")
    void traineeCertificateOwnershipBoundary() throws Exception {
        Course course = courseRepository.findByCourseCode(DEMO_COURSE).orElseThrow();

        // 본인(trainee1)의 확정 이수증 — 데모 시더가 확정해 둠
        User trainee1 = userRepository.findByLoginId("trainee1").orElseThrow();
        Completion mine = completionRepository.findByCourseIdAndTraineeId(course.getId(), trainee1.getId())
                .orElseThrow();
        mvc.perform(get("/trainee/completion-management/{id}/certificate", mine.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // 다른 훈련생의 이수 정보 — URL 조작 접근 시 404(존재 여부 미노출)
        User other = userRepository.save(User.builder()
                .loginId("trainee-a3-other").password("x").name("남의훈련생")
                .role(Role.TRAINEE).status(UserStatus.ACTIVE).build());
        Completion foreign = completionRepository.save(Completion.builder()
                .course(course).trainee(other)
                .progressRate(100).attendanceRate(100).gradesConfirmed(null)
                .result(CompletionResult.PASS).confirmStatus(ConfirmStatus.CONFIRMED)
                .evaluatedAt(LocalDateTime.now()).build());
        foreign.confirm(LocalDateTime.now());
        completionRepository.save(foreign);

        mvc.perform(get("/trainee/completion-management/{id}/certificate", foreign.getId()))
                .andExpect(status().isNotFound());
    }

    /* ===== degenerate: 확정 전 이수의 이수증 요청 (미이수/미확정) ===== */

    /**
     * 본인 소유지만 <b>미확정(PASS·EXPECTED)</b> 이수의 이수증. 발급 조건({@code PASS && CONFIRMED})을
     * 아직 못 채운 정상 상태다. 예전엔 {@code IllegalStateException} 을 던졌는데 매핑 advice 가 없어
     * whitelabel 500 으로 샜다. "아직 발급할 이수증이 없다" → 404 여야 한다.
     */
    @Test
    @DisplayName("본인 소유·미확정 이수의 이수증 다운로드는 500 이 아니라 404")
    @WithUserDetails("trainee2")
    void traineeUnconfirmedCertificateIs404() throws Exception {
        Course course = courseRepository.save(Course.builder()
                .courseCode("COURSE-UNCONF-T").courseName("미확정 이수 데모")
                .startDate(LocalDate.of(2026, 2, 1)).endDate(LocalDate.of(2026, 7, 1))
                .capacity(20).status(CourseStatus.DRAFT).completionProgressRate(80).build());
        User trainee2 = userRepository.findByLoginId("trainee2").orElseThrow();
        Completion unconfirmed = completionRepository.save(Completion.builder()
                .course(course).trainee(trainee2)
                .progressRate(100).attendanceRate(100).gradesConfirmed(null)
                .result(CompletionResult.PASS).confirmStatus(ConfirmStatus.EXPECTED) // 통과했으나 미확정
                .evaluatedAt(LocalDateTime.now()).build());

        mvc.perform(get("/trainee/completion-management/{id}/certificate", unconfirmed.getId()))
                .andExpect(status().isNotFound());
    }

    /** 관리자 이수증 발급 경로도 미확정 이수에 대해 500 이 아니라 404 여야 한다(진도/출결 미달 판정대기 케이스). */
    @Test
    @DisplayName("관리자의 미확정 이수 이수증 요청은 500 이 아니라 404")
    @WithMockUser(roles = "ADMIN")
    void adminUnconfirmedCertificateIs404() throws Exception {
        Course course = courseRepository.save(Course.builder()
                .courseCode("COURSE-UNCONF-A").courseName("판정대기 이수 데모")
                .startDate(LocalDate.of(2026, 2, 1)).endDate(LocalDate.of(2026, 7, 1))
                .capacity(20).status(CourseStatus.DRAFT).completionProgressRate(80).build());
        User trainee3 = userRepository.findByLoginId("trainee3").orElseThrow();
        Completion pending = completionRepository.save(Completion.builder()
                .course(course).trainee(trainee3)
                .progressRate(40).attendanceRate(30).gradesConfirmed(null)
                .result(CompletionResult.PENDING).confirmStatus(ConfirmStatus.PENDING)
                .evaluatedAt(LocalDateTime.now()).build());

        mvc.perform(get("/admin/completion/{id}/certificate", pending.getId()))
                .andExpect(status().isNotFound());
    }
}
