package com.ssa.lms.identity;

import com.ssa.lms.identity.entity.ExamIdentityDocument;
import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.entity.IdentitySessionStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * 신분확인 세션의 상태 전이 규칙 (LXP-015).
 *
 * <p>여기서 막지 못하면 상위 계층이 아무리 검사해도 우회된다. Spring 컨텍스트 없이
 * 엔티티만으로 도는 순수 단위 테스트다.</p>
 */
class ExamIdentitySessionStateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 23, 10, 0);

    /* 엔티티는 protected 기본 생성자 + builder 라 리플렉션 없이 만들 수 있다.
       exam/user 는 이 테스트에서 쓰지 않으므로 null 로 둔다. */
    private ExamIdentitySession newSession() {
        return ExamIdentitySession.builder().exam(null).user(null).createdIp("127.0.0.1").build();
    }

    /** 신분증 + 얼굴 + 동의를 모두 채워 승인 가능한 상태로 만든다. */
    private void complete(ExamIdentitySession s) {
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "face-consent-v1");
    }

    private ExamIdentityDocument doc(ExamIdentitySession s, ExamIdentityDocument.Kind kind) {
        return ExamIdentityDocument.builder()
                .session(s).kind(kind).storageKey("k/" + kind).contentType("image/jpeg")
                .sizeBytes(1024).sha256("h").width(800).height(500).uploadedAt(NOW).build();
    }

    @Test
    @DisplayName("새 세션은 PENDING 이고 아직 입장할 수 없다")
    void 새_세션() {
        ExamIdentitySession s = newSession();
        assertThat(s.getStatus()).isEqualTo(ExamIdentitySession.Status.PENDING);
        assertThat(s.canEnter(NOW)).isFalse();
        assertThat(s.acceptsSubmission()).isTrue();
    }

    @Test
    @DisplayName("신분증만 올린 상태는 아직 PENDING — 얼굴 사진이 있어야 검토 대기로 간다")
    void 일부제출은_PENDING() {
        ExamIdentitySession s = newSession();
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);

        assertThat(s.getStatus()).isEqualTo(ExamIdentitySession.Status.PENDING);
        assertThat(s.isSubmissionComplete()).isFalse();
        assertThat(s.canEnter(NOW)).isFalse();
    }

    @Test
    @DisplayName("둘 다 올리면 SUBMITTED — 자동 승인 경로는 없다")
    void 자동승인_없음() {
        ExamIdentitySession s = newSession();
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1");

        assertThat(s.getStatus()).isEqualTo(ExamIdentitySession.Status.SUBMITTED);
        assertThat(s.canEnter(NOW)).isFalse();
    }

    /* ---------- P0-3: 얼굴 사진 없이 최종 승인 차단 ---------- */

    @Test
    @DisplayName("[P0-3] 얼굴 확인용 사진이 없으면 승인할 수 없다")
    void 얼굴사진_없이_승인_차단() {
        ExamIdentitySession s = newSession();
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);

        assertThatThrownBy(() -> s.approve(null, NOW))
                .isInstanceOf(IdentitySessionStateException.class)
                .hasMessageContaining("얼굴");

        assertThat(s.getStatus()).isNotEqualTo(ExamIdentitySession.Status.APPROVED);
    }

    @Test
    @DisplayName("[P0-3] 신분증과 얼굴 사진이 모두 있어야 승인된다")
    void 둘다_있으면_승인() {
        ExamIdentitySession s = newSession();
        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1");
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
        s.approve(null, NOW);

        assertThat(s.getStatus()).isEqualTo(ExamIdentitySession.Status.APPROVED);
        assertThat(s.canEnter(NOW)).isTrue();
    }

    @Test
    @DisplayName("[P0-3] 승인 후에는 신분증도 얼굴 사진도 바꿀 수 없다")
    void 승인후_교체_차단() {
        ExamIdentitySession s = newSession();
        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1");
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
        s.approve(null, NOW);

        assertThatThrownBy(() -> s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW))
                .isInstanceOf(IdentitySessionStateException.class);
        assertThatThrownBy(() -> s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1"))
                .isInstanceOf(IdentitySessionStateException.class);
    }

    /* ---------- P0-4: 최종 반려 vs 재제출 요청 ---------- */

    @Test
    @DisplayName("[P0-4] 최종 반려 뒤에는 다시 제출할 수 없다")
    void 최종반려_후_제출차단() {
        ExamIdentitySession s = newSession();
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1");
        s.reject(null, "위조 의심", false, NOW);   /* requestResubmit=false = 최종 반려 */

        assertThat(s.getStatus()).isEqualTo(ExamIdentitySession.Status.REJECTED);
        assertThat(s.acceptsSubmission()).isFalse();
        assertThatThrownBy(() -> s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW))
                .isInstanceOf(IdentitySessionStateException.class);
    }

    @Test
    @DisplayName("[P0-4] 재제출 요청 상태에서만 다시 제출할 수 있다")
    void 재제출요청_후_제출허용() {
        ExamIdentitySession s = newSession();
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1");
        s.reject(null, "글자가 흐림", true, NOW);   /* requestResubmit=true */

        assertThat(s.getStatus()).isEqualTo(ExamIdentitySession.Status.RESUBMIT_REQUIRED);
        assertThat(s.acceptsSubmission()).isTrue();

        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
        assertThat(s.getStatus())
                .as("신분증만으로는 아직 완전 제출이 아니다")
                .isEqualTo(ExamIdentitySession.Status.RESUBMIT_REQUIRED);

        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1");
        assertThat(s.getStatus()).isEqualTo(ExamIdentitySession.Status.SUBMITTED);
    }

    @Test
    @DisplayName("[P0-4] 검토 대기·검토 중에는 파일을 교체할 수 없다")
    void 검토중_교체차단() {
        ExamIdentitySession s = newSession();
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1");

        assertThat(s.acceptsSubmission()).isFalse();
        assertThatThrownBy(() -> s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW))
                .isInstanceOf(IdentitySessionStateException.class);

        s.markUnderReview();
        assertThat(s.acceptsSubmission()).isFalse();
    }

    @Test
    @DisplayName("[P0-4] 재제출 횟수 상한을 넘으면 더 이상 재제출을 요청할 수 없다")
    void 재제출_횟수제한() {
        ExamIdentitySession s = newSession();
        for (int i = 0; i < ExamIdentitySession.MAX_RESUBMIT; i++) {
            s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
            s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1");
            s.reject(null, "다시", true, NOW);
        }
        assertThat(s.getResubmitCount()).isEqualTo(ExamIdentitySession.MAX_RESUBMIT);

        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1");
        assertThatThrownBy(() -> s.reject(null, "또 다시", true, NOW))
                .isInstanceOf(IdentitySessionStateException.class)
                .hasMessageContaining("초과");
    }

    @Test
    @DisplayName("반려 사유는 필수다")
    void 반려사유_필수() {
        ExamIdentitySession s = newSession();
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1");
        assertThat(s.isReviewable()).as("완전 제출이어야 사유 검증까지 도달한다").isTrue();

        assertThatThrownBy(() -> s.reject(null, "   ", true, NOW))
                .isInstanceOf(IdentitySessionStateException.class)
                .hasMessageContaining("사유");
    }

    /* ---------- 승인 만료 ---------- */

    @Test
    @DisplayName("승인 유효시간이 지나면 입장할 수 없다")
    void 승인_만료() {
        ExamIdentitySession s = newSession();
        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1");
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
        s.approve(null, NOW);

        LocalDateTime late = NOW.plusMinutes(ExamIdentitySession.APPROVAL_VALID_MINUTES + 1);
        assertThat(s.canEnter(late)).isFalse();
        assertThat(s.isApprovalExpired(late)).isTrue();
    }

    @Test
    @DisplayName("반려하면 이전 승인 흔적이 남지 않는다")
    void 반려시_승인흔적_제거() {
        ExamIdentitySession s = newSession();
        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "v1");
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);
        s.approve(null, NOW);
        s.requestResubmitAfterApproval(null, "얼굴이 흐림", NOW);

        assertThat(s.canEnter(NOW)).isFalse();
        assertThat(s.getApprovalExpiresAt()).isNull();
    }

    /* ---------- 동의 증거 ---------- */

    @Test
    @DisplayName("[P0-3] 얼굴 사진 제출 시 동의 시각과 문구 버전이 저장된다")
    void 동의증거_저장() {
        ExamIdentitySession s = newSession();
        s.attachFaceCheck(doc(s, ExamIdentityDocument.Kind.FACE_CHECK), NOW, "face-consent-v1");
        s.attachIdCard(doc(s, ExamIdentityDocument.Kind.ID_CARD), NOW);

        assertThat(s.getFaceConsentAt()).isEqualTo(NOW);
        assertThat(s.getFaceConsentVersion()).isEqualTo("face-consent-v1");
    }

    /* ---------- 검증 주기 번호 (P0-1) ---------- */

    @Test
    @DisplayName("[P0-1] 승인할 때마다 검증 주기 번호가 올라간다")
    void 승인횟수_증가() {
        ExamIdentitySession s = newSession();
        assertThat(s.getApprovalCount()).isZero();

        complete(s);
        s.approve(null, NOW);
        assertThat(s.getApprovalCount()).isEqualTo(1);

        /* 재제출 → 다시 완전 제출 → 두 번째 승인 */
        s.requestResubmitAfterApproval(null, "다시", NOW);
        complete(s);
        s.approve(null, NOW);
        assertThat(s.getApprovalCount())
                .as("각 응시가 몇 번째 검증을 통과했는지 구분하려면 값이 올라가야 한다").isEqualTo(2);
    }

    @Test
    @DisplayName("[P0-1] 재제출로 세션이 초기화돼도 승인 횟수는 되돌아가지 않는다")
    void 승인횟수_초기화되지않음() {
        ExamIdentitySession s = newSession();
        complete(s);
        s.approve(null, NOW);
        s.requestResubmitAfterApproval(null, "다시", NOW);

        assertThat(s.getApprovalCount())
                .as("초기화되면 1회차와 2회차 증거를 구분할 수 없다").isEqualTo(1);
        assertThat(s.getCurrentDocument()).isNull();
    }
}
