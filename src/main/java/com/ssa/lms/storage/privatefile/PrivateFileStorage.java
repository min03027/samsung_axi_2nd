package com.ssa.lms.storage.privatefile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * 신분증·얼굴 사진 전용 비공개 저장소.
 *
 * <p><b>왜 FileStorageService 를 안 쓰는가</b><br>
 * 기존 {@code content/service/FileStorageService} 는 {@code /content/files/...} URL 을 만들고
 * {@code ContentStorageConfig} 가 평범한 ResourceHandler 로 서빙한다. 객체별 권한 검사가 없어서
 * <b>로그인만 한 사람이면 URL 만 알면 남의 파일을 받을 수 있다.</b> 신분증에는 쓸 수 없다.</p>
 *
 * <p>여기 저장한 파일은 웹 루트 밖에 있고 URL 이 없다. 반드시 권한 검사를 통과한
 * 스트리밍 엔드포인트를 거쳐야 읽힌다.</p>
 */
@Slf4j
@Service
public class PrivateFileStorage {

    /** 허용 MIME. 확장자는 믿지 않는다. */
    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";

    /* WEBP 는 지원하지 않는다.
       JDK 기본 ImageIO 에 WEBP 리더가 없어서, magic byte(RIFF....WEBP)만 흉내 낸 파일이
       "디코딩 검증 없이" 통과해 버린다. 손상 파일·위장 파일이 그대로 저장되는 구멍이라
       검증 가능한 디코더를 붙이기 전까지는 형식에서 뺀다.
       (UI accept 속성과 안내 문구도 JPG·PNG 로 맞춰 두었다.) */

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MIN_EDGE = 200;
    private static final int MAX_EDGE = 8000;

    /**
     * 총 픽셀 수 상한 (P1-9). 4000×4000 = 1600만 화소 — 신분증·얼굴 사진에 차고 넘친다.
     *
     * <p><b>왜 변 길이만으로 부족한가</b><br>
     * 8000×8000 은 각 변이 상한 이내인데 6400만 화소다. {@code TYPE_INT_RGB} 는 화소당 4바이트라
     * 버퍼 하나가 <b>256MB</b> 다. EXIF 제거를 위해 재인코딩까지 하므로 순간 사용량은 그 이상이다.
     * 10MB 짜리 압축 파일 하나로 그만큼을 잡을 수 있고(압축비 폭탄), 업로드가 동시에 여러 개
     * 들어오면 힙이 그대로 넘어간다.</p>
     *
     * <p>1600만 화소면 최악의 경우에도 버퍼 64MB 다. 동시 업로드 몇 개가 겹쳐도 견딜 수 있는
     * 수준으로 잡았다. 실제 촬영물은 훨씬 작다 — iPhone 후면 카메라 기본값이 1200만 화소다.</p>
     */
    private static final long MAX_PIXELS = 16_000_000L;

    /**
     * 저장 루트. 기본값은 임시 디렉터리 하위 — 운영에서는 반드시 영구 경로로 바꿔야 한다.
     * (application*.yml 은 이번 변경 허용 목록 밖이라 기본값만 둔다. 최종 보고에 명시.)
     */
    @Value("${lms.identity.private-dir:#{systemProperties['java.io.tmpdir']}/lms-identity-private}")
    private String rootDir;

    /** 보존기간(일). 기관 정책이 확인되지 않아 안전한 로컬 기본값을 쓴다. */
    @Value("${lms.identity.retention-days:30}")
    private int retentionDays;

    public int retentionDays() {
        return retentionDays;
    }

    /**
     * 업로드 파일 검증 후 저장.
     *
     * <p>확장자·클라이언트 MIME 을 믿지 않고 <b>magic byte 로 실제 형식</b>을 판별한 뒤
     * ImageIO 로 실제 디코딩까지 해본다. 디코딩이 안 되면 이미지가 아니다.</p>
     */
    public Stored store(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new PrivateFileException("파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new PrivateFileException(
                    "파일이 10MB를 넘습니다 (" + String.format(Locale.ROOT, "%.1f", file.getSize() / 1048576.0) + "MB).");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new PrivateFileException("파일을 읽지 못했습니다.");
        }

        String detected = sniff(bytes);
        if (detected == null) {
            throw new PrivateFileException("JPG 또는 PNG 형식만 제출할 수 있습니다.");
        }

        /* ★ 디코딩 <b>전에</b> 헤더에서 치수를 읽어 크기를 거른다 (P1-9).
           ImageIO.read() 로 먼저 전부 디코딩하면, 거부할 이미지의 픽셀 버퍼를 이미 만든 뒤다.
           작은 압축 파일도 거대한 버퍼가 될 수 있어서(압축비 폭탄) 헤더만 먼저 본다. */
        Dimension header = readDimension(bytes);
        if (header == null) {
            throw new PrivateFileException("이미지를 열 수 없습니다. 파일이 손상되었을 수 있습니다.");
        }
        requireSaneSize(header.w, header.h);

        /* 형식 판별과 치수 검사를 통과해도 실제로 디코딩되지 않으면 거부한다 — 예외 없음.
           여기서 빠져나갈 구멍을 두면 magic byte 위장만으로 저장이 된다. */
        BufferedImage image = decode(bytes);
        if (image == null) {
            throw new PrivateFileException("이미지를 열 수 없습니다. 파일이 손상되었을 수 있습니다.");
        }
        int w = image.getWidth(), h = image.getHeight();
        /* 헤더가 거짓말을 했을 수 있으므로 실제 디코딩 결과로 한 번 더 본다. */
        requireSaneSize(w, h);

        /* ★ EXIF/GPS 제거 — 디코딩한 픽셀만 다시 인코딩한다.
           원본 바이트를 그대로 저장하면 촬영 위치·기기·시각이 신분증 사진에 그대로 남는다.
           ImageIO.write 는 우리가 만든 BufferedImage 만 쓰므로 원본 메타데이터가 따라오지 않는다. */
        byte[] sanitized = reencode(image, detected);

        /* 저장 키는 UUID 로 무작위화한다. 원본 파일명은 저장 경로에 쓰지 않는다
           — 경로 이탈(../)과 개인정보(이름.jpg) 유입을 한 번에 막는다. */
        String key = LocalDate.now() + "/" + prefix + "-" + UUID.randomUUID().toString().replace("-", "")
                + extensionOf(detected);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            /* CREATE_NEW: 이미 있으면 실패시킨다. 덮어쓰기를 허용하면 남의 증빙을 지울 수 있다. */
            Files.write(target, sanitized, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new PrivateFileException("파일 저장에 실패했습니다.");
        }

        /* 크기·해시는 <b>저장된 바이트</b> 기준으로 기록한다 — 원본 기준으로 적으면 무결성 검증이 어긋난다. */
        return new Stored(key, detected, sanitized.length, sha256(sanitized), w, h);
    }

    /** 저장된 파일 읽기. 권한 검사는 <b>호출자</b> 책임이다 — 여기서는 경로만 안전하게 푼다. */
    public byte[] read(String storageKey) {
        Path p = resolve(storageKey);
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new PrivateFileException("파일을 찾을 수 없습니다.");
        }
    }

    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    /** 파기. 파일만 지우고 메타데이터·감사 기록은 호출자가 따로 남긴다. */
    public boolean delete(String storageKey) {
        try {
            return Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            log.warn("비공개 파일 삭제 실패: {}", storageKey, e);
            return false;
        }
    }

    /* ===================== 내부 ===================== */

    /** 루트 밖으로 나가는 키를 거부한다 (`../` 경로 이탈 차단). */
    private Path resolve(String storageKey) {
        Path base = Paths.get(rootDir).toAbsolutePath().normalize();
        Path target = base.resolve(storageKey).normalize();
        if (!target.startsWith(base)) {
            throw new PrivateFileException("잘못된 파일 경로입니다.");
        }
        return target;
    }

    /** 변 길이와 <b>총 화소 수</b>를 함께 본다 (P1-9). */
    private static void requireSaneSize(int w, int h) {
        if (w < MIN_EDGE || h < MIN_EDGE) {
            throw new PrivateFileException(
                    "이미지가 너무 작습니다 (" + w + "×" + h + "). 글자가 보이도록 다시 촬영해 주세요.");
        }
        if (w > MAX_EDGE || h > MAX_EDGE) {
            throw new PrivateFileException("이미지가 너무 큽니다 (" + w + "×" + h + ").");
        }
        if ((long) w * h > MAX_PIXELS) {
            throw new PrivateFileException("이미지 화소 수가 너무 많습니다 (" + w + "×" + h
                    + "). " + (MAX_PIXELS / 1_000_000) + "메가픽셀 이하로 다시 촬영해 주세요.");
        }
    }

    /**
     * 픽셀을 만들지 않고 <b>헤더에서만</b> 치수를 읽는다 (P1-9).
     *
     * <p>{@code ImageReader.getWidth/getHeight} 는 스트림 앞부분만 훑는다. 전체 래스터를
     * 메모리에 올리지 않으므로, 거부할 이미지에 큰 버퍼를 쓰지 않는다.</p>
     */
    private static Dimension readDimension(byte[] bytes) {
        try (javax.imageio.stream.ImageInputStream in =
                     ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            if (in == null) {
                return null;
            }
            java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            /* 헤더조차 못 읽으면 이미지가 아니다. */
            return null;
        }
    }

    private record Dimension(int w, int h) {
    }

    /** magic byte 로 실제 형식 판별. 클라이언트가 보낸 Content-Type 은 쓰지 않는다. */
    private static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return PNG;
        }
        return null;
    }

    /**
     * 디코딩된 픽셀만 다시 인코딩해 메타데이터를 떨어뜨린다.
     *
     * <p>PNG 는 알파를 보존하고, JPEG 는 알파를 지원하지 않으므로 RGB 로 눕힌다
     * (그대로 쓰면 색이 깨진다).</p>
     */
    private static byte[] reencode(BufferedImage src, String mime) {
        try {
            BufferedImage out;
            if (PNG.equals(mime)) {
                out = src;
            } else {
                out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = out.createGraphics();
                g.drawImage(src, 0, 0, java.awt.Color.WHITE, null);
                g.dispose();
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            String format = PNG.equals(mime) ? "png" : "jpg";
            if (!ImageIO.write(out, format, buf)) {
                throw new PrivateFileException("이미지를 저장 형식으로 변환하지 못했습니다.");
            }
            return buf.toByteArray();
        } catch (IOException e) {
            throw new PrivateFileException("이미지 변환에 실패했습니다.");
        }
    }

    private static BufferedImage decode(byte[] bytes) {
        try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
            return ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    private static String extensionOf(String mime) {
        return PNG.equals(mime) ? ".png" : ".jpg";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    /** 저장 결과. storageKey 는 서버 내부에서만 쓴다. */
    public record Stored(String storageKey, String contentType, long sizeBytes,
                         String sha256, Integer width, Integer height) {
    }
}
