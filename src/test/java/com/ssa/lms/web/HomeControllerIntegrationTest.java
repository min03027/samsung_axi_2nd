package com.ssa.lms.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HomeControllerIntegrationTest {

    private static final List<String> PUBLIC_PAGES = List.of(
            "/v2/index.html",
            "/v2/site/class/index.html",
            "/v2/site/campus/index.html",
            "/v2/site/campus/support.html",
            "/v2/site/campus/reviews.html",
            "/v2/site/campus/review-detail.html",
            "/v2/site/class/reviews.html",
            "/v2/site/biz/index.html",
            "/v2/site/biz/programs.html",
            "/v2/site/biz/cases.html",
            "/v2/site/lxp/index.html",
            "/v2/login"
    );

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("비로그인 루트 요청은 최신 통합 랜딩으로 이동한다")
    void anonymousRootRedirectsToTeamLanding() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/v2/index.html"));
    }

    @Test
    @DisplayName("핵심 공개 화면은 모두 정상 응답한다")
    void publicEntryPagesAreAvailable() throws Exception {
        for (String path : PUBLIC_PAGES) {
            mvc.perform(get(path))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("삼성 도메인용 공개 사이트 대표 주소는 각 정적 첫 화면으로 연결된다")
    void publicDirectoryRoutesForwardToIndexPages() throws Exception {
        assertForwarded("/v2", "/v2/index.html");
        assertForwarded("/v2/", "/v2/index.html");
        assertForwarded("/v2/site/campus", "/v2/site/campus/index.html");
        assertForwarded("/v2/site/campus/", "/v2/site/campus/index.html");
        assertForwarded("/v2/site/class", "/v2/site/class/index.html");
        assertForwarded("/v2/site/class/", "/v2/site/class/index.html");
        assertForwarded("/v2/site/biz", "/v2/site/biz/index.html");
        assertForwarded("/v2/site/biz/", "/v2/site/biz/index.html");
        assertForwarded("/v2/site/lxp", "/v2/site/lxp/index.html");
        assertForwarded("/v2/site/lxp/", "/v2/site/lxp/index.html");
    }

    @Test
    @DisplayName("공개 랜딩은 통합된 팀 페이지와 실제 섹션을 연결한다")
    void publicLandingLinksIntegratedTeamPagesAndSections() throws IOException {
        String campus = resourceText("static/v2/site/campus/index.html");
        String biz = resourceText("static/v2/site/biz/index.html");
        String bizEducationTypes = resourceText("static/v2/assets/biz-education-types.js");
        String navigation = resourceText("static/v2/assets/page-section-navigation.js");

        assertFalse(campus.contains("/v2/assets/shell.js"));
        assertFalse(biz.contains("/v2/assets/shell.js"));
        assertTrue(navigation.contains("/v2/site/campus/support.html"));
        assertTrue(navigation.contains("/v2/site/biz/programs.html"));
        assertTrue(navigation.contains("/v2/site/biz/cases.html"));
        for (String id : List.of("campus-history", "why-campus", "campus-credentials",
                "campus-network", "campus-courses", "career-support", "graduate-reviews", "campus-facilities")) {
            assertTrue(campus.contains("id=\"" + id + "\""), () -> "취업캠퍼스 섹션 누락: " + id);
        }
        for (String id : List.of("education-types", "ax-execution", "ax-diagnosis",
                "job-programs", "company-cases", "biz-contact")) {
            assertTrue(biz.contains("id=\"" + id + "\""), () -> "비즈워크넥트 섹션 누락: " + id);
        }
        assertTrue(biz.contains("id=\"biz-diagnosis-form\""), "AX 간편 진단 폼 누락");
        assertTrue(biz.contains("id=\"biz-diagnosis-result\""), "AX 간편 진단 결과 영역 누락");
        assertTrue(bizEducationTypes.contains("getElementById(\"biz-diagnosis-form\")"), "AX 간편 진단 동작 누락");
        assertTrue(bizEducationTypes.contains("href=\\\"#biz-contact\\\""), "진단 결과의 도입 문의 연결 누락");
    }

    private String resourceText(String path) throws IOException {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertForwarded(String requestPath, String targetPath) throws Exception {
        mvc.perform(get(requestPath))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(targetPath));
    }
}
