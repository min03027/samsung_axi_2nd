package com.ssa.lms.identity;

import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.entity.IdentitySessionStateException;
import com.ssa.lms.identity.repository.ExamIdentitySessionRepository;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.identity.support.IdentityTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * 신분증·얼굴 사진 제출 <b>순서 독립</b> (지적 2).
 *
 * <p><b>왜 문제였나</b><br>
 * 화면은 STEP 1(QR 신분증) → STEP 2(웹캠 얼굴) 순서로 보인다. 그런데 서버는 신분증이 들어오는
 * 즉시 {@code SUBMITTED} 로 바꾸고, 얼굴 사진은 {@code PENDING}/{@code RESUBMIT_REQUIRED} 에서만
 * 받았다. 즉 <b>화면에 적힌 순서대로 하면 얼굴 사진이 거부된다.</b>
 * 이전 테스트는 얼굴 → 신분증 순서만 만들어서 이 구멍을 놓쳤다.</p>
 *
 * <p>여기서는 <b>실제 서비스 공개 메서드를 사용자 순서대로</b> 호출한다.
 * ReflectionTestUtils 로 상태를 주입하지 않는다.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class IdentitySubmissionOrderTest {

    @Autowired ExamIdentityService identityService;
    @Autowired ExamIdentitySessionRepository sessionRepository;
    @Autowired IdentityTestFixture fixture;

    private ExamIdentitySession reload(Long id) {
        return sessionRepository.findById(id).orElseThrow();
    }

    /* ===================== 순서 독립 ===================== */

    @Test
    @DisplayName("[2] 신분증 먼저 → 얼굴 사진 → SUBMITTED 전환")
    void 신분증_먼저() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");

        fixture.uploadIdCard(s.getId());
        assertThat(reload(s.getId()).getStatus())
                .as("신분증만 들어왔으면 아직 검토 대기가 아니다").isEqualTo(ExamIdentitySession.Status.PENDING);

        fixture.uploadFaceCheck(s.getId());
        assertThat(reload(s.getId()).getStatus())
                .as("둘 다 들어와야 검토 대기").isEqualTo(ExamIdentitySession.Status.SUBMITTED);
    }

    @Test
    @DisplayName("[2] 얼굴 사진 먼저 → 신분증 → SUBMITTED 전환")
    void 얼굴_먼저() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");

        fixture.uploadFaceCheck(s.getId());
        assertThat(reload(s.getId()).getStatus()).isEqualTo(ExamIdentitySession.Status.PENDING);

        fixture.uploadIdCard(s.getId());
        assertThat(reload(s.getId()).getStatus()).isEqualTo(ExamIdentitySession.Status.SUBMITTED);
    }

    /* ===================== 일부 제출 승인 차단 ===================== */

    @Test
    @DisplayName("[2] 신분증만 제출한 상태에서는 승인할 수 없다")
    void 신분증만_승인차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());

        assertThatThrownBy(() -> identityService.approve(s.getId(), c.adminId(), "ADMIN", "127.0.0.1"))
                .isInstanceOf(IdentitySessionStateException.class);
    }

    @Test
    @DisplayName("[2] 얼굴 사진만 제출한 상태에서는 승인할 수 없다")
    void 얼굴만_승인차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadFaceCheck(s.getId());

        assertThatThrownBy(() -> identityService.approve(s.getId(), c.adminId(), "ADMIN", "127.0.0.1"))
                .isInstanceOf(IdentitySessionStateException.class);
    }

    @Test
    @DisplayName("[2] 일부 제출 상태에서는 검토 시작도 할 수 없다")
    void 일부제출_검토시작_차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());

        identityService.openReview(s.getId(), c.adminId(), "ADMIN", "127.0.0.1");
        assertThat(reload(s.getId()).getStatus())
                .as("완전 제출이 아니면 검토 중으로 바뀌면 안 된다")
                .isEqualTo(ExamIdentitySession.Status.PENDING);
    }

    /* ===================== 완전 제출 후 교체 차단 ===================== */

    @Test
    @DisplayName("[2] SUBMITTED 이후에는 신분증을 교체할 수 없다")
    void SUBMITTED_신분증_교체차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());

        assertThatThrownBy(() -> fixture.uploadIdCard(s.getId()))
                .isInstanceOf(IdentitySessionStateException.class);
    }

    @Test
    @DisplayName("[2] SUBMITTED 이후에는 얼굴 사진을 교체할 수 없다")
    void SUBMITTED_얼굴_교체차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadFaceCheck(s.getId());
        fixture.uploadIdCard(s.getId());

        assertThatThrownBy(() -> fixture.uploadFaceCheck(s.getId()))
                .isInstanceOf(IdentitySessionStateException.class);
    }

    /* ===================== 재제출 ===================== */

    @Test
    @DisplayName("[2] 재제출 요청 직후 신분증·얼굴·동의·웹캠 포인터가 모두 비워진다")
    void 재제출_전체초기화() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        identityService.markWebcamChecked(s.getId(), c.userId());
        identityService.reject(s.getId(), c.adminId(), "ADMIN", "다시 촬영", true, "127.0.0.1");

        ExamIdentitySession r = reload(s.getId());
        assertThat(r.getStatus()).isEqualTo(ExamIdentitySession.Status.RESUBMIT_REQUIRED);
        assertThat(r.getCurrentDocument()).as("과거 신분증을 재사용하면 안 된다").isNull();
        assertThat(r.getFaceCheckDocument()).isNull();
        assertThat(r.getFaceConsentAt()).isNull();
        assertThat(r.getFaceConsentVersion()).isNull();
        assertThat(r.getWebcamCheckedAt()).isNull();
        assertThat(r.getApprovalExpiresAt()).isNull();
    }

    @Test
    @DisplayName("[2] 재제출 후 얼굴만 다시 올려도 승인할 수 없다")
    void 재제출_얼굴만_승인불가() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        identityService.reject(s.getId(), c.adminId(), "ADMIN", "다시", true, "127.0.0.1");

        fixture.uploadFaceCheck(s.getId());

        assertThat(reload(s.getId()).getStatus())
                .as("신분증이 없으므로 아직 완전 제출이 아니다")
                .isEqualTo(ExamIdentitySession.Status.RESUBMIT_REQUIRED);
        assertThatThrownBy(() -> identityService.approve(s.getId(), c.adminId(), "ADMIN", "127.0.0.1"))
                .isInstanceOf(IdentitySessionStateException.class);
    }

    @Test
    @DisplayName("[2] 재제출 후 신분증만 다시 올려도 승인할 수 없다")
    void 재제출_신분증만_승인불가() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        identityService.reject(s.getId(), c.adminId(), "ADMIN", "다시", true, "127.0.0.1");

        fixture.uploadIdCard(s.getId());

        assertThat(reload(s.getId()).getStatus()).isEqualTo(ExamIdentitySession.Status.RESUBMIT_REQUIRED);
        assertThatThrownBy(() -> identityService.approve(s.getId(), c.adminId(), "ADMIN", "127.0.0.1"))
                .isInstanceOf(IdentitySessionStateException.class);
    }

    @Test
    @DisplayName("[2] 재제출 후 신분증과 얼굴을 모두 새로 올리면 다시 SUBMITTED 가 된다")
    void 재제출_둘다_다시제출() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        Long oldIdDoc = reload(s.getId()).getCurrentDocument().getId();
        identityService.reject(s.getId(), c.adminId(), "ADMIN", "다시", true, "127.0.0.1");

        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());

        ExamIdentitySession r = reload(s.getId());
        assertThat(r.getStatus()).isEqualTo(ExamIdentitySession.Status.SUBMITTED);
        assertThat(r.getCurrentDocument().getId())
                .as("과거 신분증이 아니라 새 문서여야 한다").isNotEqualTo(oldIdDoc);

        /* 완전 제출이므로 이제 승인된다 */
        identityService.approve(s.getId(), c.adminId(), "ADMIN", "127.0.0.1");
        assertThat(reload(s.getId()).getStatus()).isEqualTo(ExamIdentitySession.Status.APPROVED);
    }
}
