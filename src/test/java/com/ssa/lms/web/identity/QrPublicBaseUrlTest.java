package com.ssa.lms.web.identity;

import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.entity.ExamIdentityAuditLog;
import com.ssa.lms.identity.entity.ExamIdentityToken;
import com.ssa.lms.identity.repository.ExamIdentityAuditLogRepository;
import com.ssa.lms.identity.repository.ExamIdentityTokenRepository;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.identity.support.IdentityTestFixture;
import com.ssa.lms.web.trainee.exam.precheck.TraineePrecheckController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QR 공개 주소가 <b>절대 요청 헤더로 새지 않는지</b> (P1-4).
 *
 * <p><b>무엇이 문제였나</b><br>
 * {@code normalize()} 는 "설정 없음" 과 "설정이 있는데 잘못됨" 을 <b>둘 다 null</b> 로 돌려줬고,
 * 컨트롤러는 null 이면 요청 헤더 fallback 을 썼다. 즉 운영 환경변수의 오타 하나가
 * 공격자가 넣은 {@code X-Forwarded-Host} 를 QR 목적지로 만드는 경로였다.
 * 게다가 {@code baseFromConfig} 는 원문이 비어 있지 않기만 하면 true 라, fallback 을 탔는데도
 * "설정을 썼다" 고 보고했다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class QrPublicBaseUrlTest {

    private static final String PROD = "https://lms.samsungax.com";
    private static final String EVIL = "attacker.example.com";

    @Autowired MockMvc mvc;
    @Autowired ExamIdentityService identityService;
    @Autowired TraineePrecheckController controller;
    @Autowired ExamIdentityTokenRepository tokenRepository;
    @Autowired ExamIdentityAuditLogRepository auditRepository;
    @Autowired IdentityTestFixture fixture;

    private Long freshSessionId() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        return s.getId();
    }

    private void setConfig(String value) {
        ReflectionTestUtils.setField(controller, "publicBaseUrl", value);
    }

    /* ===================== 유효한 운영 설정 ===================== */

    @Test
    @DisplayName("[P1-4] 운영 설정이면 QR 은 정확히 https://lms.samsungax.com/m/id/{token}")
    @WithUserDetails("trainee1")
    void 운영설정_정확한_URL() throws Exception {
        Long sid = freshSessionId();
        setConfig(PROD);
        try {
            mvc.perform(post("/trainee/exam/precheck/{id}/identity/qr", sid).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.baseFromConfig").value(true))
                    .andExpect(jsonPath("$.url").value(containsString(PROD + "/m/id/")))
                    .andExpect(jsonPath("$.url").value(not(containsString("//m/id"))));
        } finally {
            setConfig("");
        }
    }

    @Test
    @DisplayName("[P1-4] 끝 슬래시가 있어도 //m/id 가 생기지 않는다")
    @WithUserDetails("trainee1")
    void 끝슬래시_정규화() throws Exception {
        Long sid = freshSessionId();
        setConfig(PROD + "///");
        try {
            mvc.perform(post("/trainee/exam/precheck/{id}/identity/qr", sid).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value(containsString(PROD + "/m/id/")))
                    .andExpect(jsonPath("$.url").value(not(containsString("//m/id"))));
        } finally {
            setConfig("");
        }
    }

    @Test
    @DisplayName("[P1-4] 설정이 유효하면 악성 X-Forwarded-Host/Proto 를 완전히 무시한다")
    @WithUserDetails("trainee1")
    void 악성헤더_무시() throws Exception {
        Long sid = freshSessionId();
        setConfig(PROD);
        try {
            mvc.perform(post("/trainee/exam/precheck/{id}/identity/qr", sid)
                            .header("X-Forwarded-Host", EVIL)
                            .header("X-Forwarded-Proto", "http")
                            .header("Host", EVIL)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value(containsString(PROD + "/m/id/")))
                    .andExpect(jsonPath("$.url").value(not(containsString(EVIL))));
        } finally {
            setConfig("");
        }
    }

    /* ===================== 잘못된 설정 — fail-closed ===================== */

    @Test
    @DisplayName("[P1-4] 설정이 비어 있지 않은데 잘못됐으면 QR 발급이 실패하고 헤더로 대체하지 않는다")
    @WithUserDetails("trainee1")
    void 잘못된설정_발급실패() throws Exception {
        Long sid = freshSessionId();
        setConfig("lms.samsungax.com");     /* 스킴 없음 — 절대 URL 이 아니다 */
        try {
            String body = mvc.perform(post("/trainee/exam/precheck/{id}/identity/qr", sid)
                            .header("X-Forwarded-Host", EVIL)
                            .with(csrf()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body)
                    .as("설정 오류 응답에 공격자 호스트가 섞이면 안 된다")
                    .doesNotContain(EVIL);
        } finally {
            setConfig("");
        }
    }

    @Test
    @DisplayName("[P1-4] user-info 가 섞인 설정도 발급 실패로 막는다")
    @WithUserDetails("trainee1")
    void userinfo_설정_발급실패() throws Exception {
        Long sid = freshSessionId();
        setConfig("https://user:pass@lms.samsungax.com");
        try {
            mvc.perform(post("/trainee/exam/precheck/{id}/identity/qr", sid).with(csrf()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.ok").value(false));
        } finally {
            setConfig("");
        }
    }

    @Test
    @DisplayName("[P1-4] http/https 가 아닌 스킴도 발급 실패로 막는다")
    @WithUserDetails("trainee1")
    void 잘못된스킴_발급실패() throws Exception {
        Long sid = freshSessionId();
        setConfig("ftp://lms.samsungax.com");
        try {
            mvc.perform(post("/trainee/exam/precheck/{id}/identity/qr", sid).with(csrf()))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            setConfig("");
        }
    }

    /* ===================== 설정 없음 — 로컬 fallback ===================== */

    @Test
    @DisplayName("[P1-4] 설정이 없으면 로컬 fallback 을 쓰고 baseFromConfig=false 로 정직하게 보고한다")
    @WithUserDetails("trainee1")
    void 설정없음_로컬fallback() throws Exception {
        Long sid = freshSessionId();
        setConfig("");

        mvc.perform(post("/trainee/exam/precheck/{id}/identity/qr", sid).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.baseFromConfig").value(false))
                .andExpect(jsonPath("$.url").value(containsString("/m/id/")));
    }

    /* ===================== P0-A: 503 이 서버 상태를 바꾸지 않는가 =====================
       503 응답만 확인하는 것으로는 부족하다. 발급 순서가 잘못되면 응답이 503 이어도
       서버에는 새 토큰과 ISSUE_QR 감사 로그가 남고, 쓸 수 있던 기존 QR 이 폐기된다. */

    /** 이 세션의 토큰 행 수. */
    private int tokenCount(Long sessionId) {
        return tokenRepository.findAllBySessionId(sessionId).size();
    }

    /** 이 세션의 ISSUE_QR 감사 로그 수. */
    private long issueQrAudits(Long sessionId) {
        return auditRepository.countBySessionIdAndAction(sessionId, ExamIdentityAuditLog.Action.ISSUE_QR);
    }

    private ExamIdentityToken tokenOf(String rawToken) {
        return tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new AssertionError("발급한 토큰을 찾지 못했습니다."));
    }

    private static String sha256(String v) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(v.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("[P0-A] 시나리오 A — 잘못된 설정의 503 이 기존 QR·토큰·감사 로그를 건드리지 않는다")
    @WithUserDetails("trainee1")
    void 시나리오A_기존토큰_무부작용() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        Long sid = identityService.openSession(c.examId(), c.userId(), "127.0.0.1").getId();

        /* 1) 유효한 설정으로 첫 QR 발급 — 실제로 쓸 수 있어야 한다. */
        setConfig(PROD);
        ExamIdentityService.IssuedToken first;
        try {
            first = identityService.issueToken(sid, c.userId(), "127.0.0.1");
        } finally {
            setConfig("");
        }
        assertThat(identityService.describeMobile(first.rawToken()).blocked())
                .as("첫 QR 은 사용 가능해야 한다").isFalse();

        int tokensBefore = tokenCount(sid);
        long auditsBefore = issueQrAudits(sid);
        assertThat(tokenOf(first.rawToken()).getRevokedAt()).isNull();

        /* 2~3) 공개 주소를 잘못된 값으로 바꿔 같은 세션에서 재발급 요청 → 503 */
        setConfig("lms.samsungax.com");     /* 스킴 없음 */
        try {
            String body = mvc.perform(post("/trainee/exam/precheck/{id}/identity/qr", sid)
                            .header("X-Forwarded-Host", EVIL)
                            .with(csrf()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.ok").value(false))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(EVIL);
        } finally {
            setConfig("");
        }

        /* 4) 첫 토큰이 폐기되지 않았고 여전히 사용 가능해야 한다. */
        assertThat(tokenOf(first.rawToken()).getRevokedAt())
                .as("발급에 실패한 요청이 쓸 수 있던 QR 을 폐기하면 안 된다").isNull();
        assertThat(identityService.describeMobile(first.rawToken()).blocked())
                .as("503 이후에도 첫 QR 은 그대로 쓸 수 있어야 한다").isFalse();

        /* 5) 토큰 행 수와 ISSUE_QR 감사 로그 수가 요청 전후 같아야 한다. */
        assertThat(tokenCount(sid))
                .as("503 인데 새 토큰이 저장되면 안 된다").isEqualTo(tokensBefore);
        assertThat(issueQrAudits(sid))
                .as("발급하지 않았는데 ISSUE_QR 감사 로그가 남으면 안 된다").isEqualTo(auditsBefore);
    }

    @Test
    @DisplayName("[P0-A] 시나리오 B — 토큰이 없던 세션은 503 이후에도 토큰·감사 로그가 0건이다")
    @WithUserDetails("trainee1")
    void 시나리오B_새세션_흔적없음() throws Exception {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        Long sid = identityService.openSession(c.examId(), c.userId(), "127.0.0.1").getId();

        assertThat(tokenCount(sid)).as("아직 발급 전이므로 0 이어야 한다").isZero();
        assertThat(issueQrAudits(sid)).isZero();

        setConfig("https://user:pass@lms.samsungax.com");
        try {
            mvc.perform(post("/trainee/exam/precheck/{id}/identity/qr", sid).with(csrf()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.ok").value(false));
        } finally {
            setConfig("");
        }

        assertThat(tokenCount(sid))
                .as("설정 오류로 실패한 발급이 토큰을 남기면 안 된다").isZero();
        assertThat(issueQrAudits(sid))
                .as("설정 오류로 실패한 발급이 감사 로그를 남기면 안 된다").isZero();
        assertThat(sessionStatusOf(sid))
                .as("발급 실패가 세션 상태를 바꾸면 안 된다").isEqualTo("PENDING");
    }

    private String sessionStatusOf(Long sessionId) {
        return identityService.statusOf(sessionId, fixture.trainee1Id())
                .orElseThrow(() -> new AssertionError("세션 상태를 읽지 못했다"))
                .status();
    }
}
