package com.ssa.lms.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class CommonSearchSeoContractTest {

    private static final Path RES = Paths.get("src/main/resources");
    private static final Path STATIC = RES.resolve("static");
    private static final Path TEMPLATES = RES.resolve("templates");

    private String read(Path path) {
        assertThat(Files.exists(path)).as("파일이 없습니다: %s", path).isTrue();
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("파일을 읽지 못했습니다: " + path, exception);
        }
    }

    @Test
    @DisplayName("[공통-008] 과정·후기·기업사례·공지 통합 검색 화면과 이동 경로가 있다")
    void integratedSearchScreenContract() {
        String html = read(STATIC.resolve("v2/site/search.html"));
        String script = read(STATIC.resolve("v2/assets/search-index.js"));
        String navigation = read(STATIC.resolve("v2/assets/page-section-navigation.js"));

        assertThat(html).contains("data-integrated-search")
                .contains("data-search-type=\"course\"")
                .contains("data-search-type=\"review\"")
                .contains("data-search-type=\"case\"")
                .contains("data-search-type=\"notice\"");
        assertThat(script).contains("history.replaceState")
                .contains("/v2/site/class/course.html")
                .contains("/v2/site/class/reviews.html")
                .contains("/v2/site/biz/cases.html")
                .contains("/v2/site/campus/counsel.html");
        assertThat(navigation).contains("/v2/site/search.html").contains("통합 검색");
    }

    @Test
    @DisplayName("[공통-008] canonical·공유 메타·구조화 데이터·사이트맵 계약이 있다")
    void seoContract() {
        String search = read(STATIC.resolve("v2/site/search.html"));
        String navigation = read(STATIC.resolve("v2/assets/page-section-navigation.js"));
        String sitemap = read(STATIC.resolve("sitemap.xml"));
        String robots = read(STATIC.resolve("robots.txt"));

        assertThat(search).contains("rel=\"canonical\"")
                .contains("property=\"og:image\"")
                .contains("application/ld+json")
                .contains("SearchAction");
        assertThat(navigation).contains("applyPublicSeo")
                .contains("twitter:card")
                .contains("WebPage")
                .contains("Organization");
        assertThat(sitemap).contains("<urlset").contains("/v2/site/search.html");
        assertThat(robots).contains("Sitemap:").contains("Disallow: /admin/");
    }

    @Test
    @DisplayName("[공통-008] 관리자 검색·SEO 화면은 페이지·사이트맵·색인 상태를 시연한다")
    void adminSeoScreenContract() {
        String html = read(TEMPLATES.resolve("admin/site/seo-management.html"));
        String script = read(STATIC.resolve("js/seo-management.js"));
        String menu = read(TEMPLATES.resolve("fragments/management.html"));

        assertThat(html).contains("data-seo-panel=\"pages\"")
                .contains("data-seo-panel=\"sitemap\"")
                .contains("data-seo-panel=\"search\"")
                .contains("페이지 제목")
                .contains("공유 이미지 URL")
                .contains("2차 연동 범위");
        assertThat(script).contains("localStorage")
                .contains("data-refresh-index")
                .doesNotContain("fetch(");
        assertThat(menu).contains("href=\"/admin/site/seo\"").contains("검색·SEO 관리");
    }
}
