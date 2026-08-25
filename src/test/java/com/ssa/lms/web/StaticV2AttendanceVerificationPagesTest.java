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
 * LXP-144/146/147 출결·OTP·본인인증·HRD 전송 데모 정적 화면(/v2/**) 회귀 테스트.
 *
 * <p>정적 HTML/CSS/JS 라 컨트롤러 테스트에 걸리지 않고, 자산 경로가 어긋나면 배포
 * 후 404 로만 드러난다 — {@link StaticV2LearningPresencePagesTest}, {@link StaticV2LiveClassPagesTest}
 * 와 같은 이유로 별도 계약 테스트가 필요하다.</p>
 *
 * <p><b>범위 한계:</b> 이 테스트는 파일이 서빙되는지, 뼈대·자산·CSS 범위·데모 표시가
 * 있는지만 본다. 실제 OTP 발송·모바일 본인인증 팝업·HRD 전송·반응형 레이아웃 육안은
 * 자동 검증 대상이 아니다. 단, {@code attendance-verification-common.js} 의 판정·전이
 * 순수 함수는 Node 로 실제 실행해 검증한다 — 문자열 존재 검사가 아니다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StaticV2AttendanceVerificationPagesTest {

    @Autowired MockMvc mvc;

    private static final String[][] PAGES = {
            { "/v2/lxp/trainee/attendance.html", "출결 현황·본인인증" },
            { "/v2/admin/attendance.html",       "출결부·HRD 전송" }
    };

    private static final String[] TRAINEE_PAGES = { "/v2/lxp/trainee/attendance.html" };
    private static final String[] ADMIN_PAGES = { "/v2/admin/attendance.html" };

    private static final String[][] PAGE_ROOT = {
            { "/v2/lxp/trainee/attendance.html", "attendance-verification-page" },
            { "/v2/admin/attendance.html",       "attendance-admin-page" }
    };

    private static final String NEW_CSS = "/v2/assets/attendance-verification.css";

    private static final String[] NEW_JS_ASSETS = {
            "/v2/assets/attendance-verification-common.js",
            "/v2/assets/attendance-verification-demo-data.js",
            "/v2/assets/attendance-trainee.js",
            "/v2/assets/attendance-admin.js"
    };

    private static final String[] NEW_STATIC_FILES = {
            "/v2/lxp/trainee/attendance.html",
            "/v2/admin/attendance.html",
            "/v2/assets/attendance-verification.css",
            "/v2/assets/attendance-verification-common.js",
            "/v2/assets/attendance-verification-demo-data.js",
            "/v2/assets/attendance-trainee.js",
            "/v2/assets/attendance-admin.js"
    };

    private String body(String path) throws Exception {
        return mvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ================================================================
    //  정적 페이지·자산 계약
    // ================================================================

    @Test
    @DisplayName("[Task1] 출결 데모 2개 화면이 로그인 없이 200 으로 열리고 문서가 끝까지 온다")
    void allPagesServed() throws Exception {
        for (String[] page : PAGES) {
            String html = body(page[0]);
            assertThat(html).as("%s 응답이 잘리지 않아야 한다", page[0]).contains("</html>");
            assertThat(html).as("%s 에 <main> 본문 영역이 있어야 한다", page[0]).contains("<main");
        }
    }

    @Test
    @DisplayName("[Task1] 신규 CSS 1개와 JS 4개가 200 이고 비어 있지 않다")
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
    @DisplayName("[Task1] 2개 화면이 서로 다른 고유 제목을 가진다")
    void eachPageHasItsOwnTitle() throws Exception {
        for (String[] page : PAGES) {
            assertThat(body(page[0])).as("%s 의 제목에 '%s' 가 있어야 한다", page[0], page[1]).contains(page[1]);
        }
    }

    @Test
    @DisplayName("[Task1] 각 화면의 <body> 가 자기 페이지 루트 클래스를 달고 있다")
    void everyPageCarriesItsRootClassOnBody() throws Exception {
        for (String[] pr : PAGE_ROOT) {
            assertThat(body(pr[0])).as("%s 의 <body> 에 %s 가 있어야 한다", pr[0], pr[1]).contains("<body class=\"" + pr[1] + "\">");
        }
    }

    @Test
    @DisplayName("[Task1] 훈련생 화면은 훈련생 운영 CSS 만, 관리자 화면은 관리자 운영 CSS 만 로드한다")
    void operationalCssIsNotMixed() throws Exception {
        for (String path : TRAINEE_PAGES) {
            String html = body(path);
            assertThat(html).contains("/static/css/basic-form-trainee.css").contains("/static/css/btn-style.css").contains(NEW_CSS);
            assertThat(html).as("%s : 관리자 CSS 섞임 금지", path)
                    .doesNotContain("/static/css/common-style.css")
                    .doesNotContain("/static/css/sidebar-style.css");
        }
        for (String path : ADMIN_PAGES) {
            String html = body(path);
            assertThat(html).contains("/static/css/common-style.css").contains("/static/css/sidebar-style.css").contains("/static/css/btn-style.css").contains(NEW_CSS);
            assertThat(html).as("%s : 훈련생 CSS 섞임 금지", path).doesNotContain("/static/css/basic-form-trainee.css");
        }
    }

    @Test
    @DisplayName("[Task1] 두 화면 모두 exam-legacy-shell.js 를 각 화면 전용 JS 보다 먼저 로드한다")
    void assetsLoadInDependencyOrder() throws Exception {
        String[] ownAssets = { "/v2/assets/attendance-trainee.js", "/v2/assets/attendance-admin.js" };
        for (int i = 0; i < PAGES.length; i++) {
            String path = PAGES[i][0];
            String html = body(path);
            assertThat(html).as("%s 는 ExamShell 을 호출해야 한다", path).contains("ExamShell.");
            int s = html.indexOf("/v2/assets/exam-legacy-shell.js");
            int c = html.indexOf("/v2/assets/attendance-verification-common.js");
            int d = html.indexOf("/v2/assets/attendance-verification-demo-data.js");
            int o = html.indexOf(ownAssets[i]);
            assertThat(s).as("%s: shell 로드 위치", path).isGreaterThanOrEqualTo(0);
            assertThat(c).as("%s: common 이 shell 뒤", path).isGreaterThan(s);
            assertThat(d).as("%s: demo-data 가 common 뒤", path).isGreaterThan(c);
            assertThat(o).as("%s: 화면 전용 JS 가 demo-data 뒤", path).isGreaterThan(d);
        }
    }

    private static final Pattern ID_ATTR = Pattern.compile("\\bid=\"([^\"]+)\"");

    @Test
    @DisplayName("[Task1] 2개 화면 각각 중복 id 가 없다")
    void noDuplicateIdsWithinEachPage() throws Exception {
        for (String[] page : PAGES) {
            String html = body(page[0]);
            Set<String> seen = new HashSet<>();
            List<String> dup = new ArrayList<>();
            Matcher m = ID_ATTR.matcher(html);
            while (m.find()) { if (!seen.add(m.group(1))) dup.add(m.group(1)); }
            assertThat(dup).as("%s 에 중복된 id 가 있으면 안 된다", page[0]).isEmpty();
        }
    }

    /** href/src 가 가리키는 로컬 절대경로 자산이 실제로 200 인지 확인한다(깨진 참조 검출).
        /v2/, /static/ 로 시작하는 정적 자산만 본다 — 로그인 필요 애플리케이션 라우트는
        미로그인 시 302 로 리다이렉트되는 것이 정상이라 대상이 아니다. */
    private static final Pattern ASSET_REF = Pattern.compile("(?:href|src)=\"(/(?:v2|static)/[^\"]+)\"");

    @Test
    @DisplayName("[Task1] 두 화면이 참조하는 로컬 정적 자산(href/src)에 깨진 링크가 없다")
    void noBrokenInternalAssetReferences() throws Exception {
        for (String[] page : PAGES) {
            String html = body(page[0]);
            Matcher m = ASSET_REF.matcher(html);
            Set<String> refs = new HashSet<>();
            while (m.find()) refs.add(m.group(1));
            for (String ref : refs) mvc.perform(get(ref)).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("[Task1] 신규 정적 파일 7개 전체에 외부 URL·CDN·금지 통신 API·브라우저 저장소가 없다")
    void noExternalResourcesForbiddenApisOrStorage() throws Exception {
        assertThat(NEW_STATIC_FILES).as("신규 정적 파일은 7개여야 한다").hasSize(7);
        String[] forbidden = {
                "http://", "https://", "fetch(", "XMLHttpRequest", "WebSocket", "EventSource", "sendBeacon",
                "localStorage", "sessionStorage", "indexedDB", "caches.open", "document.cookie"
        };
        for (String path : NEW_STATIC_FILES) {
            String src = body(path);
            for (String needle : forbidden) {
                assertThat(src).as("%s 에 금지된 패턴 '%s' 이 있으면 안 된다", path, needle).doesNotContain(needle);
            }
        }
    }

    @Test
    @DisplayName("[Task1] 신규 정적 파일에 Blob·Object URL·다운로드·href=\"#\"·준비중 문구가 없다")
    void noBlobDownloadOrPlaceholder() throws Exception {
        String[] forbidden = { "new Blob", "createObjectURL", "download=", "href=\"#\"", "준비 중인 기능입니다" };
        for (String path : NEW_STATIC_FILES) {
            String src = body(path);
            for (String needle : forbidden) {
                assertThat(src).as("%s 에 금지된 패턴 '%s' 이 있으면 안 된다", path, needle).doesNotContain(needle);
            }
        }
    }

    @Test
    @DisplayName("[Task1] 데모 데이터·화면에 실제 개인정보 형태의 값이 없다")
    void noPersonalInfoPatterns() throws Exception {
        for (String[] page : PAGES) {
            String html = body(page[0]);
            assertThat(html).as("주민등록번호 형태가 없어야 한다").doesNotMatch("(?s).*\\d{6}\\s*-\\s*\\d{7}.*");
            assertThat(html).as("휴대전화 번호 형태가 없어야 한다").doesNotMatch("(?s).*01\\d-\\d{3,4}-\\d{4}.*");
            assertThat(html).as("이메일 형태가 없어야 한다").doesNotMatch("(?s).*[\\w.+-]+@[\\w-]+\\.[\\w.-]+.*");
        }
        /* 1차 보완: HTML·데모 데이터뿐 아니라 신규 JS 4개 전체를 대상으로 한다 —
           화면 JS 나 공용 로직에 개인정보 형태 리터럴이 섞여 들어가는 것도 잡아야 한다. */
        for (String jsPath : NEW_JS_ASSETS) {
            String js = body(jsPath);
            assertThat(js).as("%s : 주민등록번호 형태가 없어야 한다", jsPath).doesNotMatch("(?s).*\\d{6}\\s*-\\s*\\d{7}.*");
            assertThat(js).as("%s : 휴대전화 번호 형태가 없어야 한다", jsPath).doesNotMatch("(?s).*01\\d-\\d{3,4}-\\d{4}.*");
            assertThat(js).as("%s : 이메일 형태가 없어야 한다", jsPath).doesNotMatch("(?s).*[\\w.+-]+@[\\w-]+\\.[\\w.-]+.*");
        }
    }

    private static final Pattern BUTTON_TAG = Pattern.compile("<button\\b[^>]*>");

    @Test
    @DisplayName("[Task1] 두 화면의 모든 button 에 type=\"button\" 이 지정되어 있다")
    void everyButtonHasExplicitTypeButton() throws Exception {
        for (String[] page : PAGES) {
            String html = body(page[0]);
            Matcher m = BUTTON_TAG.matcher(html);
            int count = 0;
            while (m.find()) {
                count++;
                assertThat(m.group()).as("%s 의 버튼 태그에 type=\"button\" 이 있어야 한다: %s", page[0], m.group())
                        .contains("type=\"button\"");
            }
            assertThat(count).as("%s 에 button 요소가 있어야 한다", page[0]).isGreaterThan(0);
        }
    }

    // ================================================================
    //  CSS 범위 제한
    // ================================================================

    private static List<String> attendanceSelectorsOf(String css) {
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

    private static final String[] ATTENDANCE_ROOTS = { ".attendance-verification-page", ".attendance-admin-page" };

    @Test
    @DisplayName("[Task5] attendance-verification.css 의 모든 규칙이 2개 페이지 루트 클래스 아래로 범위 제한되어 있다")
    void cssIsScopedToPageRoots() throws Exception {
        List<String> leaked = new ArrayList<>();
        for (String selector : attendanceSelectorsOf(body(NEW_CSS))) {
            for (String part : selector.split(",")) {
                String one = part.trim().replaceAll("\\s+", " ");
                if (one.isEmpty()) continue;
                boolean scoped = false;
                for (String root : ATTENDANCE_ROOTS) {
                    if (one.startsWith(root)) { scoped = true; break; }
                }
                if (!scoped) leaked.add(one);
            }
        }
        assertThat(leaked).as("페이지 루트 클래스로 시작하지 않는 선택자는 운영/기존 데모 화면까지 영향을 준다").isEmpty();
    }

    @Test
    @DisplayName("[Task5] [hidden] 규칙이 2개 루트별로 선언되어 있고, 다른 규칙이 이를 덮어쓰지 않는다(전역 단독 규칙 없음)")
    void hiddenRulePerRootAndNoBareGlobalRules() throws Exception {
        String css = body(NEW_CSS);
        for (String root : ATTENDANCE_ROOTS) {
            assertThat(css).as("%s 에 [hidden] 규칙이 있어야 한다", root).contains(root + " [hidden]");
        }
        for (String selector : attendanceSelectorsOf(css)) {
            for (String part : selector.split(",")) {
                String one = part.trim().replaceAll("\\s+", " ");
                assertThat(one).as("전역 단독 규칙 금지").isNotIn("html", "body", ".btn", ".modal", "table");
                assertThat(one).as("id 선택자는 [hidden] 보다 특정성이 높아 우선순위 사고를 낼 수 있다 — 쓰지 않는다")
                        .doesNotMatch("^#\\w+.*");
            }
        }
    }

    // ================================================================
    //  Task 2 — attendance-verification-common.js 를 Node 로 실제 실행
    // ================================================================

    private record NodeRun(int code, String out) {
    }

    private NodeRun runNode(String script) {
        try {
            Path tmp = Files.createTempFile("attendance-verification-contract-", ".js");
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
    @DisplayName("[Task1-1] normalizeOtpCode()/validateOtpCode() 가 숫자만 정규화하고 6자리일 때만 제출 가능함을 실제 실행한다")
    void otpNormalizeAndValidate_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                }
                check("문자 제거", C.normalizeOtpCode("1a2b3c4d5e6f"), "123456");
                check("7자리는 6자리로 자름", C.normalizeOtpCode("1234567"), "123456");
                check("null 은 빈 문자열", C.normalizeOtpCode(null), "");
                check("6자리 유효", C.validateOtpCode("123456"), true);
                check("5자리는 무효", C.validateOtpCode("12345"), false);
                check("문자 섞여도 정규화 후 6자리면 유효", C.validateOtpCode("1a2b3c4d5e6"), true);
                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("attendance-verification-common.js")));
        NodeRun r = runNode(script);
        assertThat(r.code()).as("OTP 정규화/검증이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[Task1-2] deriveTraineeAuthState() 가 미발급·입력대기·인증성공·불일치·만료·미수신을 실제로 구분한다")
    void deriveTraineeAuthState_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                }
                check("미발급", C.deriveTraineeAuthState({ issued: false, attemptsLeft: 3, secondsRemaining: 0 }).code, "not_issued");
                check("입력대기", C.deriveTraineeAuthState({ issued: true, attemptsLeft: 3, secondsRemaining: 100 }).code, "awaiting_input");
                check("인증성공", C.deriveTraineeAuthState({ issued: true, verified: true, attemptsLeft: 0, secondsRemaining: 0 }).code, "verified");
                check("불일치", C.deriveTraineeAuthState({ issued: true, lastMismatch: true, attemptsLeft: 2, secondsRemaining: 100 }).code, "mismatch");
                check("만료", C.deriveTraineeAuthState({ issued: true, attemptsLeft: 3, secondsRemaining: 0 }).code, "expired");
                check("시도초과", C.deriveTraineeAuthState({ issued: true, attemptsLeft: 0, secondsRemaining: 50 }).code, "locked");
                check("미수신", C.deriveTraineeAuthState({ issued: true, reportedUnreceived: true, attemptsLeft: 1, secondsRemaining: 10 }).code, "unreceived");
                check("인증성공은 시간·시도와 무관하게 유지된다", C.deriveTraineeAuthState({ issued: true, verified: true, attemptsLeft: 0, secondsRemaining: 0, lastMismatch: true }).code, "verified");
                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("attendance-verification-common.js")));
        NodeRun r = runNode(script);
        assertThat(r.code()).as("deriveTraineeAuthState() 판정이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[Task1-3,4,5,6,7,8] transitionTransferState() 가 정상/재시도/보완전송 전이와 잘못된 이벤트 거부를 실제로 처리한다")
    void transitionTransferState_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                }

                /* 3) 인증성공 전에는 HRD 전송 대기로 이동할 수 없다 — idle 에서 QUEUE 는 거부되어야 한다 */
                const rejectQueue = C.transitionTransferState({ code: "idle", retryCount: 0 }, "QUEUE");
                check("idle+QUEUE 거부", rejectQueue.ok, false);
                check("idle+QUEUE 상태 유지", rejectQueue.code, "idle");
                if (!rejectQueue.reason) { console.log("FAIL 거부 사유가 없다"); bad++; }

                /* 4) otp_verified → hrd_queued → hrd_sending → hrd_success 정상 전이 */
                let s = { code: "idle", retryCount: 0 };
                s = C.transitionTransferState(s, "OTP_VERIFIED"); check("4-1", s.code, "otp_verified");
                s = C.transitionTransferState(s, "QUEUE");        check("4-2", s.code, "hrd_queued");
                s = C.transitionTransferState(s, "SEND");         check("4-3", s.code, "hrd_sending");
                s = C.transitionTransferState(s, "SUCCEED");      check("4-4", s.code, "hrd_success");

                /* 5) hrd_failed → retrying → hrd_success 재시도 전이(retryCount 증가) */
                let f = { code: "hrd_failed", retryCount: 0 };
                f = C.transitionTransferState(f, "RETRY");   check("5-1 code", f.code, "retrying"); check("5-1 count", f.retryCount, 1);
                f = C.transitionTransferState(f, "SUCCEED"); check("5-2", f.code, "hrd_success");

                /* 6) OTP 미수신 상태에서만 모바일 대체 인증을 시작할 수 있다 */
                const rejectMobileFromOtpVerified = C.transitionTransferState({ code: "otp_verified", retryCount: 0 }, "MOBILE_VERIFIED");
                check("otp_verified+MOBILE_VERIFIED 거부", rejectMobileFromOtpVerified.ok, false);
                const allowMobileFromUnreceived = C.transitionTransferState({ code: "otp_unreceived", retryCount: 0 }, "MOBILE_VERIFIED");
                check("otp_unreceived+MOBILE_VERIFIED 허용", allowMobileFromUnreceived.ok, true);

                /* 7) otp_unreceived → mobile_verified → fallback_queued → fallback_sent 보완 전송 전이 */
                let u = { code: "idle", retryCount: 0 };
                u = C.transitionTransferState(u, "OTP_UNRECEIVED");  check("7-1", u.code, "otp_unreceived");
                u = C.transitionTransferState(u, "MOBILE_VERIFIED"); check("7-2", u.code, "mobile_verified");
                u = C.transitionTransferState(u, "QUEUE_FALLBACK");  check("7-3", u.code, "fallback_queued");
                u = C.transitionTransferState(u, "SEND_FALLBACK");   check("7-4", u.code, "fallback_sent");

                /* 8) 허용되지 않은 이벤트는 현재 상태를 유지하고 오류 이유를 반환한다 */
                const badEvent = C.transitionTransferState({ code: "hrd_success", retryCount: 3 }, "SEND");
                check("종료 상태에서 이벤트 거부", badEvent.ok, false);
                check("종료 상태 유지", badEvent.code, "hrd_success");
                check("retryCount 보존", badEvent.retryCount, 3);
                if (!badEvent.reason || badEvent.reason.indexOf("hrd_success") === -1) {
                  console.log("FAIL 거부 사유에 현재 상태가 포함되어야 한다: " + badEvent.reason); bad++;
                }

                /* 입력 객체를 직접 바꾸지 않는다(새 객체 반환) */
                const original = { code: "idle", retryCount: 0 };
                const afterCall = C.transitionTransferState(original, "OTP_VERIFIED");
                check("원본 객체 불변(code)", original.code, "idle");
                check("반환은 새 객체", afterCall !== original, true);

                /* 9) 인증 출처(otpVerified)는 OTP 성공 이후 어떤 후속 전이를 거쳐도 사라지지
                   않는다 — 공단 미수신을 거쳐 모바일 보완 인증까지 가도 "OTP 는 이미
                   성공했었다"는 사실 자체는 보존돼야 한다(2차보완 결함1). */
                let p = { code: "idle", retryCount: 0 };
                p = C.transitionTransferState(p, "OTP_VERIFIED");       check("9-1 OTP_VERIFIED 직후 otpVerified=true", p.otpVerified, true);
                p = C.transitionTransferState(p, "QUEUE");
                p = C.transitionTransferState(p, "SEND");
                p = C.transitionTransferState(p, "SUCCEED");            check("9-2 hrd_success 에서도 otpVerified 유지", p.otpVerified, true);
                p = C.transitionTransferState(p, "MARK_HRD_UNRECEIVED"); check("9-3 공단 미수신으로 바뀌어도 otpVerified 유지", p.otpVerified, true);
                check("9-3 상태는 hrd_unreceived", p.code, "hrd_unreceived");
                p = C.transitionTransferState(p, "MOBILE_VERIFIED");    check("9-4 모바일 보완 인증을 거쳐도 otpVerified 유지", p.otpVerified, true);
                check("9-4 상태는 mobile_verified", p.code, "mobile_verified");

                /* 10) 반대로 OTP 를 거치지 않고 모바일 단독으로 인증한 경로는 otpVerified 가
                   계속 false 여야 한다 — 두 경로가 같은 코드(mobile_verified)를 공유해도
                   인증 출처는 절대 뒤섞이면 안 된다. */
                let q = { code: "idle", retryCount: 0 };
                q = C.transitionTransferState(q, "OTP_UNRECEIVED");     check("10-1 OTP 미수신 경로는 otpVerified=false", q.otpVerified, false);
                q = C.transitionTransferState(q, "MOBILE_VERIFIED");    check("10-2 모바일 단독 인증은 otpVerified 를 true 로 만들지 않는다", q.otpVerified, false);

                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("attendance-verification-common.js")));
        NodeRun r = runNode(script);
        assertThat(r.code()).as("transitionTransferState() 전이가 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[Task1-9] advanceStayClock() 이 체류시간을 음수 없이 누적하고 verifiedSeconds<=connectedSeconds 를 항상 지킨다")
    void advanceStayClock_실제실행() {
        String script = """
                const C = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                }
                let clock = { connectedSeconds: 0, verifiedSeconds: 0 };
                for (let i = 0; i < 5; i++) clock = C.advanceStayClock(clock, false);
                for (let i = 0; i < 3; i++) clock = C.advanceStayClock(clock, true);
                check("connectedSeconds", clock.connectedSeconds, 8);
                check("verifiedSeconds", clock.verifiedSeconds, 3);
                check("verified<=connected", clock.verifiedSeconds <= clock.connectedSeconds, true);

                /* 비정상 입력(음수·NaN)도 음수로 새지 않는다 */
                const bad1 = C.advanceStayClock({ connectedSeconds: -5, verifiedSeconds: -1 }, true);
                check("음수 입력도 0 이상에서 시작", bad1.connectedSeconds >= 0 && bad1.verifiedSeconds >= 0, true);
                check("음수 입력도 불변식 유지", bad1.verifiedSeconds <= bad1.connectedSeconds, true);

                check("formatDuration", C.formatDuration(3725), "01:02:05");
                check("formatDuration 음수는 0", C.formatDuration(-5), "00:00:00");

                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("attendance-verification-common.js")));
        NodeRun r = runNode(script);
        assertThat(r.code()).as("advanceStayClock() 동작이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[Task2] 데모 데이터에 훈련생 8명 이상과 5가지 전송 버킷(OTP완료/대기/성공/실패/보완필요)이 모두 존재한다")
    void demoData_최소인원과버킷분포_실제로존재() {
        String script = """
                const D = require(%s);
                let bad = 0;
                if (!Array.isArray(D.learners) || D.learners.length < 8) {
                  console.log("FAIL 훈련생은 최소 8명이어야 한다: " + (D.learners ? D.learners.length : "없음")); bad++;
                }
                const codes = new Set(D.learners.map(function (p) { return p.transferCode; }));
                function bucketOf(code) {
                  if (code === "otp_verified") return "otp_verified";
                  if (code === "hrd_queued" || code === "hrd_sending") return "pending";
                  if (code === "hrd_success" || code === "fallback_sent") return "success";
                  if (code === "hrd_failed" || code === "retrying") return "failed";
                  return "fallback_needed";
                }
                const buckets = new Set(D.learners.map(function (p) { return bucketOf(p.transferCode); }));
                ["otp_verified", "pending", "success", "failed", "fallback_needed"].forEach(function (b) {
                  if (!buckets.has(b)) { console.log("FAIL 데모 학습자 중 " + b + " 버킷이 없다(코드 목록: " + Array.from(codes).join(",") + ")"); bad++; }
                });
                if (!D.otpPolicy || !D.otpPolicy.demoSuccessCode || D.otpPolicy.demoSuccessCode.length !== 6) {
                  console.log("FAIL otpPolicy.demoSuccessCode 는 6자리여야 한다"); bad++;
                }
                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("attendance-verification-demo-data.js")));
        NodeRun r = runNode(script);
        assertThat(r.code()).as("데모 데이터 분포가 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    // ================================================================
    //  1차 보완 대응 — attendance-trainee.js / attendance-admin.js 를
    //  Node 로 실제 로드해 이벤트 오케스트레이션을 실행한다(문자열 검사 아님).
    // ================================================================

    /** DOM 없이 Node 에서 attendance-trainee.js 를 실제로 실행하기 위한 최소 스텁. */
    private String stubDomAndTraineeHarness() {
        return """
                function stubEl() {
                  var attrs = {};
                  var html = "";
                  var el = {
                    dataset: {}, style: {}, hidden: false, disabled: false, value: "", checked: false,
                    tabIndex: 0, open: false, _handlers: {}, _children: [], options: [],
                    setAttribute: function (k, v) { attrs[k] = String(v); },
                    removeAttribute: function (k) { delete attrs[k]; },
                    getAttribute: function (k) { return Object.prototype.hasOwnProperty.call(attrs, k) ? attrs[k] : null; },
                    addEventListener: function (evt, fn) { el._handlers[evt] = function (e) { return fn.call(el, e); }; },
                    removeEventListener: function () {},
                    appendChild: function (child) { el._children.push(child); return child; },
                    querySelector: function () { return stubEl(); },
                    querySelectorAll: function () { return []; },
                    focus: function () {},
                    showModal: function () { el.open = true; },
                    close: function () { el.open = false; },
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
                  createElement: function () { return stubEl(); },
                  createTextNode: function (t) { return { text: t }; },
                  querySelector: function () { return stubEl(); },
                  querySelectorAll: function () { return []; },
                  addEventListener: function () {}
                };
                global.window = {
                  isSecureContext: true,
                  setInterval: setInterval,
                  clearInterval: clearInterval,
                  setTimeout: setTimeout,
                  clearTimeout: clearTimeout,
                  addEventListener: function () {}
                };
                global.window.AttendanceVerificationCommon = require(%s);
                global.window.AttendanceVerificationDemoData = require(%s);

                function flush(ms) { return new Promise(function (resolve) { setTimeout(resolve, ms || 0); }); }
                /** eventList 의 <li> 자식 중 지정한 부분 문자열을 포함하는 것의 개수 —
                    addEvent() 가 createElement/appendChild(실제 객체)로 렌더링하므로
                    innerHTML 문자열이 아니라 _children 을 직접 센다. */
                function countEventsContaining(needle) {
                  return els["eventList"]._children.filter(function (li) {
                    return li._children.some(function (part) { return (part.text || part.textContent || "").indexOf(needle) > -1; });
                  }).length;
                }

                require(%s);   /* attendance-trainee.js 를 실제로 로드한다 — 최초 렌더까지 끝난다 */
                """.formatted(
                        jsString(STATIC_V2_ASSETS.resolve("attendance-verification-common.js")),
                        jsString(STATIC_V2_ASSETS.resolve("attendance-verification-demo-data.js")),
                        jsString(STATIC_V2_ASSETS.resolve("attendance-trainee.js")));
    }

    /** DOM 없이 Node 에서 attendance-admin.js 를 실제로 실행하기 위한 최소 스텁.
        행 클릭은 실제 브라우저에서 이벤트 위임(closest())으로 동작하는데, 이 가벼운
        스텁은 innerHTML 문자열을 실제 자식 엘리먼트 트리로 만들지 않는다 — 그래서
        admin.js 가 노출하는 최소 테스트 훅(window.__attendanceAdminTest.selectLearner)
        으로 "행을 고른다"는 사용자 동작을 실제 selectRow() 그대로 실행한다(별도
        재구현이 아니라 클릭 핸들러가 쓰는 바로 그 함수를 호출한다). */
    private String stubDomAndAdminHarness() {
        return """
                function stubEl() {
                  var attrs = {};
                  var html = "";
                  var el = {
                    dataset: {}, style: {}, hidden: false, disabled: false, value: "", checked: false,
                    tabIndex: 0, open: false, _handlers: {}, _children: [], options: [],
                    setAttribute: function (k, v) { attrs[k] = String(v); },
                    removeAttribute: function (k) { delete attrs[k]; },
                    getAttribute: function (k) { return Object.prototype.hasOwnProperty.call(attrs, k) ? attrs[k] : null; },
                    addEventListener: function (evt, fn) { el._handlers[evt] = function (e) { return fn.call(el, e); }; },
                    removeEventListener: function () {},
                    appendChild: function (child) { el._children.push(child); return child; },
                    querySelector: function () { return stubEl(); },
                    querySelectorAll: function () { return []; },
                    focus: function () { global.document.activeElement = el; },
                    contains: function () { return false; },
                    showModal: function () { el.open = true; },
                    close: function () { el.open = false; },
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
                  createElement: function () { return stubEl(); },
                  createTextNode: function (t) { return { text: t }; },
                  querySelector: function () { return stubEl(); },
                  querySelectorAll: function () { return []; },
                  addEventListener: function () {},
                  body: stubEl(),
                  activeElement: null
                };
                global.window = {
                  isSecureContext: true,
                  setInterval: setInterval,
                  clearInterval: clearInterval,
                  setTimeout: setTimeout,
                  clearTimeout: clearTimeout,
                  addEventListener: function () {}
                };
                global.window.AttendanceVerificationCommon = require(%s);
                global.window.AttendanceVerificationDemoData = require(%s);

                function flush(ms) { return new Promise(function (resolve) { setTimeout(resolve, ms || 0); }); }
                function selectLearner(id) { window.__attendanceAdminTest.selectLearner(id); }
                /* peopleBody/timelineBody 는 admin.js 가 innerHTML=문자열 로 통째로 그린다
                   (trainee.js 의 eventList 처럼 createElement/appendChild 로 실제 자식
                   엘리먼트를 만들지 않는다) — 그래서 이 스텁은 문자열 자체를 대상으로 검사한다. */
                function tableHtml() { return els["peopleBody"].innerHTML; }
                function timelineHtml() { return els["timelineBody"].innerHTML; }
                function countTableRows() {
                  var m = tableHtml().match(/data-id="/g);
                  return m ? m.length : 0;
                }

                require(%s);   /* attendance-admin.js 를 실제로 로드한다 — 최초 렌더까지 끝난다 */
                """.formatted(
                        jsString(STATIC_V2_ASSETS.resolve("attendance-verification-common.js")),
                        jsString(STATIC_V2_ASSETS.resolve("attendance-verification-demo-data.js")),
                        jsString(STATIC_V2_ASSETS.resolve("attendance-admin.js")));
    }

    @Test
    @DisplayName("[1차보완 P0-A 실행형] OTP 미수신 → 재발급 → 올바른 OTP 입력 시 실제로 인증이 완료되고 모바일 대체 UI가 남지 않는다")
    void traineeJs_미수신후_재발급_OTP성공_흐름_실제실행() {
        String script = stubDomAndTraineeHarness() + """

                (function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  els["otpIssueBtn"]._handlers.click();
                  els["otpUnreceivedBtn"]._handlers.click();
                  check("미수신 보고 후 배지", els["otpStateBadge"].textContent, "미수신");
                  check("미수신 보고 후 모바일 카드 노출", els["mobileCard"].hidden, false);

                  els["otpReissueBtn"]._handlers.click();
                  check("재발급 직후 배지는 입력대기로 돌아온다", els["otpStateBadge"].textContent, "입력대기");

                  var input = els["otpInput"];
                  input.value = "123456";
                  input._handlers.input();
                  els["otpVerifyBtn"]._handlers.click();

                  check("올바른 코드 입력 후 실제로 인증성공", els["otpStateBadge"].textContent, "인증성공");
                  check("인증성공 후 모바일 대체 UI 가 남지 않는다", els["mobileCard"].hidden, true);
                  check("전이 실패가 조용히 성공으로 둔갑하지 않았다 — 성공 이벤트가 정확히 1건", countEventsContaining("OTP 인증에 성공"), 1);

                  process.exit(bad ? 1 : 0);
                })();
                """;
        NodeRun r = runNode(script);
        assertThat(r.code()).as("OTP 미수신→재발급→성공 흐름이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P0-B 실행형] OTP 없이 모바일 인증 성공만으로도 인정 체류시간(확인시간)이 실제로 증가한다")
    void traineeJs_모바일인증만으로도_인정체류시간증가_실제실행() {
        String script = stubDomAndTraineeHarness() + """

                (async function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  await flush(1100);   /* 인증 전 최소 한 틱 — 접속시간만 늘고 확인시간은 그대로여야 한다 */
                  check("인증 전 확인된 체류시간은 0", els["verifiedTime"].textContent, "00:00:00");

                  els["otpIssueBtn"]._handlers.click();
                  els["otpUnreceivedBtn"]._handlers.click();
                  els["mobileStartBtn"]._handlers.click();
                  els["mobileDialogSuccessBtn"]._handlers.click();

                  check("모바일 인증 성공 직후 OTP 자체는 성공이 아니다(별도 경로)", els["otpStateBadge"].textContent, "미수신");

                  await flush(1100);   /* 모바일 인증 성공 후 최소 한 틱 */
                  check("모바일 인증만으로도 확인된 체류시간이 증가한다", els["verifiedTime"].textContent !== "00:00:00", true);

                  process.exit(bad ? 1 : 0);
                })().catch(function (e) { console.log("FAIL 예외: " + (e && e.stack || e)); process.exit(1); });
                """;
        NodeRun r = runNode(script);
        assertThat(r.code()).as("모바일 인증만의 체류시간 인정이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P1-H 실행형] 모바일 인증 성공 대화상자를 반복 실행해도 성공 이벤트는 1건만 남고 시작 버튼은 다시 쓸 수 없다")
    void traineeJs_모바일인증_반복클릭_방지_실제실행() {
        String script = stubDomAndTraineeHarness() + """

                (function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  els["otpIssueBtn"]._handlers.click();
                  els["otpUnreceivedBtn"]._handlers.click();
                  els["mobileStartBtn"]._handlers.click();
                  els["mobileDialogSuccessBtn"]._handlers.click();
                  check("1차 성공 이벤트 1건", countEventsContaining("모바일 본인인증에 성공"), 1);
                  check("성공 후 시작 버튼 비활성화", els["mobileStartBtn"].disabled, true);

                  /* 실제 브라우저라면 disabled 버튼은 클릭 이벤트 자체가 안 나가지만, 이 테스트는
                     핸들러를 직접 호출해 "그래도 강제로 다시 실행되면?"까지 방어됨을 확인한다. */
                  els["mobileDialogSuccessBtn"]._handlers.click();
                  check("반복 실행해도 성공 이벤트는 여전히 1건", countEventsContaining("모바일 본인인증에 성공"), 1);

                  process.exit(bad ? 1 : 0);
                })();
                """;
        NodeRun r = runNode(script);
        assertThat(r.code()).as("모바일 인증 반복 클릭 방지가 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P0-C 실행형] 관리자 '공단 미수신' 시나리오는 OTP 인증 경로를 유지하며 'OTP 미수신'으로 오표시하지 않는다")
    void adminJs_공단미수신_인증경로유지_실제실행() {
        String script = stubDomAndAdminHarness() + """

                (function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  els["scenarioSelect"].value = "hrd-unreceived";
                  els["scenarioSelect"]._handlers.change();

                  /* l03/l04 는 baseline 이 hrd_success 다 — 이 시나리오에서 공단 미수신으로 바뀐다 */
                  selectLearner("l03");
                  var authText = els["selMeta"].textContent;
                  check("OTP 인증 경로 표시가 유지된다(OTP 인증완료)", authText.indexOf("OTP 인증완료") > -1, true);
                  check("'OTP 미수신'으로 오표시되지 않는다", authText.indexOf("OTP 미수신") === -1, true);
                  check("전송상태는 공단 미수신 또는 모바일 보완 필요로 표시된다",
                      authText.indexOf("공단 미수신") > -1 || authText.indexOf("모바일 보완") > -1, true);

                  var table = tableHtml();
                  check("표에도 '공단 미수신' 문구가 실제로 나온다", table.indexOf("공단 미수신") > -1, true);

                  process.exit(bad ? 1 : 0);
                })();
                """;
        NodeRun r = runNode(script);
        assertThat(r.code()).as("공단 미수신 시나리오 표시가 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P0-D 실행형] 모바일 보완 전송 실패 후 재시도로 성공하거나 다시 실패할 수 있다")
    void adminJs_보완전송실패_재시도_실제실행() {
        String script = stubDomAndAdminHarness() + """

                (function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  /* l08 은 baseline 이 mobile_verified 다 — 시나리오로 먼저 보완 전송을 실패시킨다 */
                  els["scenarioSelect"].value = "fallback-failure";
                  els["scenarioSelect"]._handlers.change();
                  selectLearner("l08");
                  els["fallbackBtn"]._handlers.click();
                  check("보완 전송(실패 시나리오) 확인 dialog 열림", els["fallbackDialog"].open, true);
                  els["fallbackConfirmBtn"]._handlers.click();
                  check("보완 전송 실패 후 재시도 버튼이 활성화된다", els["fallbackBtn"].disabled, false);

                  var retryCountAfterFail = parseInt(els["selDetail"].textContent.match(/재시도 (\\d+)회/)[1], 10);

                  /* 이번엔 정상 시나리오로 바꿔 재시도가 성공하게 한다 */
                  els["scenarioSelect"].value = "normal";
                  els["scenarioSelect"]._handlers.change();
                  selectLearner("l08");
                  els["fallbackBtn"]._handlers.click();
                  els["fallbackConfirmBtn"]._handlers.click();
                  check("재시도 후 보완 전송 성공(전송성공 버킷)", els["selMeta"].textContent.indexOf("전송성공") > -1, true);

                  var retryCountAfterRetry = parseInt(els["selDetail"].textContent.match(/재시도 (\\d+)회/)[1], 10);
                  check("실제 재시도에서만 재시도 횟수가 증가한다", retryCountAfterRetry, retryCountAfterFail + 1);

                  process.exit(bad ? 1 : 0);
                })();
                """;
        NodeRun r = runNode(script);
        assertThat(r.code()).as("보완 전송 실패→재시도 흐름이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P1-E 실행형] 관리자 조치 후 화면의 최근 처리 시각과 새 타임라인 이벤트 시각이 동일하다")
    void adminJs_처리시각과_타임라인시각_동기화_실제실행() {
        String script = stubDomAndAdminHarness() + """

                (function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  selectLearner("l01");   /* baseline otp_verified — 전송 시작이 가능하다 */
                  els["sendBtn"]._handlers.click();

                  var detailText = els["selDetail"].textContent;
                  var m = detailText.match(/마지막 처리 (\\S+)/);
                  if (!m) { console.log("FAIL selDetail 에서 마지막 처리 시각을 찾지 못했다: " + detailText); bad++; }
                  else {
                    var detailTime = m[1];
                    /* timelineBody 도 innerHTML=문자열 로 그려진다 — 첫 번째 <tr> 의 첫 번째
                       <td class="nowrap mono"> 안 텍스트(시각)를 문자열에서 그대로 뽑는다. */
                    var rowMatch = timelineHtml().match(/<td class="nowrap mono">([^<]*)<\\/td>/);
                    if (!rowMatch) { console.log("FAIL 타임라인에서 시각 셀을 찾지 못했다: " + timelineHtml()); bad++; }
                    else {
                      check("상세 패널의 마지막 처리 시각과 타임라인 최신 이벤트 시각이 같다", rowMatch[1], detailTime);
                    }
                  }

                  process.exit(bad ? 1 : 0);
                })();
                """;
        NodeRun r = runNode(script);
        assertThat(r.code()).as("처리 시각 동기화가 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P1-F 실행형] 'OTP 인증완료' KPI 가 인증상태 필터(OTP 인증완료)와 같은 인원을 센다 — 전송 진행 인원도 포함")
    void adminJs_OTP_KPI와_필터의미일치_실제실행() {
        String script = stubDomAndAdminHarness() + """

                (function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  var kpiOtp = parseInt(els["kpiOtpVerified"].textContent, 10);

                  els["authFilter"].value = "otp";
                  els["authFilter"]._handlers.change();
                  var filteredRows = countTableRows();

                  check("KPI 의 OTP 인증완료 수가 인증상태=OTP 필터 결과 건수와 같다", kpiOtp, filteredRows);
                  check("HRD 로 이미 넘어간 인원도 KPI 에 포함되어 0이 아니다", kpiOtp > 1, true);

                  process.exit(bad ? 1 : 0);
                })();
                """;
        NodeRun r = runNode(script);
        assertThat(r.code()).as("OTP KPI 와 필터 의미 일치가 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[1차보완 P1-G 실행형] '일부 전송 실패' 시나리오는 아직 전송하지 않은 otp_verified 인원을 실패로 바꾸지 않는다")
    void adminJs_부분실패시나리오가_미전송인원을_건드리지않음_실제실행() {
        String script = stubDomAndAdminHarness() + """

                (function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  /* l01 은 baseline 이 otp_verified(아직 전송 시작 전)다 */
                  els["scenarioSelect"].value = "partial-failure";
                  els["scenarioSelect"]._handlers.change();
                  selectLearner("l01");
                  check("아직 전송하지 않은 otp_verified 는 실패로 바뀌지 않는다",
                      els["selMeta"].textContent.indexOf("전송실패") === -1, true);
                  check("여전히 전송 시작이 가능한 상태다(전송 시작 버튼 활성화)", els["sendBtn"].disabled, false);

                  /* l02 는 baseline 이 hrd_queued(전송 대기 중)다 — 이 학습자는 실패로 바뀌어야 한다 */
                  selectLearner("l02");
                  check("전송 대기 중이던 인원은 부분 실패로 전환된다",
                      els["selMeta"].textContent.indexOf("전송실패") > -1, true);

                  process.exit(bad ? 1 : 0);
                })();
                """;
        NodeRun r = runNode(script);
        assertThat(r.code()).as("부분 실패 시나리오 범위가 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[2차보완 결함1 실행형] 공단 미수신 → 모바일 보완 인증 완료 → 보완 전송까지 실제 클릭으로 진행되며 OTP 인증 이력이 보존된다")
    void adminJs_공단미수신_모바일보완흐름_완결_실제실행() {
        String script = stubDomAndAdminHarness() + """

                (function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  els["scenarioSelect"].value = "hrd-unreceived";
                  els["scenarioSelect"]._handlers.change();

                  /* l03 은 baseline 이 hrd_success(OTP 인증 완료 후 전송 성공)다 — 이 시나리오에서 공단 미수신으로 바뀐다 */
                  selectLearner("l03");
                  check("공단 미수신 상태에서도 인증상태는 OTP 인증완료를 유지한다", els["selMeta"].textContent.indexOf("OTP 인증완료") > -1, true);
                  check("아직 모바일 보완완료는 표시하지 않는다", els["selMeta"].textContent.indexOf("모바일 보완완료") > -1, false);
                  check("모바일 보완 인증 완료 버튼이 활성화된다", els["fallbackBtn"].disabled, false);
                  check("버튼 문구는 인증 완료 처리다", els["fallbackBtn"].textContent, "모바일 보완 인증 완료 처리(데모)");

                  els["fallbackBtn"]._handlers.click();
                  check("모달이 열린다", els["fallbackDialog"].open, true);
                  check("확인 버튼 문구는 인증 완료 반영이다", els["fallbackConfirmBtn"].textContent, "인증 완료 반영(데모)");
                  els["fallbackConfirmBtn"]._handlers.click();

                  check("모달 확인 후 mobile_verified 로 이동해도 OTP 인증 이력이 함께 유지된다",
                      els["selMeta"].textContent.indexOf("OTP 인증완료 · 모바일 보완완료") > -1, true);

                  check("이어서 모바일 보완 전송 버튼이 활성화된다", els["fallbackBtn"].disabled, false);
                  check("버튼 문구는 최초 보완 전송이다", els["fallbackBtn"].textContent, "모바일 보완 전송(데모)");

                  els["fallbackBtn"]._handlers.click();
                  els["fallbackConfirmBtn"]._handlers.click();
                  check("보완 전송까지 성공으로 진행할 수 있다", els["selMeta"].textContent.indexOf("전송성공") > -1, true);
                  check("전송 성공 후에도 OTP 인증완료·모바일 보완완료 이력이 함께 남는다",
                      els["selMeta"].textContent.indexOf("OTP 인증완료 · 모바일 보완완료") > -1, true);

                  /* KPI 와 인증 필터가 보존된 인증 사실과 일치한다 — 한 사람이 OTP·모바일
                     양쪽 필터에 동시에 걸릴 수 있음을 실제로 확인한다. */
                  var kpiOtp = parseInt(els["kpiOtpVerified"].textContent, 10);
                  check("KPI 의 OTP 인증완료 수가 0 보다 크다", kpiOtp > 0, true);

                  els["authFilter"].value = "otp";
                  els["authFilter"]._handlers.change();
                  check("OTP 인증완료 필터에 l03 이 남아 있다(공단 미수신을 거쳐도 OTP 이력은 유지)", tableHtml().indexOf('data-id="l03"') > -1, true);

                  els["authFilter"].value = "mobile";
                  els["authFilter"]._handlers.change();
                  check("모바일 인증완료 필터에도 l03 이 나온다(OTP·모바일 양쪽 모두 인정)", tableHtml().indexOf('data-id="l03"') > -1, true);

                  process.exit(bad ? 1 : 0);
                })();
                """;
        NodeRun r = runNode(script);
        assertThat(r.code()).as("공단 미수신 → 모바일 보완 흐름이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[2차보완 결함2 실행형] 공단미수신·최초전송·재전송 3단계의 버튼·모달 제목·본문·확인버튼 문구가 서로 다르다")
    void adminJs_보완전송_상태별_모달문구_실제실행() {
        String script = stubDomAndAdminHarness() + """

                (function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }
                  function checkContains(name, actual, needle) {
                    if (String(actual).indexOf(needle) === -1) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " 에 " + JSON.stringify(needle) + " 포함 기대"); bad++; }
                  }

                  /* 1단계: hrd_unreceived (l03) */
                  els["scenarioSelect"].value = "hrd-unreceived";
                  els["scenarioSelect"]._handlers.change();
                  selectLearner("l03");
                  var stage1 = {
                    btn: els["fallbackBtn"].textContent,
                    title: els["fallbackDialogTitle"].textContent,
                    body: els["fallbackDialogBody"].textContent,
                    confirm: els["fallbackConfirmBtn"].textContent
                  };
                  check("1단계 버튼 문구", stage1.btn, "모바일 보완 인증 완료 처리(데모)");
                  check("1단계 확인버튼 문구", stage1.confirm, "인증 완료 반영(데모)");
                  checkContains("1단계 본문에 데모임이 명시된다", stage1.body, "데모");

                  /* 2단계: mobile_verified — 1단계 확인을 실제로 눌러 도달한다 */
                  els["fallbackBtn"]._handlers.click();
                  els["fallbackConfirmBtn"]._handlers.click();
                  var stage2 = {
                    btn: els["fallbackBtn"].textContent,
                    title: els["fallbackDialogTitle"].textContent,
                    body: els["fallbackDialogBody"].textContent,
                    confirm: els["fallbackConfirmBtn"].textContent
                  };
                  check("2단계 버튼 문구", stage2.btn, "모바일 보완 전송(데모)");
                  check("2단계 확인버튼 문구", stage2.confirm, "보완 전송(데모)");
                  checkContains("2단계 본문에 데모임이 명시된다", stage2.body, "데모");

                  /* 3단계: fallback_failed — l08 을 fallback-failure 시나리오로 실패시켜 도달한다 */
                  els["scenarioSelect"].value = "fallback-failure";
                  els["scenarioSelect"]._handlers.change();
                  selectLearner("l08");
                  els["fallbackBtn"]._handlers.click();
                  els["fallbackConfirmBtn"]._handlers.click();
                  check("l08 이 보완 전송 실패 상태로 전환된다", els["selMeta"].textContent.indexOf("모바일 보완") > -1, true);
                  var stage3 = {
                    btn: els["fallbackBtn"].textContent,
                    title: els["fallbackDialogTitle"].textContent,
                    body: els["fallbackDialogBody"].textContent,
                    confirm: els["fallbackConfirmBtn"].textContent
                  };
                  check("3단계 버튼 문구", stage3.btn, "모바일 보완 재전송(데모)");
                  check("3단계 확인버튼 문구", stage3.confirm, "보완 재전송(데모)");
                  checkContains("3단계 본문에 데모임이 명시된다", stage3.body, "데모");

                  check("1·2단계 버튼 문구가 다르다", stage1.btn !== stage2.btn, true);
                  check("2·3단계 버튼 문구가 다르다", stage2.btn !== stage3.btn, true);
                  check("1·3단계 버튼 문구가 다르다", stage1.btn !== stage3.btn, true);
                  check("1·2단계 확인버튼 문구가 다르다", stage1.confirm !== stage2.confirm, true);
                  check("2·3단계 확인버튼 문구가 다르다", stage2.confirm !== stage3.confirm, true);
                  check("1·3단계 확인버튼 문구가 다르다", stage1.confirm !== stage3.confirm, true);
                  check("1·2단계 본문 문구가 다르다", stage1.body !== stage2.body, true);
                  check("2·3단계 본문 문구가 다르다", stage2.body !== stage3.body, true);

                  process.exit(bad ? 1 : 0);
                })();
                """;
        NodeRun r = runNode(script);
        assertThat(r.code()).as("보완 전송 상태별 모달 문구가 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[2차보완 결함3 실행형] 모바일 보완 재전송이 다시 실패해도 재시도 횟수만 늘고 다시 재전송할 수 있다")
    void adminJs_보완전송_재시도후_재실패_회귀_실제실행() {
        String script = stubDomAndAdminHarness() + """

                (function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }
                  function retryCountOf() { return parseInt(els["selDetail"].textContent.match(/재시도 (\\d+)회/)[1], 10); }

                  /* 1) mobile_verified (l08 baseline) */
                  els["scenarioSelect"].value = "fallback-failure";
                  els["scenarioSelect"]._handlers.change();
                  selectLearner("l08");
                  check("시작 상태는 모바일 보완 필요다", els["selMeta"].textContent.indexOf("모바일 보완") > -1, true);

                  /* 2) 최초 보완 전송 실패 → fallback_failed, retryCount 는 증가하지 않는다 */
                  els["fallbackBtn"]._handlers.click();
                  els["fallbackConfirmBtn"]._handlers.click();
                  check("최초 전송 실패로 전환된다(재전송 버튼으로 바뀜)", els["fallbackBtn"].textContent, "모바일 보완 재전송(데모)");
                  check("최초 전송 실패는 재시도가 아니므로 재시도 횟수가 늘지 않는다", retryCountOf(), 0);

                  /* 3~4) 재전송 실행 → 다시 실패 → fallback_failed */
                  els["fallbackBtn"]._handlers.click();
                  els["fallbackConfirmBtn"]._handlers.click();
                  check("재전송도 다시 실패해도 여전히 보완 필요 상태다", els["selMeta"].textContent.indexOf("모바일 보완") > -1, true);

                  /* 5) retryCount 가 정확히 1 증가한다 */
                  check("실제 재시도 1회만큼만 재시도 횟수가 늘어난다", retryCountOf(), 1);

                  /* 6) 버튼은 다시 재전송 가능 상태다 */
                  check("재실패 후에도 재전송 버튼이 다시 활성화된다", els["fallbackBtn"].disabled, false);
                  check("버튼 문구는 여전히 재전송이다", els["fallbackBtn"].textContent, "모바일 보완 재전송(데모)");

                  /* 7) 모달과 타임라인 문구가 '재전송도 다시 실패' 의미로 표시된다 */
                  check("타임라인에 재전송도 다시 실패했다는 문구가 남는다", timelineHtml().indexOf("재전송도 다시 실패") > -1, true);

                  /* 8) 다시 재시도하면 retryCount 가 한 번 더 증가하는 구조임을 FSM 수준에서도 확인한다 */
                  var afterSecondRetry = window.AttendanceVerificationCommon.transitionTransferState(
                      { code: "fallback_failed", retryCount: retryCountOf(), otpVerified: false }, "RETRY_FALLBACK");
                  check("FSM 수준에서도 재시도마다 횟수가 1씩 증가한다", afterSecondRetry.retryCount, retryCountOf() + 1);

                  process.exit(bad ? 1 : 0);
                })();
                """;
        NodeRun r = runNode(script);
        assertThat(r.code()).as("보완 재전송 후 재실패 회귀가 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[2차보완 결함4 실행형] 훈련생 화면의 보완 전송 대기/완료 문구가 실제 렌더 함수로 서로 다르게 나온다")
    void traineeJs_보완대기와_완료_문구가_다르다_실제실행() {
        String script = stubDomAndTraineeHarness() + """

                (function () {
                  var bad = 0;
                  function check(name, actual, expected) {
                    if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                  }

                  /* 훈련생 화면은 관리자 화면과 메모리를 공유하지 않아, 실제 클릭만으로는
                     fallback_sent(관리자가 보완 전송을 실행한 결과)에 도달할 수 없다 —
                     이 한계는 결과 보고서에 그대로 기록한다. 화면이 실제로 쓰는 렌더
                     함수(renderOtp → renderMobileCard)를 최소 테스트 훅으로 그대로
                     실행해 문구 차이만 검증한다 — 테스트 전용 별도 문구 구현이 아니다. */
                  window.__attendanceTraineeTest.setTransferCodeForTest("fallback_queued");
                  var queuedMsg = els["mobileResultMsg"].textContent;
                  check("대기 문구에 '보완 전송 대기'가 있다", queuedMsg.indexOf("보완 전송 대기") > -1, true);

                  window.__attendanceTraineeTest.setTransferCodeForTest("fallback_sent");
                  var sentMsg = els["mobileResultMsg"].textContent;
                  check("완료 문구에 '보완 전송이 완료' 의미가 있다", sentMsg.indexOf("보완 전송이 완료") > -1, true);

                  check("대기와 완료 문구가 서로 다르다", queuedMsg !== sentMsg, true);

                  process.exit(bad ? 1 : 0);
                })();
                """;
        NodeRun r = runNode(script);
        assertThat(r.code()).as("보완 대기/완료 문구 구분이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[2차보완 결함5 실행형] '모바일 보완 전송 실패' 시나리오 설명이 실제 동작(선택만으로는 무변화, 실제 전송 시에만 실패)과 일치한다")
    void demoData_보완실패시나리오_설명이_실제동작과_일치_실제실행() throws Exception {
        String demoDataSrc = body("/v2/assets/attendance-verification-demo-data.js");
        assertThat(demoDataSrc).as("transferCode 주석 목록에 신설 상태 hrd_unreceived 가 포함되어야 한다")
                .contains("hrd_unreceived");

        String script = """
                const D = require(%s);
                let bad = 0;
                function check(name, actual, expected) {
                  if (actual !== expected) { console.log("FAIL " + name + " → " + JSON.stringify(actual) + " (기대 " + JSON.stringify(expected) + ")"); bad++; }
                }

                const scenario = D.adminScenarios.filter(function (s) { return s.key === "fallback-failure"; })[0];
                if (!scenario) { console.log("FAIL fallback-failure 시나리오를 찾지 못했다"); bad++; }
                else {
                  check("설명이 더 이상 '대기 중이던 인원의 보완 전송이 실패로 바뀝니다'라는 즉시-적용 문구를 담지 않는다",
                      scenario.desc.indexOf("대기 중이던 인원의 보완 전송이 실패로 바뀝니다") === -1, true);
                  check("설명이 '실행하면 실패' 의미를 담는다",
                      scenario.desc.indexOf("실행하면") > -1 && scenario.desc.indexOf("실패") > -1, true);
                }

                /* baseline 학습자 중 fallback_queued 로 시작하는 사람은 없다 — 즉시 override
                   분기 자체가 원천적으로 도달 불가능한 죽은 코드였다는 전제를 데이터
                   차원에서도 재확인한다(2차보완 결함7). */
                const hasBaselineFallbackQueued = D.learners.some(function (l) { return l.transferCode === "fallback_queued"; });
                check("baseline 에 fallback_queued 학습자가 없다(즉시 override 분기는 절대 실행되지 않았다)", hasBaselineFallbackQueued, false);

                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(STATIC_V2_ASSETS.resolve("attendance-verification-demo-data.js")));
        NodeRun r = runNode(script);
        assertThat(r.code()).as("보완 실패 시나리오 설명 정합성이 기대와 다릅니다:%n%s", r.out()).isZero();
    }
}
