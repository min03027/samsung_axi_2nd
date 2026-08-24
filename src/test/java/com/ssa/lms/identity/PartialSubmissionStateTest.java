package com.ssa.lms.identity;

import com.ssa.lms.identity.dto.IdentityViews;
import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.repository.ExamIdentitySessionRepository;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.identity.support.IdentityTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부분 제출 상태가 API 응답과 화면 문구에 정확히 드러나는가 (P1-2).
 *
 * <p><b>무엇이 문제였나</b><br>
 * 도메인 전이는 옳았다 — 한쪽만 들어오면 {@code PENDING} 을 유지한다. 그런데 화면 DTO 에는
 * {@code hasFaceCheck} 만 있고 {@code hasIdCard} 가 없었고, PC 의 PENDING 라벨은 <b>항상
 * "미제출"</b> 이었다. 모바일은 신분증만 올려도 "제출이 완료되었습니다. 운영진 검토를 기다려
 * 주세요." 라고 응답했다. 실기 QA 화면에서 실제로 그렇게 표시됐다 — 아직 검토될 수 없는
 * 상태에서 훈련생이 기다리게 만드는 잘못된 안내다.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class PartialSubmissionStateTest {

    @Autowired ExamIdentityService identityService;
    @Autowired ExamIdentitySessionRepository sessionRepository;
    @Autowired IdentityTestFixture fixture;

    private record Fixture(IdentityTestFixture.Ctx ctx, Long sessionId) {
    }

    private Fixture open() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        return new Fixture(c, s.getId());
    }

    private IdentityViews.Status status(Fixture f) {
        return identityService.statusOf(f.sessionId(), f.ctx().userId())
                .orElseThrow(() -> new AssertionError("상태를 읽지 못했다"));
    }

    /* ===================== 네 가지 제출 조합 ===================== */

    @Test
    @DisplayName("[P1-2] 아무것도 제출하지 않음: 두 플래그 모두 false, 라벨이 둘 다 미제출임을 말한다")
    void 미제출() {
        IdentityViews.Status v = status(open());

        assertThat(v.status()).isEqualTo("PENDING");
        assertThat(v.hasIdCard()).isFalse();
        assertThat(v.hasFaceCheck()).isFalse();
        assertThat(v.submissionComplete()).isFalse();
        assertThat(v.canEnter()).isFalse();
        assertThat(v.statusLabel()).contains("신분증").contains("얼굴").contains("미제출");
    }

    @Test
    @DisplayName("[P1-2] 신분증만 제출: hasIdCard=true 이고 라벨이 '얼굴 사진 미제출' 을 알린다")
    void 신분증만() {
        Fixture f = open();
        fixture.uploadIdCard(f.sessionId());

        IdentityViews.Status v = status(f);
        assertThat(v.status()).as("한쪽만으로는 검토 대기가 아니다").isEqualTo("PENDING");
        assertThat(v.hasIdCard()).isTrue();
        assertThat(v.hasFaceCheck()).isFalse();
        assertThat(v.submissionComplete()).isFalse();
        assertThat(v.statusLabel())
                .as("'미제출' 한 단어로 뭉뚱그리면 신분증을 낸 사람도 미제출로 보인다")
                .contains("신분증 제출 완료")
                .contains("얼굴 사진 미제출");
    }

    @Test
    @DisplayName("[P1-2] 얼굴만 제출: hasFaceCheck=true 이고 라벨이 '신분증 미제출' 을 알린다")
    void 얼굴만() {
        Fixture f = open();
        fixture.uploadFaceCheck(f.sessionId());

        IdentityViews.Status v = status(f);
        assertThat(v.status()).isEqualTo("PENDING");
        assertThat(v.hasIdCard()).isFalse();
        assertThat(v.hasFaceCheck()).isTrue();
        assertThat(v.submissionComplete()).isFalse();
        assertThat(v.statusLabel())
                .contains("얼굴 사진 제출 완료")
                .contains("신분증 미제출");
    }

    @Test
    @DisplayName("[P1-2] 신분증 → 얼굴 순서: 두 번째 제출 직후에만 검토 대기가 된다")
    void 신분증먼저_순서() {
        Fixture f = open();

        fixture.uploadIdCard(f.sessionId());
        assertThat(status(f).submissionComplete()).isFalse();

        fixture.uploadFaceCheck(f.sessionId());
        IdentityViews.Status v = status(f);
        assertThat(v.status()).isEqualTo("SUBMITTED");
        assertThat(v.hasIdCard()).isTrue();
        assertThat(v.hasFaceCheck()).isTrue();
        assertThat(v.submissionComplete()).isTrue();
        assertThat(v.statusLabel()).contains("검토 대기");
    }

    @Test
    @DisplayName("[P1-2] 얼굴 → 신분증 순서: 결과가 같다")
    void 얼굴먼저_순서() {
        Fixture f = open();

        fixture.uploadFaceCheck(f.sessionId());
        assertThat(status(f).submissionComplete()).isFalse();

        fixture.uploadIdCard(f.sessionId());
        IdentityViews.Status v = status(f);
        assertThat(v.status()).isEqualTo("SUBMITTED");
        assertThat(v.submissionComplete()).isTrue();
        assertThat(v.statusLabel()).contains("검토 대기");
    }

    /* ===================== 판정 이후 라벨 ===================== */

    @Test
    @DisplayName("[P1-2] 검토 중 · 승인 · 최종 반려 · 재제출 요청이 서로 다른 라벨로 구분된다")
    void 판정상태_라벨_구분() {
        Fixture f = open();
        fixture.uploadIdCard(f.sessionId());
        fixture.uploadFaceCheck(f.sessionId());

        identityService.openReview(f.sessionId(), f.ctx().adminId(), "ADMIN", "127.0.0.1");
        assertThat(status(f).statusLabel()).isEqualTo("검토 중");

        identityService.approve(f.sessionId(), f.ctx().adminId(), "ADMIN", "127.0.0.1");
        IdentityViews.Status approved = status(f);
        assertThat(approved.statusLabel()).isEqualTo("승인");
        assertThat(approved.canEnter()).isTrue();

        /* 승인 유효시간이 지나면 다시 신분확인을 받아야 한다.
           (승인 후 운영진이 직접 재제출을 요청하는 API 는 현재 노출돼 있지 않다 — 결과 문서에 명시) */
        fixture.expireApproval(f.sessionId());
        identityService.openSession(f.ctx().examId(), f.ctx().userId(), "127.0.0.1");
        IdentityViews.Status reopened = status(f);
        assertThat(reopened.status()).isEqualTo("PENDING");
        assertThat(reopened.canEnter()).as("만료 후에는 입장할 수 없다").isFalse();
        assertThat(reopened.hasIdCard()).isFalse();
        assertThat(reopened.hasFaceCheck()).isFalse();
    }

    @Test
    @DisplayName("[P1-2] 재제출 요청 라벨은 검토 중·최종 반려와 구분된다")
    void 재제출요청_라벨_구분() {
        Fixture f = open();
        fixture.uploadIdCard(f.sessionId());
        fixture.uploadFaceCheck(f.sessionId());
        identityService.reject(f.sessionId(), f.ctx().adminId(), "ADMIN", "다시 촬영", true, "127.0.0.1");

        IdentityViews.Status v = status(f);
        assertThat(v.status()).isEqualTo("RESUBMIT_REQUIRED");
        assertThat(v.statusLabel()).isEqualTo("재제출 요청");
        assertThat(v.canEnter()).isFalse();
        assertThat(v.reason()).isEqualTo("다시 촬영");
    }

    @Test
    @DisplayName("[P1-2] 최종 반려 라벨은 재제출 요청과 구분된다")
    void 최종반려_라벨() {
        Fixture f = open();
        fixture.uploadIdCard(f.sessionId());
        fixture.uploadFaceCheck(f.sessionId());
        identityService.reject(f.sessionId(), f.ctx().adminId(), "ADMIN", "본인 아님", false, "127.0.0.1");

        IdentityViews.Status v = status(f);
        assertThat(v.status()).isEqualTo("REJECTED");
        assertThat(v.statusLabel()).isEqualTo("최종 반려");
        assertThat(v.reason()).isEqualTo("본인 아님");
    }

    /* ===================== 재제출 초기화 ===================== */

    @Test
    @DisplayName("[P1-2] 재제출 요청을 받으면 두 플래그와 안내가 모두 초기화된다")
    void 재제출_두포인터_초기화() {
        Fixture f = open();
        fixture.uploadIdCard(f.sessionId());
        fixture.uploadFaceCheck(f.sessionId());
        identityService.reject(f.sessionId(), f.ctx().adminId(), "ADMIN", "다시", true, "127.0.0.1");

        IdentityViews.Status v = status(f);
        assertThat(v.hasIdCard()).as("과거 신분증이 남아 있으면 새 얼굴과 섞인다").isFalse();
        assertThat(v.hasFaceCheck()).isFalse();
        assertThat(v.submissionComplete()).isFalse();
        assertThat(v.statusLabel()).isEqualTo("재제출 요청");

        /* 재제출 중 한쪽만 다시 올리면 진행 중임이 드러나야 한다. */
        fixture.uploadIdCard(f.sessionId());
        IdentityViews.Status half = status(f);
        assertThat(half.status()).isEqualTo("RESUBMIT_REQUIRED");
        assertThat(half.hasIdCard()).isTrue();
        assertThat(half.hasFaceCheck()).isFalse();
        assertThat(half.statusLabel()).contains("얼굴 사진 미제출");
    }

    /* ===================== 모바일 라벨 ===================== */

    @Test
    @DisplayName("[P1-2] 모바일 라벨도 부분 제출을 구분한다")
    void 모바일_부분제출_라벨() {
        Fixture f = open();
        ExamIdentityService.IssuedToken t =
                identityService.issueToken(f.sessionId(), f.ctx().userId(), "127.0.0.1");
        identityService.submitIdCard(t.rawToken(), IdentityTestFixture.imageFile("id.jpg"), "127.0.0.1");

        IdentityViews.Mobile m = identityService.describeMobile(t.rawToken());
        assertThat(m.blocked()).isFalse();
        assertThat(m.hasIdCard()).isTrue();
        assertThat(m.hasFaceCheck()).isFalse();
        assertThat(m.statusLabel())
                .as("모바일에서도 '검토 대기' 로 뭉뚱그리면 안 된다")
                .doesNotContain("검토 대기")
                .contains("얼굴 사진 미제출");
    }

    @Test
    @DisplayName("[P1-2] 신분증만 제출한 모바일 응답은 PC 로 돌아가라고 안내한다")
    void 모바일_부분제출_안내문구() {
        Fixture f = open();
        ExamIdentityService.IssuedToken t =
                identityService.issueToken(f.sessionId(), f.ctx().userId(), "127.0.0.1");

        ExamIdentityService.SubmitResult r = identityService.submitIdCardAndDescribe(
                t.rawToken(), IdentityTestFixture.imageFile("id.jpg"), "127.0.0.1");

        assertThat(r.submissionComplete()).isFalse();
        assertThat(r.message())
                .contains("신분증 제출이 완료되었습니다")
                .contains("PC")
                .contains("웹캠 얼굴 사진을 제출")
                .doesNotContain("운영진 검토를 기다려");
    }

    @Test
    @DisplayName("[P1-2] 얼굴이 먼저 있던 세션은 신분증 제출로 완전 제출이 되어 검토 대기를 안내한다")
    void 모바일_완전제출_안내문구() {
        Fixture f = open();
        fixture.uploadFaceCheck(f.sessionId());
        ExamIdentityService.IssuedToken t =
                identityService.issueToken(f.sessionId(), f.ctx().userId(), "127.0.0.1");

        ExamIdentityService.SubmitResult r = identityService.submitIdCardAndDescribe(
                t.rawToken(), IdentityTestFixture.imageFile("id.jpg"), "127.0.0.1");

        assertThat(r.submissionComplete()).isTrue();
        assertThat(r.status()).isEqualTo("SUBMITTED");
        assertThat(r.message()).contains("운영진 검토를 기다려");
    }
}
