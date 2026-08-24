package com.ssa.lms.identity;

import com.ssa.lms.storage.privatefile.PrivateFileException;
import com.ssa.lms.storage.privatefile.PrivateFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * 신분증 업로드 검증 (P0-6).
 *
 * <p>테스트 이미지는 <b>단색 도형</b>이다 — 실제 신분증·얼굴 사진을 저장소에 넣지 않는다.</p>
 */
class PrivateFileStorageTest {

    @TempDir
    Path tempDir;

    private PrivateFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new PrivateFileStorage();
        ReflectionTestUtils.setField(storage, "rootDir", tempDir.toString());
        ReflectionTestUtils.setField(storage, "retentionDays", 30);
    }

    /* ---------- 헬퍼: 단색 도형 이미지 ---------- */

    private static byte[] image(String format, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(240, 240, 245));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(20, 40, 90));
        g.drawRect(10, 10, w - 20, h - 20);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    /* ---------- 정상 ---------- */

    @Test
    @DisplayName("정상 JPG 는 저장되고 실제 해상도가 기록된다")
    void 정상_JPG() throws Exception {
        PrivateFileStorage.Stored s = storage.store(file("id.jpg", "image/jpeg", image("jpg", 800, 500)), "id");

        assertThat(s.contentType()).isEqualTo("image/jpeg");
        assertThat(s.width()).isEqualTo(800);
        assertThat(s.height()).isEqualTo(500);
        assertThat(s.sha256()).isNotBlank();
        assertThat(storage.exists(s.storageKey())).isTrue();
        /* 저장 키에 원본 파일명이 들어가면 안 된다 (개인정보·경로 이탈) */
        assertThat(s.storageKey()).doesNotContain("id.jpg");
    }

    @Test
    @DisplayName("정상 PNG 도 저장된다")
    void 정상_PNG() throws Exception {
        PrivateFileStorage.Stored s = storage.store(file("id.png", "image/png", image("png", 600, 400)), "id");
        assertThat(s.contentType()).isEqualTo("image/png");
    }

    /* ---------- P0-6: 위장·손상 차단 ---------- */

    @Test
    @DisplayName("[P0-6] magic byte 만 WEBP 로 위장한 파일은 거부된다")
    void WEBP_위장_거부() {
        byte[] fake = new byte[64];
        System.arraycopy("RIFF".getBytes(), 0, fake, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, fake, 8, 4);

        assertThatThrownBy(() -> storage.store(file("x.webp", "image/webp", fake), "id"))
                .isInstanceOf(PrivateFileException.class)
                .hasMessageContaining("JPG 또는 PNG");
    }

    @Test
    @DisplayName("[P0-6] 확장자만 이미지인 PDF 는 거부된다")
    void 확장자_위장_거부() {
        byte[] pdf = "%PDF-1.4 not an image".getBytes();
        assertThatThrownBy(() -> storage.store(file("id.jpg", "image/jpeg", pdf), "id"))
                .isInstanceOf(PrivateFileException.class);
    }

    @Test
    @DisplayName("[P0-6] JPEG magic byte 만 있고 디코딩되지 않는 손상 파일은 거부된다")
    void 손상_이미지_거부() {
        byte[] broken = new byte[128];
        broken[0] = (byte) 0xFF; broken[1] = (byte) 0xD8; broken[2] = (byte) 0xFF;
        /* 나머지는 쓰레기 — magic byte 는 통과하지만 ImageIO 가 못 읽는다 */
        assertThatThrownBy(() -> storage.store(file("x.jpg", "image/jpeg", broken), "id"))
                .isInstanceOf(PrivateFileException.class)
                .hasMessageContaining("열 수 없습니다");
    }

    @Test
    @DisplayName("클라이언트 MIME 을 위조해도 실제 바이트로 판별한다")
    void 클라이언트_MIME_위조() throws Exception {
        /* 진짜 PNG 인데 Content-Type 은 jpeg 라고 보낸다 → 실제 형식(png)으로 저장돼야 한다 */
        PrivateFileStorage.Stored s =
                storage.store(file("a.jpg", "image/jpeg", image("png", 400, 300)), "id");
        assertThat(s.contentType()).isEqualTo("image/png");
    }

    /* ---------- 크기·해상도 ---------- */

    @Test
    @DisplayName("빈 파일은 거부된다")
    void 빈_파일() {
        assertThatThrownBy(() -> storage.store(file("e.jpg", "image/jpeg", new byte[0]), "id"))
                .isInstanceOf(PrivateFileException.class)
                .hasMessageContaining("비어");
    }

    @Test
    @DisplayName("10MB 를 넘는 파일은 거부된다")
    void 과대_파일() {
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        big[0] = (byte) 0xFF; big[1] = (byte) 0xD8; big[2] = (byte) 0xFF;
        assertThatThrownBy(() -> storage.store(file("b.jpg", "image/jpeg", big), "id"))
                .isInstanceOf(PrivateFileException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    @DisplayName("최소 해상도 미만은 거부된다")
    void 최소_해상도() throws Exception {
        assertThatThrownBy(() -> storage.store(file("s.jpg", "image/jpeg", image("jpg", 100, 80)), "id"))
                .isInstanceOf(PrivateFileException.class)
                .hasMessageContaining("너무 작");
    }

    /* ---------- 경로 안전 ---------- */

    @Test
    @DisplayName("경로 이탈 키는 거부된다")
    void 경로_이탈_차단() {
        assertThatThrownBy(() -> storage.read("../../etc/passwd"))
                .isInstanceOf(PrivateFileException.class)
                .hasMessageContaining("잘못된 파일 경로");
    }

    @Test
    @DisplayName("저장된 파일은 웹 루트가 아니라 지정한 비공개 경로 아래에 있다")
    void 비공개_경로_저장() throws Exception {
        PrivateFileStorage.Stored s = storage.store(file("id.jpg", "image/jpeg", image("jpg", 400, 300)), "id");
        assertThat(Files.exists(tempDir.resolve(s.storageKey()))).isTrue();
    }

    @Test
    @DisplayName("삭제하면 파일이 사라진다")
    void 파기() throws Exception {
        PrivateFileStorage.Stored s = storage.store(file("id.jpg", "image/jpeg", image("jpg", 400, 300)), "id");
        assertThat(storage.delete(s.storageKey())).isTrue();
        assertThat(storage.exists(s.storageKey())).isFalse();
    }

    /* ===================== P1-9: 디코딩 전 크기 방어 ===================== */

    @Test
    @DisplayName("[P1-9] 총 화소 수가 상한을 넘는 이미지는 거부된다 — 변 길이는 상한 이내여도 막는다")
    void 총화소수_상한() {
        /* 5000×4000 = 2000만 화소. 각 변은 8000 이내라 예전 검사(변 길이)만으로는 통과했다.
           TYPE_INT_RGB 로 펼치면 80MB 다. */
        MultipartFile huge = jpeg(5000, 4000);

        assertThatThrownBy(() -> storage.store(huge, "id"))
                .isInstanceOf(PrivateFileException.class)
                .hasMessageContaining("화소");
    }

    @Test
    @DisplayName("[P1-9] 상한 이내의 큰 이미지는 정상 저장된다 — 실사용 촬영물을 막지 않는다")
    void 상한이내_정상저장() {
        /* 4000×3000 = 1200만 화소 — iPhone 후면 카메라 기본값 수준. */
        PrivateFileStorage.Stored stored = storage.store(jpeg(4000, 3000), "id");

        assertThat(stored.width()).isEqualTo(4000);
        assertThat(stored.height()).isEqualTo(3000);
        assertThat(storage.exists(stored.storageKey())).isTrue();
        storage.delete(stored.storageKey());
    }

    @Test
    @DisplayName("[P1-9] 거부된 초대형 이미지는 파일을 남기지 않는다")
    void 거부시_파일없음() {
        int before = countStoredFiles();

        assertThatThrownBy(() -> storage.store(jpeg(5000, 4000), "id"))
                .isInstanceOf(PrivateFileException.class);

        assertThat(countStoredFiles())
                .as("거부한 업로드가 파일을 남기면 저장소가 계속 커진다").isEqualTo(before);
    }

    @Test
    @DisplayName("[P1-9] 헤더만 이미지인 위장 파일은 거부된다")
    void 위장파일_거부() {
        /* JPEG magic byte 로 시작하지만 그 뒤는 쓰레기 — 헤더 파싱조차 되지 않아야 한다. */
        byte[] fake = new byte[2048];
        fake[0] = (byte) 0xFF; fake[1] = (byte) 0xD8; fake[2] = (byte) 0xFF;
        for (int i = 3; i < fake.length; i++) {
            fake[i] = (byte) (i % 251);
        }

        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("file", "fake.jpg", "image/jpeg", fake), "id"))
                .isInstanceOf(PrivateFileException.class);
    }

    @Test
    @DisplayName("[P1-9] 헤더만 남을 정도로 잘린 JPEG 은 치수를 읽지 못해 거부된다")
    void 심하게_잘린_이미지_거부() {
        byte[] full = bytesOf(jpeg(1000, 800));
        /* SOF 마커까지 도달하지 못하는 길이 — 헤더에서 치수를 못 읽는다. */
        byte[] truncated = java.util.Arrays.copyOf(full, 64);

        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("file", "cut.jpg", "image/jpeg", truncated), "id"))
                .isInstanceOf(PrivateFileException.class);
    }

    @Test
    @DisplayName("[P1-9] 뒷부분이 잘린 JPEG 은 ImageIO 가 부분 디코딩한다 — 저장되더라도 재인코딩된 정상 이미지다")
    void 부분_잘린_이미지의_실제동작() {
        byte[] full = bytesOf(jpeg(1000, 800));
        byte[] truncated = java.util.Arrays.copyOf(full, full.length / 3);
        MockMultipartFile cut = new MockMultipartFile("file", "cut.jpg", "image/jpeg", truncated);

        /* ImageIO 는 스캔이 끊긴 JPEG 도 여기까지 디코딩한 픽셀로 이미지를 만든다.
           "손상 파일은 항상 거부된다" 고 주장하면 거짓이므로, 실제 동작을 그대로 고정한다.
           중요한 것은 무엇이 저장되든 <b>우리가 다시 인코딩한 정상 이미지</b>라는 점이다. */
        try {
            PrivateFileStorage.Stored stored = storage.store(cut, "id");
            byte[] saved = storage.read(stored.storageKey());
            assertThat(decodeOrNull(saved))
                    .as("저장본은 반드시 다시 열리는 이미지여야 한다").isNotNull();
            assertThat(stored.width()).isEqualTo(1000);
            assertThat(stored.height()).isEqualTo(800);
            storage.delete(stored.storageKey());
        } catch (PrivateFileException expected) {
            /* 디코더가 거부하는 것도 정상이다 — 둘 중 어느 쪽이든 저장소는 안전하다. */
            assertThat(expected).hasMessageContaining("이미지");
        }
    }

    private static BufferedImage decodeOrNull(byte[] bytes) {
        try (java.io.InputStream in = new java.io.ByteArrayInputStream(bytes)) {
            return ImageIO.read(in);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /* ---------- P1-9 헬퍼 ---------- */

    /** 지정 크기의 합성 JPEG. 실제 신분증·얼굴 이미지는 쓰지 않는다. */
    private static MockMultipartFile jpeg(int w, int h) {
        try {
            java.awt.image.BufferedImage img =
                    new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(java.awt.Color.LIGHT_GRAY);
            g.fillRect(0, 0, w, h);
            g.setColor(java.awt.Color.DARK_GRAY);
            g.fillRect(w / 8, h / 8, w / 2, h / 4);
            g.dispose();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "jpg", out);
            return new MockMultipartFile("file", w + "x" + h + ".jpg", "image/jpeg", out.toByteArray());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] bytesOf(MockMultipartFile f) {
        try {
            return f.getBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private int countStoredFiles() {
        String root = (String) org.springframework.test.util.ReflectionTestUtils.getField(storage, "rootDir");
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
