package com.ssa.lms.identity;

import com.ssa.lms.identity.entity.ExamIdentityDocument;
import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.repository.ExamIdentityDocumentRepository;
import com.ssa.lms.identity.repository.ExamIdentitySessionRepository;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.identity.support.IdentityTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * 세션 재사용 정책 (지적 1).
 *
 * <p><b>왜 중요한가</b><br>
 * 사전점검 화면은 새로고침으로 다시 열린다. 그때마다 새 PENDING 세션이 생기면
 * 최종 반려·재제출 횟수 제한이 통째로 무력화된다 — 반려당하면 F5 한 번으로 초기화되는 셈이다.</p>
 *
 * <p><b>정책</b><br>
 * (exam, user) 의 최신 세션이 <b>종결되지 않았으면 무조건 재사용</b>한다.
 * 새로 만드는 경우는 두 가지뿐이다 — 세션이 아예 없거나, 종결(EXPIRED)된 경우.
 * 최종 반려(REJECTED)는 종결이지만 <b>새 세션을 만들지 않는다</b>. 운영진이 풀어 줘야 한다.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class IdentitySessionReuseTest {

    @Autowired ExamIdentityService identityService;
    @Autowired ExamIdentitySessionRepository sessionRepository;
    @Autowired ExamIdentityDocumentRepository documentRepository;
    @Autowired IdentityTestFixture fixture;

    private ExamIdentityDocument saveDoc(ExamIdentitySession s, ExamIdentityDocument.Kind kind) {
        return documentRepository.save(ExamIdentityDocument.builder()
                .session(s).kind(kind).storageKey("test/" + kind + "-" + System.nanoTime())
                .contentType("image/jpeg").sizeBytes(1024).sha256("h")
                .width(800).height(500).uploadedAt(LocalDateTime.now()).build());
    }

    @Test
    @DisplayName("[1] PENDING 상태에서 새로고침해도 같은 세션이 유지된다")
    void PENDING_재사용() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();

        Long first = identityService.openSession(c.examId(), c.userId(), "127.0.0.1").getId();
        Long second = identityService.openSession(c.examId(), c.userId(), "127.0.0.1").getId();

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("[1] SUBMITTED 상태에서 새로고침해도 같은 sessionId 가 유지된다")
    void SUBMITTED_재사용() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());

        Long again = identityService.openSession(c.examId(), c.userId(), "127.0.0.1").getId();

        assertThat(again).isEqualTo(s.getId());
        assertThat(sessionRepository.findById(again).orElseThrow().getStatus())
                .isEqualTo(ExamIdentitySession.Status.SUBMITTED);
    }

    @Test
    @DisplayName("[1] UNDER_REVIEW 상태에서 새로고침해도 같은 세션이 유지된다")
    void UNDER_REVIEW_재사용() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        identityService.openReview(s.getId(), c.adminId(), "ADMIN", "127.0.0.1");

        Long again = identityService.openSession(c.examId(), c.userId(), "127.0.0.1").getId();

        assertThat(again).isEqualTo(s.getId());
        assertThat(sessionRepository.findById(again).orElseThrow().getStatus())
                .isEqualTo(ExamIdentitySession.Status.UNDER_REVIEW);
    }

    @Test
    @DisplayName("[1] APPROVED 상태에서 새로고침해도 승인이 날아가지 않는다")
    void APPROVED_재사용() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);

        Long again = identityService.openSession(c.examId(), c.userId(), "127.0.0.1").getId();

        assertThat(again).isEqualTo(s.getId());
        assertThat(sessionRepository.findById(again).orElseThrow().getStatus())
                .isEqualTo(ExamIdentitySession.Status.APPROVED);
    }

    @Test
    @DisplayName("[1] 최종 반려 후 새로고침으로 새 세션을 만들어 우회할 수 없다")
    void 최종반려_우회_차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        identityService.reject(s.getId(), c.adminId(), "ADMIN", "위조 의심", false, "127.0.0.1");

        Long again = identityService.openSession(c.examId(), c.userId(), "127.0.0.1").getId();

        assertThat(again).as("새 PENDING 세션이 생기면 최종 반려가 무력화된다").isEqualTo(s.getId());
        ExamIdentitySession reloaded = sessionRepository.findById(again).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ExamIdentitySession.Status.REJECTED);
        assertThat(reloaded.acceptsSubmission()).isFalse();
    }

    @Test
    @DisplayName("[1] 재제출 요청 후 새로고침해도 횟수가 초기화되지 않는다")
    void 재제출_횟수_유지() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        identityService.reject(s.getId(), c.adminId(), "ADMIN", "흐림", true, "127.0.0.1");

        ExamIdentitySession again = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");

        assertThat(again.getId()).isEqualTo(s.getId());
        assertThat(again.getResubmitCount()).isEqualTo(1);
        assertThat(again.getStatus()).isEqualTo(ExamIdentitySession.Status.RESUBMIT_REQUIRED);
    }

    @Test
    @DisplayName("[1] 승인이 만료되면 같은 세션이 PENDING 으로 다시 열린다 — 다시 신분확인을 받아야 한다")
    void 만료후_재개방() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentitySession s = fixture.approvedSession(c);
        fixture.expireApproval(s.getId());

        ExamIdentitySession again = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");

        /* (exam,user) 유니크 제약을 지키려면 새 행을 만들 수 없다. 같은 행을 다시 연다. */
        assertThat(again.getId()).isEqualTo(s.getId());
        assertThat(again.getStatus()).isEqualTo(ExamIdentitySession.Status.PENDING);
        assertThat(again.getCurrentDocument()).as("만료 후에는 제출을 처음부터 다시 받는다").isNull();
        assertThat(again.getFaceCheckDocument()).isNull();
        assertThat(again.canEnter(java.time.LocalDateTime.now())).isFalse();
    }

    /* ---------- 지적 5: 얼굴 사진 교체 ---------- */

    @Test
    @DisplayName("[5] SUBMITTED 상태에서는 얼굴 사진을 교체할 수 없다")
    void SUBMITTED_얼굴교체_차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());

        ExamIdentitySession reloaded = sessionRepository.findById(s.getId()).orElseThrow();
        assertThat(reloaded.acceptsFaceCheck())
                .as("검토 대기 중 얼굴 사진이 바뀌면 운영진이 본 것과 저장된 것이 달라진다")
                .isFalse();
    }

    @Test
    @DisplayName("[5] 재제출 요청 시 이전 얼굴 사진이 초기화된다 — 옛 사진으로 승인할 수 없다")
    void 재제출시_얼굴사진_초기화() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        identityService.reject(s.getId(), c.adminId(), "ADMIN", "얼굴이 흐림", true, "127.0.0.1");

        ExamIdentitySession reloaded = sessionRepository.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getFaceCheckDocument())
                .as("재제출 요청 뒤 옛 얼굴 사진이 남아 있으면 그대로 승인될 수 있다")
                .isNull();
        assertThat(reloaded.acceptsFaceCheck()).isTrue();
    }
}
