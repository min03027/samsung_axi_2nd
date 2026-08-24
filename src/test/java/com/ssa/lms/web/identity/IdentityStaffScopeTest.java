package com.ssa.lms.web.identity;

import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.repository.ExamIdentityAuditLogRepository;
import com.ssa.lms.identity.repository.ExamIdentitySessionRepository;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.identity.support.IdentityTestFixture;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 운영진 객체 권한 — <b>조건 분기 없이</b> 항상 실행되는 검증 (P0-2 / P1-1).
 *
 * <p><b>무엇이 문제였나</b><br>
 * ① {@code IdentityAccessDeniedException} 은 Spring Security 의 예외가 아니라서 공용
 * {@code AccessDeniedAdvice} 가 잡지 못했고, 비담당 강사의 상세 GET·판정 POST 가
 * <b>403 이 아니라 500</b> 이 됐다.<br>
 * ② 기존 테스트는 {@code boolean owns = ...; if (owns) ... else ...} 구조라, 시드가 담당으로
 * 잡히는 날에는 "비담당 차단" assertion 이 <b>한 줄도 실행되지 않았다</b>.</p>
 *
 * <p>여기서는 담당·비담당을 픽스처가 직접 만든다. 조건 분기가 없고,
 * {@code isNotEqualTo(403)} 처럼 500 도 통과시키는 느슨한 단언을 쓰지 않는다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IdentityStaffScopeTest {

    @Autowired MockMvc mvc;
    @Autowired ExamIdentityService identityService;
    @Autowired ExamIdentitySessionRepository sessionRepository;
    @Autowired ExamIdentityAuditLogRepository auditRepository;
    @Autowired IdentityTestFixture fixture;

    /** 완전 제출(신분증 + 얼굴) 상태의 세션. 판정 API 가 상태가 아닌 권한으로 막히는지 보려면 필요하다. */
    private ExamIdentitySession submittedSession(IdentityTestFixture.Ctx c) {
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        ExamIdentitySession reloaded = sessionRepository.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("판정 가능한 상태여야 권한 때문에 막혔는지 확인할 수 있다")
                .isEqualTo(ExamIdentitySession.Status.SUBMITTED);
        return reloaded;
    }

    private Long idDocOf(Long sessionId) {
        Long id = fixture.currentIdDocumentId(sessionId);
        assertThat(id).as("신분증 문서가 있어야 이미지 권한을 검증할 수 있다").isNotNull();
        return id;
    }

    /* ===================== 비담당 강사 — 전부 정확히 403 ===================== */

    @Test
    @DisplayName("[P0-2] 비담당 강사: 상세 GET 은 정확히 403")
    @WithUserDetails("instructor1")
    void 비담당_상세_403() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        fixture.assignOtherInstructor(c.examId(), fixture.instructor1Id());
        ExamIdentitySession s = submittedSession(c);

        mvc.perform(get("/admin/evaluation/identity/{id}", s.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[P0-2] 비담당 강사: 이미지 GET 은 정확히 403")
    @WithUserDetails("instructor1")
    void 비담당_이미지_403() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        fixture.assignOtherInstructor(c.examId(), fixture.instructor1Id());
        ExamIdentitySession s = submittedSession(c);

        mvc.perform(get("/admin/evaluation/identity/document/{id}/image", idDocOf(s.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[P0-2] 비담당 강사: review POST 는 정확히 403, 상태·감사 로그 변화 없음")
    @WithUserDetails("instructor1")
    void 비담당_review_403() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        fixture.assignOtherInstructor(c.examId(), fixture.instructor1Id());
        ExamIdentitySession s = submittedSession(c);
        long auditBefore = auditRepository.findBySessionIdOrderByIdDesc(s.getId()).size();

        mvc.perform(post("/admin/evaluation/identity/{id}/review", s.getId()).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(sessionRepository.findById(s.getId()).orElseThrow().getStatus())
                .as("권한 거부가 상태를 바꾸면 안 된다")
                .isEqualTo(ExamIdentitySession.Status.SUBMITTED);
        assertThat(auditRepository.findBySessionIdOrderByIdDesc(s.getId()))
                .as("권한 거부가 감사 로그를 남기면 안 된다").hasSize((int) auditBefore);
    }

    @Test
    @DisplayName("[P0-2] 비담당 강사: approve POST 는 정확히 403, 승인되지 않음")
    @WithUserDetails("instructor1")
    void 비담당_approve_403() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        fixture.assignOtherInstructor(c.examId(), fixture.instructor1Id());
        ExamIdentitySession s = submittedSession(c);

        mvc.perform(post("/admin/evaluation/identity/{id}/approve", s.getId()).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(sessionRepository.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamIdentitySession.Status.SUBMITTED);
    }

    @Test
    @DisplayName("[P0-2] 비담당 강사: reject POST 는 정확히 403, 반려되지 않음")
    @WithUserDetails("instructor1")
    void 비담당_reject_403() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        fixture.assignOtherInstructor(c.examId(), fixture.instructor1Id());
        ExamIdentitySession s = submittedSession(c);

        mvc.perform(post("/admin/evaluation/identity/{id}/reject", s.getId())
                        .param("reason", "테스트")
                        .param("resubmit", "false")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(sessionRepository.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamIdentitySession.Status.SUBMITTED);
    }

    @Test
    @DisplayName("[P0-2] 비담당 강사: 대기열에 해당 세션이 노출되지 않는다")
    @WithUserDetails("instructor1")
    void 비담당_대기열_제외() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        fixture.assignOtherInstructor(c.examId(), fixture.instructor1Id());
        ExamIdentitySession s = submittedSession(c);

        String html = mvc.perform(get("/admin/evaluation/identity"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("/admin/evaluation/identity/" + s.getId() + "\"");
    }

    /* ===================== 담당 강사 — 정확히 허용 ===================== */

    @Test
    @DisplayName("[P0-2] 담당 강사: 대기열·상세·이미지는 200")
    @WithUserDetails("instructor1")
    void 담당_조회_200() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        fixture.assignInstructor(c.examId(), fixture.instructor1Id());
        ExamIdentitySession s = submittedSession(c);

        String html = mvc.perform(get("/admin/evaluation/identity"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("/admin/evaluation/identity/" + s.getId() + "\"");

        mvc.perform(get("/admin/evaluation/identity/{id}", s.getId()))
                .andExpect(status().isOk());

        /* 실제 저장된 파일이 있는 문서다 — 200 이 나와야 한다. */
        mvc.perform(get("/admin/evaluation/identity/document/{id}/image", idDocOf(s.getId())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[P0-2] 담당 강사: review·approve 판정이 실제로 반영된다")
    @WithUserDetails("instructor1")
    void 담당_판정_허용() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        fixture.assignInstructor(c.examId(), fixture.instructor1Id());
        ExamIdentitySession s = submittedSession(c);

        mvc.perform(post("/admin/evaluation/identity/{id}/review", s.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(sessionRepository.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamIdentitySession.Status.UNDER_REVIEW);

        mvc.perform(post("/admin/evaluation/identity/{id}/approve", s.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(sessionRepository.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamIdentitySession.Status.APPROVED);
    }

    /* ===================== ADMIN ===================== */

    @Test
    @DisplayName("[P0-2] ADMIN 은 담당이 아니어도 상세·이미지·판정이 모두 허용된다")
    @WithUserDetails("admin")
    void ADMIN_전체허용() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        fixture.assignOtherInstructor(c.examId(), fixture.instructor1Id());
        ExamIdentitySession s = submittedSession(c);

        mvc.perform(get("/admin/evaluation/identity/{id}", s.getId()))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/evaluation/identity/document/{id}/image", idDocOf(s.getId())))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/evaluation/identity/{id}/approve", s.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(sessionRepository.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamIdentitySession.Status.APPROVED);
    }
}
