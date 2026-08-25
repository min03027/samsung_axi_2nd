package com.ssa.lms.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LXP-140/141/142 학습 참여 확인·자리 이탈 데모 정적 화면(/v2/**) 회귀 테스트.
 *
 * <p>이 2개 화면은 {@link StaticV2LiveClassPagesTest} 가 지키는 화상강의 데모 3종과
 * 같은 이유로 별도 계약 테스트가 필요하다 — Thymeleaf 가 아니라 순수 정적 HTML/CSS/JS 라
 * 컨트롤러 테스트에 걸리지 않고, 자산 경로가 어긋나면 배포 후 404 로만 드러난다.</p>
 *
 * <p><b>범위 한계:</b> 이 테스트는 파일이 서빙되는지, 뼈대·자산·CSS 범위·데모 표시가
 * 있는지만 본다. 카메라 권한 팝업, 장치 선택 변경, 반응형 레이아웃은 자동 검증 대상이
 * 아니라 실기기 육안 확인 항목이다. 단, {@code learning-presence-common.js} 의 판정
 * 순수 함수(derivePresenceState/calculateLearningTime/classifyLearner/createMediaSlot)
 * 는 Node 로 실제 실행해 검증한다 — 문자열 존재 검사가 아니다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StaticV2LearningPresencePagesTest {

    @Autowired MockMvc mvc;

    /** {경로, 해당 페이지에만 있는 제목} */
    private static final String[][] PRESENCE_PAGES = {
            { "/v2/lxp/trainee/learning-presence-check.html", "학습 참여 확인" },
            { "/v2/admin/presence-monitor.html",              "학습 참여 모니터링" }
    };

    private static final String[] TRAINEE_PAGES = {
            "/v2/lxp/trainee/learning-presence-check.html"
    };

    private static final String[] ADMIN_PAGES = {
            "/v2/admin/presence-monitor.html"
    };

    /** {페이지 경로, <body> 에 붙어야 하는 루트 클래스} */
    private static final String[][] PAGE_ROOT = {
            { "/v2/lxp/trainee/learning-presence-check.html", "learning-presence-page" },
            { "/v2/admin/presence-monitor.html",              "presence-monitor-page" }
    };

    private static final String NEW_CSS = "/v2/assets/learning-presence.css";

    /** 신규 JS 자산 4종 — 이 목록에서 빠지면 어느 화면인가는 반드시 깨진다. */
    private static final String[] NEW_JS_ASSETS = {
            "/v2/assets/learning-presence-common.js",
            "/v2/assets/learning-presence-demo-data.js",
            "/v2/assets/learning-presence-check.js",
            "/v2/assets/presence-monitor.js"
    };

    /** 신규 정적 파일 7개(2 HTML + 1 CSS + 4 JS) — 외부 자원·금지 API 검사 대상. */
    private static final String[] NEW_STATIC_FILES = {
            "/v2/lxp/trainee/learning-presence-check.html",
            "/v2/admin/presence-monitor.html",
            "/v2/assets/learning-presence.css",
            "/v2/assets/learning-presence-common.js",
            "/v2/assets/learning-presence-demo-data.js",
            "/v2/assets/learning-presence-check.js",
            "/v2/assets/presence-monitor.js"
    };

    private String body(String path) throws Exception {
        return mvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ================================================================
    //  Task 1 · Step 1~8 — 서빙·제목·자산·루트클래스·CSS 분리·로드 순서·데모 표시
    // ================================================================

    @Test
    @DisplayName("[1] 학습 참여 데모 2개 화면이 로그인 없이 200 으로 열리고 문서가 끝까지 온다")
    void allPresencePagesServed() throws Exception {
        for (String[] page : PRESENCE_PAGES) {
            String html = body(page[0]);
            assertThat(html).as("%s 응답이 잘리지 않아야 한다", page[0]).contains("</html>");
            assertThat(html).as("%s 에 <main> 본문 영역이 있어야 한다", page[0]).contains("<main");
        }
    }

    @Test
    @DisplayName("[1] 신규 CSS 1개와 JS 4개가 200 이고 비어 있지 않다")
    void newAssetsServedAndNotEmpty() throws Exception {
        assertThat(NEW_JS_ASSETS).as("신규 JS 자산은 4개여야 한다").hasSize(4);
        List<String> assets = new ArrayList<>();
        assets.add(NEW_CSS);
        for (String js : NEW_JS_ASSETS) assets.add(js);
        for (String asset : assets) {
            mvc.perform(get(asset)).andExpect(status().isOk());
            assertThat(body(asset)).as("%s 가 빈 파일이면 안 된다", asset).isNotBlank();
        }
    }

    @Test
    @DisplayName("[2] 2개 화면이 서로 다른 고유 제목을 가진다 — 파일이 뒤바뀌지 않았다")
    void eachPageHasItsOwnTitle() throws Exception {
        for (String[] page : PRESENCE_PAGES) {
            assertThat(body(page[0]))
                    .as("%s 의 제목에 '%s' 가 있어야 한다", page[0], page[1])
                    .contains(page[1]);
        }
    }

    @Test
    @DisplayName("[2] 각 화면의 <body> 가 자기 페이지 루트 클래스를 달고 있다")
    void everyPageCarriesItsRootClassOnBody() throws Exception {
        for (String[] pr : PAGE_ROOT) {
            assertThat(body(pr[0]))
                    .as("%s 의 <body> 에 %s 가 있어야 한다", pr[0], pr[1])
                    .contains("<body class=\"" + pr[1] + "\">");
        }
    }

    @Test
    @DisplayName("[3] 훈련생 화면은 훈련생 운영 CSS + learning-presence.css 만 로드한다 — 관리자 CSS 는 섞지 않는다")
    void traineePageLoadsTraineeOperationalCssOnly() throws Exception {
        for (String path : TRAINEE_PAGES) {
            String html = body(path);
            assertThat(html).as("%s : 훈련생 운영 CSS", path).contains("/static/css/basic-form-trainee.css");
            assertThat(html).as("%s : 공통 버튼", path).contains("/static/css/btn-style.css");
            assertThat(html).as("%s : 데모 전용 스타일", path).contains("/v2/assets/learning-presence.css");
            assertThat(html).as("%s : 관리자 CSS 를 섞으면 헤더 높이가 충돌한다", path)
                    .doesNotContain("/static/css/common-style.css")
                    .doesNotContain("/static/css/sidebar-style.css");
        }
    }

    @Test
    @DisplayName("[3] 관리자 화면은 관리자 운영 CSS + learning-presence.css 만 로드한다 — 훈련생 CSS 는 섞지 않는다")
    void adminPageLoadsAdminOperationalCssOnly() throws Exception {
        for (String path : ADMIN_PAGES) {
            String html = body(path);
            assertThat(html).as("%s : 관리자 토큰", path).contains("/static/css/common-style.css");
            assertThat(html).as("%s : 사이드바", path).contains("/static/css/sidebar-style.css");
            assertThat(html).as("%s : 공통 버튼", path).contains("/static/css/btn-style.css");
            assertThat(html).as("%s : 데모 전용 스타일", path).contains("/v2/assets/learning-presence.css");
            assertThat(html).as("%s : 훈련생 CSS 를 섞으면 헤더 높이가 충돌한다", path)
                    .doesNotContain("/static/css/basic-form-trainee.css");
        }
    }

    @Test
    @DisplayName("[3] 두 화면 모두 exam-legacy-shell.js 를 로드한다 — 없으면 ExamShell 호출이 즉시 ReferenceError")
    void everyPageLoadsExamLegacyShell() throws Exception {
        for (String[] page : PRESENCE_PAGES) {
            String html = body(page[0]);
            assertThat(html).as("%s 는 ExamShell 을 호출해야 한다", page[0]).contains("ExamShell.");
            assertThat(html).as("%s 는 exam-legacy-shell.js 를 로드해야 한다", page[0])
                    .contains("/v2/assets/exam-legacy-shell.js");
        }
    }

    @Test
    @DisplayName("[4] 자산 로드 순서가 shell → common → demo-data → 화면 전용 JS 순이다")
    void assetsLoadInDependencyOrder() throws Exception {
        String[] ownAssets = { "/v2/assets/learning-presence-check.js", "/v2/assets/presence-monitor.js" };
        for (int i = 0; i < PRESENCE_PAGES.length; i++) {
            String path = PRESENCE_PAGES[i][0];
            String html = body(path);
            int s = html.indexOf("/v2/assets/exam-legacy-shell.js");
            int c = html.indexOf("/v2/assets/learning-presence-common.js");
            int d = html.indexOf("/v2/assets/learning-presence-demo-data.js");
            int o = html.indexOf(ownAssets[i]);
            assertThat(s).as("%s: shell 로드 위치", path).isGreaterThanOrEqualTo(0);
            assertThat(c).as("%s: common 이 shell 뒤", path).isGreaterThan(s);
            assertThat(d).as("%s: demo-data 가 common 뒤", path).isGreaterThan(c);
            assertThat(o).as("%s: 화면 전용 JS 가 demo-data 뒤", path).isGreaterThan(d);
        }
    }

    @Test
    @DisplayName("[5] 모든 화면에 데모/실제 얼굴인식 없음/저장 없음/출결 미반영 안내가 있고 미완성 안내는 없다")
    void demoLabelPresentAndNoPlaceholder() throws Exception {
        for (String[] page : PRESENCE_PAGES) {
            String html = body(page[0]);
            assertThat(html).as("%s 에 데모 표시가 있어야 한다", page[0]).contains("데모");
            assertThat(html).as("%s 에 실제 얼굴 인식이 없다는 안내가 있어야 한다", page[0]).contains("실제 얼굴 인식");
            assertThat(html.contains("저장") || html.contains("전송되지 않")).as("%s 에 저장 없음 안내가 있어야 한다", page[0]).isTrue();
            assertThat(html).as("%s 에 출결 미반영 안내가 있어야 한다", page[0]).contains("출결");
            assertThat(html).as("%s 에 미완성 안내가 남아 있으면 안 된다", page[0])
                    .doesNotContain("준비 중인 기능입니다");
        }
    }

    @Test
    @DisplayName("[6] 신규 정적 파일 7개 전체에 외부 URL·CDN·실시간/비동기 통신 API 가 없다")
    void noExternalResourcesOrForbiddenCommunicationApis() throws Exception {
        assertThat(NEW_STATIC_FILES).as("신규 정적 파일은 7개여야 한다").hasSize(7);
        String[] forbidden = { "http://", "https://", "fetch(", "XMLHttpRequest", "WebSocket", "EventSource", "sendBeacon", "RTCPeerConnection" };
        for (String path : NEW_STATIC_FILES) {
            String src = body(path);
            for (String needle : forbidden) {
                assertThat(src).as("%s 에 금지된 패턴 '%s' 이 있으면 안 된다", path, needle)
                        .doesNotContain(needle);
            }
        }
    }

    @Test
    @DisplayName("[6] 신규 JS 4개에 브라우저 저장소(localStorage/sessionStorage/IndexedDB/Cache API) 가 없다")
    void noBrowserStorageApis() throws Exception {
        String[] forbidden = { "localStorage", "sessionStorage", "indexedDB", "caches.open" };
        for (String path : NEW_JS_ASSETS) {
            String src = body(path);
            for (String needle : forbidden) {
                assertThat(src).as("%s 에 금지된 저장소 API '%s' 가 있으면 안 된다", path, needle)
                        .doesNotContain(needle);
            }
        }
    }

    @Test
    @DisplayName("[7] 데모 데이터에 실제 개인정보 형태의 값이 없다")
    void demoDataHasNoPersonalInfoPatterns() throws Exception {
        String data = body("/v2/assets/learning-presence-demo-data.js");
        assertThat(data).as("주민등록번호 형태가 없어야 한다").doesNotMatch("(?s).*\\d{6}\\s*-\\s*\\d{7}.*");
        assertThat(data).as("전화번호 형태가 없어야 한다").doesNotMatch("(?s).*01\\d-\\d{3,4}-\\d{4}.*");
        assertThat(data).as("이메일 형태가 없어야 한다").doesNotMatch("(?s).*[\\w.+-]+@[\\w-]+\\.[\\w.-]+.*");
        assertThat(data).as("얼굴 이미지 URL을 넣지 않는다").doesNotMatch("(?s).*\\.(jpg|jpeg|png|gif|webp).*");
    }

    private static final Pattern ID_ATTR = Pattern.compile("\\bid=\"([^\"]+)\"");

    @Test
    @DisplayName("[7] 2개 화면 각각 중복 id 가 없다")
    void noDuplicateIdsWithinEachPage() throws Exception {
        for (String[] page : PRESENCE_PAGES) {
            String html = body(page[0]);
            Set<String> seen = new HashSet<>();
            List<String> dup = new ArrayList<>();
            Matcher m = ID_ATTR.matcher(html);
            while (m.find()) {
                String id = m.group(1);
                if (!seen.add(id)) dup.add(id);
            }
            assertThat(dup).as("%s 에 중복된 id 가 있으면 안 된다", page[0]).isEmpty();
        }
    }

    @Test
    @DisplayName("[7] 2개 화면에 href=\"#\" 더미 링크가 없고 CSRF 토큰 없는 /logout 폼도 없다")
    void noHashLinksAndNoTokenlessLogoutForm() throws Exception {
        for (String[] page : PRESENCE_PAGES) {
            String src = body(page[0]);
            assertThat(src).as("%s 에 href=\"#\" 가 있으면 안 된다", page[0]).doesNotContain("href=\"#\"");
            assertThat(src).as("%s 에 동작하지 않는 로그아웃 POST 폼이 남아 있다", page[0]).doesNotContain("action=\"/logout\"");
        }
    }

    /** href/src 가 가리키는 로컬 절대경로 자산이 실제로 200 인지 확인한다(깨진 참조 검출).
        /v2/, /static/ 로 시작하는 정적 자산만 본다 — /trainee/contents 같은 로그인 필요
        애플리케이션 라우트는 미로그인 시 302 로 리다이렉트되는 것이 정상이라 대상이 아니다. */
    private static final Pattern ASSET_REF = Pattern.compile("(?:href|src)=\"(/(?:v2|static)/[^\"]+)\"");

    @Test
    @DisplayName("[9] 두 화면이 참조하는 로컬 정적 자산(href/src)에 깨진 링크가 없다")
    void noBrokenInternalAssetReferences() throws Exception {
        for (String[] page : PRESENCE_PAGES) {
            String html = body(page[0]);
            Matcher m = ASSET_REF.matcher(html);
            Set<String> refs = new HashSet<>();
            while (m.find()) refs.add(m.group(1));
            for (String ref : refs) {
                mvc.perform(get(ref)).andExpect(status().isOk());
            }
        }
    }

    // ================================================================
    //  CSS 범위 제한 (Task 5)
    // ================================================================

    /** 주석을 걷어내고 중괄호를 세어 규칙 선택자만 뽑는다(@media 등 at-rule 은 건너뛴다). */
    private static List<String> presenceSelectorsOf(String css) {
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

    private static final String[] PRESENCE_ROOTS = { ".learning-presence-page", ".presence-monitor-page" };

    @Test
    @DisplayName("[8] learning-presence.css 의 모든 규칙이 2개 페이지 루트 클래스 아래로 범위 제한되어 있다")
    void presenceCssIsScopedToPageRoots() throws Exception {
        List<String> leaked = new ArrayList<>();
        for (String selector : presenceSelectorsOf(body(NEW_CSS))) {
            for (String part : selector.split(",")) {
                String one = part.trim().replaceAll("\\s+", " ");
                if (one.isEmpty()) continue;
                boolean scoped = false;
                for (String root : PRESENCE_ROOTS) {
                    if (one.startsWith(root)) { scoped = true; break; }
                }
                if (!scoped) leaked.add(one);
            }
        }
        assertThat(leaked)
                .as("페이지 루트 클래스로 시작하지 않는 선택자는 운영/기존 데모 화면까지 영향을 준다")
                .isEmpty();
    }

    @Test
    @DisplayName("[8] [hidden] 규칙이 2개 루트별로만 선언되어 있고, html/body/.btn/.modal/table 단독 규칙이 없다")
    void hiddenRulePerRootAndNoBareGlobalRules() throws Exception {
        String css = body(NEW_CSS);
        for (String root : PRESENCE_ROOTS) {
            assertThat(css).as("%s 에 [hidden] 규칙이 있어야 한다", root).contains(root + " [hidden]");
        }
        for (String selector : presenceSelectorsOf(css)) {
            for (String part : selector.split(",")) {
                String one = part.trim().replaceAll("\\s+", " ");
                assertThat(one).as("전역 단독 규칙 금지").isNotIn("html", "body", ".btn", ".modal", "table");
            }
        }
    }

    // ================================================================
    //  필수 컨트롤 존재 확인 (Task 3 · Task 4)
    // ================================================================

    @Test
    @DisplayName("[QA 발견] check.js 가 cameraSelect 의 change 를 실제로 처리한다 — 없으면 장치 전환이 조용히 아무 일도 안 한다")
    void checkJsHandlesCameraSelectChange() throws Exception {
        String js = body("/v2/assets/learning-presence-check.js");
        assertThat(js).as("cameraSelect 에 change 리스너가 있어야 한다").contains("cameraSelect.addEventListener(\"change\"");
        String handler = sliceFrom(js, "cameraSelect.addEventListener(\"change\"", 200);
        assertThat(handler).as("change 리스너가 카메라를 다시 시작해야 한다").contains("startCamera()");
    }

    @Test
    @DisplayName("[QA 발견] check.js 가 select 에 없는 deviceId 를 그대로 대입해 선택을 무너뜨리지 않는다")
    void checkJsGuardsSelectRestoreAgainstUnknownDeviceId() throws Exception {
        String js = body("/v2/assets/learning-presence-check.js");
        assertThat(js).as("존재 여부 확인 헬퍼가 있어야 한다").contains("function selectHasOption(");
        String restore = sliceFrom(js, "function restoreSelectAfterFailedSwitch(", 300);
        assertThat(restore).as("select 복원 전 selectHasOption() 으로 실제 옵션인지 확인해야 한다 — " +
                "그렇지 않으면 canvas.captureStream() 류의 트랙이 돌려주는, 목록에 없는 deviceId 를 그대로 대입해 " +
                "select 가 선택 없음 상태로 무너진다")
                .contains("selectHasOption(activeDeviceId)");
    }

    /** {@code needle} 이 처음 나오는 지점부터 {@code len} 글자를 잘라 온다(없으면 빈 문자열). */
    private static String sliceFrom(String src, String needle, int len) {
        int i = src.indexOf(needle);
        if (i < 0) return "";
        return src.substring(i, Math.min(src.length(), i + len));
    }

    @Test
    @DisplayName("[9] 훈련생 화면에 동의·카메라 제어·시나리오·상태·시간·이벤트 필수 컨트롤이 있다")
    void traineePageHasRequiredControls() throws Exception {
        String html = body("/v2/lxp/trainee/learning-presence-check.html");
        String[] requiredIds = {
                "consentCheckbox", "cameraSelect", "startBtn", "stopBtn", "retryBtn",
                "selfPreview", "selfPlaceholder", "scenarioSelect", "scenarioDesc",
                "stateBadge", "stateMsg", "connectedTime", "verifiedTime", "awayTime", "recognizedTime",
                "lastCheckedAt", "nextCheckedAt", "eventList"
        };
        for (String id : requiredIds) {
            assertThat(html).as("훈련생 화면에 id=\"%s\" 컨트롤이 있어야 한다", id).contains("id=\"" + id + "\"");
        }
        assertThat(html).as("동의 전 시작 버튼은 disabled 로 시작해야 한다")
                .containsPattern(Pattern.compile("id=\"startBtn\"[^>]*disabled"));
    }

    @Test
    @DisplayName("[9] 관리자 화면에 검색·필터·시나리오·KPI·표·상세·타임라인·안내 모달 필수 컨트롤이 있다")
    void adminPageHasRequiredControls() throws Exception {
        String html = body("/v2/admin/presence-monitor.html");
        String[] requiredIds = {
                "peopleSearch", "stateFilter", "scenarioSelect",
                "kpiTotal", "kpiNormal", "kpiWarning", "kpiFocus",
                "peopleBody", "selName", "selMeta", "selReason", "noticeBtn",
                "timelineBody", "noticeDialog", "noticeConfirm", "noticeCancel",
                "courseTitle", "policyLine"
        };
        for (String id : requiredIds) {
            assertThat(html).as("관리자 화면에 id=\"%s\" 컨트롤이 있어야 한다", id).contains("id=\"" + id + "\"");
        }
        assertThat(html).as("안내 보내기는 확인 모달을 거쳐야 한다").contains("<dialog");
    }

    // ================================================================
    //  Task 2 — learning-presence-common.js 를 Node 로 실제 실행 (Task1 · Step10/11)
    // ================================================================

    private record NodeRun(int code, String out) {
    }

    private NodeRun runNode(String script) {
        try {
            Path tmp = Files.createTempFile("learning-presence-contract-", ".js");
            Files.writeString(tmp, script, StandardCharsets.UTF_8);
            try {
                ProcessBuilder pb = new ProcessBuilder("node", tmp.toString());
                pb.redirectErrorStream(true);
                pb.directory(Paths.get(".").toAbsolutePath().normalize().toFile());
                Process proc = pb.start();
                String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                boolean done = proc.waitFor(90, java.util.concurrent.TimeUnit.SECONDS);
                if (!done) {
                    proc.destroyForcibly();
                    throw new AssertionError("node 실행이 시간을 초과했습니다");
                }
                return new NodeRun(proc.exitValue(), out);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException | InterruptedException e) {
            throw new AssertionError("node 를 실행하지 못했습니다: " + e.getMessage(), e);
        }
    }

    private static final Path STATIC_V2_ASSETS =
            Paths.get("src/main/resources/static/v2/assets").toAbsolutePath().normalize();

    private String jsString(Object path) {
        return "\"" + path.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    @DisplayName("[10][1차보완] derivePresenceState() 가 currentAwaySeconds·grace/warning/focus 4단계 경계값과 정규화를 Node 로 실제 실행한다")
    void derivePresenceState_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                }
                const G = 120, W = 180, F = 300;

                /* 카메라 연결 끊김이 얼굴 개수보다 우선한다 */
                const disc = C.derivePresenceState({ faceCount: 1, cameraConnected: false, currentAwaySeconds: 0, graceSeconds: G, warningSeconds: W, focusSeconds: F });
                check("카메라끊김 code", disc.code, "camera_disconnected");
                check("카메라끊김 countsAsPresent", disc.countsAsPresent, false);

                const present = C.derivePresenceState({ faceCount: 1, cameraConnected: true, currentAwaySeconds: 0, graceSeconds: G, warningSeconds: W, focusSeconds: F });
                check("정상 code", present.code, "present");
                check("정상 tone", present.tone, "ok");
                check("정상 countsAsPresent", present.countsAsPresent, true);

                const noFace = C.derivePresenceState({ faceCount: 0, cameraConnected: true, currentAwaySeconds: 10, graceSeconds: G, warningSeconds: W, focusSeconds: F });
                check("얼굴없음(허용시간 내) code", noFace.code, "no_face");
                check("얼굴없음 countsAsPresent", noFace.countsAsPresent, false);

                /* grace/warning/focus 4단계 경계 — 바로 전(-1)과 경계값을 모두 확인한다 */
                check("grace 바로 전(119) → no_face",
                    C.derivePresenceState({ faceCount: 0, cameraConnected: true, currentAwaySeconds: G - 1, graceSeconds: G, warningSeconds: W, focusSeconds: F }).code, "no_face");
                check("grace 경계(120) → 자리 복귀 필요",
                    C.derivePresenceState({ faceCount: 0, cameraConnected: true, currentAwaySeconds: G, graceSeconds: G, warningSeconds: W, focusSeconds: F }).code, "away_return_needed");
                check("warning 바로 전(179) → 자리 복귀 필요",
                    C.derivePresenceState({ faceCount: 0, cameraConnected: true, currentAwaySeconds: W - 1, graceSeconds: G, warningSeconds: W, focusSeconds: F }).code, "away_return_needed");
                check("warning 경계(180) → 자리 이탈 경고",
                    C.derivePresenceState({ faceCount: 0, cameraConnected: true, currentAwaySeconds: W, graceSeconds: G, warningSeconds: W, focusSeconds: F }).code, "away_warning");
                check("focus 바로 전(299) → 자리 이탈 경고",
                    C.derivePresenceState({ faceCount: 0, cameraConnected: true, currentAwaySeconds: F - 1, graceSeconds: G, warningSeconds: W, focusSeconds: F }).code, "away_warning");
                check("focus 경계(300) → 집중관리 확인 필요",
                    C.derivePresenceState({ faceCount: 0, cameraConnected: true, currentAwaySeconds: F, graceSeconds: G, warningSeconds: W, focusSeconds: F }).code, "away_focus");

                const multi = C.derivePresenceState({ faceCount: 2, cameraConnected: true, currentAwaySeconds: 0, graceSeconds: G, warningSeconds: W, focusSeconds: F });
                check("복수얼굴 code", multi.code, "multiple_faces");
                check("복수얼굴 countsAsPresent", multi.countsAsPresent, false);

                /* 음수·NaN·문자열 입력 정규화 */
                const norm = C.derivePresenceState({ faceCount: "1", cameraConnected: true, currentAwaySeconds: -5, graceSeconds: NaN, warningSeconds: NaN, focusSeconds: "300" });
                check("정규화 후 정상 판정", norm.code, "present");

                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("learning-presence-common.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("derivePresenceState() 판정이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[10][1차보완] calculateLearningTime() 이 recognizedSeconds===verifiedSeconds 를 항상 지킨다(이중 차감 제거, 경계값 스윕을 실제 실행)")
    void calculateLearningTime_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                }

                /* 지시서 §2 실제 예시: 접속 35 / 확인 23 / 누적이탈 12 → 인정 예정은 23 (이중 차감 없음) */
                const example = C.calculateLearningTime({ connectedSeconds: 35, verifiedSeconds: 23, cumulativeAwaySeconds: 12 });
                check("예시: recognizedSeconds", example.recognizedSeconds, 23);
                check("예시: recognizedSeconds === verifiedSeconds", example.recognizedSeconds, example.verifiedSeconds);

                const cases = [
                  { connectedSeconds: 100, verifiedSeconds: 90,  cumulativeAwaySeconds: 10 },
                  { connectedSeconds: 100, verifiedSeconds: 150, cumulativeAwaySeconds: 5 },
                  { connectedSeconds: 100, verifiedSeconds: 50,  cumulativeAwaySeconds: 80 },
                  { connectedSeconds: -10, verifiedSeconds: -5,  cumulativeAwaySeconds: -1 },
                  { connectedSeconds: "200", verifiedSeconds: "NaN", cumulativeAwaySeconds: "20" }
                ];
                cases.forEach(function (c, i) {
                  const r = C.calculateLearningTime(c);
                  if (!(r.recognizedSeconds === r.verifiedSeconds && r.verifiedSeconds <= r.connectedSeconds && r.cumulativeAwaySeconds >= 0)) {
                    console.log("FAIL 불변식 깨짐 case" + i + " → " + JSON.stringify(r));
                    bad++;
                  }
                });

                check("verified 가 connected 를 넘으면 잘린다", C.calculateLearningTime(cases[1]).verifiedSeconds, 100);
                check("이탈시간이 커도 recognized 는 verified 와 같다(이중 차감 없음)", C.calculateLearningTime(cases[2]).recognizedSeconds, 50);
                const allZero = C.calculateLearningTime(cases[3]);
                check("음수 입력은 전부 0", allZero.connectedSeconds, 0);
                check("음수 입력은 전부 0(verified)", allZero.verifiedSeconds, 0);
                check("음수 입력은 전부 0(cumulativeAway)", allZero.cumulativeAwaySeconds, 0);
                const stringy = C.calculateLearningTime(cases[4]);
                check("문자열 connected 정규화", stringy.connectedSeconds, 200);
                check("NaN verified 정규화", stringy.verifiedSeconds, 0);
                check("문자열 cumulativeAway 정규화되어도 recognized 는 verified 와 같다", stringy.recognizedSeconds, 0);

                /* 하위 호환: 다른 화면(presence-monitor.js, 이번 차수 수정 대상 아님)이 예전
                   이름 awaySeconds 로 불러도 정상 계산되어야 한다 — 그 화면을 깨뜨리지 않는다. */
                const legacyKey = C.calculateLearningTime({ connectedSeconds: 3600, verifiedSeconds: 3400, awaySeconds: 200 });
                check("하위 호환: awaySeconds 로도 cumulativeAwaySeconds 가 채워진다", legacyKey.cumulativeAwaySeconds, 200);
                check("하위 호환: verifiedSeconds 는 그대로", legacyKey.verifiedSeconds, 3400);

                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("learning-presence-common.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("calculateLearningTime() 판정이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P0] advanceLearningClock() 이 정상→이탈→정상 시퀀스에서 누적 이탈은 줄지 않고 인정시간이 소급 증가하지 않음을 실제 실행한다")
    void advanceLearningClock_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                }
                const PRESENT = { code: "present", countsAsPresent: true };
                const NO_FACE = { code: "no_face", countsAsPresent: false };
                const MULTI = { code: "multiple_faces", countsAsPresent: false };

                /* 지시서 §2 실제 예시: 정상 5초 → 이탈 3초 → 정상 2초 */
                let clock = { connectedSeconds: 0, verifiedSeconds: 0, cumulativeAwaySeconds: 0, currentAwaySeconds: 0 };
                for (let i = 0; i < 5; i++) clock = C.advanceLearningClock(clock, PRESENT);
                for (let i = 0; i < 3; i++) clock = C.advanceLearningClock(clock, NO_FACE);
                const afterAway = clock;
                for (let i = 0; i < 2; i++) clock = C.advanceLearningClock(clock, PRESENT);

                check("connectedSeconds", clock.connectedSeconds, 10);
                check("verifiedSeconds", clock.verifiedSeconds, 7);
                check("cumulativeAwaySeconds", clock.cumulativeAwaySeconds, 3);
                check("currentAwaySeconds(정상 복귀로 0)", clock.currentAwaySeconds, 0);
                check("recognizedSeconds === verifiedSeconds", clock.recognizedSeconds, 7);

                /* 정상 복귀 후에도 누적 이탈은 줄지 않는다(역전 없음) */
                check("누적 이탈은 복귀 후에도 줄지 않는다", clock.cumulativeAwaySeconds >= afterAway.cumulativeAwaySeconds, true);
                check("인정 예정 시간이 과거 이탈을 소급해 증가하지 않는다(감소한 적 없던 verified 만큼만)", clock.recognizedSeconds, clock.verifiedSeconds);

                /* 복수 얼굴: 정상 확인도 자리 이탈 확정도 아니다 — 두 누적값과 연속 이탈 모두 그대로 */
                let mclock = { connectedSeconds: 5, verifiedSeconds: 3, cumulativeAwaySeconds: 2, currentAwaySeconds: 1 };
                const after = C.advanceLearningClock(mclock, MULTI);
                check("복수얼굴: connected 만 흐른다", after.connectedSeconds, 6);
                check("복수얼굴: verified 불변", after.verifiedSeconds, 3);
                check("복수얼굴: 누적 이탈 불변", after.cumulativeAwaySeconds, 2);
                check("복수얼굴: 연속 이탈 불변(유지, 초기화도 증가도 안 함)", after.currentAwaySeconds, 1);

                /* 카메라 끊김도 이탈로 누적된다 */
                let dclock = { connectedSeconds: 0, verifiedSeconds: 0, cumulativeAwaySeconds: 0, currentAwaySeconds: 0 };
                dclock = C.advanceLearningClock(dclock, { code: "camera_disconnected", countsAsPresent: false });
                check("카메라 끊김도 누적 이탈에 포함된다", dclock.cumulativeAwaySeconds, 1);
                check("카메라 끊김도 연속 이탈에 포함된다", dclock.currentAwaySeconds, 1);

                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("learning-presence-common.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("advanceLearningClock() 동작이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[10] classifyLearner() 이 누적 이탈시간 경계값으로 정상/주의/집중관리를 판정하고 사유에 실제 값을 담는다")
    void classifyLearner_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                }

                const normal = C.classifyLearner({ awaySeconds: 50, awayCount: 1, warningSeconds: 180, focusSeconds: 300 });
                check("정상 code", normal.code, "normal");

                const warnBoundary = C.classifyLearner({ awaySeconds: 180, awayCount: 2, warningSeconds: 180, focusSeconds: 300 });
                check("주의 경계값(같음)", warnBoundary.code, "warning");
                if (warnBoundary.reason.indexOf("180") === -1 || warnBoundary.reason.indexOf("2회") === -1) {
                  console.log("FAIL 주의 사유에 실제 값이 없다: " + warnBoundary.reason); bad++;
                }

                const focusBoundary = C.classifyLearner({ awaySeconds: 300, awayCount: 5, warningSeconds: 180, focusSeconds: 300 });
                check("집중관리 경계값(같음)", focusBoundary.code, "focus");
                if (focusBoundary.reason.indexOf("300") === -1 || focusBoundary.reason.indexOf("5회") === -1) {
                  console.log("FAIL 집중관리 사유에 실제 값이 없다: " + focusBoundary.reason); bad++;
                }

                /* 음수·NaN·문자열 정규화 */
                const norm = C.classifyLearner({ awaySeconds: -5, awayCount: "NaN", warningSeconds: 180, focusSeconds: 300 });
                check("정규화 후 정상", norm.code, "normal");

                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("learning-presence-common.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("classifyLearner() 판정이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[11] createMediaSlot() 이 스트림 교체·낡은요청 폐기·stop 멱등성·대기 중 토큰 무효화를 실제로 처리한다")
    void createMediaSlot_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + actual); bad++; }
                }
                function fakeTrack() { return { stopped: false, stop() { this.stopped = true; }, addEventListener() {} }; }
                function fakeStream(tracks) { return { _t: tracks, getTracks() { return this._t; } }; }

                const slot = C.createMediaSlot();

                /* ① 첫 채택 성공 — adopt() 는 채택한 스트림 자체를 돌려준다 */
                const tA = fakeTrack(), sA = fakeStream([tA]);
                const tok1 = slot.request();
                check("첫 채택은 스트림을 그대로 반환", slot.adopt(tok1, sA) === sA, true);
                check("최초엔 정지 안 됨", tA.stopped, false);

                /* ② 다음 채택 시 이전 스트림을 정지한다 */
                const tB = fakeTrack(), sB = fakeStream([tB]);
                const tok2 = slot.request();
                check("두 번째 채택 성공", slot.adopt(tok2, sB) === sB, true);
                check("이전 스트림이 정지됨", tA.stopped, true);
                check("새 스트림은 살아있음", tB.stopped, false);

                /* ③ 더 최신 요청이 있으면 낡은 토큰의 결과는 채택되지 않고 즉시 정지된다 */
                const tokOld = slot.request();
                const tokNew = slot.request();
                const tStale = fakeTrack(), sStale = fakeStream([tStale]);
                check("낡은 토큰은 거부됨(null)", slot.adopt(tokOld, sStale), null);
                check("낡은 스트림은 즉시 정지됨", tStale.stopped, true);
                check("직전에 채택된 스트림은 안 건드림", tB.stopped, false);
                void tokNew;

                /* ④ stop() 은 현재 스트림을 정지하고, 이후 대기 중이던(아직 응답 안 온) 요청의
                   토큰도 낡은 것으로 만든다 — 그래서 stop() 이후 뒤늦게 도착하는 결과도 거부된다. */
                const tC = fakeTrack(), sC = fakeStream([tC]);
                const tok3 = slot.request();
                slot.adopt(tok3, sC);
                const tokPending = slot.request();   /* 이 토큰으로는 아직 adopt() 를 부르지 않는다 — "대기 중" 흉내 */
                slot.stop();
                check("stop() 이 현재 스트림을 정지함", sC._t[0].stopped, true);
                check("stop() 이후 대기 중이던 토큰은 더 이상 최신이 아님", slot.isCurrent(tokPending), false);
                const tLate = fakeTrack(), sLate = fakeStream([tLate]);
                check("stop() 이후 뒤늦게 온 결과는 채택되지 않음", slot.adopt(tokPending, sLate), null);
                check("뒤늦게 온 스트림도 즉시 정지됨", tLate.stopped, true);

                /* ⑤ stop() 은 여러 번 호출해도 안전하다(예외 없음) */
                let threw = false;
                try { slot.stop(); slot.stop(); } catch (e) { threw = true; }
                check("stop() 반복 호출은 예외를 던지지 않음", threw, false);

                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("learning-presence-common.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("createMediaSlot() 동작이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[10] 데모 데이터의 정상/주의/집중관리 학습자가 실제로 모두 존재한다(하드코딩 대조가 아니라 규칙으로 직접 판정)")
    void demoData_세가지상태가모두_실제로존재() {
        String script = """
                const C = require(%s);
                const D = require(%s);
                let bad = 0;
                if (!Array.isArray(D.learners) || D.learners.length < 8) {
                  console.log("FAIL 훈련생은 최소 8명이어야 한다: " + (D.learners ? D.learners.length : "없음")); bad++;
                }
                const codes = new Set();
                D.learners.forEach(function (p) {
                  const c = C.classifyLearner({ awaySeconds: p.awaySeconds, awayCount: p.awayCount, warningSeconds: D.policy.warningSeconds, focusSeconds: D.policy.focusSeconds });
                  codes.add(c.code);
                });
                ["normal", "warning", "focus"].forEach(function (code) {
                  if (!codes.has(code)) { console.log("FAIL 데모 학습자 중 " + code + " 상태가 없다"); bad++; }
                });
                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(
                        jsString(STATIC_V2_ASSETS.resolve("learning-presence-common.js")),
                        jsString(STATIC_V2_ASSETS.resolve("learning-presence-demo-data.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("데모 데이터 상태 분포가 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    // ================================================================
    //  1차 보완 대응 — P1(중지·동의해제·track ended 배지 정리),
    //  P2(enumerateDevices reject·devicechange reject·낡은 목록 조회)
    //  Node 로 learning-presence-check.js 를 실제 로드해 실행한다(문자열 검사 아님).
    // ================================================================

    /** DOM 없이 Node 에서 learning-presence-check.js 를 실제로 실행하기 위한 최소 스텁.
        select 는 innerHTML="" 대입 시 options/자식이 실제로 비워지도록 흉내낸다 —
        그래야 fillCameraSelect() 가 반복 호출돼도 옵션이 누적되지 않는다. */
    private String stubDomAndCheckHarness() {
        return """
                function stubEl() {
                  var attrs = {};
                  var html = "";
                  var el = {
                    dataset: {}, style: {}, hidden: false, disabled: false, value: "", checked: false,
                    tabIndex: 0, srcObject: null, _handlers: {}, _children: [], options: [],
                    setAttribute: function (k, v) { attrs[k] = String(v); },
                    removeAttribute: function (k) { delete attrs[k]; },
                    getAttribute: function (k) { return Object.prototype.hasOwnProperty.call(attrs, k) ? attrs[k] : null; },
                    addEventListener: function (evt, fn) { el._handlers[evt] = fn; },
                    removeEventListener: function () {},
                    appendChild: function (child) { el._children.push(child); if (child && child._isOption) el.options.push(child); return child; },
                    querySelector: function () { return stubEl(); },
                    querySelectorAll: function () { return []; },
                    play: function () { return Promise.resolve(); },
                    classList: { toggle: function () {}, add: function () {}, remove: function () {} }
                  };
                  Object.defineProperty(el, "innerHTML", {
                    get: function () { return html; },
                    set: function (v) { html = v; if (v === "") { el._children = []; el.options = []; } }
                  });
                  var text = "";
                  Object.defineProperty(el, "textContent", {
                    get: function () { return text; },
                    set: function (v) { text = v; }
                  });
                  return el;
                }

                var els = {};
                function byId(id) { if (!els[id]) els[id] = stubEl(); return els[id]; }

                global.document = {
                  getElementById: byId,
                  createElement: function (tag) { var e = stubEl(); if (tag === "option") e._isOption = true; return e; },
                  createTextNode: function (t) { return { text: t }; },
                  querySelector: function () { return stubEl(); },
                  querySelectorAll: function () { return []; },
                  addEventListener: function () {}
                };

                var mediaDevicesHandlers = {};
                global.window = {
                  isSecureContext: true,
                  setInterval: setInterval,
                  clearInterval: clearInterval,
                  addEventListener: function () {}
                };

                global.window.LearningPresence = require(%s);
                global.window.LearningPresenceDemoData = require(%s);

                Object.defineProperty(global, "navigator", {
                  value: {
                    mediaDevices: {
                      getUserMedia: function (c) { return global.__gum(c); },
                      enumerateDevices: function () { return global.__enumerate ? global.__enumerate() : Promise.resolve([]); },
                      addEventListener: function (evt, fn) { mediaDevicesHandlers[evt] = fn; }
                    }
                  },
                  writable: true, configurable: true
                });
                global.__mediaDevicesHandlers = mediaDevicesHandlers;

                function flush(ms) { return new Promise(function (resolve) { setTimeout(resolve, ms || 0); }); }
                function fakeVideoStream() {
                  var track = {
                    stopped: false, _handlers: {},
                    stop: function () { this.stopped = true; },
                    addEventListener: function (evt, fn) { this._handlers[evt] = fn; },
                    getSettings: function () { return {}; }
                  };
                  return { _track: track, getVideoTracks: function () { return [track]; }, getTracks: function () { return [track]; } };
                }

                require(%s);   /* learning-presence-check.js 를 실제로 로드한다 — 여기서 최초 렌더까지 끝난다 */
                """.formatted(
                        jsString(STATIC_V2_ASSETS.resolve("learning-presence-common.js")),
                        jsString(STATIC_V2_ASSETS.resolve("learning-presence-demo-data.js")),
                        jsString(STATIC_V2_ASSETS.resolve("learning-presence-check.js")));
    }

    @Test
    @DisplayName("[1차보완 P1 실행형] active 정상 상태에서 중지를 누르면 배지가 즉시 미확인으로 돌아가고 interval 도 멈춘다")
    void checkJs_중지시_배지즉시초기화_실제실행() {
        String script = stubDomAndCheckHarness() + """

                (async function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  global.__gum = function () { return Promise.resolve(fakeVideoStream()); };
                  global.__enumerate = function () { return Promise.resolve([{ deviceId: "cam-1", kind: "videoinput", label: "카메라 1" }]); };

                  els["consentCheckbox"].checked = true;
                  els["consentCheckbox"]._handlers.change();
                  els["startBtn"]._handlers.click();
                  await flush(1100);   /* 최소 한 틱이 지나 "정상 참여 중"이 실제로 보여야 사전조건이 성립한다 */

                  check("사전조건: active 상태에서 정상 참여 중 배지", els["stateBadge"].textContent, "정상 참여 중");

                  els["stopBtn"]._handlers.click();
                  await flush();

                  check("중지 직후 배지 미확인", els["stateBadge"].textContent, "미확인");
                  check("중지 직후 안내 갱신", els["stateMsg"].textContent, "카메라를 시작하면 참여 상태 확인이 시작됩니다.");

                  var connectedAfterStop = els["connectedTime"].textContent;
                  await flush(1200);
                  check("interval 정지 — 더 이상 시간이 흐르지 않는다", els["connectedTime"].textContent, connectedAfterStop);
                  check("interval 정지 — 배지도 그대로 미확인", els["stateBadge"].textContent, "미확인");

                  if (!bad) console.log("OK");
                  process.exit(bad ? 1 : 0);   /* 카메라가 켜져 있으면 setInterval 이 살아있어 자연 종료를 기다리면
                                                   프로세스가 멈춘다 — 성공 시에도 명시적으로 종료해야 한다 */
                })().catch(function (e) { console.log("FAIL 예외: " + (e && e.stack || e)); process.exit(1); });
                """;

        NodeRun r = runNode(script);
        assertThat(r.code()).as("중지 후 배지 초기화 동작이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P1 실행형] active 정상 상태에서 동의를 해제하면 track 이 정지되고 배지가 즉시 미확인으로 돌아간다")
    void checkJs_동의해제시_트랙정지및배지초기화_실제실행() {
        String script = stubDomAndCheckHarness() + """

                (async function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  var lastStream = null;
                  global.__gum = function () { lastStream = fakeVideoStream(); return Promise.resolve(lastStream); };
                  global.__enumerate = function () { return Promise.resolve([{ deviceId: "cam-1", kind: "videoinput", label: "카메라 1" }]); };

                  els["consentCheckbox"].checked = true;
                  els["consentCheckbox"]._handlers.change();
                  els["startBtn"]._handlers.click();
                  await flush(1100);

                  check("사전조건: 정상 참여 중", els["stateBadge"].textContent, "정상 참여 중");
                  check("사전조건: 트랙 아직 안 멈춤", lastStream._track.stopped, false);

                  els["consentCheckbox"].checked = false;
                  els["consentCheckbox"]._handlers.change();
                  await flush();

                  check("동의 해제 후 track 정지", lastStream._track.stopped, true);
                  check("동의 해제 후 배지 미확인", els["stateBadge"].textContent, "미확인");

                  var connectedAfterRevoke = els["connectedTime"].textContent;
                  await flush(1200);
                  check("동의 해제 후 interval 정지", els["connectedTime"].textContent, connectedAfterRevoke);

                  if (!bad) console.log("OK");
                  process.exit(bad ? 1 : 0);   /* 카메라가 켜져 있으면 setInterval 이 살아있어 자연 종료를 기다리면
                                                   프로세스가 멈춘다 — 성공 시에도 명시적으로 종료해야 한다 */
                })().catch(function (e) { console.log("FAIL 예외: " + (e && e.stack || e)); process.exit(1); });
                """;

        NodeRun r = runNode(script);
        assertThat(r.code()).as("동의 해제 동작이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P1 실행형] active 정상 상태에서 track 이 ended 되면 미리보기가 숨겨지고 배지가 미확인, 재시도가 가능해진다")
    void checkJs_트랙종료시_미리보기숨김및배지초기화_실제실행() {
        String script = stubDomAndCheckHarness() + """

                (async function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  var lastStream = null;
                  global.__gum = function () { lastStream = fakeVideoStream(); return Promise.resolve(lastStream); };
                  global.__enumerate = function () { return Promise.resolve([{ deviceId: "cam-1", kind: "videoinput", label: "카메라 1" }]); };

                  els["consentCheckbox"].checked = true;
                  els["consentCheckbox"]._handlers.change();
                  els["startBtn"]._handlers.click();
                  await flush(1100);

                  check("사전조건: 정상 참여 중", els["stateBadge"].textContent, "정상 참여 중");
                  check("사전조건: 미리보기 보임", els["selfPreview"].hidden, false);

                  lastStream._track._handlers.ended();
                  await flush();

                  check("트랙 종료 후 미리보기 숨김", els["selfPreview"].hidden, true);
                  check("트랙 종료 후 배지 미확인", els["stateBadge"].textContent, "미확인");
                  check("트랙 종료 후 재시도 버튼이 보인다", els["retryBtn"].hidden, false);
                  check("트랙 종료 후 재시도 가능(비활성 아님)", els["retryBtn"].disabled, false);

                  if (!bad) console.log("OK");
                  process.exit(bad ? 1 : 0);   /* 카메라가 켜져 있으면 setInterval 이 살아있어 자연 종료를 기다리면
                                                   프로세스가 멈춘다 — 성공 시에도 명시적으로 종료해야 한다 */
                })().catch(function (e) { console.log("FAIL 예외: " + (e && e.stack || e)); process.exit(1); });
                """;

        NodeRun r = runNode(script);
        assertThat(r.code()).as("track ended 동작이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P2 실행형] 카메라 연결 성공 후 enumerateDevices 가 reject 되어도 active 를 유지하고 안내만 표시하며 unhandled rejection 이 없다")
    void checkJs_장치목록조회실패시_active유지및무처리거부없음_실제실행() {
        String script = stubDomAndCheckHarness() + """

                (async function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }
                  var unhandled = 0;
                  process.on("unhandledRejection", function (e) { unhandled++; console.log("UNHANDLED: " + (e && e.stack || e)); });

                  global.__gum = function () { return Promise.resolve(fakeVideoStream()); };
                  global.__enumerate = function () { return Promise.reject(new Error("enumerateDevices 실패(모의)")); };

                  els["consentCheckbox"].checked = true;
                  els["consentCheckbox"]._handlers.change();
                  els["startBtn"]._handlers.click();
                  await flush(50);
                  await flush(50);   /* getUserMedia 성공 → refreshDeviceList() 의 reject 가 소비될 시간을 준다 */

                  check("장치 목록 조회 실패 후에도 카메라는 active 유지(중지 가능)", els["stopBtn"].disabled, false);
                  check("비차단 안내 표시", els["cameraMsg"].textContent, "카메라는 연결됐지만 장치 목록을 새로 읽지 못했습니다.");

                  await flush(100);
                  check("unhandled rejection 0건", unhandled, 0);

                  if (!bad) console.log("OK");
                  process.exit(bad ? 1 : 0);   /* 카메라가 켜져 있으면 setInterval 이 살아있어 자연 종료를 기다리면
                                                   프로세스가 멈춘다 — 성공 시에도 명시적으로 종료해야 한다 */
                })().catch(function (e) { console.log("FAIL 예외: " + (e && e.stack || e)); process.exit(1); });
                """;

        NodeRun r = runNode(script);
        assertThat(r.code()).as("enumerateDevices reject 처리가 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P2 실행형] devicechange 로 트리거된 장치 목록 조회가 reject 되어도 예외가 전파되지 않고 기존 select 를 유지한다")
    void checkJs_devicechange_reject시_예외전파없음_실제실행() {
        String script = stubDomAndCheckHarness() + """

                (async function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }
                  var unhandled = 0;
                  process.on("unhandledRejection", function () { unhandled++; });

                  /* idle 상태에서 먼저 정상 목록으로 한 번 채워 둔다 */
                  global.__enumerate = function () { return Promise.resolve([{ deviceId: "cam-1", kind: "videoinput", label: "카메라 1" }]); };
                  global.__mediaDevicesHandlers.devicechange();
                  await flush(20);
                  check("사전조건: 기존 목록이 채워짐", els["cameraSelect"].options.length, 1);

                  global.__enumerate = function () { return Promise.reject(new Error("devicechange 중 실패(모의)")); };
                  var threw = false;
                  try {
                    global.__mediaDevicesHandlers.devicechange();
                  } catch (e) { threw = true; }
                  await flush(50);

                  check("devicechange 호출 자체가 동기적으로 예외를 던지지 않는다", threw, false);
                  check("unhandled rejection 0건", unhandled, 0);
                  check("실패해도 기존 select 옵션을 지우지 않는다", els["cameraSelect"].options.length, 1);
                  check("실패해도 기존 옵션 값 유지", els["cameraSelect"].options[0].value, "cam-1");

                  if (!bad) console.log("OK");
                  process.exit(bad ? 1 : 0);   /* 카메라가 켜져 있으면 setInterval 이 살아있어 자연 종료를 기다리면
                                                   프로세스가 멈춘다 — 성공 시에도 명시적으로 종료해야 한다 */
                })().catch(function (e) { console.log("FAIL 예외: " + (e && e.stack || e)); process.exit(1); });
                """;

        NodeRun r = runNode(script);
        assertThat(r.code()).as("devicechange reject 처리가 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P2 실행형] 오래된 장치 목록 조회 A 가 늦게 끝나도 더 최신인 B 의 결과를 덮어쓰지 않는다")
    void checkJs_낡은장치목록조회가_최신결과를_덮어쓰지않음_실제실행() {
        String script = stubDomAndCheckHarness() + """

                (async function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  var resolvers = [];
                  global.__enumerate = function () {
                    return new Promise(function (resolve) { resolvers.push(resolve); });
                  };

                  /* idle 상태에서 devicechange 를 두 번 연달아 일으켜 A(먼저 보낸 요청), B(나중에
                     보낸 더 최신 요청) 두 개의 enumerateDevices() 호출을 대기시킨다. */
                  global.__mediaDevicesHandlers.devicechange();   /* A */
                  global.__mediaDevicesHandlers.devicechange();   /* B */
                  check("두 조회가 모두 대기 중", resolvers.length, 2);

                  /* B(더 최신, index 1)가 먼저 응답한다 */
                  resolvers[1]([{ deviceId: "dev-B", kind: "videoinput", label: "장치 B" }]);
                  await flush(20);
                  check("B 응답 직후 select 가 B 를 반영", els["cameraSelect"].options.map(function (o) { return o.value; }).join(","), "dev-B");

                  /* A(더 낡음, index 0)가 늦게 응답한다 — 이미 B 가 반영된 뒤이므로 무시되어야 한다 */
                  resolvers[0]([{ deviceId: "dev-A", kind: "videoinput", label: "장치 A" }]);
                  await flush(20);
                  check("A 의 늦은 응답이 B 를 덮어쓰지 않는다", els["cameraSelect"].options.map(function (o) { return o.value; }).join(","), "dev-B");

                  if (!bad) console.log("OK");
                  process.exit(bad ? 1 : 0);   /* 카메라가 켜져 있으면 setInterval 이 살아있어 자연 종료를 기다리면
                                                   프로세스가 멈춘다 — 성공 시에도 명시적으로 종료해야 한다 */
                })().catch(function (e) { console.log("FAIL 예외: " + (e && e.stack || e)); process.exit(1); });
                """;

        NodeRun r = runNode(script);
        assertThat(r.code()).as("낡은 장치 목록 조회 처리가 기대와 다릅니다:%n%s", r.out()).isZero();
    }
}
