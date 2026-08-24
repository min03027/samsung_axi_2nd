package com.ssa.lms.web.identity;

import com.ssa.lms.exam.service.ExamAttemptService;
import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.entity.ExamIdentityVerification;
import com.ssa.lms.identity.repository.ExamIdentityDocumentRepository;
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
 * 재응시 시 각 응시가 <b>자신이 통과한 검증 증거</b>에 연결되는가 (P0-1).
 *
 * <p><b>무엇이 문제였나</b><br>
 * 세션은 {@code (exam,user)} 당 한 행이고, 승인 만료 후 {@code reopen()} 하면 승인 시각·문서
 * 포인터가 초기화된다. 그런데 attempt 링크는 세션에 <b>하나뿐</b>이었고
 * {@code linkAttemptToSession()} 은 이미 링크가 있으면 새 attempt 를 <b>조용히 무시</b>했다.
 * 그래서 2회차 응시는 검증 기록이 남지 않고, 1회차 링크는 남았는데 그 링크가 가리키는 세션의
 * 자료는 2회차 것으로 바뀌어 있었다. LXP-016 사후 감사가 양쪽 모두 거짓이 된다.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class IdentityRetakeEvidenceTest {

    @Autowired ExamIdentityService identityService;
    @Autowired ExamAttemptService examAttemptService;
    @Autowired ExamIdentitySessionRepository sessionRepository;
    @Autowired ExamIdentityDocumentRepository documentRepository;
    @Autowired IdentityTestFixture fixture;

    @Test
    @DisplayName("[P0-1] 두 번 응시하면 각 회차가 자기 검증 증거에 연결되고 1회차 기록이 보존된다")
    void 재응시_증거_분리보존() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();

        /* ---------- 1) 첫 검증 자료 제출·승인 ---------- */
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s1 = fixture.approvedSession(c);
        Long sessionId = s1.getId();
        Long firstIdDoc = fixture.currentIdDocumentId(sessionId);
        assertThat(firstIdDoc).isNotNull();

        /* ---------- 2) 첫 attempt 생성 및 연결 ---------- */
        Long attemptA = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", sessionId);
        assertThat(attemptA).isNotNull();

        ExamIdentityVerification vA = identityService.findByAttempt(attemptA)
                .orElseThrow(() -> new AssertionError("1회차 증거가 남지 않았다"));
        assertThat(vA.getCycleNo()).isEqualTo(1);
        assertThat(vA.getIdDocument().getId()).isEqualTo(firstIdDoc);
        assertThat(vA.getApprovedAt()).isNotNull();

        /* ---------- 3) 첫 attempt 종료 ---------- */
        fixture.finishAttempt(attemptA);

        /* ---------- 4) 재응시를 위한 새 검증 주기 ---------- */
        fixture.expireApproval(sessionId);
        ExamIdentitySession reopened = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        assertThat(reopened.getId()).as("같은 행을 다시 연다").isEqualTo(sessionId);
        assertThat(reopened.getStatus()).isEqualTo(ExamIdentitySession.Status.PENDING);
        assertThat(reopened.getCurrentDocument()).as("세션의 현재 자료는 초기화된다").isNull();

        /* ---------- 5) 두 번째 승인 ---------- */
        ExamIdentitySession s2 = fixture.approvedSession(c);
        Long secondIdDoc = fixture.currentIdDocumentId(sessionId);
        assertThat(secondIdDoc).as("2회차는 새 문서여야 한다").isNotEqualTo(firstIdDoc);
        assertThat(s2.getApprovalCount()).isEqualTo(2);

        /* ---------- 6) 두 번째 attempt 생성 ---------- */
        Long attemptB = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", sessionId);
        assertThat(attemptB).as("재응시 회차가 새로 만들어져야 한다").isNotEqualTo(attemptA);

        /* ---------- 7) 두 attempt 가 각각 자기 증거에 연결 ---------- */
        ExamIdentityVerification vB = identityService.findByAttempt(attemptB)
                .orElseThrow(() -> new AssertionError("2회차 증거가 남지 않았다 — 조용히 무시된 결함"));
        assertThat(vB.getId()).isNotEqualTo(vA.getId());
        assertThat(vB.getCycleNo()).isEqualTo(2);
        assertThat(vB.getIdDocument().getId()).isEqualTo(secondIdDoc);

        /* ---------- 8) 1회차 기록·문서 보존 ---------- */
        ExamIdentityVerification vAAgain = identityService.findByAttempt(attemptA).orElseThrow();
        assertThat(vAAgain.getCycleNo()).as("2회차 검증이 1회차 기록을 덮어쓰면 안 된다").isEqualTo(1);
        assertThat(vAAgain.getIdDocument().getId())
                .as("1회차 증거는 여전히 1회차 신분증을 가리켜야 한다").isEqualTo(firstIdDoc);
        assertThat(vAAgain.getApprovedAt()).isEqualTo(vA.getApprovedAt());
        assertThat(documentRepository.findById(firstIdDoc))
                .as("1회차 문서 행이 지워지면 감사 이력이 사라진다").isPresent();

        /* ---------- 9) findByAttempt 가 각각 정확한 기록을 반환 ---------- */
        assertThat(identityService.findByAttempt(attemptA).orElseThrow().getId()).isEqualTo(vA.getId());
        assertThat(identityService.findByAttempt(attemptB).orElseThrow().getId()).isEqualTo(vB.getId());
        assertThat(vA.getIdDocument().getId()).isNotEqualTo(vB.getIdDocument().getId());
        assertThat(vA.getFaceDocument().getId()).isNotEqualTo(vB.getFaceDocument().getId());
    }

    @Test
    @DisplayName("[P0-1] 같은 attempt 를 두 번 연결해도 증거는 하나다 (멱등)")
    void 중복연결_멱등() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);

        Long attemptId = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());
        ExamIdentityVerification first = identityService.findByAttempt(attemptId).orElseThrow();

        /* start() 가 재시도돼 같은 attempt 로 다시 들어와도 증거를 새로 만들지 않는다. */
        ExamIdentityVerification again = identityService.linkAttemptToSession(
                s.getId(), fixture.attemptOf(attemptId));

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(fixture.verificationCountOf(s.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("[P0-1] 승인되지 않은 세션에는 증거를 만들지 않는다")
    void 미승인_세션_연결거부() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);
        Long attemptId = examAttemptService.start(
                c.examId(), c.userId(), null, null, "127.0.0.1", "junit", s.getId());
        fixture.finishAttempt(attemptId);

        /* 세션을 PENDING 으로 되돌린 뒤 연결을 시도한다 */
        fixture.expireApproval(s.getId());
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");

        assertThatThrownBy(() -> identityService.linkAttemptToSession(
                s.getId(), fixture.attemptOf(attemptId)))
                .isInstanceOf(com.ssa.lms.identity.entity.IdentitySessionStateException.class);

        assertThat(fixture.verificationCountOf(s.getId()))
                .as("거부됐으면 증거가 늘면 안 된다").isEqualTo(1);
    }
}
