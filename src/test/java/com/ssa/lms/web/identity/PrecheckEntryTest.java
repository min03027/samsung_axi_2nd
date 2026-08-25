package com.ssa.lms.web.identity;

import com.ssa.lms.exam.dto.ExamTakeRow;
import com.ssa.lms.exam.entity.Exam;
import com.ssa.lms.exam.service.ExamAttemptService;
import com.ssa.lms.identity.policy.PrecheckPolicy;
import com.ssa.lms.identity.repository.ExamIdentitySessionRepository;
import com.ssa.lms.identity.support.IdentityTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 시험 목록 → 사전점검 진입 연결 (지적 3) + 직접 URL 접근 권한 (지적 4).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PrecheckEntryTest {

    @Autowired MockMvc mvc;
    @Autowired ExamAttemptService examAttemptService;
    @Autowired ExamIdentitySessionRepository sessionRepository;
    @Autowired IdentityTestFixture fixture;

    /* ===================== 지적 3: precheckRequired 판정 ===================== */

    @Test
    @DisplayName("[3] 감독+본인확인 시험만 precheckRequired=true")
    void 판정_감독과본인확인() {
        Exam proctored = fixture.proctoredExam();
        assertThat(PrecheckPolicy.requiresPrecheck(proctored)).isTrue();
        assertThat(examAttemptService.requiresPrecheck(proctored.getId())).isTrue();
    }

    @Test
    @DisplayName("[3] 본인확인만 true·감독 false 이면 precheckRequired=false — 비밀번호 모달 유지")
    void 판정_본인확인만() {
        Exam e = fixture.examWith(false, true);
        assertThat(PrecheckPolicy.requiresPrecheck(e)).isFalse();
        assertThat(PrecheckPolicy.requiresPasswordVerification(e))
                .as("비밀번호 본인인증 대상이어야 한다").isTrue();
    }

    @Test
    @DisplayName("[3] 감독만 true·본인확인 false 이면 precheckRequired=false")
    void 판정_감독만() {
        Exam e = fixture.examWith(true, false);
        assertThat(PrecheckPolicy.requiresPrecheck(e)).isFalse();
        assertThat(PrecheckPolicy.requiresPasswordVerification(e)).isFalse();
    }

    @Test
    @DisplayName("[3] 둘 다 false 면 기존 직접 시작 흐름")
    void 판정_둘다false() {
        Exam e = fixture.examWith(false, false);
        assertThat(PrecheckPolicy.requiresPrecheck(e)).isFalse();
        assertThat(PrecheckPolicy.requiresPasswordVerification(e)).isFalse();
    }

    @Test
    @DisplayName("[3] 목록 DTO 가 precheckRequired 를 정확히 내려준다")
    @WithUserDetails("trainee1")
    void 목록DTO_판정() {
        Exam proctored = fixture.proctoredExam();
        List<ExamTakeRow> rows = examAttemptService.availableExams(fixture.trainee1Id());

        ExamTakeRow row = rows.stream()
                .filter(r -> r.id().equals(String.valueOf(proctored.getId())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("대상 시험이 목록에 없습니다 — 수강 데이터를 확인하세요."));
        assertThat(row.precheckRequired()).isTrue();

        /* 모든 행이 정책과 정확히 일치해야 한다 — DTO 와 게이트가 어긋나면 안 된다.
           (시드에 감독 시험이 여러 개일 수 있으므로 "하나만 true" 로 단정하지 않는다) */
        rows.forEach(r -> {
            boolean expected = examAttemptService.requiresPrecheck(Long.valueOf(r.id()));
            assertThat(r.precheckRequired())
                    .as("시험 %s 의 목록 판정이 정책과 달라졌다", r.id()).isEqualTo(expected);
        });
    }

    @Test
    @DisplayName("[3] 목록 화면이 precheck URL 계약을 내려준다")
    @WithUserDetails("trainee1")
    void 화면_URL계약() throws Exception {
        String html = mvc.perform(get("/trainee/exam"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("</html>");        /* 잘린 응답이 아닌지 */
        assertThat(html).contains("/trainee/exam/precheck/{id}");
        assertThat(html).as("기존 start·attempt 계약도 유지").contains("/trainee/exam/{id}/start");
    }

    /* ===================== 지적 4: 직접 URL 접근 ===================== */

    @Test
    @DisplayName("[4] 수강 중인 훈련생은 사전점검에 진입한다")
    @WithUserDetails("trainee1")
    void 수강생_진입성공() throws Exception {
        Exam proctored = fixture.proctoredExam();
        fixture.clearSessions(proctored.getId(), fixture.trainee1Id());

        mvc.perform(get("/trainee/exam/precheck/{examId}", proctored.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("응시환경 사전점검")));

        assertThat(sessionRepository.findTopByExamIdAndUserIdOrderByIdDesc(
                proctored.getId(), fixture.trainee1Id())).isPresent();
    }

    @Test
    @DisplayName("[4] 사전점검 대상이 아닌 시험 ID 로는 진입할 수 없고 세션도 생기지 않는다")
    @WithUserDetails("trainee1")
    void 비대상_시험_차단() throws Exception {
        Exam plain = fixture.examWith(false, false);

        mvc.perform(get("/trainee/exam/precheck/{examId}", plain.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/trainee/exam"));

        assertThat(sessionRepository.findTopByExamIdAndUserIdOrderByIdDesc(
                plain.getId(), fixture.trainee1Id()))
                .as("거부 시 세션을 만들면 안 된다").isEmpty();
    }

    @Test
    @DisplayName("[4] 없는 examId 는 안전하게 목록으로 돌린다")
    @WithUserDetails("trainee1")
    void 없는_시험() throws Exception {
        mvc.perform(get("/trainee/exam/precheck/{examId}", 99999999L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/trainee/exam"));
    }

    @Test
    @DisplayName("[4] 비수강 훈련생은 직접 URL 로 진입할 수 없고 세션도 생기지 않는다")
    void 비수강생_차단() {
        Exam proctored = fixture.proctoredExam();
        Long outsider = fixture.userNotEnrolledIn(proctored.getId());

        assertThatThrownBy(() -> fixture.openSessionAs(proctored.getId(), outsider))
                .isInstanceOf(com.ssa.lms.identity.entity.IdentityAccessDeniedException.class);

        assertThat(sessionRepository.findTopByExamIdAndUserIdOrderByIdDesc(proctored.getId(), outsider))
                .as("거부 시 세션을 만들면 안 된다").isEmpty();
    }
}
