package com.ssa.lms.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LXP 시험 데모 정적 화면(/v2/**) 회귀 테스트.
 *
 * <p><b>왜 필요한가:</b> 이 7개 화면은 Thymeleaf 가 아니라 {@code static/v2} 아래의
 * 순수 HTML 이라 컨트롤러 테스트에 걸리지 않는다. 파일이 지워지거나 경로가 바뀌면
 * 배포 후 404 로만 드러나는데, 실제로 같은 사고가 있었다
 * (v2 '수강생 로그인' 404 — 커밋 fe68b54/ebef5af).</p>
 *
 * <p>정적 자원이므로 로그인 없이 200 이어야 한다. {@code /v2/**} 는 SecurityConfig 에서
 * permitAll 이다. 응답이 끝까지 왔는지 {@code </html>} 로 확인하고, 페이지가 서로
 * 뒤바뀌지 않았는지 고유 제목으로 확인한다.</p>
 *
 * <p><b>범위 한계:</b> 이 테스트는 파일이 서빙되는지와 뼈대가 있는지만 본다.
 * 카메라 권한, 패널 리사이즈, 시뮬레이션 상태 전이 같은 브라우저 동작은
 * 자동 검증 대상이 아니라 수동 확인 항목이다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StaticV2ExamPagesTest {

    @Autowired MockMvc mvc;

    /** {경로, 해당 페이지에만 있는 제목} */
    private static final String[][] EXAM_PAGES = {
            { "/v2/lxp/trainee/exams.html",           "온라인 시험" },
            { "/v2/lxp/trainee/exam-precheck.html",   "응시환경 사전점검" },
            { "/v2/lxp/trainee/exam-id-upload.html",  "신분 확인" },
            { "/v2/lxp/trainee/exam-workspace.html",  "코딩 시험장" },
            { "/v2/admin/proctor.html",               "실시간 감독관제" },
            { "/v2/admin/proctor-review.html",        "사후 검토" },
            { "/v2/admin/execution-infra.html",       "실행환경 관제" }
    };

    private String body(String path) throws Exception {
        return mvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("시험 데모 7개 화면이 로그인 없이 200 으로 열리고 문서가 끝까지 온다")
    void allExamPagesServed() throws Exception {
        for (String[] page : EXAM_PAGES) {
            String html = body(page[0]);
            assertThat(html)
                    .as("%s 응답이 잘리지 않아야 한다", page[0])
                    .contains("</html>");
            assertThat(html)
                    .as("%s 에 <main> 본문 영역이 있어야 한다", page[0])
                    .contains("<main");
        }
    }

    @Test
    @DisplayName("각 화면이 고유한 제목을 가진다 — 파일이 서로 뒤바뀌지 않았다")
    void eachPageHasItsOwnTitle() throws Exception {
        for (String[] page : EXAM_PAGES) {
            assertThat(body(page[0]))
                    .as("%s 의 제목에 '%s' 가 있어야 한다", page[0], page[1])
                    .contains(page[1]);
        }
    }

    @Test
    @DisplayName("공용 자산 4종이 함께 서빙된다")
    void sharedAssetsServed() throws Exception {
        String[] assets = {
                "/v2/assets/exam.css",
                "/v2/assets/exam-demo-data.js",
                "/v2/assets/exam-common.js",
                "/v2/assets/exam-workspace.js"
        };
        for (String asset : assets) {
            mvc.perform(get(asset)).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("새 화면에 '준비 중인 기능입니다' 안내가 남아 있지 않다")
    void noPlaceholderLinks() throws Exception {
        for (String[] page : EXAM_PAGES) {
            assertThat(body(page[0]))
                    .as("%s 에 미완성 링크가 남아 있으면 안 된다", page[0])
                    .doesNotContain("준비 중인 기능입니다");
        }
    }

    @Test
    @DisplayName("모의 동작 화면에 데모 표시가 있다 — 실제 연동으로 오인시키지 않는다")
    void simulationPagesAreLabelled() throws Exception {
        String[] mustBeLabelled = {
                "/v2/lxp/trainee/exam-workspace.html",
                "/v2/admin/proctor.html",
                "/v2/admin/proctor-review.html",
                "/v2/admin/execution-infra.html"
        };
        for (String path : mustBeLabelled) {
            String html = body(path);
            assertThat(html.contains("시뮬레이션") || html.contains("데모"))
                    .as("%s 에 데모/시뮬레이션 표시가 있어야 한다", path)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("데모 데이터에 실제 개인정보 형태의 값이 없다")
    void demoDataHasNoPersonalNumbers() throws Exception {
        String data = body("/v2/assets/exam-demo-data.js");
        // 주민등록번호 형태(6자리-7자리)가 들어가면 안 된다
        assertThat(data).doesNotMatch("(?s).*\\d{6}\\s*-\\s*\\d{7}.*");
    }

    // ================================================================
    //  아래 3건은 "운영 화면을 깨뜨리지 않는다"는 조건을 코드로 고정한다.
    //  exam.css 는 운영 CSS(basic-form-trainee / common-style / sidebar-style)
    //  위에 얹히므로, 범위가 풀린 규칙 하나가 운영 전 화면에 새어 나간다.
    //  실제로 .video-item / .info-section / .modal-backdrop 처럼 운영에도 있는
    //  이름을 쓰기 때문에 눈으로는 늦게 발견된다.
    // ================================================================

    /** 7개 데모 화면의 페이지 루트 클래스 — 각 화면 &lt;body&gt; 에 붙는다. */
    private static final String[] PAGE_ROOTS = {
            ".exam-demo-page", ".exam-precheck-page", ".exam-id-page", ".exam-workspace",
            ".proctor-page", ".proctor-review-page", ".execution-infra-page"
    };

    /** 주석을 걷어내고 중괄호를 세어 규칙 선택자만 뽑는다(@media 등 at-rule 은 건너뛴다). */
    private static List<String> selectorsOf(String css) {
        String stripped = css.replaceAll("(?s)/\\*.*?\\*/", "");
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c == '{') {
                String sel = buf.toString().trim();
                buf.setLength(0);
                if (!sel.isEmpty() && !sel.startsWith("@")) out.add(sel);
            } else if (c == '}') {
                buf.setLength(0);
            } else {
                buf.append(c);
            }
        }
        return out;
    }

    @Test
    @DisplayName("exam.css 의 모든 규칙이 7개 페이지 루트 클래스 아래로 범위 제한되어 있다")
    void examCssIsScopedToPageRoots() throws Exception {
        List<String> leaked = new ArrayList<>();
        for (String selector : selectorsOf(body("/v2/assets/exam.css"))) {
            for (String part : selector.split(",")) {
                String one = part.trim().replaceAll("\\s+", " ");
                if (one.isEmpty()) continue;
                boolean scoped = false;
                for (String root : PAGE_ROOTS) {
                    if (one.startsWith(root)) { scoped = true; break; }
                }
                if (!scoped) leaked.add(one);
            }
        }
        assertThat(leaked)
                .as("페이지 루트 클래스로 시작하지 않는 선택자는 운영 화면까지 영향을 준다")
                .isEmpty();
    }

    @Test
    @DisplayName("각 데모 화면의 body 가 자기 페이지 루트 클래스를 달고 있다 — 없으면 스타일이 전부 죽는다")
    void everyDemoPageCarriesItsRootClassOnBody() throws Exception {
        String[][] pageRoot = {
                { "/v2/lxp/trainee/exams.html",          "exam-demo-page" },
                { "/v2/lxp/trainee/exam-precheck.html",  "exam-precheck-page" },
                { "/v2/lxp/trainee/exam-id-upload.html", "exam-id-page" },
                { "/v2/lxp/trainee/exam-workspace.html", "exam-workspace" },
                { "/v2/admin/proctor.html",              "proctor-page" },
                { "/v2/admin/proctor-review.html",       "proctor-review-page" },
                { "/v2/admin/execution-infra.html",      "execution-infra-page" }
        };
        for (String[] pr : pageRoot) {
            assertThat(body(pr[0]))
                    .as("%s 의 <body> 에 %s 가 있어야 한다", pr[0], pr[1])
                    .contains("<body class=\"" + pr[1] + "\">");
        }
    }

    @Test
    @DisplayName("CSS 미디어쿼리와 JS matchMedia 의 경계값이 같다 — 어긋나면 경계 픽셀에서 상태가 깨진다")
    void breakpointsAgreeBetweenCssAndJs() throws Exception {
        String css = body("/v2/assets/exam.css");

        // 3분할 → 탭 전환 (1023)
        assertThat(css).contains("max-width: 1023px");
        for (String js : new String[] { "/v2/assets/exam-workspace.js", "/v2/assets/proctor-demo.js" }) {
            assertThat(body(js))
                    .as("%s 의 탭 전환 경계값", js)
                    .contains("matchMedia(\"(max-width: 1023px)\")");
        }

        // 관리자 사이드바 자동 접힘 (768)
        assertThat(css).contains("max-width: 768px");
        assertThat(body("/v2/assets/exam-legacy-shell.js"))
                .as("사이드바 자동 접힘 경계값")
                .contains("\"(max-width: 768px)\"");
    }

    // ================================================================
    //  자산 · 셸 회귀
    //  이 화면들은 Thymeleaf 가 아니라 정적 파일이라, 자산 하나가 빠지거나
    //  경로가 바뀌어도 컴파일도 렌더 테스트도 통과한다. 배포 후 404 로만 드러난다.
    // ================================================================

    /** 시험 데모 전용 JS 자산 — 이 목록에서 빠지면 어느 화면인가는 반드시 깨진다. */
    private static final String[] EXAM_JS_ASSETS = {
            "/v2/assets/exam-common.js",
            "/v2/assets/exam-demo-data.js",
            "/v2/assets/exam-hub.js",
            "/v2/assets/exam-id-upload.js",
            "/v2/assets/exam-legacy-shell.js",
            "/v2/assets/exam-precheck.js",
            "/v2/assets/exam-workspace.js",
            "/v2/assets/execution-infra.js",
            "/v2/assets/proctor-demo.js",
            "/v2/assets/proctor-review.js"
    };

    private static final String[] TRAINEE_PAGES = {
            "/v2/lxp/trainee/exams.html",
            "/v2/lxp/trainee/exam-precheck.html",
            "/v2/lxp/trainee/exam-id-upload.html",
            "/v2/lxp/trainee/exam-workspace.html"
    };

    private static final String[] ADMIN_PAGES = {
            "/v2/admin/proctor.html",
            "/v2/admin/proctor-review.html",
            "/v2/admin/execution-infra.html"
    };

    @Test
    @DisplayName("시험 데모 JS 자산 10종이 모두 200 으로 서빙된다")
    void allExamJsAssetsServed() throws Exception {
        assertThat(EXAM_JS_ASSETS).as("자산 목록이 10개여야 한다").hasSize(10);
        for (String asset : EXAM_JS_ASSETS) {
            mvc.perform(get(asset))
                    .andExpect(status().isOk());
            assertThat(body(asset)).as("%s 가 빈 파일이면 안 된다", asset).isNotBlank();
        }
    }

    @Test
    @DisplayName("정적 시험 셸(exam-legacy-shell.js)이 200 으로 서빙되고 훈련생·관리자 셸을 모두 노출한다")
    void examLegacyShellServed() throws Exception {
        String js = body("/v2/assets/exam-legacy-shell.js");
        // 화면 6개가 ExamShell.trainee / ExamShell.admin / ExamShell.footer 를 호출한다.
        assertThat(js).contains("window.ExamShell");
        assertThat(js).contains("trainee:");
        assertThat(js).contains("admin:");
        assertThat(js).contains("footer:");
    }

    @Test
    @DisplayName("화면마다 자기가 호출하는 셸 스크립트를 실제로 로드한다 — 스크립트 없이 호출하면 즉시 ReferenceError")
    void everyPageLoadsTheShellItCalls() throws Exception {
        for (String[] page : EXAM_PAGES) {
            String html = body(page[0]);
            if (html.contains("ExamShell.")) {
                assertThat(html)
                        .as("%s 는 ExamShell 을 호출하므로 exam-legacy-shell.js 를 로드해야 한다", page[0])
                        .contains("/v2/assets/exam-legacy-shell.js");
            }
        }
    }

    @Test
    @DisplayName("훈련생 시험 화면이 훈련생 운영 CSS 와 exam.css 를 로드한다 — 관리자 CSS 는 섞지 않는다")
    void traineePagesLoadTraineeOperationalCss() throws Exception {
        for (String path : TRAINEE_PAGES) {
            String html = body(path);
            assertThat(html).as("%s : 훈련생 토큰(--header-height:100px)", path)
                    .contains("/static/css/basic-form-trainee.css");
            assertThat(html).as("%s : 공통 버튼", path).contains("/static/css/btn-style.css");
            assertThat(html).as("%s : 데모 전용 스타일", path).contains("/v2/assets/exam.css");
            // --header-height 가 100 vs 70 으로 충돌하므로 관리자 CSS 를 함께 로드하면 안 된다
            assertThat(html).as("%s : 관리자 CSS 를 섞으면 헤더 높이가 충돌한다", path)
                    .doesNotContain("/static/css/common-style.css")
                    .doesNotContain("/static/css/sidebar-style.css");
        }
    }

    @Test
    @DisplayName("관리자 시험 화면이 관리자 운영 CSS 와 exam.css 를 로드한다 — 훈련생 CSS 는 섞지 않는다")
    void adminPagesLoadAdminOperationalCss() throws Exception {
        for (String path : ADMIN_PAGES) {
            String html = body(path);
            assertThat(html).as("%s : 관리자 토큰(--header-height:70px)", path)
                    .contains("/static/css/common-style.css");
            assertThat(html).as("%s : 사이드바", path).contains("/static/css/sidebar-style.css");
            assertThat(html).as("%s : 공통 버튼", path).contains("/static/css/btn-style.css");
            assertThat(html).as("%s : 데모 전용 스타일", path).contains("/v2/assets/exam.css");
            assertThat(html).as("%s : 훈련생 CSS 를 섞으면 헤더 높이가 충돌한다", path)
                    .doesNotContain("/static/css/basic-form-trainee.css");
        }
    }

    @Test
    @DisplayName("7개 화면이 폐기한 /v2 디자인 시스템(tokens/base/components.css)을 로드하지 않는다")
    void examPagesDoNotLoadRetiredV2DesignSystem() throws Exception {
        for (String[] page : EXAM_PAGES) {
            assertThat(body(page[0]))
                    .as("%s 는 운영 UI 문법만 쓴다", page[0])
                    .doesNotContain("/v2/assets/tokens.css")
                    .doesNotContain("/v2/assets/base.css")
                    .doesNotContain("/v2/assets/components.css");
        }
    }

    @Test
    @DisplayName("정적 시험 화면과 셸에 CSRF 토큰 없는 /logout POST 폼이 없다 — 있으면 눌러도 403 이다")
    void noTokenlessLogoutPostFormInStaticExamShell() throws Exception {
        // 정적 HTML 에는 Thymeleaf 가 CSRF 히든필드를 넣어 주지 못하고
        // SecurityConfig 의 CSRF 예외는 /h2-console/** 뿐이다.
        List<String> sources = new ArrayList<>();
        for (String[] page : EXAM_PAGES) sources.add(page[0]);
        sources.add("/v2/assets/exam-legacy-shell.js");

        for (String path : sources) {
            String src = body(path);
            // 폼 action 으로 /logout 을 겨냥하는 순간(HTML 이든 JS 문자열 조립이든) 토큰 없는 POST 가 된다.
            assertThat(src)
                    .as("%s 에 동작하지 않는 로그아웃 POST 폼이 남아 있다", path)
                    .doesNotContain("action=\"/logout\"");
            assertThat(src)
                    .as("%s 에 <form ... /logout ... method=post> 조합이 남아 있다", path)
                    .doesNotContainPattern("(?s)<form[^>]{0,120}/logout");
        }
    }

    // ================================================================
    //  동작 회귀 — 문자열 한 줄이 아니라 "그 함수가 그 자리에서 불리는지"를 본다
    // ================================================================

    /** {@code needle} 이 처음 나오는 지점부터 {@code len} 글자를 잘라 온다(없으면 빈 문자열). */
    private static String sliceFrom(String src, String needle, int len) {
        int i = src.indexOf(needle);
        if (i < 0) return "";
        return src.substring(i, Math.min(src.length(), i + len));
    }

    /**
     * 주석을 걷어내고 실행되는 코드만 남긴다.
     * "이렇게 하면 안 된다"고 설명한 주석 문구까지 금지 패턴으로 잡히면 안 되기 때문이다.
     * 줄 주석은 줄 첫머리 형태만 지운다 — 문자열 안의 {@code http://...} 를 깨뜨리지 않기 위해서다.
     */
    private static String codeOnly(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ")
                  .replaceAll("(?m)^\\s*//.*$", " ");
    }

    @Test
    @DisplayName("편집기의 Tab 들여쓰기가 저장 함수를 부른다 — 안 부르면 문제·언어 전환에서 Tab 입력이 사라진다")
    void tabIndentPersistsThroughSaveFunction() throws Exception {
        String js = body("/v2/assets/exam-workspace.js");

        assertThat(js).as("저장 로직이 한 함수로 뽑혀 있어야 한다")
                .contains("function saveCurrentCode()");

        // 저장 함수는 화면 state 와 sessionStorage 둘 다 갱신해야 한다.
        String saveFn = sliceFrom(js, "function saveCurrentCode()", 400);
        assertThat(saveFn).as("화면 state 갱신").contains("state.workspace.code[");
        assertThat(saveFn).as("sessionStorage 갱신").contains("E.patch(");

        // Tab 분기 안에서 저장 함수가 불려야 한다.
        // (스크립트로 value 를 바꾸면 input 이벤트가 발생하지 않는다)
        String tabBranch = sliceFrom(js, "if (e.key !== \"Tab\"", 600);
        assertThat(tabBranch).as("Tab 처리 분기를 찾지 못했다").isNotEmpty();
        assertThat(tabBranch).as("Tab 들여쓰기 직후 저장 호출이 없다")
                .contains("saveCurrentCode()");

        // 일반 입력 경로도 같은 함수를 쓴다.
        String inputHandler = sliceFrom(js, "codeArea.addEventListener(\"input\"", 300);
        assertThat(inputHandler).as("일반 입력도 같은 저장 함수를 써야 한다")
                .contains("saveCurrentCode()");
    }

    @Test
    @DisplayName("관리자 셸 토글이 aria-controls/aria-expanded 와 고유 id 로 사이드바를 가리킨다")
    void adminSidebarToggleIsWiredForScreenReaders() throws Exception {
        String js = body("/v2/assets/exam-legacy-shell.js");

        assertThat(js).as("사이드바에 고유 id 가 있어야 aria-controls 가 성립한다")
                .contains("var SIDEBAR_ID = \"examShellSidebar\"");
        assertThat(js).as("토글이 사이드바를 가리켜야 한다").contains("aria-controls=");
        assertThat(js).as("초기 마크업에도 aria-expanded 가 있어야 한다").contains("aria-expanded=\"");
        assertThat(js).as("상태가 바뀔 때 aria-expanded 도 갱신해야 한다")
                .contains("setAttribute(\"aria-expanded\"");

        // 좁은 화면 drawer: backdrop 과 Escape 로 닫힌다
        assertThat(js).as("drawer 뒤 backdrop").contains("shell-backdrop");
        assertThat(js).as("Escape 로 닫기").contains("\"Escape\"");
        assertThat(js).as("드로어 상태는 데스크톱 취향과 분리한다").contains("drawerOpen");

        // backdrop 스타일은 exam.css 에 있고, 데모 3개 화면으로 범위가 제한돼야 한다
        String css = body("/v2/assets/exam.css");
        assertThat(css).as("backdrop 스타일이 있어야 한다").contains(".shell-backdrop");
    }

    @Test
    @DisplayName("관리자 홈 링크가 라벨 전용 요소를 항상 만들고, 접기/펼치기가 홈 아이콘 SVG 를 건드리지 않는다")
    void adminHomeLinkKeepsItsIconWhenCollapsed() throws Exception {
        String js = body("/v2/assets/exam-legacy-shell.js");

        // ── 마크업: 라벨 <span> 은 접힘 여부와 무관하게 항상 생성되고, SVG 와 형제여야 한다.
        String markup = sliceFrom(js, "'<a class=\"logout shell-home\"", 420);
        assertThat(markup).as("홈 링크 마크업을 찾지 못했다").isNotEmpty();
        assertThat(markup).as("링크는 /admin 으로 가야 한다").contains("href=\"/admin\"");
        assertThat(markup).as("접힘 상태에서도 접근성 이름이 남아야 한다").contains("aria-label=\"관리자 홈으로\"");
        assertThat(markup).as("라벨 전용 요소가 항상 있어야 한다").contains("shell-home-label");
        assertThat(markup).as("라벨은 감출 때 hidden 속성만 붙인다").contains(" hidden");

        int labelEnd = markup.indexOf("</span>");
        int iconAt = markup.indexOf("iconHome(");
        assertThat(labelEnd).as("라벨 <span> 이 닫히지 않았다").isGreaterThan(-1);
        assertThat(iconAt)
                .as("홈 아이콘이 라벨 <span> 바깥의 형제로 있어야 한다 (안에 넣으면 라벨 조작이 아이콘을 지운다)")
                .isGreaterThan(labelEnd);

        // ── 상태 갱신: 라벨을 이름으로 찾아야 하고, 위치 기반 childNodes 접근은 없어야 한다.
        //    (주석은 걷어낸다 — "childNodes 를 쓰지 마라"는 설명까지 잡히면 안 된다)
        String paint = codeOnly(sliceFrom(js, "function paintSidebar(", 1200));
        assertThat(paint).as("paintSidebar 를 찾지 못했다").isNotEmpty();
        assertThat(paint).as("라벨을 명시적으로 조회해야 한다")
                .contains("querySelector(\".shell-home-label\")");
        assertThat(paint).as("라벨은 hidden 으로만 토글한다").contains(".hidden = ");
        assertThat(paint)
                .as("childNodes 로 홈 라벨을 고치면 접힘 시작 시 childNodes[0] 이 <svg> 라 아이콘이 지워진다")
                .doesNotContain("childNodes");
        assertThat(paint)
                .as("접기/펼치기에서 SVG 를 다시 만들면 안 된다")
                .doesNotContain("iconHome(");

        // 코드 전체에서 childNodes 기반 텍스트 덮어쓰기가 사라졌는지
        assertThat(codeOnly(js))
                .as("childNodes 로 노드 내용을 덮어쓰는 코드가 남아 있다")
                .doesNotContain("childNodes");
    }
}
