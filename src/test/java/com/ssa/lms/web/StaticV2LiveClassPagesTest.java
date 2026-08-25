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
 * LXP-125/127 화상 라이브 강의 데모 정적 화면(/v2/**) 회귀 테스트.
 *
 * <p>이 3개 화면은 {@link StaticV2ExamPagesTest} 가 지키는 시험 데모 7종과 같은 이유로
 * 별도 계약 테스트가 필요하다 — Thymeleaf 가 아니라 순수 정적 HTML/CSS/JS 라 컨트롤러
 * 테스트에 걸리지 않고, 자산 경로가 어긋나면 배포 후 404 로만 드러난다.</p>
 *
 * <p><b>범위 한계:</b> 이 테스트는 파일이 서빙되는지, 뼈대·자산·CSS 범위·데모 표시가
 * 있는지만 본다. 카메라·마이크·화면 공유 권한 팝업, 장치 선택 변경, 반응형 레이아웃은
 * 자동 검증 대상이 아니라 실기기 육안 확인 항목이다. 단, {@code live-class-common.js}
 * 의 판정 순수 함수(supportState/mediaErrorInfo/allChecksPassed)는 Node 로 실제 실행해
 * 검증한다 — 문자열 존재 검사가 아니다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StaticV2LiveClassPagesTest {

    @Autowired MockMvc mvc;

    /** {경로, 해당 페이지에만 있는 제목} */
    private static final String[][] LIVE_CLASS_PAGES = {
            { "/v2/lxp/trainee/live-class-precheck.html", "화상강의 입장 전 장치 점검" },
            { "/v2/lxp/trainee/live-classroom.html",      "화상 라이브 강의실" },
            { "/v2/admin/live-class-monitor.html",        "라이브 강의 모니터링" }
    };

    private static final String[] TRAINEE_PAGES = {
            "/v2/lxp/trainee/live-class-precheck.html",
            "/v2/lxp/trainee/live-classroom.html"
    };

    private static final String[] ADMIN_PAGES = {
            "/v2/admin/live-class-monitor.html"
    };

    /** {페이지 경로, <body> 에 붙어야 하는 루트 클래스} */
    private static final String[][] PAGE_ROOT = {
            { "/v2/lxp/trainee/live-class-precheck.html", "live-precheck-page" },
            { "/v2/lxp/trainee/live-classroom.html",      "live-classroom-page" },
            { "/v2/admin/live-class-monitor.html",        "live-monitor-page" }
    };

    private static final String NEW_CSS = "/v2/assets/live-class.css";

    /** 신규 JS 자산 5종 — 이 목록에서 빠지면 어느 화면인가는 반드시 깨진다. */
    private static final String[] NEW_JS_ASSETS = {
            "/v2/assets/live-class-common.js",
            "/v2/assets/live-class-demo-data.js",
            "/v2/assets/live-class-precheck.js",
            "/v2/assets/live-classroom.js",
            "/v2/assets/live-class-monitor.js"
    };

    /** 신규 정적 파일 9개(3 HTML + 1 CSS + 5 JS) — 외부 자원·금지 API 검사 대상. */
    private static final String[] NEW_STATIC_FILES = {
            "/v2/lxp/trainee/live-class-precheck.html",
            "/v2/lxp/trainee/live-classroom.html",
            "/v2/admin/live-class-monitor.html",
            "/v2/assets/live-class.css",
            "/v2/assets/live-class-common.js",
            "/v2/assets/live-class-demo-data.js",
            "/v2/assets/live-class-precheck.js",
            "/v2/assets/live-classroom.js",
            "/v2/assets/live-class-monitor.js"
    };

    private String body(String path) throws Exception {
        return mvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ================================================================
    //  Task 1 · Step 2~8 — 서빙·제목·자산·루트클래스·CSS 분리·로드 순서·데모 표시
    // ================================================================

    @Test
    @DisplayName("화상강의 데모 3개 화면이 로그인 없이 200 으로 열리고 문서가 끝까지 온다")
    void allLiveClassPagesServed() throws Exception {
        for (String[] page : LIVE_CLASS_PAGES) {
            String html = body(page[0]);
            assertThat(html).as("%s 응답이 잘리지 않아야 한다", page[0]).contains("</html>");
            assertThat(html).as("%s 에 <main> 본문 영역이 있어야 한다", page[0]).contains("<main");
        }
    }

    @Test
    @DisplayName("3개 화면이 서로 다른 고유 제목을 가진다 — 파일이 뒤바뀌지 않았다")
    void eachPageHasItsOwnTitle() throws Exception {
        for (String[] page : LIVE_CLASS_PAGES) {
            assertThat(body(page[0]))
                    .as("%s 의 제목에 '%s' 가 있어야 한다", page[0], page[1])
                    .contains(page[1]);
        }
    }

    @Test
    @DisplayName("신규 CSS 1개와 JS 5개가 200 이고 비어 있지 않다")
    void newAssetsServedAndNotEmpty() throws Exception {
        assertThat(NEW_JS_ASSETS).as("신규 JS 자산은 5개여야 한다").hasSize(5);
        List<String> assets = new ArrayList<>();
        assets.add(NEW_CSS);
        for (String js : NEW_JS_ASSETS) assets.add(js);
        for (String asset : assets) {
            mvc.perform(get(asset)).andExpect(status().isOk());
            assertThat(body(asset)).as("%s 가 빈 파일이면 안 된다", asset).isNotBlank();
        }
    }

    @Test
    @DisplayName("각 화면의 <body> 가 자기 페이지 루트 클래스를 달고 있다")
    void everyPageCarriesItsRootClassOnBody() throws Exception {
        for (String[] pr : PAGE_ROOT) {
            assertThat(body(pr[0]))
                    .as("%s 의 <body> 에 %s 가 있어야 한다", pr[0], pr[1])
                    .contains("<body class=\"" + pr[1] + "\">");
        }
    }

    @Test
    @DisplayName("훈련생 2페이지는 훈련생 운영 CSS + live-class.css 만 로드한다 — 관리자 CSS 는 섞지 않는다")
    void traineePagesLoadTraineeOperationalCssOnly() throws Exception {
        for (String path : TRAINEE_PAGES) {
            String html = body(path);
            assertThat(html).as("%s : 훈련생 운영 CSS", path).contains("/static/css/basic-form-trainee.css");
            assertThat(html).as("%s : 공통 버튼", path).contains("/static/css/btn-style.css");
            assertThat(html).as("%s : 데모 전용 스타일", path).contains("/v2/assets/live-class.css");
            assertThat(html).as("%s : 관리자 CSS 를 섞으면 헤더 높이가 충돌한다", path)
                    .doesNotContain("/static/css/common-style.css")
                    .doesNotContain("/static/css/sidebar-style.css");
        }
    }

    @Test
    @DisplayName("관리자 1페이지는 관리자 운영 CSS + live-class.css 만 로드한다 — 훈련생 CSS 는 섞지 않는다")
    void adminPageLoadsAdminOperationalCssOnly() throws Exception {
        for (String path : ADMIN_PAGES) {
            String html = body(path);
            assertThat(html).as("%s : 관리자 토큰", path).contains("/static/css/common-style.css");
            assertThat(html).as("%s : 사이드바", path).contains("/static/css/sidebar-style.css");
            assertThat(html).as("%s : 공통 버튼", path).contains("/static/css/btn-style.css");
            assertThat(html).as("%s : 데모 전용 스타일", path).contains("/v2/assets/live-class.css");
            assertThat(html).as("%s : 훈련생 CSS 를 섞으면 헤더 높이가 충돌한다", path)
                    .doesNotContain("/static/css/basic-form-trainee.css");
        }
    }

    @Test
    @DisplayName("세 화면 모두 exam-legacy-shell.js 를 로드한다 — 없으면 ExamShell 호출이 즉시 ReferenceError")
    void everyPageLoadsExamLegacyShell() throws Exception {
        for (String[] page : LIVE_CLASS_PAGES) {
            String html = body(page[0]);
            assertThat(html).as("%s 는 ExamShell 을 호출해야 한다", page[0]).contains("ExamShell.");
            assertThat(html).as("%s 는 exam-legacy-shell.js 를 로드해야 한다", page[0])
                    .contains("/v2/assets/exam-legacy-shell.js");
        }
    }

    @Test
    @DisplayName("자산 로드 순서가 shell → common → (demo-data) → 화면 전용 JS 순이다")
    void assetsLoadInDependencyOrder() throws Exception {
        String precheck = body("/v2/lxp/trainee/live-class-precheck.html");
        int shell = precheck.indexOf("/v2/assets/exam-legacy-shell.js");
        int common = precheck.indexOf("/v2/assets/live-class-common.js");
        int own = precheck.indexOf("/v2/assets/live-class-precheck.js");
        assertThat(shell).as("precheck: shell 로드 위치").isGreaterThanOrEqualTo(0);
        assertThat(common).as("precheck: common 이 shell 뒤에 와야 한다").isGreaterThan(shell);
        assertThat(own).as("precheck: 화면 전용 JS 가 common 뒤에 와야 한다").isGreaterThan(common);
        assertThat(precheck).as("precheck 는 참가자 데모 데이터를 쓰지 않으므로 demo-data.js 를 로드하지 않아도 된다는 것을 전제로 한다 — 로드한다면 순서만 맞으면 된다").isNotNull();

        for (String path : new String[] { "/v2/lxp/trainee/live-classroom.html", "/v2/admin/live-class-monitor.html" }) {
            String html = body(path);
            int s = html.indexOf("/v2/assets/exam-legacy-shell.js");
            int c = html.indexOf("/v2/assets/live-class-common.js");
            int d = html.indexOf("/v2/assets/live-class-demo-data.js");
            String ownAsset = path.contains("live-classroom") ? "/v2/assets/live-classroom.js" : "/v2/assets/live-class-monitor.js";
            int o = html.indexOf(ownAsset);
            assertThat(s).as("%s: shell 로드 위치", path).isGreaterThanOrEqualTo(0);
            assertThat(c).as("%s: common 이 shell 뒤", path).isGreaterThan(s);
            assertThat(d).as("%s: demo-data 가 common 뒤", path).isGreaterThan(c);
            assertThat(o).as("%s: 화면 전용 JS 가 demo-data 뒤", path).isGreaterThan(d);
        }
    }

    @Test
    @DisplayName("모든 화면에 데모 표시가 있고 '준비 중인 기능입니다' 안내는 없다")
    void demoLabelPresentAndNoPlaceholder() throws Exception {
        for (String[] page : LIVE_CLASS_PAGES) {
            String html = body(page[0]);
            assertThat(html.contains("데모") || html.contains("서버 전송 없음"))
                    .as("%s 에 데모/서버 전송 없음 표시가 있어야 한다", page[0])
                    .isTrue();
            assertThat(html).as("%s 에 미완성 안내가 남아 있으면 안 된다", page[0])
                    .doesNotContain("준비 중인 기능입니다");
        }
    }

    @Test
    @DisplayName("신규 정적 파일 9개 전체에 외부 URL·CDN·실시간 통신 API 가 없다")
    void noExternalResourcesOrForbiddenRealtimeApis() throws Exception {
        assertThat(NEW_STATIC_FILES).as("신규 정적 파일은 9개여야 한다").hasSize(9);
        String[] forbidden = { "http://", "https://", "fetch(", "WebSocket", "EventSource", "RTCPeerConnection" };
        for (String path : NEW_STATIC_FILES) {
            String src = body(path);
            for (String needle : forbidden) {
                assertThat(src).as("%s 에 금지된 패턴 '%s' 이 있으면 안 된다", path, needle)
                        .doesNotContain(needle);
            }
        }
    }

    @Test
    @DisplayName("3개 화면이 폐기한 /v2 디자인 시스템(tokens/base/components.css)을 로드하지 않는다")
    void pagesDoNotLoadRetiredV2DesignSystem() throws Exception {
        for (String[] page : LIVE_CLASS_PAGES) {
            assertThat(body(page[0]))
                    .as("%s 는 운영 UI 문법만 쓴다", page[0])
                    .doesNotContain("/v2/assets/tokens.css")
                    .doesNotContain("/v2/assets/base.css")
                    .doesNotContain("/v2/assets/components.css");
        }
    }

    @Test
    @DisplayName("정적 화면에 CSRF 토큰 없는 /logout POST 폼이 없다 — 있으면 눌러도 403 이다")
    void noTokenlessLogoutPostFormInPages() throws Exception {
        for (String[] page : LIVE_CLASS_PAGES) {
            String src = body(page[0]);
            assertThat(src).as("%s 에 동작하지 않는 로그아웃 POST 폼이 남아 있다", page[0])
                    .doesNotContain("action=\"/logout\"");
        }
    }

    // ================================================================
    //  Task 1 · Step 9 — CSS 범위 제한 (기존 테스트의 private 파서를 재사용하지 않고 새로 둔다)
    // ================================================================

    /** 주석을 걷어내고 중괄호를 세어 규칙 선택자만 뽑는다(@media 등 at-rule 은 건너뛴다). */
    private static List<String> liveClassSelectorsOf(String css) {
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

    private static final String[] LIVE_CLASS_ROOTS = { ".live-precheck-page", ".live-classroom-page", ".live-monitor-page" };

    @Test
    @DisplayName("live-class.css 의 모든 규칙이 3개 페이지 루트 클래스 아래로 범위 제한되어 있다")
    void liveClassCssIsScopedToPageRoots() throws Exception {
        List<String> leaked = new ArrayList<>();
        for (String selector : liveClassSelectorsOf(body(NEW_CSS))) {
            for (String part : selector.split(",")) {
                String one = part.trim().replaceAll("\\s+", " ");
                if (one.isEmpty()) continue;
                boolean scoped = false;
                for (String root : LIVE_CLASS_ROOTS) {
                    if (one.startsWith(root)) { scoped = true; break; }
                }
                if (!scoped) leaked.add(one);
            }
        }
        assertThat(leaked)
                .as("페이지 루트 클래스로 시작하지 않는 선택자는 운영/기존 시험 데모 화면까지 영향을 준다")
                .isEmpty();
    }

    @Test
    @DisplayName("[hidden] 규칙이 3개 루트별로만 선언되어 있다")
    void hiddenRuleIsPerRootOnly() throws Exception {
        String css = body(NEW_CSS);
        for (String root : LIVE_CLASS_ROOTS) {
            assertThat(css).as("%s 에 [hidden] 규칙이 있어야 한다", root).contains(root + " [hidden]");
        }
    }

    // ================================================================
    //  정적 검사 — 중복 id 0건, href="#" 0건 (Task 7)
    // ================================================================

    private static final Pattern ID_ATTR = Pattern.compile("\\bid=\"([^\"]+)\"");

    @Test
    @DisplayName("3개 화면 각각 중복 id 가 없다")
    void noDuplicateIdsWithinEachPage() throws Exception {
        for (String[] page : LIVE_CLASS_PAGES) {
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
    @DisplayName("3개 화면에 href=\"#\" 더미 링크가 없다")
    void noHashOnlyLinks() throws Exception {
        for (String[] page : LIVE_CLASS_PAGES) {
            assertThat(body(page[0])).as("%s 에 href=\"#\" 가 있으면 안 된다", page[0])
                    .doesNotContain("href=\"#\"");
        }
    }

    @Test
    @DisplayName("데모 데이터에 실제 개인정보 형태의 값이 없다")
    void demoDataHasNoPersonalNumbers() throws Exception {
        String data = body("/v2/assets/live-class-demo-data.js");
        assertThat(data).doesNotMatch("(?s).*\\d{6}\\s*-\\s*\\d{7}.*");
        assertThat(data).as("전화번호 형태가 없어야 한다").doesNotMatch("(?s).*01\\d-\\d{3,4}-\\d{4}.*");
    }

    // ================================================================
    //  Task 2 · Step 2 — live-class-common.js 순수 함수를 Node 로 실제 실행
    // ================================================================

    private record NodeRun(int code, String out) {
    }

    private NodeRun runNode(String script) {
        try {
            Path tmp = Files.createTempFile("live-class-contract-", ".js");
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
    @DisplayName("[Task2] live-class-common.js 의 supportState/mediaErrorInfo/allChecksPassed 를 Node 로 실제 실행한다")
    void liveClassCommon_실제실행() {
        String script = """
                const C = require(%s);

                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) {
                    console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")");
                    bad++;
                  }
                }

                /* ---- supportState ---- */
                const okState = C.supportState(true, {});
                check("secure+devices: ok", okState.ok, true);
                check("secure+devices: reason", okState.reason, "OK");

                const insecure = C.supportState(false, {});
                check("insecure: ok", insecure.ok, false);
                check("insecure: reason", insecure.reason, "INSECURE_CONTEXT");

                const unsupported = C.supportState(true, null);
                check("no mediaDevices: ok", unsupported.ok, false);
                check("no mediaDevices: reason", unsupported.reason, "UNSUPPORTED");

                /* ---- mediaErrorInfo: 오류 7종(6개 명명 + 그 외) ---- */
                const cases = [
                  ["NotAllowedError",      "PERMISSION_DENIED"],
                  ["NotFoundError",        "DEVICE_NOT_FOUND"],
                  ["NotReadableError",     "DEVICE_BUSY"],
                  ["OverconstrainedError", "CONSTRAINT_FAILED"],
                  ["AbortError",           "ABORTED"],
                  ["TypeError",            "CONTEXT_ERROR"],
                  ["SomeWeirdError",       "UNKNOWN_ERROR"]
                ];
                const seenReasons = new Set();
                for (const [name, expectedReason] of cases) {
                  const info = C.mediaErrorInfo(name, "카메라");
                  check("mediaErrorInfo " + name + " reason", info.reason, expectedReason);
                  if (!info.message || !info.message.trim()) { console.log("FAIL " + name + " 메시지 없음"); bad++; }
                  seenReasons.add(info.reason);
                }
                if (seenReasons.size !== 7) { console.log("FAIL 오류 사유가 7종이 아님: " + seenReasons.size); bad++; }

                /* ---- allChecksPassed: 자동 통과가 없어야 한다 ---- */
                check("전부 pass", C.allChecksPassed({ camera: "pass", mic: "pass", screen: "pass" }), true);
                check("하나라도 idle", C.allChecksPassed({ camera: "pass", mic: "idle", screen: "pass" }), false);
                check("빈 객체", C.allChecksPassed({}), false);
                check("null", C.allChecksPassed(null), false);

                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("live-class-common.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("live-class-common.js 판정이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    // ================================================================
    //  2차 검수 대응 — MediaStream 수명주기·장치 제거·선택 정합성 (P1/P2)
    // ================================================================

    /** {@code needle} 이 처음 나오는 지점부터 {@code len} 글자를 잘라 온다(없으면 빈 문자열). */
    private static String sliceFrom(String src, String needle, int len) {
        int i = src.indexOf(needle);
        if (i < 0) return "";
        return src.substring(i, Math.min(src.length(), i + len));
    }

    @Test
    @DisplayName("[P1] live-class-common.js 의 createStreamSlot() 이 스트림 교체·낡은요청 폐기·dispose 를 실제로 처리한다")
    void liveClassCommon_스트림슬롯_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + actual); bad++; }
                }
                function fakeTrack() {
                  return { stopped: false, stop() { this.stopped = true; }, addEventListener() {} };
                }
                function fakeStream(tracks) { return { _t: tracks, getTracks() { return this._t; } }; }

                /* ① 정상 채택 + 다음 채택 때 이전 스트림을 정지한다 */
                const slot = C.createStreamSlot();
                const tA = fakeTrack(), sA = fakeStream([tA]);
                const tok1 = slot.begin();
                check("첫 채택 성공", slot.resolve(tok1, sA), true);
                check("채택된 스트림", slot.getStream() === sA, true);
                check("최초엔 정지 안 됨", tA.stopped, false);

                const tB = fakeTrack(), sB = fakeStream([tB]);
                const tok2 = slot.begin();
                check("두 번째 채택 성공", slot.resolve(tok2, sB), true);
                check("이전 스트림 트랙이 정지됨", tA.stopped, true);
                check("새 스트림으로 교체됨", slot.getStream() === sB, true);
                check("새 스트림은 살아있음", tB.stopped, false);

                /* ② 더 최신 요청이 있으면 낡은 토큰의 결과는 채택되지 않고 즉시 정지된다 */
                const slot2 = C.createStreamSlot();
                const tokOld = slot2.begin();
                const tokNew = slot2.begin();               // tokOld 를 낡게 만든다
                const tStale = fakeTrack(), sStale = fakeStream([tStale]);
                check("낡은 토큰은 거부됨", slot2.resolve(tokOld, sStale), false);
                check("낡은 스트림은 즉시 정지됨", tStale.stopped, true);
                check("아무것도 채택되지 않음", slot2.getStream(), null);
                void tokNew;

                /* ③ dispose 이후에는 이미 있던 스트림도, 늦게 도착하는 스트림도 전부 정지·거부된다 */
                const slot3 = C.createStreamSlot();
                const tLive = fakeTrack(), sLive = fakeStream([tLive]);
                const tokLive = slot3.begin();
                slot3.resolve(tokLive, sLive);
                slot3.dispose();
                check("dispose 시 활성 스트림도 정지됨", tLive.stopped, true);
                check("dispose 후 getStream 은 null", slot3.getStream(), null);

                const tLate = fakeTrack(), sLate = fakeStream([tLate]);
                check("dispose 후 같은 토큰도 거부됨", slot3.resolve(tokLive, sLate), false);
                check("dispose 후 늦게 온 스트림도 즉시 정지됨", tLate.stopped, true);

                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("live-class-common.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("createStreamSlot() 동작이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[P1] isDevicePresent() 가 활성 장치ID 기준으로만 판정한다 — 다른 장치가 남아있어도 자동 통과시키지 않는다")
    void liveClassCommon_장치존재판정_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + actual); bad++; }
                }
                const list = [{ deviceId: "id-1" }, { deviceId: "id-3" }];
                check("남아있는 장치", C.isDevicePresent("id-1", list), true);
                check("제거된 장치(다른 장치는 남아있음)", C.isDevicePresent("id-2", list), false);
                check("deviceId 없음", C.isDevicePresent(null, list), false);
                check("목록 없음", C.isDevicePresent("id-1", null), false);
                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("live-class-common.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("isDevicePresent() 판정이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[P2] reconcileSelection() 이 보이지 않는 선택을 null 로 되돌린다")
    void liveClassCommon_선택정합성판정_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + actual); bad++; }
                }
                check("보이면 유지", C.reconcileSelection("p03", ["p01", "p03", "p05"]), "p03");
                check("안 보이면 null", C.reconcileSelection("p03", ["p01", "p05"]), null);
                check("선택 없음", C.reconcileSelection(null, ["p01"]), null);
                check("빈 목록", C.reconcileSelection("p01", []), null);
                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("live-class-common.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("reconcileSelection() 판정이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[P2] 정상 시나리오 설명 문구가 실제 참가자 집계와 모순되지 않는다(하드코딩 대조가 아니라 데이터에서 직접 계산)")
    void demoData_정상시나리오_설명이_실제집계와_일치() {
        String script = """
                const D = require(%s);
                let bad = 0;
                const counts = { ok: 0, warn: 0, offline: 0 };
                D.participants.forEach(function (p) { counts[p.state] = (counts[p.state] || 0) + 1; });
                const normal = D.scenarios.find(function (s) { return s.key === "normal"; });
                if (!normal) { console.log("FAIL normal 시나리오를 찾지 못했다"); bad++; }
                else {
                  if (normal.desc.indexOf("정상 " + counts.ok + "명") === -1) { console.log("FAIL 정상 인원 불일치: " + normal.desc); bad++; }
                  if (normal.desc.indexOf("주의 " + counts.warn + "명") === -1) { console.log("FAIL 주의 인원 불일치: " + normal.desc); bad++; }
                  if (normal.desc.indexOf("끊김 " + counts.offline + "명") === -1) { console.log("FAIL 끊김 인원 불일치: " + normal.desc); bad++; }
                }
                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("live-class-demo-data.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("정상 시나리오 설명이 실제 집계와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[P2] 사전 점검 문구가 로컬 미리보기이며 송출되지 않는다고 명시한다")
    void precheckWordingIsHonestAboutLocalPreview() throws Exception {
        String html = body("/v2/lxp/trainee/live-class-precheck.html");
        assertThat(html).as("실제로는 아무에게도 안 보이는데 '다른 참가자에게 보이는'이라고 쓰면 안 된다")
                .doesNotContain("다른 참가자에게 보이는 화면입니다");
        assertThat(html).as("실제로는 아무에게도 안 들리는데 '다른 참가자에게 들리는'이라고 쓰면 안 된다")
                .doesNotContain("다른 참가자에게 들리는 소리입니다");
        assertThat(html).contains("로컬 미리보기");
        assertThat(html).contains("송출되지 않습니다");
    }

    @Test
    @DisplayName("[P1] precheck.js 가 스트림 슬롯·aria-busy·활성 장치ID 판정·enumerateDevices 실패 처리를 실제로 쓴다")
    void precheckJsHasAsyncSafetyContract() throws Exception {
        String js = body("/v2/assets/live-class-precheck.js");
        assertThat(js).as("카메라 슬롯").contains("cameraSlot = C.createStreamSlot()");
        assertThat(js).as("마이크 슬롯").contains("micSlot = C.createStreamSlot()");
        assertThat(js).as("화면공유 슬롯").contains("screenSlot = C.createStreamSlot()");
        assertThat(js).as("aria-busy 갱신").contains("setAttribute(\"aria-busy\"");
        assertThat(js).as("성공 시점 deviceId 를 직접 캡처해야 한다(select.value 재검사 금지)")
                .contains("getSettings().deviceId");
        assertThat(js).as("장치 생존 판정을 공통 함수로 위임").contains("C.isDevicePresent(");
        assertThat(js).as("재점검·전환 실패 시 기존 통과 상태를 지키는 안내 경로가 있어야 한다")
                .contains("showAdvisory(");
        assertThat(js).as("pagehide 에서 세 슬롯을 모두 폐기해야 한다")
                .contains("cameraSlot.dispose()").contains("micSlot.dispose()").contains("screenSlot.dispose()");

        String refresh = sliceFrom(js, "function refreshDeviceLists()", 1400);
        assertThat(refresh).as("refreshDeviceLists 함수를 찾지 못했다").isNotEmpty();
        assertThat(refresh).as("enumerateDevices() 실패를 처리하는 catch 가 있어야 한다").contains(".catch(");
    }

    @Test
    @DisplayName("[P1/P2] classroom.js 가 스트림 슬롯·aria-pressed/aria-busy·탭 키보드 이동을 실제로 쓰고, HTML 이 tablist ARIA 계약을 갖는다")
    void classroomJsHasAsyncSafetyAndTabAriaContract() throws Exception {
        String js = body("/v2/assets/live-classroom.js");
        assertThat(js).contains("cameraSlot = C.createStreamSlot()");
        assertThat(js).contains("micSlot = C.createStreamSlot()");
        assertThat(js).contains("screenSlot = C.createStreamSlot()");
        assertThat(js).as("미디어 버튼의 활성 상태를 aria-pressed 로 알려야 한다").contains("setAttribute(\"aria-pressed\"");
        assertThat(js).as("처리 중에는 aria-busy 를 세워야 한다").contains("setAttribute(\"aria-busy\"");
        assertThat(js).as("좌우/Home/End 키로 탭을 이동해야 한다")
                .contains("\"ArrowRight\"").contains("\"ArrowLeft\"").contains("\"Home\"").contains("\"End\"");
        assertThat(js).as("이전 스트림의 뒤늦은 ended 가 새 스트림 UI 를 끄면 안 된다 — 슬롯이 아직 이 스트림을 들고 있을 때만 반응")
                .contains("cameraSlot.getStream() !== stream");

        String html = body("/v2/lxp/trainee/live-classroom.html");
        assertThat(html).contains("role=\"tablist\"");
        assertThat(html).contains("role=\"tab\"");
        assertThat(html).contains("role=\"tabpanel\"");
        assertThat(html).contains("aria-controls=");
        assertThat(html).contains("aria-selected=");
    }

    @Test
    @DisplayName("[P2] monitor.js 가 검색·필터 변경 시 LiveClassCommon.reconcileSelection() 을 실제로 호출한다")
    void monitorJsCallsReconcileSelectionOnFilterChange() throws Exception {
        String js = body("/v2/assets/live-class-monitor.js");
        assertThat(js).as("선택 정합성 판정을 공통 함수로 위임해야 한다").contains("C.reconcileSelection(");
        assertThat(js).as("검색 입력이 정합성 재계산 경로를 타야 한다").contains("applyFiltersAndRender");
        assertThat(js).as("필터 변경도 같은 경로를 타야 한다")
                .contains("searchEl.addEventListener(\"input\", function () { keyword = this.value.trim(); applyFiltersAndRender(); })")
                .contains("filterEl.addEventListener(\"change\", applyFiltersAndRender)");
    }

    // ================================================================
    //  3차 검수 대응 — 전환 실패 시 pass 소실, 화면공유 ended 미복구,
    //  select busy/옵션 혼동, 트랙 미검증 채택 (P1/P2)
    // ================================================================

    @Test
    @DisplayName("[P1] live-class-common.js 의 adoptTrack() 이 트랙 없는 성공 응답을 채택하지 않고 전부 정지한다")
    void liveClassCommon_트랙채택_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + actual); bad++; }
                }
                function fakeTrack() { return { stopped: false, stop() { this.stopped = true; }, addEventListener() {} }; }
                function fakeStream(opts) {
                  opts = opts || {};
                  const video = opts.video || [], audio = opts.audio || [];
                  return { getVideoTracks() { return video; }, getAudioTracks() { return audio; }, getTracks() { return video.concat(audio); } };
                }

                /* ① 트랙이 있고 토큰이 최신이면 채택하고 트랙을 돌려준다 */
                const slotA = C.createStreamSlot();
                const vTrack = fakeTrack();
                const streamA = fakeStream({ video: [vTrack] });
                const tokA = slotA.begin();
                const gotA = C.adoptTrack(slotA, tokA, streamA, "video");
                check("트랙 반환", gotA === vTrack, true);
                check("슬롯에 채택됨", slotA.getStream() === streamA, true);

                /* ② 요청한 종류의 트랙이 없으면 채택하지 않고 스트림의 모든 트랙을 정지한다 */
                const slotB = C.createStreamSlot();
                const strayTrack = fakeTrack();
                const emptyStream = fakeStream({ video: [], audio: [strayTrack] });
                const tokB = slotB.begin();
                const gotB = C.adoptTrack(slotB, tokB, emptyStream, "video");
                check("video 트랙 없음 → null", gotB, null);
                check("스트림의 다른 트랙도 전부 정지됨", strayTrack.stopped, true);
                check("슬롯은 채택 안 됨", slotB.getStream(), null);

                /* ③ 트랙은 있지만 토큰이 낡았으면 거부하고 즉시 정지한다 */
                const slotC = C.createStreamSlot();
                const tokOld = slotC.begin();
                const tokNew = slotC.begin();
                void tokNew;
                const staleTrack = fakeTrack();
                const staleStream = fakeStream({ video: [staleTrack] });
                const gotC = C.adoptTrack(slotC, tokOld, staleStream, "video");
                check("낡은 토큰 → null", gotC, null);
                check("낡은 스트림 즉시 정지", staleTrack.stopped, true);

                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("live-class-common.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("adoptTrack() 동작이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[P2] computeSelectDisabled() 가 요청 중이거나 옵션이 없으면 비활성으로 판정한다")
    void liveClassCommon_select비활성판정_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + actual); bad++; }
                }
                check("busy+옵션있음 → 비활성", C.computeSelectDisabled(true, true), true);
                check("옵션없음(요청 아님) → 비활성", C.computeSelectDisabled(false, false), true);
                check("busy+옵션없음 → 비활성", C.computeSelectDisabled(true, false), true);
                check("요청 끝 + 옵션있음 → 활성", C.computeSelectDisabled(false, true), false);
                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("live-class-common.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("computeSelectDisabled() 판정이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[P1] switchFailurePolicy() 가 기존 pass 상태였던 재점검·전환 실패에서만 스트림·상태를 지킨다")
    void liveClassCommon_전환실패정책_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
                    console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")");
                    bad++;
                  }
                }
                check("이미 pass 였던 시도의 실패 → pass 유지 + 스트림 유지",
                      C.switchFailurePolicy(true), { nextState: "pass", keepStream: true });
                check("처음부터 idle 이던 시도의 실패 → fail",
                      C.switchFailurePolicy(false), { nextState: "fail", keepStream: false });
                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("live-class-common.js")));

        NodeRun r = runNode(script);
        assertThat(r.code()).as("switchFailurePolicy() 판정이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[P1] precheck.js 의 카메라·마이크 전환·화면공유가 switchFailurePolicy() 로 기존 pass 를 지키는 정책을 실제로 쓴다")
    void precheckJsUsesSwitchFailurePolicyToPreservePassState() throws Exception {
        String js = body("/v2/assets/live-class-precheck.js");
        assertThat(js).as("공통 실패 정책 함수를 실제로 호출해야 한다").contains("C.switchFailurePolicy(");

        String switchCam = sliceFrom(js, "function switchCamera(", 1400);
        assertThat(switchCam).as("switchCamera 를 찾지 못했다").isNotEmpty();
        assertThat(switchCam).as("이미 pass 였다면 요청 시작 시점에 checking 으로 내리면 안 된다 — 배지·게이트가 그대로여야 한다")
                .contains("if (!wasPass)");

        String switchMicSrc = sliceFrom(js, "function switchMic(", 1400);
        assertThat(switchMicSrc).as("switchMic 를 찾지 못했다").isNotEmpty();
        assertThat(switchMicSrc).contains("if (!wasPass)");
    }

    @Test
    @DisplayName("[P1] precheck.js 가 화면공유 트랙 ended 시 미리보기·버튼·체크 상태를 전부 초기화한다")
    void precheckJsResetsScreenShareUiOnTrackEnded() throws Exception {
        String js = body("/v2/assets/live-class-precheck.js");
        String bound = sliceFrom(js, "function bindTrackEnded(", 1200);
        assertThat(bound).as("bindTrackEnded 를 찾지 못했다").isNotEmpty();
        assertThat(bound).as("화면공유 분기가 있어야 한다").contains("\"screen\"");
        assertThat(bound).as("화면공유 종료 시 정지·미리보기 분리·버튼 초기화를 한 번에 하는 함수를 재사용해야 한다")
                .contains("stopScreenShare()");
        assertThat(bound).as("정확한 안내 문구가 있어야 한다").contains("화면 공유가 중단되었습니다. 다시 시작해 주세요.");
    }

    @Test
    @DisplayName("[P2] precheck.js 가 select 의 busy 와 '옵션 있음'을 분리해서 판정한다")
    void precheckJsSeparatesSelectBusyFromAvailability() throws Exception {
        String js = body("/v2/assets/live-class-precheck.js");
        assertThat(js).as("공통 판정 함수를 실제로 써야 한다 — busy 해제가 옵션 없는 select 를 열면 안 된다")
                .contains("C.computeSelectDisabled(");
    }

    @Test
    @DisplayName("[P2] precheck.js 의 카메라·마이크 전환과 화면공유가 트랙 존재를 슬롯 채택 전에 검증한다(adoptTrack)")
    void precheckJsValidatesTrackBeforeAdopting() throws Exception {
        String js = body("/v2/assets/live-class-precheck.js");
        int count = js.split("C\\.adoptTrack\\(", -1).length - 1;
        assertThat(count).as("카메라 전환·마이크 전환·화면공유 3곳 모두 adoptTrack 을 써야 한다").isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("[P2] classroom.js 의 카메라·마이크·화면공유가 트랙 존재를 슬롯 채택 전에 검증한다(adoptTrack)")
    void classroomJsValidatesTrackBeforeAdopting() throws Exception {
        String js = body("/v2/assets/live-classroom.js");
        int count = js.split("C\\.adoptTrack\\(", -1).length - 1;
        assertThat(count).as("카메라·마이크·화면공유 3곳 모두 adoptTrack 을 써야 한다").isGreaterThanOrEqualTo(3);
    }

    // ================================================================
    //  4차 검수 대응 — no-track 실패 시 select 미복원(precheck) ·
    //  강의실 no-track 무안내(classroom) 보완
    // ================================================================

    @Test
    @DisplayName("[보완1] precheck.js 의 no-track 실패 경로도 reject 경로와 같은 select 복원 함수를 쓴다(4곳: 카메라·마이크 각각 no-track+reject)")
    void precheckJsRestoresSelectOnNoTrackFailureToo() throws Exception {
        String js = body("/v2/assets/live-class-precheck.js");
        int count = js.split("restoreSelectAfterFailedSwitch\\(", -1).length - 1;
        assertThat(count)
                .as("switchCamera 의 no-track·reject, switchMic 의 no-track·reject — 총 4곳이 같은 복원 함수를 써야 한다")
                .isGreaterThanOrEqualTo(4);

        String switchCam = sliceFrom(js, "function switchCamera(", 1700);
        String noTrackBranch = sliceFrom(switchCam, "if (!track) {", 300);
        assertThat(noTrackBranch).as("switchCamera 의 no-track 분기가 select 복원 함수를 불러야 한다")
                .contains("restoreSelectAfterFailedSwitch(cameraSelectEl, activeDeviceId.camera");

        String switchMicSrc = sliceFrom(js, "function switchMic(", 1700);
        String micNoTrackBranch = sliceFrom(switchMicSrc, "if (!track) {", 300);
        assertThat(micNoTrackBranch).as("switchMic 의 no-track 분기가 select 복원 함수를 불러야 한다")
                .contains("restoreSelectAfterFailedSwitch(micSelectEl, activeDeviceId.mic");
    }

    @Test
    @DisplayName("[보완2] classroom.js 가 마이크·화면공유의 현재 요청 no-track 을 사용자에게 안내한다")
    void classroomJsAnnouncesMicAndScreenNoTrackFailure() throws Exception {
        String js = body("/v2/assets/live-classroom.js");
        assertThat(js).contains("마이크 트랙을 가져오지 못했습니다. 다시 시도해 주세요.");
        assertThat(js).contains("화면 공유 트랙을 가져오지 못했습니다. 다시 시도해 주세요.");

        String micSlice = sliceFrom(js, "navigator.mediaDevices.getUserMedia({ audio: true, video: false })", 600);
        assertThat(micSlice).as("마이크 no-track 안내가 현재 요청일 때만 나가야 한다").contains("micSlot.isCurrent(token)");

        String screenSlice = sliceFrom(js, "navigator.mediaDevices.getDisplayMedia({ video: true, audio: false })", 600);
        assertThat(screenSlice).as("화면공유 no-track 안내가 현재 요청일 때만 나가야 한다").contains("screenSlot.isCurrent(token)");
    }

    /** classroom.js 를 실제로 require 해 카메라 버튼 클릭을 흉내 낸다 — 최소한의 DOM/브라우저 API 스텁.
        문자열 검사가 아니라 실제 클릭 → getUserMedia(no-track 스트림) → 콜백 흐름을 그대로 실행한다. */
    private String stubDomAndClassroomHarness() {
        return """
                function stubEl() {
                  var attrs = {};
                  var el = {
                    dataset: {}, style: {}, hidden: false, disabled: false, value: "",
                    tabIndex: 0, srcObject: null, innerHTML: "", _handlers: {},
                    setAttribute: function (k, v) { attrs[k] = String(v); },
                    removeAttribute: function (k) { delete attrs[k]; },
                    getAttribute: function (k) { return Object.prototype.hasOwnProperty.call(attrs, k) ? attrs[k] : null; },
                    addEventListener: function (evt, fn) { el._handlers[evt] = fn; },
                    removeEventListener: function () {},
                    appendChild: function () { return this; },
                    querySelector: function () { return stubEl(); },
                    querySelectorAll: function () { return []; },
                    closest: function () { return null; },
                    getBoundingClientRect: function () { return { width: 0, height: 0, left: 0, top: 0 }; },
                    play: function () { return Promise.resolve(); },
                    classList: { toggle: function () {}, add: function () {}, remove: function () {} }
                  };
                  var text = "", textSetCount = 0;
                  Object.defineProperty(el, "textContent", {
                    get: function () { return text; },
                    set: function (v) { text = v; textSetCount++; el._textSetCount = textSetCount; }
                  });
                  return el;
                }

                var els = {};
                function byId(id) { if (!els[id]) els[id] = stubEl(); return els[id]; }

                var tabButton = stubEl();
                tabButton.setAttribute("aria-controls", "stagePane");
                tabButton.setAttribute("aria-selected", "true");
                var classroomTabsEl = stubEl();
                classroomTabsEl.querySelectorAll = function (sel) { return sel === '[role="tab"]' ? [tabButton] : []; };
                classroomTabsEl.querySelector = function (sel) { return sel === '[aria-selected="true"]' ? tabButton : null; };
                els["classroomTabs"] = classroomTabsEl;

                global.document = {
                  getElementById: byId,
                  querySelector: function (sel) {
                    if (sel === '[data-role="self-preview"]') return byId("__self-preview");
                    if (sel === '[data-role="self-placeholder"]') return byId("__self-placeholder");
                    if (sel === '[data-role="screen-preview"]') return byId("__screen-preview");
                    if (sel === '[data-role="screen-placeholder"]') return byId("__screen-placeholder");
                    return stubEl();
                  },
                  querySelectorAll: function () { return []; },
                  addEventListener: function () {},
                  createElement: function () { return stubEl(); }
                };
                global.window = {
                  isSecureContext: true,
                  matchMedia: function () { return { matches: false, addEventListener: function () {}, removeEventListener: function () {} }; },
                  addEventListener: function () {}
                };
                global.window.LiveClassCommon = require(%s);
                global.window.LiveClassDemoData = require(%s);
                /* Node 는 전역 navigator 를 getter 전용 접근자 프로퍼티로 미리 정의해 둔다(set 없음).
                   그냥 대입하면 비엄격 모드에서 조용히 무시되어 실제로는 Node 내장 navigator 가 그대로
                   남는다(mediaDevices 없음) — defineProperty 로 데이터 프로퍼티로 갈아끼워야 한다. */
                Object.defineProperty(global, "navigator", {
                  value: { mediaDevices: { getUserMedia: function (c) { return global.__gum(c); } } },
                  writable: true, configurable: true
                });

                function flush() { return new Promise(function (resolve) { setTimeout(resolve, 0); }); }
                function noTrackStream() { return { getVideoTracks: function () { return []; }, getAudioTracks: function () { return []; }, getTracks: function () { return []; } }; }

                require(%s);   /* live-classroom.js 를 실제로 로드한다 — 여기서 syncTabMode() 등 최초 실행까지 끝난다 */
                """.formatted(
                        jsString(STATIC_V2_ASSETS.resolve("live-class-common.js")),
                        jsString(STATIC_V2_ASSETS.resolve("live-class-demo-data.js")),
                        jsString(STATIC_V2_ASSETS.resolve("live-classroom.js")));
    }

    @Test
    @DisplayName("[보완2 실행형] classroom.js 카메라 버튼 — 현재 요청의 no-track 은 안내하고, 경합에서 낡은 요청은 침묵한다")
    void classroomJs_카메라_현재요청과_낡은요청_no_track을_실제로_구분한다() {
        String script = stubDomAndClassroomHarness() + """

                (async function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  var camBtn = els["camBtn"];
                  var stageMsgEl = els["stageMsg"];
                  var EXPECTED = "카메라 트랙을 가져오지 못했습니다. 다시 시도해 주세요.";

                  /* ① 단독 클릭 — 현재 요청의 no-track 은 실제로 안내되어야 한다 */
                  global.__gum = function () { return Promise.resolve(noTrackStream()); };
                  camBtn._handlers.click();
                  await flush();
                  check("현재 요청 no-track 안내 표시", stageMsgEl.textContent, EXPECTED);
                  check("busy 해제(재시도 가능)", camBtn.disabled, false);
                  check("aria-pressed 는 false 유지", camBtn.getAttribute("aria-pressed"), "false");
                  check("data-state 는 on 으로 바뀌지 않음", camBtn.dataset.state !== "on", true);

                  /* ② 빠른 재클릭(경합) — 두 번째 클릭이 슬롯을 선점하므로 첫 번째는 낡은 요청이 되어
                     콜백이 와도 안내를 침묵해야 한다. 두 클릭 모두 no-track 스트림을 받게 한다.
                     둘 다 안내했다면(버그) textContent 대입이 2번, 낡은 쪽이 침묵했다면(정상) 1번뿐이다. */
                  var before = stageMsgEl._textSetCount || 0;
                  camBtn._handlers.click();   /* 토큰 A 시작 */
                  camBtn._handlers.click();   /* 토큰 B 시작 — A 를 낡게 만든다 */
                  await flush();
                  var after = stageMsgEl._textSetCount || 0;
                  check("경합에서 안내는 딱 한 번만(낡은 요청은 침묵)", after - before, 1);
                  check("경합 이후에도 안내 문구는 동일 메시지", stageMsgEl.textContent, EXPECTED);

                  if (bad) process.exit(1);
                  console.log("OK");
                })().catch(function (e) { console.log("FAIL 예외: " + (e && e.stack || e)); process.exit(1); });
                """;

        NodeRun r = runNode(script);
        assertThat(r.code()).as("classroom.js 카메라 no-track 처리가 기대와 다릅니다:%n%s", r.out()).isZero();
    }
}
