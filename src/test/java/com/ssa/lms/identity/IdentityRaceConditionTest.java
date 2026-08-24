package com.ssa.lms.identity;

import com.ssa.lms.identity.entity.ExamIdentityDocument;
import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.repository.ExamIdentityDocumentRepository;
import com.ssa.lms.identity.repository.ExamIdentitySessionRepository;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.identity.support.IdentityTestFixture;
import com.ssa.lms.storage.privatefile.PrivateFileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션을 동시에 바꾸는 경로들의 경쟁 (P1-3).
 *
 * <p><b>무엇이 부족했나</b><br>
 * 신분증 업로드는 토큰 행만 잠갔고, 얼굴 제출·검토 시작·승인·반려는 세션을 그냥
 * {@code findById} 로 읽었다. 서로 다른 트랜잭션이 같은 세션을 동시에 바꾸면 문서 포인터나
 * 상태 중 한쪽이 유실될 수 있었다. 기존 동시성 테스트는 <b>세션 생성</b>과 <b>같은 토큰
 * 중복 업로드</b>만 봤고 아래 경쟁은 하나도 검증하지 않았다.</p>
 *
 * <p>모든 스레드는 {@link CountDownLatch} 로 시작 시점을 맞춘다 — 우연한 직렬화로
 * 통과하는 것을 막는다. 예외를 통째로 삼키지 않고 <b>유형별로 분류</b>해 최종 상태와 함께 본다.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class IdentityRaceConditionTest {

    @Autowired ExamIdentityService identityService;
    @Autowired ExamIdentitySessionRepository sessionRepository;
    @Autowired ExamIdentityDocumentRepository documentRepository;
    @Autowired PrivateFileStorage storage;
    @Autowired IdentityTestFixture fixture;

    /** 각 작업의 결과 — 성공/도메인 거부/그 밖의 실패를 구분해 기록한다. */
    private record Outcome(String name, boolean ok, String failureType, String message) {
    }

    private List<Outcome> runTogether(List<Runnable> tasks, List<String> names) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        List<Future<Outcome>> futures = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            final Runnable task = tasks.get(i);
            final String name = names.get(i);
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    task.run();
                    return new Outcome(name, true, null, null);
                } catch (Throwable t) {
                    return new Outcome(name, false, t.getClass().getSimpleName(), String.valueOf(t.getMessage()));
                }
            }));
        }
        start.countDown();
        List<Outcome> out = new ArrayList<>();
        for (Future<Outcome> f : futures) {
            out.add(f.get(40, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return out;
    }

    /** 도메인이 의도적으로 거부한 것인지 — 예상 밖 오류와 구분한다. */
    private static boolean isDomainRejection(Outcome o) {
        return "IdentitySessionStateException".equals(o.failureType())
                || "IdentityAccessDeniedException".equals(o.failureType())
                || "PrivateFileException".equals(o.failureType())
                || "CannotAcquireLockException".equals(o.failureType())
                || "PessimisticLockingFailureException".equals(o.failureType())
                || "ObjectOptimisticLockingFailureException".equals(o.failureType());
    }

    private static void assertNoUnexpectedFailure(List<Outcome> outcomes) {
        List<Outcome> bad = outcomes.stream()
                .filter(o -> !o.ok() && !isDomainRejection(o))
                .toList();
        assertThat(bad)
                .as("도메인 거부가 아닌 오류가 나면 안 된다: %s", bad)
                .isEmpty();
    }

    private long fileCount() {
        return documentRepository.findAll().stream()
                .filter(d -> d.getPurgedAt() == null)
                .filter(d -> storage.exists(d.getStorageKey()))
                .count();
    }

    /* ===================== 1) 신분증 + 얼굴 동시 제출 ===================== */

    @Test
    @DisplayName("[P1-3] 신분증과 얼굴 사진을 동시에 제출해도 두 문서가 모두 연결되고 SUBMITTED 가 된다")
    void 신분증_얼굴_동시제출() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentityService.IssuedToken token =
                identityService.issueToken(s.getId(), c.userId(), "127.0.0.1");

        List<Outcome> outcomes = runTogether(
                List.of(
                        () -> identityService.submitIdCard(token.rawToken(),
                                IdentityTestFixture.imageFile("id.jpg"), "127.0.0.1"),
                        () -> identityService.submitFaceCheck(s.getId(), c.userId(),
                                IdentityTestFixture.imageFile("face.jpg"), true, "face-consent-v1", "127.0.0.1")),
                List.of("ID", "FACE"));

        assertNoUnexpectedFailure(outcomes);
        assertThat(outcomes).allMatch(Outcome::ok, "두 제출은 서로 배타적이지 않으므로 모두 성공해야 한다");

        ExamIdentitySession r = sessionRepository.findById(s.getId()).orElseThrow();
        assertThat(fixture.currentIdDocumentId(s.getId())).as("신분증 포인터가 유실되면 안 된다").isNotNull();
        assertThat(fixture.faceDocumentId(s.getId())).as("얼굴 포인터가 유실되면 안 된다").isNotNull();
        assertThat(r.getStatus())
                .as("두 자료가 모두 들어왔으므로 검토 대기여야 한다")
                .isEqualTo(ExamIdentitySession.Status.SUBMITTED);
    }

    /* ===================== 2) 제출 vs 승인 ===================== */

    @Test
    @DisplayName("[P1-3] 제출과 승인이 경쟁해도 불완전한 자료가 승인되지 않는다")
    void 제출과_승인_경쟁() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        /* 신분증만 있는 상태 — 지금 승인되면 안 된다. */
        fixture.uploadIdCard(s.getId());

        List<Outcome> outcomes = runTogether(
                List.of(
                        () -> identityService.submitFaceCheck(s.getId(), c.userId(),
                                IdentityTestFixture.imageFile("face.jpg"), true, "face-consent-v1", "127.0.0.1"),
                        () -> identityService.approve(s.getId(), c.adminId(), "ADMIN", "127.0.0.1")),
                List.of("FACE", "APPROVE"));

        assertNoUnexpectedFailure(outcomes);

        ExamIdentitySession r = sessionRepository.findById(s.getId()).orElseThrow();
        Outcome approve = outcomes.stream().filter(o -> o.name().equals("APPROVE")).findFirst().orElseThrow();

        if (approve.ok()) {
            /* 승인이 통과했다면, 그 시점에 자료가 실제로 완전했어야 한다. */
            assertThat(r.getStatus()).isEqualTo(ExamIdentitySession.Status.APPROVED);
            assertThat(fixture.currentIdDocumentId(s.getId())).isNotNull();
            assertThat(fixture.faceDocumentId(s.getId()))
                    .as("얼굴 없이 승인됐다면 대조 자체가 성립하지 않는다").isNotNull();
        } else {
            /* 승인이 거부됐다면 도메인 거부여야 하고, 상태는 안 바뀌어야 한다. */
            assertThat(isDomainRejection(approve))
                    .as("승인 거부는 도메인 거부여야 한다: %s", approve).isTrue();
            assertThat(r.getStatus())
                    .isIn(ExamIdentitySession.Status.PENDING, ExamIdentitySession.Status.SUBMITTED);
        }
    }

    /* ===================== 3) 재제출 요청 vs 업로드 ===================== */

    @Test
    @DisplayName("[P1-3] 재제출 요청과 업로드가 경쟁해도 과거 자료와 새 자료가 섞이지 않는다")
    void 재제출요청과_업로드_경쟁() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        Long oldId = fixture.currentIdDocumentId(s.getId());
        Long oldFace = fixture.faceDocumentId(s.getId());

        List<Outcome> outcomes = runTogether(
                List.of(
                        () -> identityService.reject(s.getId(), c.adminId(), "ADMIN", "다시", true, "127.0.0.1"),
                        () -> identityService.submitFaceCheck(s.getId(), c.userId(),
                                IdentityTestFixture.imageFile("face2.jpg"), true, "face-consent-v1", "127.0.0.1")),
                List.of("RESUBMIT", "FACE"));

        assertNoUnexpectedFailure(outcomes);

        ExamIdentitySession r = sessionRepository.findById(s.getId()).orElseThrow();
        Long nowId = fixture.currentIdDocumentId(s.getId());
        Long nowFace = fixture.faceDocumentId(s.getId());

        /* 핵심: "과거 신분증 + 새 얼굴" 조합이 승인 가능한 상태로 남으면 안 된다. */
        boolean mixed = nowId != null && nowId.equals(oldId)
                && nowFace != null && !nowFace.equals(oldFace);
        assertThat(mixed)
                .as("과거 신분증과 새 얼굴이 섞인 채 남으면 안 된다 (id=%s→%s, face=%s→%s, status=%s)",
                        oldId, nowId, oldFace, nowFace, r.getStatus())
                .isFalse();

        /* 재제출이 성공했다면 두 포인터가 함께 비워져 있어야 한다. */
        Outcome resubmit = outcomes.stream().filter(o -> o.name().equals("RESUBMIT")).findFirst().orElseThrow();
        if (resubmit.ok() && r.getStatus() == ExamIdentitySession.Status.RESUBMIT_REQUIRED) {
            Outcome face = outcomes.stream().filter(o -> o.name().equals("FACE")).findFirst().orElseThrow();
            if (!face.ok()) {
                assertThat(nowId).as("재제출 요청이 이겼으면 신분증도 비워진다").isNull();
                assertThat(nowFace).isNull();
            }
        }
        assertThat(r.getStatus()).isNotEqualTo(ExamIdentitySession.Status.APPROVED);
    }

    /* ===================== 4) 검토 중 새 제출 ===================== */

    @Test
    @DisplayName("[P1-3] 검토 시작과 새 얼굴 제출이 경쟁해도 검토본과 저장본이 어긋나지 않는다")
    void 검토시작과_제출_경쟁() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());
        Long reviewedFace = fixture.faceDocumentId(s.getId());

        List<Outcome> outcomes = runTogether(
                List.of(
                        () -> identityService.openReview(s.getId(), c.adminId(), "ADMIN", "127.0.0.1"),
                        () -> identityService.submitFaceCheck(s.getId(), c.userId(),
                                IdentityTestFixture.imageFile("face2.jpg"), true, "face-consent-v1", "127.0.0.1")),
                List.of("REVIEW", "FACE"));

        assertNoUnexpectedFailure(outcomes);

        ExamIdentitySession r = sessionRepository.findById(s.getId()).orElseThrow();
        Outcome face = outcomes.stream().filter(o -> o.name().equals("FACE")).findFirst().orElseThrow();

        assertThat(face.ok())
                .as("완전 제출(SUBMITTED) 이후의 얼굴 교체는 항상 거부돼야 한다 — 운영진이 본 사진이 바뀐다")
                .isFalse();
        assertThat(isDomainRejection(face)).as("거부는 도메인 거부여야 한다: %s", face).isTrue();
        assertThat(fixture.faceDocumentId(s.getId()))
                .as("검토 대상 사진이 바뀌면 안 된다").isEqualTo(reviewedFace);
        assertThat(r.getStatus())
                .isIn(ExamIdentitySession.Status.SUBMITTED, ExamIdentitySession.Status.UNDER_REVIEW);
    }

    /* ===================== 5) 같은 QR 동시 업로드 ===================== */

    @Test
    @DisplayName("[P1-3] 같은 QR 로 동시에 여러 번 올려도 현재 신분증 문서는 정확히 하나이고 고아 파일이 없다")
    void 같은QR_동시업로드_문서하나() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentityService.IssuedToken token =
                identityService.issueToken(s.getId(), c.userId(), "127.0.0.1");

        long filesBefore = fileCount();

        int threads = 5;
        List<Runnable> tasks = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> identityService.submitIdCard(token.rawToken(),
                    IdentityTestFixture.imageFile("id.jpg"), "127.0.0.1"));
            names.add("UPLOAD");
        }
        List<Outcome> outcomes = runTogether(tasks, names);
        assertNoUnexpectedFailure(outcomes);

        long succeeded = outcomes.stream().filter(Outcome::ok).count();
        assertThat(succeeded).as("최소 한 번은 성공해야 한다").isGreaterThanOrEqualTo(1);

        /* 현재 신분증 포인터는 정확히 하나 */
        Long current = fixture.currentIdDocumentId(s.getId());
        assertThat(current).isNotNull();

        /* 저장된 ID_CARD 문서 수 = 성공 횟수. 실패한 요청의 파일은 롤백으로 사라져야 한다. */
        List<ExamIdentityDocument> idDocs = documentRepository
                .findBySessionIdOrderByIdDesc(s.getId()).stream()
                .filter(d -> d.getKind() == ExamIdentityDocument.Kind.ID_CARD)
                .toList();
        assertThat(idDocs).hasSize((int) succeeded);
        assertThat(fileCount() - filesBefore)
                .as("성공한 업로드 수만큼만 파일이 늘어야 한다 — 실패분은 고아로 남으면 안 된다")
                .isEqualTo(succeeded);
        assertThat(idDocs.stream().map(ExamIdentityDocument::getId))
                .as("현재 포인터는 저장된 문서 중 하나여야 한다").contains(current);
    }
}
