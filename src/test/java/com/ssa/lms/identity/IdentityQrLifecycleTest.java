package com.ssa.lms.identity;

import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.entity.ExamIdentityToken;
import com.ssa.lms.identity.entity.IdentitySessionStateException;
import com.ssa.lms.identity.repository.ExamIdentitySessionRepository;
import com.ssa.lms.identity.repository.ExamIdentityTokenRepository;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.identity.support.IdentityTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * QR 만료 → 재발급 계약 (P1-5).
 *
 * <p>사용자 실기 QA 관찰: "QR 시간이 만료되면 QR 이미지가 사라지고 만료된 QR 은 더 이상
 * 사용할 수 없었다." 이 문장만으로는 <b>재발급 버그</b>인지 <b>정상 동작</b>인지 알 수 없다.
 * 두 경우를 나눠 각각 검증한다.</p>
 *
 * <ol>
 *   <li>신분증 제출 <b>전</b> 만료 → 새 QR 을 받을 수 있어야 한다</li>
 *   <li>제출 <b>후</b> → 업로드용 QR 은 더 발급되지 않는다(발급할 이유가 없다)</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("local")
class IdentityQrLifecycleTest {

    @Autowired ExamIdentityService identityService;
    @Autowired ExamIdentitySessionRepository sessionRepository;
    @Autowired ExamIdentityTokenRepository tokenRepository;
    @Autowired IdentityTestFixture fixture;

    private ExamIdentitySession open(IdentityTestFixture.Ctx c) {
        return identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
    }

    @Test
    @DisplayName("[P1-5] 제출 전 만료: 같은 PENDING 세션에서 새 QR 을 재발급할 수 있고 이전 토큰은 계속 막힌다")
    void 제출전_만료_재발급() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = open(c);
        assertThat(s.getStatus()).isEqualTo(ExamIdentitySession.Status.PENDING);

        /* 1) PENDING 세션에서 QR 발급 */
        ExamIdentityService.IssuedToken first =
                identityService.issueToken(s.getId(), c.userId(), "127.0.0.1");
        assertThat(first.rawToken()).isNotBlank();

        /* 2) 만료 처리 */
        expire(first.rawToken());

        /* 3) 만료 토큰으로는 페이지도 업로드도 안 된다 */
        assertThat(identityService.describeMobile(first.rawToken()).blocked())
                .as("만료 토큰으로 모바일 화면이 열리면 안 된다").isTrue();
        assertThatThrownBy(() -> identityService.submitIdCard(
                first.rawToken(), IdentityTestFixture.imageFile("id.jpg"), "127.0.0.1"))
                .isInstanceOf(IdentitySessionStateException.class);

        /* 4) 같은 세션에서 새 QR 재발급 성공 */
        ExamIdentityService.IssuedToken second =
                identityService.issueToken(s.getId(), c.userId(), "127.0.0.1");
        assertThat(second.rawToken()).isNotEqualTo(first.rawToken());

        /* 5) 새 토큰은 사용 가능 */
        assertThat(identityService.describeMobile(second.rawToken()).blocked()).isFalse();
        identityService.submitIdCard(second.rawToken(),
                IdentityTestFixture.imageFile("id.jpg"), "127.0.0.1");
        assertThat(fixture.currentIdDocumentId(s.getId())).isNotNull();

        /* 6) 이전 토큰은 여전히 차단 */
        assertThat(identityService.describeMobile(first.rawToken()).blocked()).isTrue();
    }

    @Test
    @DisplayName("[P1-5] 새 QR 을 발급하면 아직 살아 있던 이전 토큰이 즉시 폐기된다")
    void 재발급시_이전토큰_폐기() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = open(c);

        ExamIdentityService.IssuedToken first =
                identityService.issueToken(s.getId(), c.userId(), "127.0.0.1");
        assertThat(identityService.describeMobile(first.rawToken()).blocked())
                .as("아직 만료되지 않았으므로 살아 있어야 한다").isFalse();

        ExamIdentityService.IssuedToken second =
                identityService.issueToken(s.getId(), c.userId(), "127.0.0.1");

        assertThat(identityService.describeMobile(first.rawToken()).blocked())
                .as("재발급하면 이전 QR 은 즉시 못 쓰게 해야 한다 — URL 이 노출돼도 한 장만 유효").isTrue();
        assertThat(identityService.describeMobile(second.rawToken()).blocked()).isFalse();

        assertThatThrownBy(() -> identityService.submitIdCard(
                first.rawToken(), IdentityTestFixture.imageFile("id.jpg"), "127.0.0.1"))
                .isInstanceOf(IdentitySessionStateException.class);
        assertThat(fixture.currentIdDocumentId(s.getId())).isNull();
    }

    @Test
    @DisplayName("[P1-5] SUBMITTED 상태에서는 새 업로드 QR 을 발급하지 않는다")
    void 제출완료_발급차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = open(c);
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        assertThat(sessionRepository.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamIdentitySession.Status.SUBMITTED);

        assertThatThrownBy(() -> identityService.issueToken(s.getId(), c.userId(), "127.0.0.1"))
                .isInstanceOf(IdentitySessionStateException.class);
    }

    @Test
    @DisplayName("[P1-5] UNDER_REVIEW 상태에서는 새 업로드 QR 을 발급하지 않는다")
    void 검토중_발급차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = open(c);
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        identityService.openReview(s.getId(), c.adminId(), "ADMIN", "127.0.0.1");
        assertThat(sessionRepository.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamIdentitySession.Status.UNDER_REVIEW);

        assertThatThrownBy(() -> identityService.issueToken(s.getId(), c.userId(), "127.0.0.1"))
                .isInstanceOf(IdentitySessionStateException.class);
    }

    @Test
    @DisplayName("[P1-5] APPROVED 상태에서는 새 업로드 QR 을 발급하지 않는다")
    void 승인후_발급차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        open(c);
        ExamIdentitySession s = fixture.approvedSession(c);
        assertThat(s.getStatus()).isEqualTo(ExamIdentitySession.Status.APPROVED);

        assertThatThrownBy(() -> identityService.issueToken(s.getId(), c.userId(), "127.0.0.1"))
                .isInstanceOf(IdentitySessionStateException.class);
    }

    @Test
    @DisplayName("[P1-5] 재제출 요청을 받으면 QR 을 다시 발급받을 수 있다 — 영구 차단이 아니다")
    void 재제출요청후_재발급가능() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = open(c);
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        identityService.reject(s.getId(), c.adminId(), "ADMIN", "다시 촬영", true, "127.0.0.1");

        assertThat(sessionRepository.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamIdentitySession.Status.RESUBMIT_REQUIRED);

        ExamIdentityService.IssuedToken t =
                identityService.issueToken(s.getId(), c.userId(), "127.0.0.1");
        assertThat(identityService.describeMobile(t.rawToken()).blocked()).isFalse();
    }

    @Test
    @DisplayName("[P1-5] 최종 반려 상태에서는 QR 을 발급하지 않는다")
    void 최종반려_발급차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = open(c);
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        identityService.reject(s.getId(), c.adminId(), "ADMIN", "본인 아님", false, "127.0.0.1");

        assertThatThrownBy(() -> identityService.issueToken(s.getId(), c.userId(), "127.0.0.1"))
                .isInstanceOf(IdentitySessionStateException.class);
    }

    private void expire(String rawToken) {
        ExamIdentityToken t = tokenRepository.findByTokenHash(sha256(rawToken)).orElseThrow();
        ReflectionTestUtils.setField(t, "expiresAt", LocalDateTime.now().minusSeconds(1));
        tokenRepository.saveAndFlush(t);
    }

    private static String sha256(String v) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(v.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
