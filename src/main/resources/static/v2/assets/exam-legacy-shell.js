/* ============================================================
   exam-legacy-shell.js — 시험 데모 전용 공통 셸

   목적: 정적 데모 화면(/v2/.../exam*, /v2/admin/proctor*, execution-infra)이
   운영 중인 Samsung Academy LXP 와 같은 머리·꼬리를 갖게 한다.

   Thymeleaf fragment(fragments/trainee.html, fragments/admin.html)를 정적
   HTML 에서 쓸 수 없어서, 같은 마크업·같은 클래스명을 그대로 찍는다.
   클래스명이 같아야 /static/css/basic-form-trainee.css, common-style.css,
   sidebar-style.css 가 그대로 먹는다.

   ⚠ /v2/assets/shell.js 와 역할이 겹치지 않는다. 이 파일은 시험 데모 화면만
     쓰고, shell.js 는 나머지 /v2 화면이 쓴다. 서로 로드하지 않는다.

   사용법:
     ExamShell.trainee({ active: "test", title: "시험/과제 > 온라인 시험" });
     ExamShell.admin({ active: "eval-monitoring", title: "평가 관리 > 평가 모니터링" });
   ============================================================ */

(function () {
  "use strict";

  var LOGO = "/static/img/logo-white.png";

  function frag(html) {
    var t = document.createElement("template");
    t.innerHTML = html.trim();
    return t.content;
  }

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  /* ---------------------------------------------------------
     훈련생 GNB — fragments/trainee.html :: header(active) 와 동일 구조
     active 키: my-course | contents | test | ai | notices | attendance
     --------------------------------------------------------- */
  var TRAINEE_NAV = [
    { key: "my-course", label: "나의 과정", href: "/trainee/my-course", sub: [
      { label: "수강 과정 목록", href: "/trainee/my-course" },
      { label: "이어서 학습", href: "/trainee/learning" }
    ]},
    { key: "contents", label: "학습콘텐츠", href: "/trainee/contents" },
    { key: "test", label: "시험/과제", href: "/trainee/exam", sub: [
      { label: "온라인 시험", href: "/trainee/exam" },
      { label: "응시 데모(사전점검)", href: "/v2/lxp/trainee/exams.html" },
      { label: "과제", href: "/trainee/assignment" }
    ]},
    { key: "ai", label: "AI 학습지원", href: "/trainee/ai/qna", sub: [
      { label: "AI 학습 도우미", href: "/trainee/ai/qna" },
      { label: "튜터링", href: "/trainee/qna/tutoring" },
      { label: "맞춤 커리큘럼 추천", href: "/trainee/ai/curriculum" },
      { label: "직무 로드맵", href: "/trainee/ai/roadmap" }
    ]},
    { key: "notices", label: "공지사항", href: "/trainee/notice" },
    { key: "attendance", label: "출결/이수관리", href: "/trainee/survey", sub: [
      { label: "출결현황", href: "/trainee/attendance" },
      { label: "이수관리", href: "/trainee/completion-management" },
      { label: "설문조사", href: "/trainee/survey" }
    ]}
  ];

  var ICON_BELL =
    '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
    '<path d="M18.9342 14.98C18.6358 14.5033 18.4775 13.9524 18.4772 13.39V9.22602C18.4772 7.50822 17.7948 5.86077 16.5801 4.64609C15.3654 3.43142 13.718 2.74902 12.0002 2.74902C10.2824 2.74902 8.63492 3.43142 7.42024 4.64609C6.20557 5.86077 5.52317 7.50822 5.52317 9.22602V13.388C5.52325 13.9511 5.36488 14.5028 5.06617 14.98L3.97817 16.72C3.88357 16.8714 3.8312 17.0453 3.82652 17.2238C3.82183 17.4022 3.865 17.5787 3.95153 17.7348C4.03806 17.8909 4.16481 18.021 4.31861 18.1116C4.47242 18.2022 4.64767 18.25 4.82617 18.25H19.1742C19.3527 18.25 19.5279 18.2022 19.6817 18.1116C19.8355 18.021 19.9623 17.8909 20.0488 17.7348C20.1354 17.5787 20.1785 17.4022 20.1738 17.2238C20.1691 17.0453 20.1168 16.8714 20.0222 16.72L18.9342 14.98Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>' +
    '<path d="M10 21.25H14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>';

  var ICON_USER =
    '<svg width="30" height="30" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
    '<path d="M19.6524 19.405C20.2044 19.29 20.5344 18.712 20.2594 18.218C19.6534 17.131 18.6994 16.175 17.4794 15.447C15.9074 14.509 13.9804 14 12.0004 14C10.0204 14 8.09339 14.508 6.52139 15.447C5.30139 16.175 4.34739 17.131 3.74139 18.218C3.46639 18.712 3.79639 19.29 4.34839 19.405C9.39517 20.4569 14.6046 20.4569 19.6514 19.405" fill="currentColor"/>' +
    '<path d="M12 13C14.7614 13 17 10.7614 17 8C17 5.23858 14.7614 3 12 3C9.23858 3 7 5.23858 7 8C7 10.7614 9.23858 13 12 13Z" fill="currentColor"/></svg>';

  /* 로그아웃 아이콘은 쓰지 않는다.
     이 화면들은 정적 HTML 이라 Thymeleaf 가 CSRF 히든필드를 넣어 주지 못하고,
     SecurityConfig 는 /logout 을 CSRF 예외로 두지 않는다(예외는 /h2-console/** 뿐).
     따라서 POST /logout 폼은 눌러도 403 이 난다 — 동작하지 않는 버튼을 두는 대신
     실제로 되는 일("홈으로 이동")만 남긴다. 백엔드는 건드리지 않는다. */
  function iconHome(size) {
    return '<svg xmlns="http://www.w3.org/2000/svg" height="' + size + 'px" width="' + size + 'px" ' +
           'viewBox="0 -960 960 960" fill="currentColor" aria-hidden="true" focusable="false">' +
           '<path d="M240-200h120v-240h240v240h120v-360L480-740 240-560v360Zm-80 80v-480l320-240 320 240v480H520v-240h-80v240H160Z"/></svg>';
  }

  function trainee(opts) {
    opts = opts || {};
    var active = opts.active || "test";

    var menu = TRAINEE_NAV.map(function (m) {
      var sub = m.sub
        ? "<ul>" + m.sub.map(function (s) {
            return '<li><a href="' + s.href + '">' + esc(s.label) + "</a></li>";
          }).join("") + "</ul>"
        : "";
      return '<li' + (m.key === active ? ' class="active_menu"' : "") + ">" +
               '<span><a href="' + m.href + '">' + esc(m.label) + "</a></span>" + sub +
             "</li>";
    }).join("");

    var header = frag(
      '<header>' +
        '<div class="header-logo">' +
          '<a href="/trainee"><img src="' + LOGO + '" alt="Samsung Academy LXP" style="width:130px;cursor:pointer;"></a>' +
        "</div>" +
        '<nav class="gnb">' +
          "<ul>" + menu + "</ul>" +
          '<div class="icon-container">' +
            '<div class="icon-box"><a href="/trainee/alarm" title="알림함">' + ICON_BELL + "</a></div>" +
            '<div class="icon-box"><a href="/trainee/my-info" title="내 정보">' + ICON_USER + "</a></div>" +
            '<div class="icon-box">' +
              '<a href="/trainee" title="훈련생 홈으로" aria-label="훈련생 홈으로">' + iconHome(27) + "</a>" +
            "</div>" +
          "</div>" +
        "</nav>" +
      "</header>"
    );
    document.body.insertBefore(header, document.body.firstChild);

    /* 본문 상단 경로 제목 — .main-content-top > .main-wrap > h2 */
    if (opts.title) {
      var main = document.querySelector("main");
      if (main) {
        var top = frag(
          '<section class="main-content-top"><div class="main-wrap"><h2>' + esc(opts.title) + "</h2></div></section>"
        );
        main.insertBefore(top, main.firstChild);
      }
    }

    footer();
  }

  function footer() {
    var f = frag(
      '<footer class="simple-footer">' +
        '<div class="footer-inner">' +
          '<span class="footer-left">© 2026 Samsung Academy LXP</span>' +
          '<span class="footer-right">' +
            '<a href="/terms" class="footer-link">이용약관</a>' +
            '<span class="footer-dot">·</span>' +
            '<a href="/privacy" class="footer-link">개인정보처리방침</a>' +
          "</span>" +
        "</div>" +
      "</footer>"
    );
    document.body.appendChild(f);
  }

  /* ---------------------------------------------------------
     관리자 셸 — fragments/admin.html :: header(title) + sidebar(active)
     --------------------------------------------------------- */
  var ADMIN_NAV = [
    { key: "dashboard", label: "대시보드", href: "/admin", icon: "01-dashboard.svg", w: 26 },
    { key: "analytics-dropout", label: "이탈 예측", href: "/admin/analytics/dropout", icon: "08-analytics.svg", w: 26 },
    { key: "user", label: "사용자 관리", href: "/admin/users/trainees", icon: "02-user.svg", w: 28, sub: [
      { key: "user-approval", label: "가입 승인", href: "/admin/users/pending" },
      { key: "user-trainee", label: "훈련생 관리", href: "/admin/users/trainees" },
      { key: "user-instructor", label: "강사 관리", href: "/admin/users/instructors" },
      { key: "user-admin", label: "관리자 관리", href: "/admin/admins" }
    ]},
    { key: "contents", label: "콘텐츠 관리", href: "/instructor/contents", icon: "03-description.svg", w: 26, sub: [
      { key: "contents", label: "학습 콘텐츠(VOD/문서)", href: "/instructor/contents" },
      { key: "contents-qbank", label: "문제은행", href: "/admin/evaluation/questions" }
    ]},
    { key: "courses", label: "과정/운영 관리", href: "/admin/courses", icon: "03-course.svg", w: 25, sub: [
      { key: "courses", label: "과정 관리", href: "/admin/courses" },
      { key: "enrollment-approval", label: "수강신청 승인", href: "/admin/enrollments/pending" },
      { key: "schedule", label: "일정 관리", href: "/admin/courses/schedule" }
    ]},
    { key: "eval", label: "평가 관리", href: "/admin/evaluation/assignments", icon: "04-evaluation.svg", w: 30, sub: [
      { key: "eval-exam", label: "시험 관리", href: "/admin/evaluation/exams" },
      { key: "eval-assignment", label: "과제 채점", href: "/admin/evaluation/assignments" },
      { key: "eval-grading", label: "시험 채점", href: "/admin/evaluation/grading" },
      { key: "eval-monitoring", label: "평가 모니터링", href: "/admin/evaluation/monitoring" },
      { key: "eval-proctor-demo", label: "실시간 감독(데모)", href: "/v2/admin/proctor.html" },
      { key: "eval-review-demo", label: "사후 검토(데모)", href: "/v2/admin/proctor-review.html" },
      { key: "eval-infra-demo", label: "실행환경(데모)", href: "/v2/admin/execution-infra.html" }
    ]},
    { key: "att", label: "출결/이수 관리", href: "/admin/attendance", icon: "05-attendance.svg", w: 28, sub: [
      { key: "att-status", label: "출결현황", href: "/admin/attendance" },
      { key: "att-graduate", label: "이수 관리", href: "/admin/completion" },
      { key: "att-survey", label: "설문조사 관리", href: "/admin/survey" }
    ]},
    { key: "support", label: "학습 지원", href: "/admin/support/tutoring", icon: "06-support.svg", w: 25, sub: [
      { key: "support-tutoring", label: "튜터링/Q&A", href: "/admin/support/tutoring" },
      { key: "support-response", label: "응답현황", href: "/admin/support/response" }
    ]},
    { key: "notice", label: "공지/알림", href: "/admin/notice", icon: "bell.svg", w: 26, white: true, sub: [
      { key: "notice", label: "공지 관리", href: "/admin/notice" },
      { key: "alarm", label: "알림 관리", href: "/admin/notice/alarms" },
      { key: "reminder", label: "리마인드 설정", href: "/admin/settings/reminder" }
    ]}
  ];

  var AMBLEM = "/static/img/amblem-white.png";
  /* ⚠ 이 768 은 exam.css 의 @media (max-width: 768px) 와 반드시 같아야 한다.
     어긋나면 경계 픽셀에서 사이드바 폭과 헤더 여백이 따로 논다. */
  var NARROW = "(max-width: 768px)";
  var SIDEBAR_ID = "examShellSidebar";     /* toggle 의 aria-controls 대상 */
  /* 뒤 공백을 두지 않는다 — .sidebar .logout 이 flex + gap:4px 라 간격은 CSS 가 준다 */
  var HOME_LABEL = "관리자 홈";

  function savedCollapsed() {
    try { return localStorage.getItem("sidebarCollapsed") === "true"; } catch (err) { return false; }
  }

  /* 처음 그릴 때부터 접힘 여부를 정한다.
     그려 놓고 나중에 img.src 를 바꾸면 로고 요청이 진행 중에 취소돼(ERR_ABORTED)
     404 로 잡히고 요청도 한 번 낭비된다. */
  function wantCollapsed() {
    return window.matchMedia(NARROW).matches || savedCollapsed();
  }

  function admin(opts) {
    opts = opts || {};
    var active = opts.active || "eval-monitoring";
    var collapsed = wantCollapsed();

    /* 상단 헤더 */
    var header = frag(
      '<header class="header">' +
        "<h1>" + esc(opts.title || "") + "</h1>" +
        '<div class="icon-container">' +
          '<div class="icon-box" onclick="location.href=\'/admin/notice/alarms\'" title="알림 관리">' +
            '<img src="/static/icons/bell.svg" alt="알림" style="width:28px;"></div>' +
          '<div class="icon-box" onclick="location.href=\'/admin/my-info\'" title="내 정보">' +
            '<img src="/static/icons/user.svg" alt="내 정보" style="width:30px;"></div>' +
          '<div class="icon-box" onclick="location.href=\'/admin/settings/reminder\'" title="리마인드 설정">' +
            '<img src="/static/icons/setting.svg" alt="설정" style="width:25px;"></div>' +
        "</div>" +
      "</header>"
    );

    /* 사이드바 */
    var items = ADMIN_NAV.map(function (m) {
      var groupActive = active === m.key || (m.sub && m.sub.some(function (s) { return s.key === active; }));
      var sub = m.sub
        ? "<ul>" + m.sub.map(function (s) {
            return '<li class="submenu' + (s.key === active ? " active_menu" : "") +
                   '" data-href="' + s.href + '">' + esc(s.label) + "</li>";
          }).join("") + "</ul>"
        : "";
      return '<li data-page="' + m.href + '"' + (m.white ? ' class="white-icon"' : "") +
             (groupActive ? ' data-open="1"' : "") + ">" +
               '<img src="/static/icons/' + m.icon + '" alt="" aria-hidden="true" style="width:' + m.w +
               'px;margin-right:8px;vertical-align:middle;padding-bottom:4px;">' +
               '<span class="menu-text' + (groupActive ? " active_menu" : "") + '">' + esc(m.label) + "</span>" +
               sub +
             "</li>";
    }).join("");

    var sidebar = frag(
      '<aside id="' + SIDEBAR_ID + '" class="sidebar' + (collapsed ? " collapsed" : "") + '">' +
        '<div class="logo-box"><a href="/admin"><img id="sidebarLogo" src="' + (collapsed ? AMBLEM : LOGO) +
          '" alt="Samsung Academy LXP" style="width:' + (collapsed ? 30 : 130) + 'px;"></a></div>' +
        '<nav class="menu" aria-label="관리 메뉴"><ul>' + items + "</ul></nav>" +
        /* .logout 은 sidebar-style.css 의 "맨 아래 한 줄" 배치를 그대로 쓰기 위한 것이고,
           .shell-home 이 의미상 이름이다. 이름과 동작이 어긋나지 않도록 라벨도 홈 이동으로 적는다.

           라벨은 접힘 여부와 상관없이 <span> 으로 "항상" 만들고 hidden 으로만 감춘다.
           접힘 상태에서 라벨을 아예 안 만들면 childNodes[0] 이 <svg> 가 되어,
           나중에 그 자리에 textContent 를 쓰는 순간 아이콘 <path> 가 통째로 지워진다.
           (실제로 그렇게 깨졌다 — 모바일 초기 접힘과 데스크톱 저장 접힘에서 재현) */
        '<a class="logout shell-home" href="/admin" aria-label="관리자 홈으로">' +
          '<span class="shell-home-label"' + (collapsed ? " hidden" : "") + ">" + HOME_LABEL + "</span>" +
          iconHome(20) +
        "</a>" +
      "</aside>" +
      '<button class="toggle-btn' + (collapsed ? " collapsed" : "") + '" type="button"' +
        ' aria-label="관리 메뉴 접기/펼치기" aria-controls="' + SIDEBAR_ID + '"' +
        ' aria-expanded="' + String(!collapsed) + '">' + (collapsed ? "▶" : "◀") + "</button>" +
      /* 좁은 화면 drawer 뒤를 덮는 판 — 클릭하면 닫힌다. 장식이라 초점 대상이 아니다. */
      '<div class="shell-backdrop" hidden aria-hidden="true"></div>'
    );

    if (collapsed) document.body.classList.add("sidebar-collapsed");

    document.body.insertBefore(sidebar, document.body.firstChild);
    document.body.insertBefore(header, document.body.firstChild);

    bindAdminNav();
  }

  /* 사이드바 동작 — sidebar-function.js 의 toggleSidebar/handleMenuClick 과 같은 규칙.
     정적 데모라 원본 파일을 로드하지 않고 필요한 부분만 여기서 구현한다. */
  function bindAdminNav() {
    var body = document.body;
    var aside = document.getElementById(SIDEBAR_ID);
    var toggle = document.querySelector(".toggle-btn");
    var logo = document.getElementById("sidebarLogo");
    var backdrop = document.querySelector(".shell-backdrop");

    /* 활성 그룹의 서브메뉴는 처음부터 열어 둔다 */
    aside.querySelectorAll('li[data-open="1"]').forEach(function (li) { li.classList.add("open"); });

    aside.querySelectorAll(".menu > ul > li").forEach(function (li) {
      li.addEventListener("click", function (e) {
        if (e.target.closest(".submenu")) return;              /* 서브 항목은 아래 핸들러가 처리 */
        var sub = li.querySelector("ul");
        /* 기준은 body 가 아니라 사이드바 자신의 상태다.
           좁은 화면 drawer 는 body 를 접힘으로 둔 채 사이드바만 펼치므로,
           body 를 보면 펼쳐진 drawer 에서도 서브메뉴가 안 열리고 바로 이동해 버린다. */
        if (aside.classList.contains("collapsed") || !sub) {
          location.href = li.dataset.page;
          return;
        }
        aside.querySelectorAll(".menu > ul > li.open").forEach(function (o) {
          if (o !== li) o.classList.remove("open");
        });
        li.classList.toggle("open");
      });
    });

    aside.querySelectorAll(".submenu").forEach(function (s) {
      s.addEventListener("click", function (e) {
        e.stopPropagation();
        location.href = s.dataset.href;
      });
    });

    /* ── 접힘 / drawer ────────────────────────────────────────────
       운영 common-style.css 에는 모바일 분기가 없어 240px 사이드바가 본문을 다 먹는다.
       그래서 768px 이하에서는 두 가지를 분리한다.

         본문 오프셋(body.sidebar-collapsed) : 항상 60px 레일에 고정 — 본문 폭은 안 변한다
         사이드바 겉모습(aside.collapsed)     : 펼치면 240px 로 본문 "위에" 겹친다(position:fixed)

       둘을 같이 토글하던 기존 방식은 390px 에서 펼쳤을 때 본문을 150px 로 밀어 버렸다.
       새 컴포넌트를 만들지 않고 운영이 이미 가진 두 클래스를 따로 쓰는 것이 요점이다. */
    var narrow = window.matchMedia(NARROW);
    var drawerOpen = false;   /* 좁은 화면 전용 — 데스크톱 취향(localStorage)과 섞지 않는다 */

    /* 사이드바 겉모습만 칠한다. 본문 오프셋은 건드리지 않는다. */
    function paintSidebar(collapsed) {
      aside.classList.toggle("collapsed", collapsed);
      toggle.classList.toggle("collapsed", collapsed);
      toggle.textContent = collapsed ? "▶" : "◀";
      if (logo) {
        var want = collapsed ? AMBLEM : LOGO;
        if (logo.getAttribute("src") !== want) logo.src = want;   /* 같은 값이면 재요청하지 않는다 */
        logo.style.width = collapsed ? "30px" : "130px";
      }
      /* 라벨 요소를 이름으로 찾아 보이기/숨기기만 한다.
         childNodes[0] 같은 위치 기반 접근은 쓰지 않는다 — 접힘으로 시작하면
         그 자리가 <svg> 라서 아이콘을 지워 버린다.
         SVG 와 그 안의 <path> 는 접기/펼치기에서 절대 건드리지 않는다. */
      var homeLabel = aside.querySelector(".shell-home-label");
      if (homeLabel) homeLabel.hidden = collapsed;
    }

    function render() {
      var mobile = narrow.matches;
      var collapsed = mobile ? !drawerOpen : savedCollapsed();

      body.classList.toggle("sidebar-collapsed", mobile ? true : collapsed);
      paintSidebar(collapsed);

      if (backdrop) backdrop.hidden = !(mobile && drawerOpen);
      toggle.setAttribute("aria-expanded", String(!collapsed));
    }

    function closeDrawer() {
      if (!drawerOpen) return false;
      drawerOpen = false;
      render();
      return true;
    }

    toggle.addEventListener("click", function () {
      if (narrow.matches) {
        drawerOpen = !drawerOpen;
      } else {
        try { localStorage.setItem("sidebarCollapsed", String(!savedCollapsed())); } catch (err) { /* 무시 */ }
      }
      render();
    });

    if (backdrop) {
      backdrop.addEventListener("click", function () {
        if (closeDrawer()) toggle.focus();
      });
    }

    document.addEventListener("keydown", function (e) {
      if (e.key !== "Escape") return;
      /* 모달이 열려 있으면 Escape 의 주인은 모달이다 (ExamDemo.openModal) */
      if (document.querySelector(".modal-backdrop.open")) return;
      if (closeDrawer()) toggle.focus();
    });

    narrow.addEventListener("change", function () {
      drawerOpen = false;    /* 폭이 바뀌면 drawer 는 닫고 그 화면의 규칙을 따른다 */
      render();
    });

    render();
  }

  window.ExamShell = { trainee: trainee, admin: admin, footer: footer };
})();
