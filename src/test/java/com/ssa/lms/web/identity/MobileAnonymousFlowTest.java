package com.ssa.lms.web.identity;

import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.entity.ExamIdentityToken;
import com.ssa.lms.identity.repository.ExamIdentitySessionRepository;
import com.ssa.lms.identity.repository.ExamIdentityTokenRepository;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.identity.support.IdentityTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QR 로 열리는 모바일 흐름을 <b>실제 익명 요청</b>으로 검증한다 (P1-7).
 *
 * <p><b>무엇이 문제였나</b><br>
 * 기존 {@code 토큰으로_모바일_열림()} 에는 {@code @WithUserDetails("trainee1")} 이 붙어 있었다.
 * 이름은 "로그인 없이 열린다" 인데 실제로는 <b>로그인 상태로</b> 요청했다. 즉 휴대폰에서
 * 로그인 세션 없이 열리는지를 한 번도 검증하지 않았다 — 사용자가 iPhone 으로 확인해 준
 * 바로 그 경로다.</p>
 *
 * <p>여기서는 어떤 인증도 붙이지 않는다. 업로드까지 익명 multipart 로 실제로 보낸다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MobileAnonymousFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ExamIdentityService identityService;
    @Autowired ExamIdentitySessionRepository sessionRepository;
    @Autowired ExamIdentityTokenRepository tokenRepository;
    @Autowired IdentityTestFixture fixture;

    private record Fresh(IdentityTestFixture.Ctx ctx, Long sessionId, String token) {
    }

    private Fresh freshToken() {
        IdentityTestFixture.Ctx c = fixture.newProctoredExamAndTrainee();
        ExamIdentitySession s = identityService.openSession(c.examId(), c.userId(), "127.0.0.1");
        ExamIdentityService.IssuedToken t = identityService.issueToken(s.getId(), c.userId(), "127.0.0.1");
        return new Fresh(c, s.getId(), t.rawToken());
    }

    /* ===================== 익명 GET ===================== */

    @Test
    @DisplayName("[P1-7] 로그인 없이 유효 토큰으로 모바일 화면이 열린다 — 인증 붙이지 않음")
    void 익명_모바일_열림() throws Exception {
        Fresh f = freshToken();

        MvcResult res = mvc.perform(get("/m/id/{token}", f.token()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("신분증 앞면 제출")))
                .andReturn();

        assertThat(res.getRequest().getUserPrincipal())
                .as("이 요청에는 인증 주체가 없어야 한다 — 있으면 익명 검증이 아니다").isNull();
    }

    /* ===================== 익명 업로드 ===================== */

    @Test
    @DisplayName("[P1-7] 익명 화면에서 받은 CSRF 토큰으로 실제 multipart 업로드가 성공한다")
    void 익명_업로드_성공() throws Exception {
        Fresh f = freshToken();

        /* 모바일 화면을 먼저 열어 CSRF 토큰과 세션 쿠키를 받는다 — 실제 브라우저와 같은 순서다. */
        MvcResult page = mvc.perform(get("/m/id/{token}", f.token()))
                .andExpect(status().isOk())
                .andReturn();
        String html = page.getResponse().getContentAsString();
        String csrf = extractCsrf(html);
        assertThat(csrf).as("모바일 화면이 CSRF 토큰을 내려줘야 업로드할 수 있다").isNotBlank();

        mvc.perform(multipart("/m/id/{token}/upload", f.token())
                        .file(IdentityTestFixture.imageFile("id.jpg"))
                        .param("consent", "true")
                        .param("_csrf", csrf)
                        .session((org.springframework.mock.web.MockHttpSession) page.getRequest().getSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.hasIdCard").value(true));

        /* 서버 상태로 확인한다 — 응답 문구만 보고 통과시키지 않는다. */
        assertThat(fixture.currentIdDocumentId(f.sessionId()))
                .as("익명 업로드가 실제로 저장돼야 한다").isNotNull();
    }

    @Test
    @DisplayName("[P1-7] 얼굴 사진이 아직 없으면 안내는 '검토 대기' 가 아니라 'PC 로 돌아가라' 다")
    void 익명_업로드_부분제출_안내() throws Exception {
        Fresh f = freshToken();

        MvcResult page = mvc.perform(get("/m/id/{token}", f.token())).andReturn();

        mvc.perform(multipart("/m/id/{token}/upload", f.token())
                        .file(IdentityTestFixture.imageFile("id.jpg"))
                        .param("consent", "true")
                        .param("_csrf", extractCsrf(page.getResponse().getContentAsString()))
                        .session((org.springframework.mock.web.MockHttpSession) page.getRequest().getSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionComplete").value(false))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value(containsString("웹캠 얼굴 사진을 제출")));
    }

    @Test
    @DisplayName("[P1-7] 얼굴 사진이 먼저 들어와 있으면 이번 신분증 제출로 검토 대기가 된다")
    void 익명_업로드_완전제출_안내() throws Exception {
        Fresh f = freshToken();
        fixture.uploadFaceCheck(f.sessionId());     /* PC 에서 얼굴을 먼저 제출한 경우 */

        MvcResult page = mvc.perform(get("/m/id/{token}", f.token())).andReturn();

        mvc.perform(multipart("/m/id/{token}/upload", f.token())
                        .file(IdentityTestFixture.imageFile("id.jpg"))
                        .param("consent", "true")
                        .param("_csrf", extractCsrf(page.getResponse().getContentAsString()))
                        .session((org.springframework.mock.web.MockHttpSession) page.getRequest().getSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionComplete").value(true))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.message").value(containsString("운영진 검토")));
    }

    /* ===================== 익명 업로드 차단 ===================== */

    @Test
    @DisplayName("[P1-7] 잘못된 토큰의 익명 업로드는 차단된다")
    void 익명_잘못된토큰_차단() throws Exception {
        mvc.perform(multipart("/m/id/{token}/upload", "NOT-A-REAL-TOKEN")
                        .file(IdentityTestFixture.imageFile("id.jpg"))
                        .param("consent", "true")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    @DisplayName("[P1-7] 만료된 토큰의 익명 업로드는 차단되고 저장되지 않는다")
    void 익명_만료토큰_차단() throws Exception {
        Fresh f = freshToken();
        expireToken(f.token());

        mvc.perform(multipart("/m/id/{token}/upload", f.token())
                        .file(IdentityTestFixture.imageFile("id.jpg"))
                        .param("consent", "true")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());

        assertThat(fixture.currentIdDocumentId(f.sessionId()))
                .as("만료 토큰으로 저장되면 안 된다").isNull();
    }

    @Test
    @DisplayName("[P1-7] 재발급으로 폐기된 이전 토큰의 익명 업로드는 차단된다")
    void 익명_폐기토큰_차단() throws Exception {
        Fresh f = freshToken();
        /* 새 QR 을 발급하면 이전 토큰은 서버에서 폐기된다. */
        identityService.issueToken(f.sessionId(), f.ctx().userId(), "127.0.0.1");

        mvc.perform(multipart("/m/id/{token}/upload", f.token())
                        .file(IdentityTestFixture.imageFile("id.jpg"))
                        .param("consent", "true")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());

        assertThat(fixture.currentIdDocumentId(f.sessionId())).isNull();
    }

    @Test
    @DisplayName("[P1-7] 사용 횟수를 넘긴 토큰의 익명 업로드는 차단된다")
    void 익명_사용횟수초과_차단() throws Exception {
        Fresh f = freshToken();
        exhaustToken(f.token());

        mvc.perform(multipart("/m/id/{token}/upload", f.token())
                        .file(IdentityTestFixture.imageFile("id.jpg"))
                        .param("consent", "true")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isBadRequest());

        assertThat(fixture.currentIdDocumentId(f.sessionId())).isNull();
    }

    /* ===================== 헬퍼 ===================== */

    /** {@code <meta name="_csrf" content="...">} 에서 값을 뽑는다. */
    private static String extractCsrf(String html) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("name=\"_csrf\"[^>]*content=\"([^\"]+)\"")
                .matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        m = java.util.regex.Pattern
                .compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        return m.find() ? m.group(1) : "";
    }

    private void expireToken(String rawToken) {
        ExamIdentityToken t = tokenOf(rawToken);
        ReflectionTestUtils.setField(t, "expiresAt", LocalDateTime.now().minusMinutes(1));
        tokenRepository.saveAndFlush(t);
    }

    private void exhaustToken(String rawToken) {
        ExamIdentityToken t = tokenOf(rawToken);
        ReflectionTestUtils.setField(t, "useCount", ExamIdentityToken.DEFAULT_MAX_USE);
        tokenRepository.saveAndFlush(t);
    }

    private ExamIdentityToken tokenOf(String rawToken) {
        return tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new IllegalStateException("발급한 토큰을 찾지 못했습니다."));
    }

    private static String sha256(String v) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(v.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
