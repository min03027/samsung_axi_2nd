package com.ssa.lms.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

/**
 * LXP 시험 프론트엔드 <b>템플릿 계약</b> 테스트.
 *
 * <p><b>이 테스트가 보장하는 것과 보장하지 않는 것</b><br>
 * 보장: 새 정적 파일이 실제로 존재하고, 템플릿이 그것을 <b>한 번씩만</b> 로드하며,
 * 접근성 속성·필수 컨트롤·한계 고지 문구가 마크업에 있고, 외부 CDN·무동작 링크·
 * 개인정보 형태 데이터가 없다는 것.</p>
 *
 * <p><b>보장하지 않음</b>: 드래그가 실제로 동작하는지, 카메라가 열리는지, 화면 공유가
 * 되는지. 문자열이 있다는 사실만으로 기능 전체가 통과한다고 주장하지 않는다.
 * 이 테스트는 <b>누락 방지</b>용이며, 실제 동작은 브라우저 QA 로 확인한다.</p>
 *
 * <p>스프링 컨텍스트를 띄우지 않는다 — 파일 내용만 읽는다. 빠르고, 다른 테스트와
 * 상태를 공유하지 않는다.</p>
 */
class LxpExamFrontendContractTest {

    private static final Path RES = Paths.get("src/main/resources");
    private static final Path TPL = RES.resolve("templates");
    private static final Path STATIC = RES.resolve("static");

    /* ===================== 헬퍼 ===================== */

    private String read(Path p) {
        assertThat(Files.exists(p)).as("파일이 없습니다: %s", p).isTrue();
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("파일을 읽지 못했습니다: " + p, e);
        }
    }

    private String doTest()      { return read(TPL.resolve("trainee/do-test.html")); }
    private String precheck()    { return read(TPL.resolve("trainee/exam-precheck.html")); }
    private String monitoring()  { return read(TPL.resolve("admin/admin-04-evaluation/admin-evaluation-monitoring-live.html")); }
    private String identityList(){ return read(TPL.resolve("admin/admin-04-evaluation/admin-evaluation-identity.html")); }
    private String identityDetail(){ return read(TPL.resolve("admin/admin-04-evaluation/admin-evaluation-identity-detail.html")); }
    private String graderSettings(){ return read(TPL.resolve("admin/admin-04-evaluation/admin-evaluation-grader-settings.html")); }

    /** 문자열이 정확히 n 번 나오는지. 중복 로드를 잡는 데 쓴다. */
    private int countOf(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    /* ===================== 1. 새 정적 파일 존재 ===================== */

    @Test
    @DisplayName("[계약] 새로 만든 정적 파일과 참조 자산이 모두 존재한다")
    void 신규_정적파일_존재() {
        List<String> expected = List.of(
                "css/exam-workspace-enhancements.css",
                "js/exam-workspace-layout.js",
                "js/exam-coding-workspace.js",
                "js/exam-integrity-controls.js",
                "css/proctor-enhancements.css",
                "js/proctor-enhancements.js",
                "css/grader-settings.css",
                "js/grader-settings.js",
                "css/auth-guide.css",
                "manuals/identity-program-notice.html",
                "manuals/otp-auth-manual.html",
                "manuals/identity-onboarding.html"
        );

        List<String> missing = new ArrayList<>();
        for (String rel : expected) {
            Path p = STATIC.resolve(rel);
            if (!Files.exists(p) || !Files.isRegularFile(p)) {
                missing.add(rel);
            }
        }
        assertThat(missing).as("없는 정적 파일: %s", missing).isEmpty();
    }

    @Test
    @DisplayName("[계약] 템플릿이 참조하는 /static 자산이 모두 실제로 존재한다 (404 방지)")
    void 참조자산_404없음() {
        List<Path> templates = List.of(
                TPL.resolve("trainee/do-test.html"),
                TPL.resolve("trainee/exam-precheck.html"),
                TPL.resolve("admin/admin-04-evaluation/admin-evaluation-monitoring-live.html"),
                TPL.resolve("admin/admin-04-evaluation/admin-evaluation-identity.html"),
                TPL.resolve("admin/admin-04-evaluation/admin-evaluation-identity-detail.html"),
                TPL.resolve("admin/admin-04-evaluation/admin-evaluation-grader-settings.html"),
                STATIC.resolve("manuals/identity-program-notice.html"),
                STATIC.resolve("manuals/otp-auth-manual.html"),
                STATIC.resolve("manuals/identity-onboarding.html")
        );

        Pattern ref = Pattern.compile("(?:href|src)=\"(/static/[^\"?#]+)\"");
        List<String> missing = new ArrayList<>();

        for (Path t : templates) {
            Matcher m = ref.matcher(read(t));
            while (m.find()) {
                String url = m.group(1);              // /static/js/foo.js
                Path p = STATIC.resolve(url.substring("/static/".length()));
                if (!Files.exists(p)) {
                    missing.add(t.getFileName() + " → " + url);
                }
            }
        }
        assertThat(missing).as("참조하지만 없는 자산: %s", missing).isEmpty();
    }

    @Test
    @DisplayName("[LXP-022~025] 그레이더 설정 화면에 채점 방식·코드·케이스·부분점수 흐름이 있다")
    void 그레이더_설정화면_계약() {
        String html = graderSettings();
        String js = read(STATIC.resolve("js/grader-settings.js"));

        assertThat(html).contains("name=\"gradingMode\"")
                .contains("id=\"graderSource\"")
                .contains("id=\"caseBody\"")
                .contains("id=\"graderScore\"")
                .contains("id=\"validateGrader\"")
                .contains("id=\"applyGrader\"");
        assertThat(html).contains("실제 제출 코드의 격리 실행")
                .contains("서버 저장은 채점 엔진 연동 단계");
        assertThat(js).contains("addCase")
                .contains("updateSummary")
                .contains("localStorage")
                .contains("실제 코드 실행은 2차 연동 범위");
    }

    /* ===================== 2. do-test 로드 계약 ===================== */

    @Test
    @DisplayName("[LXP-009/010] do-test.html 이 새 JS·CSS 를 한 번씩만 로드한다")
    void doTest_자산_중복로드없음() {
        String html = doTest();

        assertThat(countOf(html, "/static/css/exam-workspace-enhancements.css"))
                .as("레이아웃 CSS 는 정확히 한 번만 로드해야 한다").isEqualTo(1);
        assertThat(countOf(html, "/static/js/exam-workspace-layout.js")).isEqualTo(1);
        assertThat(countOf(html, "/static/js/exam-coding-workspace.js")).isEqualTo(1);
        assertThat(countOf(html, "/static/js/exam-integrity-controls.js")).isEqualTo(1);
    }

    @Test
    @DisplayName("[LXP-009] separator 접근성 속성과 초기화 컨트롤이 있다")
    void 레이아웃_접근성_컨트롤() {
        String html = doTest();
        String js = read(STATIC.resolve("js/exam-workspace-layout.js"));

        /* 마크업: separator 요소와 레이아웃 루트, 초기화 버튼, 좁은 화면 탭 호스트 */
        assertThat(html).contains("id=\"navSep\"").contains("class=\"pane-sep\"");
        assertThat(html).contains("id=\"examLayout\"");
        assertThat(html).contains("id=\"layoutResetBtn\"");
        assertThat(html).contains("id=\"paneTabs\"");

        /* ARIA 는 JS 가 부여한다 — 네 속성이 모두 설정돼야 한다. */
        assertThat(js).contains("\"role\", \"separator\"");
        assertThat(js).contains("aria-orientation");
        assertThat(js).contains("aria-valuemin");
        assertThat(js).contains("aria-valuemax");
        assertThat(js).contains("aria-valuenow");

        /* 방향키 2% / Shift 10% 계약 */
        assertThat(js).contains("var STEP = 2;");
        assertThat(js).contains("var BIG_STEP = 10;");

        /* 저장 키에 버전이 들어가야 한다 */
        assertThat(js).contains("lxp.exam.layout.v1");

        /* Pointer Capture 를 쓴다 (화면 밖 이동에도 끊기지 않게) */
        assertThat(js).contains("setPointerCapture");
    }

    @Test
    @DisplayName("[LXP-010] 코딩 UI 가 CODING 타입에만 활성화되는 계약이 있다")
    void 코딩UI_타입계약() {
        String html = doTest();
        String js = read(STATIC.resolve("js/exam-coding-workspace.js"));

        /* 템플릿은 ExamCoding.handles(q) 로만 분기한다 */
        assertThat(html).contains("window.ExamCoding.handles(q)");
        /* handles 는 CODING 만 받는다 */
        assertThat(js).contains("q.type === \"CODING\"");

        /* 언어 선택 · 줄번호 · 탭 · 시뮬레이션 배지 */
        assertThat(js).contains("JavaScript").contains("Python").contains("Java");
        assertThat(js).contains("code-gutter");
        assertThat(js).contains("lxp.exam.coding.lang.v1");
        assertThat(js).contains("sim-badge");
        assertThat(js).contains("로컬 시뮬레이션");

        /* 기존 답안 저장 계약을 그대로 쓴다 */
        assertThat(html).contains("answerText: text");

        /* 실행 샌드박스가 없다는 사실을 명시한다 */
        assertThat(js).contains("실행 샌드박스");
        assertThat(js).contains("연동이 필요");
    }

    @Test
    @DisplayName("[LXP-010] 정답·해설·숨은 테스트케이스를 프론트에 넣지 않았다")
    void 코딩UI_정답노출없음() {
        String js = read(STATIC.resolve("js/exam-coding-workspace.js"));
        /* 서버 DTO 에 없는 필드를 읽으려 하면 값을 만들어냈다는 뜻이다. */
        assertThat(js).doesNotContain("correctAnswer");
        assertThat(js).doesNotContain("expectedOutput");
        assertThat(js).doesNotContain("hiddenTestCase");
        /* 테스트케이스가 없다는 사실을 화면에 적어야 한다. */
        assertThat(js).contains("테스트케이스가 연동되지 않았습니다");
    }

    @Test
    @DisplayName("[LXP-014/017/021] 무결성 UI 가 서버가 아는 이벤트만 보내고 한계를 표시한다")
    void 무결성_이벤트_계약() {
        String js = read(STATIC.resolve("js/exam-integrity-controls.js"));

        /* 이 파일이 새로 보내는 것은 FULLSCREEN_EXIT 하나뿐이다.
           기존 스크립트가 보내는 TAB_BLUR·COPY·PASTE 를 중복 전송하면 안 된다. */
        assertThat(js).contains("send(\"FULLSCREEN_EXIT\"");
        assertThat(countOf(js, "send(\"TAB_BLUR\"")).isZero();
        assertThat(countOf(js, "send(\"COPY\"")).isZero();
        assertThat(countOf(js, "send(\"PASTE\"")).isZero();
        /* 서버 enum 에 없는 유형을 만들어 보내지 않는다. */
        assertThat(countOf(js, "send(\"CUT\"")).isZero();

        /* 기존 시험 설정 플래그를 존중한다 */
        assertThat(js).contains("attempt.blockTabSwitch");
        assertThat(js).contains("attempt.blockCopyPaste");

        /* 차단할 수 없는 것을 차단한다고 말하지 않는다 */
        assertThat(js).contains("감지 한계");
        assertThat(js).contains("개발자도구");

        /* debounce 로 반복 전송을 막는다 */
        assertThat(js).contains("DEBOUNCE_MS");

        /* 마운트 지점이 템플릿에 있다 */
        assertThat(doTest()).contains("id=\"integrityMount\"");
    }

    /* ===================== 3. 사전점검 ===================== */

    @Test
    @DisplayName("[LXP-018/019/148/149] 사전점검에 화면 공유·모니터 상태·매뉴얼 링크가 있다")
    void 사전점검_확장_계약() {
        String html = precheck();
        String js = read(STATIC.resolve("js/exam-precheck.js"));

        /* 화면 공유 카드 */
        assertThat(html).contains("id=\"shareCard\"")
                .contains("id=\"shareStartBtn\"")
                .contains("id=\"shareStopBtn\"")
                .contains("id=\"sharePreview\"");
        assertThat(js).contains("getDisplayMedia");
        /* 공유 종료 시 되돌린다 */
        assertThat(js).contains("track.addEventListener(\"ended\"");

        /* 모니터 확인 카드 */
        assertThat(html).contains("id=\"monitorCard\"").contains("id=\"monitorCheckBtn\"");
        assertThat(js).contains("isExtended");
        /* 미지원을 통과로 가장하지 않는다 */
        assertThat(js).contains("확인 불가");

        /* 게이트가 네 항목을 모두 본다 */
        assertThat(js).contains("identityApproved && camPassed && sharePassed && monitorPassed");

        /* 매뉴얼 링크 */
        assertThat(html).contains("/static/manuals/identity-program-notice.html")
                .contains("/static/manuals/otp-auth-manual.html")
                .contains("/static/manuals/identity-onboarding.html");

        /* 프론트 확인 단계라는 한계를 한 번 표시한다 */
        assertThat(html).contains("id=\"precheckLimits\"");
        assertThat(html).contains("서버 강제는 연동이 필요");
    }

    /* ===================== 4. 감독 화면 ===================== */

    @Test
    @DisplayName("[LXP-003/005/013/020] 감독 화면에 3면 소스·검색·필터·실행환경 패널이 있다")
    void 감독화면_확장_계약() {
        String html = monitoring();
        String js = read(STATIC.resolve("js/proctor-enhancements.js"));

        /* 마운트 지점과 자산 로드 (한 번씩만) */
        assertThat(html).contains("id=\"proctorToolsMount\"");
        assertThat(html).contains("id=\"runtimePanelMount\"");
        assertThat(countOf(html, "/static/css/proctor-enhancements.css")).isEqualTo(1);
        assertThat(countOf(html, "/static/js/proctor-enhancements.js")).isEqualTo(1);

        /* 서버 행을 그대로 넘기는 계약 */
        assertThat(html).contains("window._proctorRows");
        assertThat(html).contains("window._proctorUrls");

        /* 검색·상태·위험도 필터 */
        assertThat(js).contains("pxSearch").contains("pxStatus").contains("pxRisk");

        /* 요약 5종 */
        assertThat(js).contains("전체").contains("정상").contains("주의")
                .contains("위험").contains("연결 끊김");

        /* 3면 소스 */
        assertThat(js).contains("웹캠").contains("화면").contains("모바일");
        assertThat(js).contains("실시간 스트리밍 연동 필요");
        /* 가짜 영상을 넣지 않는다 — video 요소를 만들지 않는다 */
        assertThat(js).doesNotContain("createElement(\"video\")");

        /* 경고 모달: 키보드 조작 (Esc, 포커스 트랩) */
        assertThat(js).contains("aria-modal").contains("\"Escape\"").contains("\"Tab\"");
        /* 실제 응답 확인 후에만 성공 처리 */
        assertThat(js).contains("if (!res.ok)");

        /* 실행환경 패널: KPI · 3 시나리오 · 증설 3단계 · 재시도 · 데모 고지 */
        assertThat(js).contains("활성 세션").contains("대기 요청")
                .contains("평균 시작 시간").contains("오류율");
        assertThat(js).contains("normal").contains("busy").contains("fault");
        assertThat(js).contains("증설 요청").contains("프로비저닝").contains("준비 완료");
        assertThat(js).contains("프론트 데모 — 실제 인프라 미연결");

        /* 데모 데이터는 상수로 고립된다. WebSocket 을 열지 않는다. */
        assertThat(js).contains("var DEMO = {");
        assertThat(js).doesNotContain("new WebSocket");
    }

    /* ===================== 5. 신분확인 UI ===================== */

    @Test
    @DisplayName("[LXP-016] 신분확인 목록·상세에 필터·증거·오류 상태가 있다")
    void 신분확인_UI_계약() {
        String list = identityList();
        String detail = identityDetail();

        /* 목록: 시험명·응시자 검색 + 상태 + 제출 시각 범위 */
        assertThat(list).contains("id=\"q\"").contains("id=\"status\"");
        assertThat(list).contains("id=\"fromDate\"").contains("id=\"toDate\"");

        /* 상세: 나란히 비교 + 사실 표 + attempt 증거 */
        assertThat(detail).contains("compare-row").contains("compare-card");
        assertThat(detail).contains("fact-grid");
        assertThat(detail).contains("row.attemptId");

        /* 이미지 실패를 숨기지 않는다 — 410 / 403 을 구분해 안내 */
        assertThat(detail).contains("doc-error");
        assertThat(detail).contains("410").contains("403");

        /* 없는 값을 만들지 않고 연동 필요를 표시한다 */
        assertThat(detail).contains("연동 필요");

        /* 실시간 영상이 아니라는 사실을 유지한다 */
        assertThat(detail).contains("실시간 영상이 아니");
    }

    /* ===================== 6. 정적 안내 3종 ===================== */

    @Test
    @DisplayName("[LXP-148/149/143/145] 정적 안내 3개에 외부 연동 전·연동 필요 고지가 있다")
    void 안내문서_고지_계약() {
        String notice = read(STATIC.resolve("manuals/identity-program-notice.html"));
        String otp = read(STATIC.resolve("manuals/otp-auth-manual.html"));
        String onboarding = read(STATIC.resolve("manuals/identity-onboarding.html"));

        for (String doc : List.of(notice, otp, onboarding)) {
            /* 독립 정적 HTML — Thymeleaf 에 의존하지 않는다 */
            assertThat(doc).doesNotContain("th:").doesNotContain("xmlns:th");
            /* 공용 CSS 만 쓴다 */
            assertThat(doc).contains("/static/css/auth-guide.css");
            /* 외부 연동 전이라는 사실을 적는다 */
            assertThat(doc).contains("연동");
        }

        /* LXP-148 — 사전 고지 필수 항목 */
        assertThat(notice).contains("지원 환경").contains("권한")
                .contains("개인정보").contains("HTTP");
        assertThat(notice).contains("외부 OTP 인증은 아직 연동되지 않았습니다");

        /* LXP-149 — 매뉴얼 필수 항목 + 다운로드 + 인쇄 */
        assertThat(otp).contains("준비 사항").contains("오류 해결").contains("문의");
        /* 다운로드는 단순 링크가 아니라 CSS 를 심은 Blob 을 만드는 버튼이다 (P2-2). */
        assertThat(otp).contains("id=\"downloadBtn\"");
        assertThat(otp).contains("a.download = FILE_NAME");
        assertThat(otp).contains("window.print()");

        /* LXP-143/145 — 온보딩. localStorage 로 완료를 가장하지 않는다. */
        assertThat(onboarding).contains("역할별 접근").contains("접속 기록")
                .contains("신분확인 기록").contains("부정행위 기록");
        assertThat(onboarding).doesNotContain("localStorage");
        assertThat(onboarding).contains("서버가 강제하지 않");

        /* 인쇄 CSS 가 있다 */
        assertThat(read(STATIC.resolve("css/auth-guide.css"))).contains("@media print");
    }

    /* ===================== 7. 금지 항목 ===================== */

    /** 이번 작업이 만들거나 수정한 프론트 파일 전체. */
    private List<Path> touchedFrontFiles() {
        return List.of(
                TPL.resolve("trainee/do-test.html"),
                TPL.resolve("trainee/exam-precheck.html"),
                TPL.resolve("admin/admin-04-evaluation/admin-evaluation-monitoring-live.html"),
                TPL.resolve("admin/admin-04-evaluation/admin-evaluation-identity.html"),
                TPL.resolve("admin/admin-04-evaluation/admin-evaluation-identity-detail.html"),
                STATIC.resolve("manuals/identity-program-notice.html"),
                STATIC.resolve("manuals/otp-auth-manual.html"),
                STATIC.resolve("manuals/identity-onboarding.html"),
                STATIC.resolve("js/exam-workspace-layout.js"),
                STATIC.resolve("js/exam-coding-workspace.js"),
                STATIC.resolve("js/exam-integrity-controls.js"),
                STATIC.resolve("js/proctor-enhancements.js"),
                STATIC.resolve("js/exam-precheck.js"),
                STATIC.resolve("css/exam-workspace-enhancements.css"),
                STATIC.resolve("css/proctor-enhancements.css"),
                STATIC.resolve("css/auth-guide.css"),
                STATIC.resolve("css/admin-identity.css")
        );
    }

    @Test
    @DisplayName("[계약] 외부 CDN 을 추가하지 않았다")
    void 외부CDN_없음() {
        Pattern remote = Pattern.compile("(?:src|href)=\"https?://(?!localhost)[^\"]+\"");
        List<String> hits = new ArrayList<>();

        for (Path p : touchedFrontFiles()) {
            Matcher m = remote.matcher(read(p));
            while (m.find()) {
                String v = m.group();
                /* xmlns 등 네임스페이스 선언은 자산 로드가 아니다. */
                if (v.contains("www.w3.org")) continue;
                hits.add(p.getFileName() + " → " + v);
            }
        }
        assertThat(hits).as("외부에서 불러오는 자산: %s", hits).isEmpty();
    }

    @Test
    @DisplayName("[계약] href=\"#\" 무동작 링크와 인라인 javascript: URL 이 없다")
    void 무동작링크_없음() {
        List<String> hits = new ArrayList<>();
        for (Path p : touchedFrontFiles()) {
            String body = read(p);
            if (body.contains("href=\"#\"")) {
                hits.add(p.getFileName() + " → href=\"#\"");
            }
            if (body.contains("href=\"javascript:")) {
                hits.add(p.getFileName() + " → javascript: URL");
            }
        }
        assertThat(hits).as("무동작 링크: %s", hits).isEmpty();
    }

    @Test
    @DisplayName("[계약] 주민등록번호 형태 데이터와 실제 개인정보 샘플이 없다")
    void 개인정보_샘플_없음() {
        /* 주민등록번호 형태: 6자리-7자리(성별코드 1~4로 시작) */
        Pattern rrn = Pattern.compile("\\b\\d{6}-[1-4]\\d{6}\\b");
        /* 실제로 보일 만한 휴대폰 번호 형태 */
        Pattern phone = Pattern.compile("\\b01[016-9]-\\d{3,4}-\\d{4}\\b");

        List<String> hits = new ArrayList<>();
        for (Path p : touchedFrontFiles()) {
            String body = read(p);
            if (rrn.matcher(body).find()) {
                hits.add(p.getFileName() + " → 주민등록번호 형태");
            }
            Matcher pm = phone.matcher(body);
            while (pm.find()) {
                hits.add(p.getFileName() + " → 전화번호 형태 " + pm.group());
            }
        }
        assertThat(hits).as("개인정보 형태 데이터: %s", hits).isEmpty();
    }

    @Test
    @DisplayName("[계약] 수정한 템플릿에 중복 id 가 없다")
    void 중복id_없음() {
        Pattern idAttr = Pattern.compile("\\sid=\"([^\"]+)\"");
        List<String> problems = new ArrayList<>();

        for (Path p : touchedFrontFiles()) {
            String name = p.getFileName().toString();
            if (!name.endsWith(".html")) continue;

            Set<String> seen = new HashSet<>();
            Matcher m = idAttr.matcher(read(p));
            while (m.find()) {
                String id = m.group(1);
                /* Thymeleaf 표현식이 들어간 동적 id 는 정적으로 비교할 수 없다. */
                if (id.contains("${") || id.contains("|")) continue;
                if (!seen.add(id)) {
                    problems.add(name + " → 중복 id: " + id);
                }
            }
        }
        assertThat(problems).as("중복 id: %s", problems).isEmpty();
    }

    @Test
    @DisplayName("[계약] 수정 금지 파일을 건드리지 않았다 — 새 자산을 주입하지 않았다")
    void 수정금지파일_미변경() {
        /* 공용 셸·IA·로그인·공용 CSS 에 이번 작업의 자산이 들어가면 안 된다. */
        List<String> forbidden = List.of(
                "templates/fragments/admin.html",
                "templates/fragments/trainee.html",
                "templates/fragments/instructor.html",
                "templates/admin/index.html",
                "templates/trainee/index.html",
                "templates/01-login/login.html"
        );
        List<String> ours = List.of(
                "exam-workspace-enhancements.css", "exam-workspace-layout.js",
                "exam-coding-workspace.js", "exam-integrity-controls.js",
                "proctor-enhancements.css", "proctor-enhancements.js", "auth-guide.css"
        );

        List<String> hits = new ArrayList<>();
        for (String rel : forbidden) {
            Path p = RES.resolve(rel);
            if (!Files.exists(p)) continue;      // 없는 파일은 검사 대상이 아니다
            String body = read(p);
            for (String asset : ours) {
                if (body.contains(asset)) {
                    hits.add(rel + " → " + asset);
                }
            }
        }
        assertThat(hits).as("수정 금지 파일에 주입된 자산: %s", hits).isEmpty();

        /* 공용 CSS 에도 우리 선택자를 넣지 않았다. */
        for (String rel : List.of("static/css/common-style.css", "static/css/sidebar-style.css",
                                  "static/css/basic-form-trainee.css", "static/css/btn-style.css")) {
            Path p = RES.resolve(rel);
            if (!Files.exists(p)) continue;
            assertThat(read(p))
                    .as("%s 에 이번 작업 선택자가 들어갔다", rel)
                    .doesNotContain(".pane-sep")
                    .doesNotContain(".tri-sources")
                    .doesNotContain(".runtime-panel");
        }
    }

    /* ===================== 8. 보완 차수 — 실제 동작 검증 =====================
       문자열 존재 검사만으로는 아래 결함을 잡지 못했다. node 로 <b>실제 실행</b>한다.
       ======================================================================= */

    /** node 스크립트를 실행하고 (exitCode, 출력) 을 돌려준다. */
    private record NodeRun(int code, String out) {
    }

    private NodeRun runNode(String script) {
        try {
            Path tmp = Files.createTempFile("lxp-contract-", ".js");
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

    private String abs(String rel) {
        return STATIC.resolve(rel).toAbsolutePath().normalize().toString();
    }

    @Test
    @DisplayName("[P1-1] 경고 응답 판정이 same-origin 성공·외부 origin·401·403·로그인 redirect·예상 밖 redirect 를 구분한다")
    void 경고응답_판정_실제실행() {
        String script = """
                const m = require(%s);
                const V = m.warningVerdict;
                const ORIGIN = "http://lxp.local";
                const cases = [
                  ["정상(관리자, same-origin)", {ok:true, status:200, url:"http://lxp.local/admin/evaluation/monitoring/12"}, ORIGIN, true,  "OK"],
                  ["정상(강사, same-origin)",   {ok:true, status:200, url:"http://lxp.local/instructor/proctor/12"},          ORIGIN, true,  "OK"],
                  ["외부 origin(관리자 경로 흉내)", {ok:true, status:200, url:"https://evil.example/admin/evaluation/monitoring/12"}, ORIGIN, false, "ORIGIN_MISMATCH"],
                  ["외부 origin(강사 경로 흉내)",   {ok:true, status:200, url:"http://other.local/instructor/proctor/12"},              ORIGIN, false, "ORIGIN_MISMATCH"],
                  ["401",          {ok:false,status:401, url:"http://lxp.local/x"},                              ORIGIN, false, "UNAUTHORIZED"],
                  ["403",          {ok:false,status:403, url:"http://lxp.local/x"},                              ORIGIN, false, "FORBIDDEN"],
                  ["500",          {ok:false,status:500, url:"http://lxp.local/x"},                              ORIGIN, false, "HTTP_ERROR"],
                  ["로그인 리다이렉트(same-origin)", {ok:true, status:200, url:"http://lxp.local/login"},         ORIGIN, false, "SESSION_EXPIRED"],
                  ["로그인+쿼리(same-origin)",   {ok:true, status:200, url:"http://lxp.local/login?expired"},    ORIGIN, false, "SESSION_EXPIRED"],
                  ["예상 밖(same-origin)",      {ok:true, status:200, url:"http://lxp.local/error/access-denied"}, ORIGIN, false, "UNEXPECTED_REDIRECT"],
                  ["URL 없음",     {ok:true, status:200, url:null},                                      ORIGIN, false, "UNKNOWN_TARGET"],
                  ["응답 없음",    null,                                                                  ORIGIN, false, "NO_RESPONSE"],
                  ["expectedOrigin 없음(undefined)",  {ok:true, status:200, url:"http://lxp.local/admin/evaluation/monitoring/12"}, undefined,      false, "ORIGIN_MISMATCH"],
                  ["expectedOrigin 파싱 불가",         {ok:true, status:200, url:"http://lxp.local/admin/evaluation/monitoring/12"}, "not-a-url",   false, "ORIGIN_MISMATCH"]
                ];
                let bad = 0;
                for (const [name, res, expectedOrigin, expOk, expReason] of cases) {
                  const v = V(res, expectedOrigin);
                  if (v.ok !== expOk || v.reason !== expReason) {
                    console.log("FAIL " + name + " → ok=" + v.ok + " reason=" + v.reason
                                + " (기대 ok=" + expOk + " reason=" + expReason + ")");
                    bad++;
                  }
                }
                /* 실패 판정에는 사용자에게 보일 메시지가 반드시 있어야 한다. */
                for (const r of [
                  [{ok:false,status:403,url:"http://lxp.local/x"}, ORIGIN],
                  [{ok:true,status:200,url:"http://lxp.local/login"}, ORIGIN],
                  [{ok:true,status:200,url:"https://evil.example/admin/evaluation/monitoring/12"}, ORIGIN]
                ]) {
                  const v = V(r[0], r[1]);
                  if (!v.message || !v.message.trim()) { console.log("FAIL 실패 메시지 없음: " + v.reason); bad++; }
                }
                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(abs("js/proctor-enhancements.js")));

        NodeRun r = runNode(script);
        assertThat(r.code())
                .as("경고 응답 판정이 기대와 다릅니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[P1-2] layout register → unregister → reset 순서에서 해제된 pane 이 남지 않는다")
    void 레이아웃_해제_실제실행() {
        String script = """
                /* 아주 작은 DOM 스텁 — 이 모듈이 실제로 쓰는 것만 흉내 낸다. */
                function stubEl() {
                  const attrs = {}, ds = {};
                  return {
                    attrs, dataset: ds,
                    style: { props: {}, setProperty(k, v) { this.props[k] = v; } },
                    setAttribute(k, v) { attrs[k] = String(v); },
                    removeAttribute(k) { delete attrs[k]; },
                    getAttribute(k) { return attrs[k]; },
                    addEventListener() {},
                    getBoundingClientRect() { return { width: 1000, height: 800, left: 0, top: 0 }; }
                  };
                }
                global.window = { addEventListener() {}, innerWidth: 1600,
                                  localStorage: { getItem() { return null; }, setItem() {} } };
                global.document = undefined;

                const L = require(%s);

                const rootA = stubEl(), sepA = stubEl();
                const rootB = stubEl(), sepB = stubEl();

                const a = L.register({ key: "a", root: rootA, sep: sepA, cssVar: "--a",
                                       axis: "vertical", min: 10, max: 50, def: 20 });
                const b = L.register({ key: "b", root: rootB, sep: sepB, cssVar: "--b",
                                       axis: "horizontal", min: 20, max: 80, def: 40 });

                let bad = 0;
                function check(cond, msg) { if (!cond) { console.log("FAIL " + msg); bad++; } }

                check(a && b, "register 가 pane 을 반환해야 한다");
                check(L.paneCount() === 2, "등록 후 pane 2개 (실제 " + L.paneCount() + ")");

                /* ARIA 가 실제로 설정됐는가 */
                check(sepA.getAttribute("role") === "separator", "role=separator");
                check(sepA.getAttribute("aria-orientation") === "vertical", "aria-orientation");
                check(sepA.getAttribute("aria-valuenow") !== undefined, "aria-valuenow");
                check(sepB.getAttribute("aria-orientation") === "horizontal", "가로 축 방향");

                /* ① 해제하면 배열에서 빠진다 */
                check(L.unregister(a) === true, "unregister 가 true 를 반환");
                check(L.paneCount() === 1, "해제 후 pane 1개 (실제 " + L.paneCount() + ")");
                check(!("a" in L.values()), "해제된 pane 이 values() 에 남지 않아야 한다");
                check(a.released === true, "해제 표시가 남아야 한다");

                /* ② 두 번 해제해도 오류가 없다 (멱등) */
                let threw = false;
                try { L.unregister(a); } catch (e) { threw = true; }
                check(!threw, "두 번째 unregister 에서 예외가 나면 안 된다");
                check(L.paneCount() === 1, "두 번째 해제 후에도 pane 1개");

                /* ③ null 을 넘겨도 안전하다 */
                threw = false;
                try { L.unregister(null); } catch (e) { threw = true; }
                check(!threw, "null unregister 에서 예외가 나면 안 된다");

                /* ④ reset 은 살아 있는 pane 만 건드린다 */
                rootA.style.props["--a"] = "SHOULD_NOT_CHANGE";
                L.reset();
                check(rootA.style.props["--a"] === "SHOULD_NOT_CHANGE",
                      "해제된 pane 의 CSS 변수를 reset 이 건드리면 안 된다 (실제 "
                      + rootA.style.props["--a"] + ")");
                check(rootB.style.props["--b"] === "40%%",
                      "살아 있는 pane 은 기본값으로 돌아가야 한다 (실제 " + rootB.style.props["--b"] + ")");

                /* ⑤ 반복 등록·해제로 누적되지 않는다 */
                for (let i = 0; i < 20; i++) {
                  const p = L.register({ key: "loop", root: stubEl(), sep: stubEl(),
                                         cssVar: "--loop", axis: "horizontal",
                                         min: 20, max: 80, def: 40 });
                  L.unregister(p);
                }
                check(L.paneCount() === 1,
                      "20회 왕복 후에도 pane 1개여야 한다 (실제 " + L.paneCount() + ")");

                if (bad) process.exit(1);
                console.log("OK");
                """.formatted(jsString(abs("js/exam-workspace-layout.js")));

        NodeRun r = runNode(script);
        assertThat(r.code())
                .as("레이아웃 등록·해제 계약이 깨졌습니다:%n%s", r.out()).isZero();
    }

    @Test
    @DisplayName("[P1-2] 코딩 workspace dispose 가 layout unregister 를 호출하고 멱등이다")
    void 코딩_dispose_계약() {
        String js = read(STATIC.resolve("js/exam-coding-workspace.js"));

        /* register 결과를 보관해야 해제할 수 있다 */
        assertThat(js).contains("layoutPane = window.ExamLayout.register(");
        /* dispose 가 unregister 를 호출한다 */
        assertThat(js).contains("window.ExamLayout.unregister(layoutPane)");
        /* 저장 타이머도 함께 정리한다 */
        assertThat(js).contains("clearTimeout(timer)");
        /* 멱등 가드 */
        assertThat(js).contains("if (disposed) return;");
    }

    @Test
    @DisplayName("[P1-2] renderQuestion 이 answerArea 를 덮어쓰기 전에 이전 코딩 workspace 를 정리한다")
    void renderQuestion_정리순서() {
        String html = doTest();

        int cleanup = html.indexOf("if (codingWs) { codingWs.dispose(); codingWs = null; }");
        int firstBranch = html.indexOf("if (q.type === 'MULTIPLE_CHOICE')");
        int codingRender = html.indexOf("window.ExamCoding.render(answerArea, q,");

        assertThat(cleanup).as("정리 호출이 있어야 한다").isGreaterThan(0);
        assertThat(firstBranch).isGreaterThan(0);
        assertThat(codingRender).isGreaterThan(0);

        /* 정리가 유형 분기보다 <b>앞</b>에 있어야 CODING → 객관식 이동에서도 정리된다. */
        assertThat(cleanup)
                .as("정리는 문항 유형 분기보다 먼저 실행돼야 한다")
                .isLessThan(firstBranch);
        assertThat(cleanup)
                .as("정리는 새 workspace 렌더보다 먼저 실행돼야 한다")
                .isLessThan(codingRender);

        /* 정리 호출은 한 번만 — 분기 안에 중복으로 남아 있으면 안 된다. */
        assertThat(countOf(html, "codingWs.dispose()"))
                .as("dispose 호출은 renderQuestion 진입부 한 곳이어야 한다").isEqualTo(1);

        /* 문항 이동은 saveCurrent() 완료 뒤 renderQuestion 을 부른다 — 답안 보존 */
        assertThat(html).contains("saveCurrent().then(() => { idx++; renderQuestion(); })");
        /* saveCurrent 는 #answerText 를 읽으므로 코딩 코드도 저장된다 */
        assertThat(html).contains("document.getElementById('answerText')");
    }

    @Test
    @DisplayName("[P2-1] 실제 타일 상태와 데모 상태가 분리되어 있다")
    void 타일상태_데모분리() {
        String js = read(STATIC.resolve("js/proctor-enhancements.js"));

        /* 실제 타일은 서버 사실(live)만 쓴다 — connecting / offline 두 가지 */
        assertThat(js).contains("box.dataset.state = r.live ? \"connecting\" : \"offline\"");

        /* 실제 타일에 거짓 상태를 주입하지 않는다 */
        assertThat(js).doesNotContain("box.dataset.state = \"connected\"");
        assertThat(js).doesNotContain("box.dataset.state = \"denied\"");
        assertThat(js).doesNotContain("box.dataset.state = \"unsupported\"");

        /* 데모 영역이 별도로 있고 다섯 상태를 모두 보여 준다 */
        assertThat(js).contains("DEMO_STATES");
        assertThat(js).contains("setupStateDemo");
        assertThat(js).contains("\"connected\"").contains("\"connecting\"")
                .contains("\"offline\"").contains("\"denied\"").contains("\"unsupported\"");
        assertThat(js).contains("연결").contains("연결 중").contains("끊김")
                .contains("권한 거부").contains("미지원");

        /* 데모임을 명확히 표시하고, 실제 데이터와 구분하는 표식이 있다 */
        assertThat(js).contains("프론트 상태 미리보기(데모)");
        assertThat(js).contains("cell.dataset.demo = \"true\"");

        /* 데모가 실제 행 데이터를 건드리지 않는다 — setupStateDemo 는 rows 를 받지 않는다 */
        assertThat(js).contains("function setupStateDemo() {");

        /* 색만으로 전달하지 않는다 */
        assertThat(js).contains("aria-label\", \"데모 상태 ");

        /* per-source 판정은 연동 필요임을 유지 */
        assertThat(js).contains("미디어 상태 API 연동이 필요");

        /* CSS 에 다섯 상태 선택자가 모두 있다 */
        String css = read(STATIC.resolve("css/proctor-enhancements.css"));
        for (String st : List.of("connected", "connecting", "offline", "denied", "unsupported")) {
            assertThat(css).as("CSS 에 %s 상태 스타일이 있어야 한다", st)
                    .contains("data-state=\"" + st + "\"");
        }
        assertThat(css).contains(".state-demo");
    }

    @Test
    @DisplayName("[P2-2] OTP 다운로드가 CSS 를 포함한 Blob 을 만들고 Object URL 을 해제하며 실패를 표시한다")
    void OTP_오프라인_다운로드_계약() {
        String html = read(STATIC.resolve("manuals/otp-auth-manual.html"));

        /* 단순 파일 링크가 아니라 버튼 + 스크립트로 만든다 */
        assertThat(html).contains("id=\"downloadBtn\"");
        assertThat(html).doesNotContain("download=\"LXP_OTP_인증_매뉴얼_v1.0.html\"");
        assertThat(html).contains("a.download = FILE_NAME");

        /* 같은 출처 CSS 를 읽어 <style> 로 심는다 */
        assertThat(html).contains("/static/css/auth-guide.css");
        assertThat(html).contains("createElement(\"style\")");
        assertThat(html).contains("style.textContent = css");
        /* 외부 CSS 링크는 사본에서 제거한다 */
        assertThat(html).contains("link[rel=\"stylesheet\"]");

        /* Blob + 파일명 */
        assertThat(html).contains("new Blob(").contains("text/html;charset=utf-8");
        assertThat(html).contains("LXP_OTP_인증_매뉴얼_v1.0.html");

        /* Object URL 회수 */
        assertThat(html).contains("URL.createObjectURL");
        assertThat(html).contains("URL.revokeObjectURL");

        /* 실패를 조용히 넘기지 않는다 */
        assertThat(html).contains(".catch(");
        assertThat(html).contains("내려받기에 실패했습니다");

        /* 오프라인 링크 한계를 명시하고, 확정되지 않은 도메인을 만들지 않는다 */
        assertThat(html).contains("오프라인 사본");
        assertThat(html).doesNotContain("https://lms.samsungax.com");

        /* print CSS 는 사본에도 포함된다 (auth-guide.css 안에 있으므로) */
        assertThat(read(STATIC.resolve("css/auth-guide.css"))).contains("@media print");
    }

    /* ===================== 9. 인라인 스크립트 파싱 ===================== */

    @Test
    @DisplayName("[계약] 템플릿의 인라인 <script> 가 모두 파싱된다 (Thymeleaf 블록은 더미로 치환)")
    void 인라인스크립트_파싱() {
        List<Path> pages = List.of(
                TPL.resolve("trainee/do-test.html"),
                TPL.resolve("trainee/exam-precheck.html"),
                TPL.resolve("admin/admin-04-evaluation/admin-evaluation-monitoring-live.html"),
                TPL.resolve("admin/admin-04-evaluation/admin-evaluation-identity.html"),
                TPL.resolve("admin/admin-04-evaluation/admin-evaluation-identity-detail.html"),
                STATIC.resolve("manuals/identity-program-notice.html"),
                STATIC.resolve("manuals/otp-auth-manual.html"),
                STATIC.resolve("manuals/identity-onboarding.html")
        );

        Pattern scriptTag = Pattern.compile(
                "<script(?![^>]*\\bsrc=)[^>]*>(.*?)</script>", Pattern.DOTALL);
        /* Thymeleaf 인라인 치환: /*[[ ... ]]*\/ 'default' → 안전한 더미 값 */
        Pattern thymeleaf = Pattern.compile("/\\*\\[\\[.*?\\]\\]\\*/", Pattern.DOTALL);

        List<String> failures = new ArrayList<>();
        int checked = 0;

        for (Path page : pages) {
            Matcher m = scriptTag.matcher(read(page));
            int idx = 0;
            while (m.find()) {
                idx++;
                String src = m.group(1);
                if (src.isBlank()) continue;

                /* Thymeleaf 표현식을 더미로 바꾼다. 뒤따르는 기본값 리터럴이 남아
                   `null null` 이 되지 않도록 표현식 자체를 지운다. */
                String js = thymeleaf.matcher(src).replaceAll("");
                /* CDATA 래퍼 제거 */
                js = js.replace("/*<![CDATA[*/", "").replace("/*]]>*/", "");

                checked++;
                NodeRun r = checkJs(js);
                if (r.code() != 0) {
                    failures.add(page.getFileName() + " script#" + idx + " → " + firstLine(r.out()));
                }
            }
        }

        assertThat(checked).as("검사한 인라인 스크립트가 있어야 한다").isGreaterThan(0);
        assertThat(failures).as("파싱 실패한 인라인 스크립트: %s", failures).isEmpty();
    }

    private NodeRun checkJs(String js) {
        try {
            Path tmp = Files.createTempFile("lxp-inline-", ".js");
            Files.writeString(tmp, js, StandardCharsets.UTF_8);
            try {
                ProcessBuilder pb = new ProcessBuilder("node", "--check", tmp.toString());
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
                return new NodeRun(proc.exitValue(), out);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException | InterruptedException e) {
            throw new AssertionError("node --check 실행 실패: " + e.getMessage(), e);
        }
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int i = s.indexOf('\n');
        String head = i < 0 ? s : s.substring(0, i);
        return head.length() > 160 ? head.substring(0, 160) : head;
    }

    /** 자바 문자열을 JS 리터럴로 안전하게 감싼다. */
    private static String jsString(String v) {
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
