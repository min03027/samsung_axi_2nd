package com.ssa.lms.web.identity;

import com.ssa.lms.exam.entity.Exam;
import com.ssa.lms.exam.entity.ExamAttempt;
import com.ssa.lms.exam.repository.ExamAttemptRepository;
import com.ssa.lms.exam.service.ExamAttemptService;
import com.ssa.lms.exam.service.ExamTakeException;
import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.entity.ExamIdentityVerification;
import com.ssa.lms.identity.repository.ExamIdentitySessionRepository;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.identity.support.IdentityTestFixture;
import com.ssa.lms.proctor.entity.ExamEventLog;
import com.ssa.lms.proctor.repository.ExamEventLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * 서버 입장 게이트 (지적 2·3·4·8).
 *
 * <p>실패는 <b>정확한 코드</b>로 검증한다. isIn(...) 으로 여러 코드를 허용하면
 * "왜 막혔는지" 를 검증하지 않는 것과 같다.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class ExamStartGateTest {

    private static final String IDENTITY_REQUIRED = "IDENTITY_REQUIRED";

    @Autowired ExamAttemptService examAttemptService;
    @Autowired ExamIdentityService identityService;
    @Autowired ExamIdentitySessionRepository sessionRepository;
    @Autowired ExamAttemptRepository attemptRepository;
    @Autowired ExamEventLogRepository eventLogRepository;
    @Autowired IdentityTestFixture fixture;
    @Autowired com.ssa.lms.auth.IdentityVerificationService identityVerificationService;

    private static void assertCode(Throwable t, String expected) {
        assertThat(t).isInstanceOf(ExamTakeException.class);
        assertThat(((ExamTakeException) t).getCode())
                .as("실패 코드가 정확해야 한다").isEqualTo(expected);
    }

    /* ===================== 차단 경로 ===================== */

    @Test
    @DisplayName("[4] 승인 없이 시작하면 IDENTITY_REQUIRED 로 정확히 차단된다")
    void 승인없이_시작_차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        long before = attemptRepository.countByExamIdAndUserId(c.examId(), c.userId());

        Throwable t = catchThrowable(() -> examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId()));

        assertCode(t, IDENTITY_REQUIRED);
        assertThat(attemptRepository.countByExamIdAndUserId(c.examId(), c.userId()))
                .as("차단됐으면 attempt 가 생기지 않아야 한다").isEqualTo(before);
    }

    @Test
    @DisplayName("[4] precheckSessionId 누락도 IDENTITY_REQUIRED 로 차단된다")
    void 세션id_누락_차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");

        Throwable t = catchThrowable(() -> examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", null));

        assertCode(t, IDENTITY_REQUIRED);
        assertThat(t).hasMessageContaining("사전점검");
    }

    @Test
    @DisplayName("[4] 승인됐어도 다른 세션 ID 를 보내면 차단된다")
    void 세션id_불일치_차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession approved = fixture.approvedSession(c);

        Throwable t = catchThrowable(() -> examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", approved.getId() + 99999));

        assertCode(t, IDENTITY_REQUIRED);
        assertThat(t).hasMessageContaining("일치하지 않");
    }

    @Test
    @DisplayName("[4] 웹캠 점검이 오래되면 차단된다")
    void 웹캠_신선도_차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);
        fixture.staleWebcam(s.getId());

        Throwable t = catchThrowable(() -> examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId()));

        assertCode(t, IDENTITY_REQUIRED);
        assertThat(t).hasMessageContaining("웹캠");
    }

    @Test
    @DisplayName("[4] 승인이 만료되면 차단된다")
    void 승인_만료_차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);
        fixture.expireApproval(s.getId());

        Throwable t = catchThrowable(() -> examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId()));

        assertCode(t, IDENTITY_REQUIRED);
    }

    /* ===================== 성공 경로 (지적 2·3·8) ===================== */

    @Test
    @DisplayName("[2·3·8] 승인+얼굴+웹캠이 끝나면 credential 없이 시작에 성공하고 DB 에 실제로 저장된다")
    void 정상_시작_성공() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);

        /* credential 을 주지 않는다 — QR 승인이 본인확인 근거다 (지적 2) */
        Long attemptId = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());

        assertThat(attemptId).isNotNull();

        /* 지적 3 — 재조회해서 실제 DB 반영을 확인한다 */
        ExamAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(attempt.getExam().getId()).isEqualTo(c.examId());
        assertThat(attempt.getUser().getId()).isEqualTo(c.userId());
        assertThat(attempt.getIdentityVerifiedAt()).as("QR 승인이 본인확인 근거로 기록돼야 한다").isNotNull();
        assertThat(attempt.getIdentityVerifyMethod()).isEqualTo("ID_CARD_QR");

        /* ENTER 이벤트가 실제로 남았는가 */
        boolean hasEnter = eventLogRepository.findByAttemptIdOrderByOccurredAtAsc(attemptId).stream()
                .anyMatch(e -> e.getEventType() == ExamEventLog.EventType.ENTER);
        assertThat(hasEnter).as("ENTER 이벤트가 DB 에 있어야 한다").isTrue();

        /* 이 응시의 증거 스냅샷이 실제로 만들어졌는가 (P0-1 / LXP-016) */
        ExamIdentityVerification v = identityService.findByAttempt(attemptId)
                .orElseThrow(() -> new AssertionError("응시별 증거가 남지 않았다"));
        assertThat(v.getSession().getId()).isEqualTo(s.getId());
        assertThat(v.getCycleNo()).isEqualTo(1);
        assertThat(v.getApprovedAt()).isNotNull();
    }

    /* ===================== 회귀 ===================== */

    @Test
    @DisplayName("[P1-6] 비감독·비본인확인 시험은 credential 없이 실제로 시작에 성공한다")
    void 일반시험_실제_시작성공() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        Exam plain = fixture.startablePlainExam(c.userId(), false);

        /* 조건부 assertion 으로 성공을 대신하지 않는다 — start() 가 진짜 성공해야 한다. */
        Long attemptId = examAttemptService.start(
                plain.getId(), c.userId(), null, null, "127.0.0.1", "junit", null);

        assertThat(attemptId).as("일반 시험은 credential 없이 시작돼야 한다").isNotNull();

        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new AssertionError("회차가 DB 에 저장되지 않았다"));
        assertThat(attempt.getExam().getId()).isEqualTo(plain.getId());
        assertThat(attempt.getUser().getId()).isEqualTo(c.userId());

        boolean hasEnter = eventLogRepository.findByAttemptIdOrderByOccurredAtAsc(attemptId).stream()
                .anyMatch(e -> e.getEventType() == ExamEventLog.EventType.ENTER);
        assertThat(hasEnter).as("ENTER 이벤트가 남아야 한다").isTrue();

        /* 사전점검 증거는 만들어지지 않는다 — 게이트를 타지 않았기 때문이다. */
        assertThat(identityService.findByAttempt(attemptId))
                .as("게이트를 타지 않은 회차에 신분확인 증거가 붙으면 안 된다").isEmpty();
    }

    @Test
    @DisplayName("[P1-6] 비밀번호 본인인증 시험: 올바른 비밀번호면 시작에 성공한다")
    void 비밀번호시험_정답_성공() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        Exam pw = fixture.startablePlainExam(c.userId(), true);
        fixture.clearRecentIdentityVerification(c.userId());

        Long attemptId = examAttemptService.start(
                pw.getId(), c.userId(), "PASSWORD", "1234", "127.0.0.1", "junit", null);

        assertThat(attemptId).isNotNull();
        ExamAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(attempt.getIdentityVerifiedAt()).isNotNull();
        assertThat(attempt.getIdentityVerifyMethod())
                .as("QR 이 아니라 비밀번호로 인증됐어야 한다").isNotEqualTo("ID_CARD_QR");
    }

    @Test
    @DisplayName("[P1-6] 비밀번호 본인인증 시험: 틀린 비밀번호면 시작에 실패하고 회차가 생기지 않는다")
    void 비밀번호시험_오답_실패() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        Exam pw = fixture.startablePlainExam(c.userId(), true);
        /* 최근 인증 이력이 있으면 비밀번호를 보지 않고 통과한다 — 지우고 시작한다. */
        fixture.clearRecentIdentityVerification(c.userId());

        long before = attemptRepository.findByExamIdAndUserIdOrderByAttemptNoDesc(
                pw.getId(), c.userId()).size();

        Throwable t = catchThrowable(() -> examAttemptService.start(
                pw.getId(), c.userId(), "PASSWORD", "wrong-password", "127.0.0.1", "junit", null));

        assertThat(t).isInstanceOf(ExamTakeException.class);
        assertThat(((ExamTakeException) t).getCode()).isEqualTo(IDENTITY_REQUIRED);
        assertThat(attemptRepository.findByExamIdAndUserIdOrderByAttemptNoDesc(pw.getId(), c.userId()))
                .as("인증 실패 시 회차가 만들어지면 안 된다").hasSize((int) before);
    }

    @Test
    @DisplayName("[P1-6] 비밀번호 시험은 사전점검 대상이 아니다 — QR 흐름으로 새지 않는다")
    void 비밀번호시험_사전점검_비대상() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        Exam pw = fixture.startablePlainExam(c.userId(), true);

        assertThat(identityService.requiresPrecheck(pw.getId())).isFalse();
    }

    @Test
    @DisplayName("requiresPrecheck 는 감독+본인확인이 모두 켜진 시험만 대상으로 본다")
    void 사전점검_대상_판정() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        assertThat(identityService.requiresPrecheck(c.examId())).isTrue();
        assertThat(identityService.requiresPrecheck(fixture.plainExam().getId())).isFalse();
    }

    /* ===================== P0-B: 살아 있는 진행 회차 이어하기 =====================
       게이트가 진행 회차 조회보다 먼저 실행되면, 최초 입장 후 정상 응시 중이던 사람이
       네트워크 단절·새로고침으로 돌아왔을 때 승인·웹캠 시간이 지났다는 이유로
       <b>이미 진행 중인 회차까지</b> 막힌다. 새 회차에는 게이트가 계속 필요하다. */

    /** 이 시험·사용자의 전체 회차 수. */
    private int attemptCount(IdentityTestFixture.Ctx c) {
        return attemptRepository.findByExamIdAndUserIdOrderByAttemptNoDesc(c.examId(), c.userId()).size();
    }

    private boolean hasResume(Long attemptId) {
        return eventLogRepository.findByAttemptIdOrderByOccurredAtAsc(attemptId).stream()
                .anyMatch(e -> e.getEventType() == ExamEventLog.EventType.RESUME);
    }

    @Test
    @DisplayName("[P0-B/A] 승인·웹캠이 만료돼도 살아 있는 진행 회차는 같은 attempt 로 이어진다")
    void 만료후_진행회차_이어하기() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);

        /* 1~2) 최초 입장 */
        Long first = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());
        assertThat(first).isNotNull();
        int attemptsAfterFirst = attemptCount(c);
        int snapshotsAfterFirst = fixture.verificationCountOf(s.getId());
        ExamIdentityVerification snapshotBefore = identityService.findByAttempt(first).orElseThrow();
        Long snapshotId = snapshotBefore.getId();
        int cycleBefore = snapshotBefore.getCycleNo();
        java.time.LocalDateTime approvedAtBefore = snapshotBefore.getApprovedAt();

        /* 3) 승인과 웹캠 점검을 모두 만료 상태로 */
        fixture.expireApproval(s.getId());
        fixture.staleWebcam(s.getId());

        /* 4~5) 다시 start() — 같은 attempt 로 이어져야 한다 */
        Long again = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());
        assertThat(again)
                .as("응시 중이던 사람이 새로고침했다고 시험에서 쫓겨나면 안 된다")
                .isEqualTo(first);

        /* 6) 회차·검증 스냅샷이 늘지 않아야 한다 */
        assertThat(attemptCount(c)).as("이어하기가 새 회차를 만들면 안 된다").isEqualTo(attemptsAfterFirst);
        assertThat(fixture.verificationCountOf(s.getId()))
                .as("이어하기가 새 검증 주기를 만들면 안 된다").isEqualTo(snapshotsAfterFirst);

        /* 7) 기존 스냅샷이 변경되지 않아야 한다 */
        ExamIdentityVerification snapshotAfter = identityService.findByAttempt(first).orElseThrow();
        assertThat(snapshotAfter.getId()).isEqualTo(snapshotId);
        assertThat(snapshotAfter.getCycleNo()).isEqualTo(cycleBefore);
        assertThat(snapshotAfter.getApprovedAt())
                .as("최초 입장 때의 승인 시각이 이어하기로 덮어써지면 안 된다").isEqualTo(approvedAtBefore);

        /* 8) RESUME 이벤트 */
        assertThat(hasResume(first)).as("이어하기는 RESUME 으로 기록돼야 한다").isTrue();
    }

    @Test
    @DisplayName("[P0-B/A] 이어하기는 precheckSessionId 가 null 이어도 동작한다")
    void 이어하기_세션id_없어도_동작() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);

        Long first = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());
        fixture.expireApproval(s.getId());
        fixture.staleWebcam(s.getId());

        /* 이어하기는 최초 입장 때 저장된 근거로 판단하므로 사전점검 세션 id 가 없어도 된다. */
        Long again = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", null);

        assertThat(again).isEqualTo(first);
        assertThat(fixture.verificationCountOf(s.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("[P0-B/B] 진행 회차가 없으면 만료된 승인으로 새 회차를 만들 수 없다")
    void 만료후_새회차는_차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);

        /* 진행 중 회차를 만들지 않은 채 승인만 만료시킨다. */
        fixture.expireApproval(s.getId());
        int attemptsBefore = attemptCount(c);
        int snapshotsBefore = fixture.verificationCountOf(s.getId());

        Throwable t = catchThrowable(() -> examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId()));

        assertCode(t, IDENTITY_REQUIRED);
        assertThat(attemptCount(c)).as("차단됐으면 회차가 생기면 안 된다").isEqualTo(attemptsBefore);
        assertThat(fixture.verificationCountOf(s.getId()))
                .as("차단됐으면 검증 스냅샷이 생기면 안 된다").isEqualTo(snapshotsBefore);
    }

    @Test
    @DisplayName("[P0-B/B] 웹캠 점검만 오래돼도 새 회차는 차단된다")
    void 웹캠만_만료돼도_새회차_차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);
        fixture.staleWebcam(s.getId());

        int attemptsBefore = attemptCount(c);

        Throwable t = catchThrowable(() -> examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId()));

        assertCode(t, IDENTITY_REQUIRED);
        assertThat(attemptCount(c)).isEqualTo(attemptsBefore);
    }

    @Test
    @DisplayName("[P0-B/C] 제출로 끝난 회차는 이어가지 않고 새 사전점검을 요구한다")
    void 종료회차는_이어가지_않음() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);

        Long first = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());
        fixture.finishAttempt(first);           /* 제출로 종료 */
        fixture.expireApproval(s.getId());      /* 유효한 새 사전점검 없음 */

        Throwable t = catchThrowable(() -> examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId()));

        assertCode(t, IDENTITY_REQUIRED);
        assertThat(attemptRepository.findById(first).orElseThrow().getStatus())
                .as("종료된 회차가 되살아나면 안 된다")
                .isNotEqualTo(ExamAttempt.AttemptStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("[P0-B/C] 시간이 지나 만료된 진행 회차는 이어가지 않고 새 회차에 게이트가 걸린다")
    void 만료된_진행회차는_이어가지_않음() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);

        Long first = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());
        /* 응시 제한 시간이 지나 회차 자체가 만료된 상황 */
        fixture.expireAttempt(first);
        fixture.expireApproval(s.getId());

        Throwable t = catchThrowable(() -> examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId()));

        /* 핵심 요구: 만료된 회차를 <b>이어주지 않는다</b>. 새 회차로 취급해 게이트가 걸린다. */
        assertCode(t, IDENTITY_REQUIRED);
        assertThat(t).as("만료된 회차 id 를 그대로 돌려주면 안 된다").isNotNull();

        /* 이 상태에서 다시 시도해도 여전히 이어지지 않는다. */
        assertCode(catchThrowable(() -> examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId())), IDENTITY_REQUIRED);

        /* 관찰된 실제 동작을 숨기지 않고 고정한다.
           start() 는 하나의 트랜잭션이라 finish(AUTO_SUBMITTED) 가 실행돼도
           뒤이은 게이트 예외로 함께 롤백되어 상태가 IN_PROGRESS 로 남는다.
           이는 게이트가 앞에 있던 수정 전에도 동일했다(그때는 finish 에 도달조차 못 했다).
           이번 변경으로 생긴 회귀가 아니며, 정리 시점을 바꾸는 것은 이번 범위가 아니다. */
        assertThat(attemptRepository.findById(first).orElseThrow().getStatus())
                .as("게이트 실패로 롤백되므로 닫힘 처리가 커밋되지 않는다 (기존 동작)")
                .isEqualTo(ExamAttempt.AttemptStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("[P0-B/C] 만료된 진행 회차는 게이트를 통과하면 자동 제출로 닫히고 새 회차가 열린다")
    void 만료된_진행회차_게이트통과시_새회차() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);

        Long first = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());
        fixture.expireAttempt(first);

        /* 승인이 아직 유효하므로 게이트를 통과한다 → 만료 회차가 닫히고 새 회차가 만들어진다. */
        Long second = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());

        assertThat(second).as("만료된 회차를 이어주면 안 된다").isNotEqualTo(first);
        assertThat(attemptRepository.findById(first).orElseThrow().getStatus())
                .as("만료된 회차는 자동 제출로 닫힌다")
                .isEqualTo(ExamAttempt.AttemptStatus.AUTO_SUBMITTED);
        assertThat(identityService.findByAttempt(second))
                .as("새 회차에는 자기 검증 증거가 생긴다").isPresent();
    }

    @Test
    @DisplayName("[P0-B] 다른 사용자의 진행 회차는 이어받을 수 없다")
    void 타인_진행회차_재사용_불가() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);
        Long first = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());

        Long outsider = fixture.userNotEnrolledIn(c.examId());

        Throwable t = catchThrowable(() -> examAttemptService.start(
                c.examId(), outsider, null, null, "127.0.0.1", "junit", s.getId()));

        assertThat(t).as("남의 진행 회차를 이어받으면 안 된다").isNotNull();
        assertThat(attemptRepository.findById(first).orElseThrow().getUser().getId())
                .isEqualTo(c.userId());
    }

    /* ===================== P1: 이어하기가 본인확인 시각을 덮어쓰지 않는가 =====================
       QR 시험 이어하기는 신분증을 다시 검토하지도, 게이트를 다시 통과하지도 않는다.
       그런데도 markIdentityVerified(now) 로 현재 시각을 찍으면
       ① 실제 최초 본인확인 시각이 사라지고
       ② 재검증하지 않았는데 방금 검증한 것처럼 감사 데이터가 남고
       ③ 이어갈 때마다 시각이 갱신돼 증거 의미가 훼손된다.
       6차 테스트는 스냅샷 approvedAt 만 봤기 때문에 이 문제를 놓쳤다. */

    @Test
    @DisplayName("[P1] QR 시험 이어하기는 identityVerifiedAt · identityVerifyMethod 를 덮어쓰지 않는다")
    void 이어하기_본인확인시각_불변() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);

        /* 1~2) 최초 입장 후 감사 값 기록 */
        Long first = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());
        ExamAttempt before = attemptRepository.findById(first).orElseThrow();
        java.time.LocalDateTime verifiedAtBefore = before.getIdentityVerifiedAt();
        String methodBefore = before.getIdentityVerifyMethod();
        assertThat(verifiedAtBefore).as("최초 입장에서 본인확인 시각이 찍혀야 한다").isNotNull();
        assertThat(methodBefore).isEqualTo("ID_CARD_QR");

        int snapshotsBefore = fixture.verificationCountOf(s.getId());
        java.time.LocalDateTime approvedAtBefore =
                identityService.findByAttempt(first).orElseThrow().getApprovedAt();

        /* 3) 승인·웹캠 만료 */
        fixture.expireApproval(s.getId());
        fixture.staleWebcam(s.getId());

        /* 시각 비교가 의미를 갖도록 최소한의 간격을 둔다. */
        Thread.sleep(20);

        /* 4~5) 이어하기 — precheckSessionId 없이 */
        Long again = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", null);
        assertThat(again).isEqualTo(first);

        /* 6~7) 감사 값이 <b>정확히</b> 그대로여야 한다 */
        ExamAttempt after = attemptRepository.findById(first).orElseThrow();
        assertThat(after.getIdentityVerifiedAt())
                .as("재검증하지 않았는데 본인확인 시각이 갱신되면 감사 데이터가 거짓이 된다")
                .isEqualTo(verifiedAtBefore);
        assertThat(after.getIdentityVerifyMethod())
                .as("본인확인 수단도 그대로여야 한다")
                .isEqualTo(methodBefore);

        /* 8) 스냅샷 수·approvedAt 불변 */
        assertThat(fixture.verificationCountOf(s.getId())).isEqualTo(snapshotsBefore);
        assertThat(identityService.findByAttempt(first).orElseThrow().getApprovedAt())
                .isEqualTo(approvedAtBefore);

        /* 9) RESUME 이벤트 */
        assertThat(hasResume(first)).isTrue();
    }

    @Test
    @DisplayName("[P1] 여러 번 이어가도 본인확인 시각이 계속 최초 값으로 유지된다")
    void 반복_이어하기_시각_유지() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);

        Long first = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());
        java.time.LocalDateTime original =
                attemptRepository.findById(first).orElseThrow().getIdentityVerifiedAt();

        fixture.expireApproval(s.getId());
        fixture.staleWebcam(s.getId());

        /* 세 번 이어간다 — 매번 갱신되면 증거로서 의미가 없다. */
        for (int i = 0; i < 3; i++) {
            Thread.sleep(20);
            Long again = examAttemptService.start(
                    c.examId(), c.userId(), null, null, "127.0.0.1", "junit", null);
            assertThat(again).isEqualTo(first);
            assertThat(attemptRepository.findById(first).orElseThrow().getIdentityVerifiedAt())
                    .as("%d번째 이어하기에서 시각이 바뀌었다", i + 1)
                    .isEqualTo(original);
        }
    }

    @Test
    @DisplayName("[P1] 비밀번호 인증 시험의 이어하기는 기존 갱신 동작을 유지한다")
    void 비밀번호시험_이어하기_기존동작_유지() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        Exam pw = fixture.startablePlainExam(c.userId(), true);
        fixture.clearRecentIdentityVerification(c.userId());

        /* ---------- 1) 최초 비밀번호 인증 입장 ---------- */
        Long first = examAttemptService.start(
                pw.getId(), c.userId(), "PASSWORD", "1234", "127.0.0.1", "junit", null);
        assertThat(first).isNotNull();

        ExamAttempt before = attemptRepository.findById(first).orElseThrow();
        java.time.LocalDateTime verifiedAtFirst = before.getIdentityVerifiedAt();
        assertThat(verifiedAtFirst).as("최초 입장에서 본인확인 시각이 찍혀야 한다").isNotNull();
        assertThat(before.getIdentityVerifyMethod())
                .as("비밀번호를 실제로 확인했으므로 수단은 PASSWORD 여야 한다")
                .isEqualTo("PASSWORD");

        /* ---------- 2) 최근 인증 이력 조회 ---------- */
        java.util.Optional<java.time.LocalDateTime> lastVerified =
                identityVerificationService.lastVerifiedAt(c.userId());
        assertThat(lastVerified)
                .as("비밀번호 확인에 성공했으면 최근 인증 이력이 남아야 한다").isPresent();
        java.time.LocalDateTime lastVerifiedAt = lastVerified.orElseThrow();

        /* ---------- 3) 유효기간 안에서 credential 없이 이어하기 ---------- */
        /* 비감독 시험은 게이트를 타지 않으므로 기존 verifyIdentity 경로 그대로다.
           최근 인증(기본 30분)이 살아 있으므로 비밀번호를 다시 묻지 않는다.
           대기 없이 곧바로 호출한다 — 유효기간 안이면 시각 경과는 판정에 영향이 없다. */
        java.time.LocalDateTime beforeResume = java.time.LocalDateTime.now();
        assertThat(lastVerifiedAt)
                .as("최근 인증 시각이 이어하기 시점보다 앞서야 아래 동일성 단언이 의미를 갖는다 "
                        + "— 코드가 현재 시각을 새로 찍으면 반드시 값이 달라진다")
                .isBefore(beforeResume);

        Long again = examAttemptService.start(
                pw.getId(), c.userId(), null, null, "127.0.0.1", "junit", null);

        /* ---------- 4) 이어하기 후 단언 ---------- */
        assertThat(again).as("같은 회차로 이어져야 한다").isEqualTo(first);

        ExamAttempt after = attemptRepository.findById(first).orElseThrow();
        assertThat(after.getIdentityVerifiedAt())
                .as("이어하기는 최근 인증 이력의 시각을 그대로 기록해야 한다 — "
                        + "현재 시각을 새로 찍으면 인증하지 않은 시점이 인증 시각이 된다")
                .isEqualTo(lastVerifiedAt);
        assertThat(after.getIdentityVerifyMethod())
                .as("비밀번호를 다시 묻지 않고 최근 이력으로 통과했으므로 수단은 RECENT 여야 한다")
                .isEqualTo("RECENT");
        assertThat(hasResume(first)).as("이어하기는 RESUME 으로 기록돼야 한다").isTrue();

        /* 최근 인증 이력 자체는 이어하기로 갱신되지 않는다 — 재인증한 적이 없기 때문이다. */
        assertThat(identityVerificationService.lastVerifiedAt(c.userId()).orElseThrow())
                .as("이어하기가 최근 인증 시각을 갱신하면 재인증한 것처럼 보인다")
                .isEqualTo(lastVerifiedAt);
    }
}
