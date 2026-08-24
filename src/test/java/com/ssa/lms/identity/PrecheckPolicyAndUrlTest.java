package com.ssa.lms.identity;

import com.ssa.lms.identity.policy.PublicBaseUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QR 공개 base URL 검증·정규화 (지적 7).
 *
 * <p>운영 계약: {@code LMS_IDENTITY_PUBLIC_BASE_URL=https://lms.samsungax.com}
 * → QR 은 {@code https://lms.samsungax.com/m/id/{token}}</p>
 */
class PrecheckPolicyAndUrlTest {

    private static final String PROD = "https://lms.samsungax.com";

    @Test
    @DisplayName("[7] 운영 주소는 그대로 통과한다")
    void 운영주소() {
        assertThat(PublicBaseUrl.normalize(PROD)).isEqualTo(PROD);
        assertThat(PublicBaseUrl.normalize(PROD) + "/m/id/abc")
                .isEqualTo("https://lms.samsungax.com/m/id/abc");
    }

    @Test
    @DisplayName("[7] 끝 슬래시가 있어도 //m/id 가 되지 않는다")
    void 끝슬래시_정규화() {
        assertThat(PublicBaseUrl.normalize(PROD + "/")).isEqualTo(PROD);
        assertThat(PublicBaseUrl.normalize(PROD + "///")).isEqualTo(PROD);
        assertThat(PublicBaseUrl.normalize(PROD + "/") + "/m/id/t")
                .doesNotContain("//m/id")
                .isEqualTo("https://lms.samsungax.com/m/id/t");
    }

    @Test
    @DisplayName("[7] 설정이 없으면 null — 호출부가 로컬 fallback 을 쓴다")
    void 설정없음() {
        assertThat(PublicBaseUrl.normalize(null)).isNull();
        assertThat(PublicBaseUrl.normalize("")).isNull();
        assertThat(PublicBaseUrl.normalize("   ")).isNull();
    }

    @Test
    @DisplayName("[7] http/https 가 아닌 스킴은 거부한다")
    void 스킴_검증() {
        assertThat(PublicBaseUrl.normalize("ftp://lms.samsungax.com")).isNull();
        assertThat(PublicBaseUrl.normalize("javascript:alert(1)")).isNull();
        assertThat(PublicBaseUrl.normalize("lms.samsungax.com")).as("절대 URL 이 아니면 거부").isNull();
        assertThat(PublicBaseUrl.normalize("http://lms.samsungax.com")).isEqualTo("http://lms.samsungax.com");
    }

    @Test
    @DisplayName("[7] user-info · query · fragment 가 들어간 값은 거부한다")
    void 위험한_구성요소_거부() {
        assertThat(PublicBaseUrl.normalize("https://user:pass@lms.samsungax.com")).isNull();
        assertThat(PublicBaseUrl.normalize("https://lms.samsungax.com?a=b")).isNull();
        assertThat(PublicBaseUrl.normalize("https://lms.samsungax.com#frag")).isNull();
    }

    @Test
    @DisplayName("[7] 호스트가 없는 값은 거부한다")
    void 호스트_없음() {
        assertThat(PublicBaseUrl.normalize("https://")).isNull();
        assertThat(PublicBaseUrl.normalize("https:///path")).isNull();
    }
}
