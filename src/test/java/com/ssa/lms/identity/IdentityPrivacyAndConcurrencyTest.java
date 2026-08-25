package com.ssa.lms.identity;

import com.ssa.lms.exam.entity.Exam;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 개인정보 저장 안정성(지적 9)과 동시성(지적 10).
 */
@SpringBootTest
@ActiveProfiles("local")
class IdentityPrivacyAndConcurrencyTest {

    @Autowired ExamIdentityService identityService;
    @Autowired ExamIdentitySessionRepository sessionRepository;
    @Autowired ExamIdentityDocumentRepository documentRepository;
    @Autowired PrivateFileStorage storage;
    @Autowired IdentityTestFixture fixture;

    /* ===================== 9.2 EXIF 제거 ===================== */

    @Test
    @DisplayName("[9.2] EXIF 가 들어간 JPEG 를 올려도 저장본에는 EXIF 가 남지 않는다")
    void EXIF_제거() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");

        byte[] withExif = IdentityTestFixture.jpegWithExif();
        assertThat(containsExif(withExif)).as("입력에는 EXIF 가 있어야 테스트가 의미 있다").isTrue();

        fixture.uploadIdCardBytes(s.getId(), withExif);

        byte[] saved = storage.read(fixture.storageKeyOf(fixture.currentIdDocumentId(s.getId())));
        assertThat(containsExif(saved)).as("저장본에 EXIF 가 남으면 촬영 위치·기기가 그대로 보관된다").isFalse();
    }

    @Test
    @DisplayName("[9.2] 크기·해시는 저장된 바이트 기준으로 기록된다")
    void 저장본_기준_해시() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());

        Long docId = fixture.currentIdDocumentId(s.getId());
        byte[] saved = storage.read(fixture.storageKeyOf(docId));

        assertThat(fixture.sizeOf(docId)[0]).isEqualTo(saved.length);
        assertThat(fixture.sha256Of(docId)).isEqualTo(sha256Hex(saved));
    }

    /* ===================== 9.1 고아 파일 ===================== */

    @Test
    @DisplayName("[9.1] 정상 커밋이면 파일이 유지된다")
    void 정상커밋_파일유지() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());

        assertThat(storage.exists(fixture.storageKeyOf(fixture.currentIdDocumentId(s.getId())))).isTrue();
    }

    @Test
    @DisplayName("[9.1] 상태 전이가 실패해 롤백되면 저장된 파일이 남지 않는다")
    void 롤백시_고아파일_제거() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        /* 완전 제출로 만들어 더 이상 신분증을 받지 않는 상태로 둔다 */
        fixture.uploadIdCard(s.getId());
        fixture.uploadFaceCheck(s.getId());

        int before = countFiles();

        /* 이 업로드는 상태 전이에서 거부된다 — 파일은 이미 저장된 뒤다 */
        assertThatThrownBy(() -> fixture.uploadIdCard(s.getId()))
                .isInstanceOf(RuntimeException.class);

        assertThat(countFiles())
                .as("롤백됐으면 방금 저장한 파일이 남으면 안 된다").isEqualTo(before);
    }

    /* ===================== 9.3 보존기간 파기 ===================== */

    @Test
    @DisplayName("[9.3] 만료 전 문서는 유지된다")
    void 만료전_유지() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        Long docId = fixture.currentIdDocumentId(s.getId());
        String key = fixture.storageKeyOf(docId);

        identityService.purgeExpiredDocuments();

        assertThat(fixture.isPurged(docId)).isFalse();
        assertThat(storage.exists(key)).isTrue();
    }

    @Test
    @DisplayName("[9.3] 만료 문서는 파일이 삭제되고 purgedAt 이 기록된다 — 재실행해도 결과가 같다")
    void 만료_파기_멱등() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        Long docId = fixture.currentIdDocumentId(s.getId());
        String key = fixture.storageKeyOf(docId);
        fixture.expirePurge(docId);

        int first = identityService.purgeExpiredDocuments();
        assertThat(first).isGreaterThanOrEqualTo(1);
        assertThat(storage.exists(key)).isFalse();
        assertThat(fixture.isPurged(docId)).isTrue();
        LocalDateTime firstPurgedAt = fixture.purgedAtOf(docId);

        /* 다시 돌려도 이미 파기된 문서는 건너뛴다 (멱등) */
        identityService.purgeExpiredDocuments();
        assertThat(fixture.purgedAtOf(docId)).isEqualTo(firstPurgedAt);
    }

    @Test
    @DisplayName("[9.3] 파기된 문서 조회는 410 계열 예외로 막힌다")
    void 파기후_조회차단() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        fixture.uploadIdCard(s.getId());
        Long docId = fixture.currentIdDocumentId(s.getId());
        fixture.expirePurge(docId);
        identityService.purgeExpiredDocuments();

        assertThatThrownBy(() -> identityService.readImageFor(
                docId, c.adminId(), "ADMIN", "127.0.0.1"))
                .isInstanceOf(com.ssa.lms.identity.entity.IdentityGoneException.class);
    }

    /* ===================== 10 동시성 ===================== */

    @Test
    @DisplayName("[10] 같은 (시험,사용자) 로 동시에 세션을 열어도 활성 세션은 하나다")
    void 동시_세션생성() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Long>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();      /* 시작 시점을 맞춰 우연한 직렬화를 피한다 */
                return identityService.openSession(c.examId(), c.userId(), "127.0.0.1").getId();
            }));
        }
        start.countDown();

        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Future<Long> f : futures) {
            ids.add(f.get(20, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(ids).as("동시 요청이 서로 다른 세션을 만들면 안 된다").hasSize(1);
    }

    @Test
    @DisplayName("[10] 같은 토큰으로 동시에 업로드해도 허용 횟수를 넘지 않는다")
    void 동시_토큰소비() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentityService.IssuedToken token =
                identityService.issueToken(s.getId(), c.userId(), "127.0.0.1");

        int threads = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    identityService.submitIdCard(token.rawToken(),
                            IdentityTestFixture.imageFile("id.jpg"), "127.0.0.1");
                    ok.incrementAndGet();
                } catch (Exception ignored) {
                    /* 경쟁에서 진 요청은 상태·토큰 검사로 거부된다 */
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        ExamIdentitySession reloaded = sessionRepository.findById(s.getId()).orElseThrow();
        assertThat(ok.get())
                .as("동시 업로드가 허용 횟수를 넘겨 확정되면 안 된다")
                .isBetween(1, com.ssa.lms.identity.entity.ExamIdentityToken.DEFAULT_MAX_USE);
        assertThat(reloaded.getCurrentDocument()).as("확정된 문서는 하나여야 한다").isNotNull();
    }

    /* ===================== 헬퍼 ===================== */

    /** JPEG APP1(EXIF) 마커 존재 여부. */
    private static boolean containsExif(byte[] b) {
        for (int i = 0; i + 5 < b.length; i++) {
            if ((b[i] & 0xFF) == 0xFF && (b[i + 1] & 0xFF) == 0xE1
                    && b[i + 4] == 'E' && b[i + 5] == 'x') {
                return true;
            }
        }
        return false;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 비공개 저장소의 파일 개수 — 고아 파일 검증용. */
    private int countFiles() {
        String root = (String) ReflectionTestUtils.getField(storage, "rootDir");
        java.nio.file.Path base = java.nio.file.Paths.get(root);
        if (!java.nio.file.Files.exists(base)) {
            return 0;
        }
        try (var walk = java.nio.file.Files.walk(base)) {
            return (int) walk.filter(java.nio.file.Files::isRegularFile).count();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
