package com.ssa.lms.web.identity;

import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.repository.ExamIdentityAuditLogRepository;
import com.ssa.lms.identity.repository.ExamIdentitySessionRepository;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.identity.support.IdentityTestFixture;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 신분확인 권한 경계 (P0-1 / P0-5).
 *
 * <p>local 프로필 시더가 trainee1·admin 계정과 시험을 심어 둔다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IdentityAccessControlTest {

    @Autowired MockMvc mvc;
    @Autowired ExamIdentityService identityService;
    @Autowired ExamIdentitySessionRepository sessionRepository;
    @Autowired ExamIdentityAuditLogRepository auditRepository;
    @Autowired UserRepository userRepository;
    @Autowired IdentityTestFixture fixture;

    private Long trainee1Id() {
        return userRepository.findByLoginId("trainee1").map(User::getId).orElseThrow();
    }

    private Long otherTraineeId() {
        return userRepository.findAll().stream()
                .filter(u -> u.getLoginId().startsWith("trainee") && !u.getLoginId().equals("trainee1"))
                .map(User::getId).findFirst().orElseThrow();
    }

    /**
     * trainee1 소유의 <b>깨끗한</b> 세션을 만든다.
     * 세션 재사용 정책 때문에 앞 테스트 상태가 남으면 안 되므로 fixture 가 정리해 준다.
     */
    private ExamIdentitySession freshSession() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        return identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
    }

    /* ===================== P0-1: 타인 세션 QR 발급 차단 ===================== */

    @Test
    @DisplayName("[P0-1] 본인 세션이면 QR 을 발급할 수 있다")
    @WithUserDetails("trainee1")
    void 본인_QR_발급() throws Exception {
        Long sid = freshSession().getId();

        mvc.perform(post("/trainee/exam/precheck/{id}/identity/qr", sid).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.url").exists());
    }

    @Test
    @DisplayName("[P0-1] 남의 세션 id 로는 QR 을 발급할 수 없다 — 403")
    @WithUserDetails("trainee1")
    void 타인_QR_발급_차단() throws Exception {
        /* 다른 훈련생 소유 세션을 만들고, trainee1 이 그 id 로 발급을 시도한다. */
        /* 타인 소유 세션을 만든다. 다른 훈련생이 이 과정을 수강 중이 아닐 수 있으므로
           서비스의 수강 검증을 거치지 않고 fixture 로 직접 만든다 —
           이 테스트의 목적은 '소유자 검증' 이지 '수강 검증' 이 아니다. */
        /* (exam,user) 유니크 제약 때문에 같은 시험에 두 세션을 둘 수 없다.
           소유자 검증만 보면 되므로 타인 세션은 다른 시험으로 만든다. */
        ExamIdentitySession victim = fixture.sessionOwnedByOnOtherExam(otherTraineeId());

        mvc.perform(post("/trainee/exam/precheck/{id}/identity/qr", victim.getId()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    @DisplayName("[P0-1] 남의 세션 상태는 조회할 수 없다 — 404")
    @WithUserDetails("trainee1")
    void 타인_상태조회_차단() throws Exception {
        /* 타인 소유 세션을 만든다. 다른 훈련생이 이 과정을 수강 중이 아닐 수 있으므로
           서비스의 수강 검증을 거치지 않고 fixture 로 직접 만든다 —
           이 테스트의 목적은 '소유자 검증' 이지 '수강 검증' 이 아니다. */
        /* (exam,user) 유니크 제약 때문에 같은 시험에 두 세션을 둘 수 없다.
           소유자 검증만 보면 되므로 타인 세션은 다른 시험으로 만든다. */
        ExamIdentitySession victim = fixture.sessionOwnedByOnOtherExam(otherTraineeId());

        mvc.perform(get("/trainee/exam/precheck/{id}/identity/status", victim.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[P0-1] 남의 세션에 얼굴 사진을 올릴 수 없다 — 403")
    @WithUserDetails("trainee1")
    void 타인_얼굴사진_차단() throws Exception {
        /* 타인 소유 세션을 만든다. 다른 훈련생이 이 과정을 수강 중이 아닐 수 있으므로
           서비스의 수강 검증을 거치지 않고 fixture 로 직접 만든다 —
           이 테스트의 목적은 '소유자 검증' 이지 '수강 검증' 이 아니다. */
        /* (exam,user) 유니크 제약 때문에 같은 시험에 두 세션을 둘 수 없다.
           소유자 검증만 보면 되므로 타인 세션은 다른 시험으로 만든다. */
        ExamIdentitySession victim = fixture.sessionOwnedByOnOtherExam(otherTraineeId());

        mvc.perform(multipart("/trainee/exam/precheck/{id}/face-check", victim.getId())
                        .file("file", new byte[]{1, 2, 3})
                        .param("consent", "true")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    /* ===================== 모바일 토큰 ===================== */

    @Test
    @DisplayName("잘못된 토큰으로 모바일 화면을 열면 업로드 폼이 나오지 않는다")
    void 잘못된_토큰() throws Exception {
        mvc.perform(get("/m/id/{token}", "INVALID-TOKEN-VALUE"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("제출할 수 없습니다")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("id=\"idFile\""))));
    }

    /* ===================== P0-5: 이미지 권한·감사 ===================== */

    @Test
    @DisplayName("[P0-5] 훈련생은 신분증 이미지를 조회할 수 없다")
    @WithUserDetails("trainee1")
    void 훈련생_이미지조회_차단() throws Exception {
        mvc.perform(get("/admin/evaluation/identity/document/{id}/image", 999999L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[P0-5] 비로그인 사용자는 신분증 이미지에 접근할 수 없다")
    void 비로그인_이미지조회_차단() throws Exception {
        mvc.perform(get("/admin/evaluation/identity/document/{id}/image", 999999L))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("[P0-5] 존재하지 않는 documentId 는 403 — 존재 여부를 흘리지 않는다")
    @WithUserDetails("admin")
    void 없는_문서_추측접근() throws Exception {
        mvc.perform(get("/admin/evaluation/identity/document/{id}/image", 999999L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[P0-5] 대기열 GET 은 세션 상태를 바꾸지 않는다")
    @WithUserDetails("admin")
    void GET_상태변경_없음() throws Exception {
        ExamIdentitySession s = freshSession();
        ExamIdentitySession.Status before = s.getStatus();

        mvc.perform(get("/admin/evaluation/identity/{id}", s.getId()))
                .andExpect(status().isOk());

        ExamIdentitySession after = sessionRepository.findById(s.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(before);
    }

    @Test
    @DisplayName("[P0-5] QR 발급이 감사 로그에 남는다")
    @WithUserDetails("trainee1")
    void QR발급_감사로그() throws Exception {
        Long sid = freshSession().getId();
        long before = auditRepository.countBySessionIdAndAction(
                sid, com.ssa.lms.identity.entity.ExamIdentityAuditLog.Action.ISSUE_QR);

        mvc.perform(post("/trainee/exam/precheck/{id}/identity/qr", sid).with(csrf()))
                .andExpect(status().isOk());

        long after = auditRepository.countBySessionIdAndAction(
                sid, com.ssa.lms.identity.entity.ExamIdentityAuditLog.Action.ISSUE_QR);
        assertThat(after).isGreaterThan(before);
    }

    /* ===================== 지적 6: 강사 담당 범위 =====================
       담당·비담당 강사의 GET/POST 상태 코드는 IdentityStaffScopeTest 가
       조건 분기 없이 검증한다. 여기에는 픽스처 결과에 따라 assertion 이 건너뛰어지던
       if (owns) 형태의 테스트가 있었고, 그래서 "비담당 차단" 이 한 줄도 실행되지 않는
       날이 있었다 (P1-1). */

    @Test
    @DisplayName("[6] ADMIN 은 담당 여부와 무관하게 전체를 본다")
    @WithUserDetails("admin")
    void ADMIN_전체접근() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.submitIdCard(s.getId());

        mvc.perform(get("/admin/evaluation/identity/{id}", s.getId()))
                .andExpect(status().isOk());
    }

    /* ===================== 화면 규칙 ===================== */

    @Test
    @DisplayName("PC 사전점검 화면에는 신분증 파일 입력이 없다")
    @WithUserDetails("trainee1")
    void PC에_파일입력_없음() throws Exception {
        String html = mvc.perform(get("/trainee/exam/precheck/{examId}", 1L))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("</html>");           /* 잘린 응답이 아닌지 (CLAUDE.md 규칙 3) */
        assertThat(html).doesNotContain("type=\"file\"");
        assertThat(html).contains("웹캠 테스트 시작");
    }

    @Test
    @DisplayName("모바일 화면은 후면 카메라 촬영과 동의를 제공한다")
    @WithUserDetails("trainee1")
    void 모바일_촬영_동의() throws Exception {
        Long sid = freshSession().getId();
        ExamIdentityService.IssuedToken t = identityService.issueToken(sid, trainee1Id(), "127.0.0.1");

        String html = mvc.perform(get("/m/id/{token}", t.rawToken()))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("</html>");
        assertThat(html).contains("capture=\"environment\"");
        assertThat(html).contains("id=\"consent\"");
        /* WEBP 는 서버가 거부하므로 화면 문구·accept 도 맞아야 한다 (P0-6) */
        assertThat(html).doesNotContain("webp");
    }
}
