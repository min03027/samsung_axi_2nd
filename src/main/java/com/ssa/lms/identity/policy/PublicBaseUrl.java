package com.ssa.lms.identity.policy;

import java.net.URI;

/**
 * QR 에 넣을 공개 base URL 검증·정규화 (지적 7).
 *
 * <p>요청 {@code Host}/{@code X-Forwarded-Host} 를 그대로 믿으면 공격자가 헤더를 바꿔
 * QR 을 자기 도메인으로 만들 수 있다(호스트 헤더 주입). 설정값이 있으면 <b>요청 헤더를 보지 않는다.</b></p>
 *
 * <p>운영: {@code LMS_IDENTITY_PUBLIC_BASE_URL=https://lms.samsungax.com}</p>
 */
public final class PublicBaseUrl {

    private PublicBaseUrl() {
    }

    /**
     * 설정이 <b>주어졌는가</b>. 유효성과는 별개다.
     *
     * <p>이 구분이 없으면 "오타 난 운영 설정" 이 "설정 없음" 과 똑같이 취급되어
     * 요청 헤더 fallback 으로 넘어간다. 즉 환경변수 오타 하나가 공격자가 제어하는
     * {@code X-Forwarded-Host} 를 QR 에 싣는 결과가 된다 (P1-4).</p>
     */
    public static boolean isConfigured(String raw) {
        return raw != null && !raw.isBlank();
    }

    /**
     * 설정값을 검증하고 정규화한다.
     *
     * <p><b>fail-open 금지</b>: 값이 비어 있지 않은데 부적합하면 호출부는 fallback 으로
     * 넘어가면 안 되고 발급 자체를 실패시켜야 한다. {@link #require(String)} 을 쓰면
     * 그 판단이 강제된다.</p>
     *
     * @return 끝 슬래시가 제거된 base URL, 값이 없거나 부적합하면 null
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.strip();
        /* 끝 슬래시는 하나만 정규화한다 — 그대로 두면 "https://host//m/id/..." 가 된다. */
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!uri.isAbsolute()) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return null;
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return null;
        }
        /* user-info(user:pass@host), query, fragment 가 들어간 값은 거부한다 —
           QR 에 자격증명이 실리거나 경로가 잘리는 사고를 막는다. */
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            return null;
        }
        /* 경로가 남아 있으면(예: https://host/lms) 그대로 두되, 루트 슬래시만 있는 경우는 위에서 제거됐다. */
        return v;
    }

    /**
     * 설정이 있으면 <b>반드시 유효해야</b> 한다.
     *
     * @return 유효한 base URL, 또는 설정이 아예 없으면 null(호출부가 로컬 fallback 을 쓴다)
     * @throws InvalidPublicBaseUrlException 값이 비어 있지 않은데 부적합한 경우
     */
    public static String require(String raw) {
        if (!isConfigured(raw)) {
            return null;
        }
        String normalized = normalize(raw);
        if (normalized == null) {
            throw new InvalidPublicBaseUrlException(
                    "QR 공개 주소 설정(lms.identity.public-base-url)이 올바르지 않습니다. "
                            + "http(s) 절대 주소여야 하며 사용자 정보·질의문자열·프래그먼트를 넣을 수 없습니다.");
        }
        return normalized;
    }

    /** 운영 설정이 잘못됐다. 요청 헤더로 넘어가지 않고 여기서 멈춘다. */
    public static class InvalidPublicBaseUrlException extends RuntimeException {
        public InvalidPublicBaseUrlException(String message) {
            super(message);
        }
    }
}
