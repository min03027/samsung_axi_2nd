/* ============================================================
   shell.js — 공용 셸(GNB·푸터·사이드바·상단바) 렌더러

   화면 80개가 같은 머리·꼬리 마크업을 반복하지 않게 한다.
   각 화면은 본문만 쓰고 아래 한 줄로 셸을 붙인다.

     <script>Shell.site({ service: "class", nav: "catalog" })</script>
     <script>Shell.app({ role: "trainee", nav: "home", title: "학습 홈" })</script>

   경로는 전부 절대경로(/v2/...)라 어느 깊이의 파일에서도 동작한다.
   ============================================================ */

(function () {
  "use strict";

  var B = window.BRAND;

  function el(html) {
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
     공개사이트 메뉴 — 서비스별로 다르다
     --------------------------------------------------------- */
  var SITE_NAV = {
    campus: [
      { key: "about",   label: "브랜드 스토리", href: "/v2/site/campus/about.html" },
      { key: "outcome", label: "취업 성과",     href: "/v2/site/campus/outcome.html" },
      { key: "review",  label: "수료생 후기",   href: "/v2/site/campus/reviews.html" },
      { key: "support", label: "취업 지원",     href: "/v2/site/campus/support.html" },
      { key: "facility",label: "캠퍼스·기숙사", href: "/v2/site/campus/facility.html" }
    ],
    class: [
      { key: "catalog", label: "전체 과정",   href: "/v2/site/class/index.html" },
      { key: "detail",  label: "과정 상세",   href: "/v2/site/class/index.html#course-list-all" },
      { key: "apply",   label: "수강 신청",   href: "/v2/site/class/apply.html" }
    ],
    biz: [
      { key: "diagnosis", label: "조직 진단",   href: "/v2/site/biz/diagnosis.html" },
      { key: "flow",      label: "AX Flow",     href: "/v2/site/biz/flow.html" },
      { key: "programs",  label: "직무별 과정", href: "/v2/site/biz/programs.html" },
      { key: "cases",     label: "기업 사례",   href: "/v2/site/biz/cases.html" },
      { key: "contact",   label: "도입 문의",   href: "/v2/site/biz/contact.html" }
    ]
  };

  var SITE_HOME = {
    campus: "/v2/site/campus/index.html",
    class:  "/v2/site/class/index.html",
    biz:    "/v2/site/biz/index.html"
  };

  /* ---------------------------------------------------------
     앱 사이드바 — 수강생 / 관리자
     data-level 이 붙은 항목은 해당 등급 이상만 본다.
     --------------------------------------------------------- */
  var TRAINEE_NAV = [
    { group: "학습", items: [
      { key: "home",     label: "학습 홈",      href: "/v2/lxp/trainee/index.html" },
      { key: "mycourse", label: "내 과정",      href: "/v2/lxp/trainee/my-course.html" },
      { key: "contents", label: "학습 콘텐츠",  href: "/v2/lxp/trainee/contents.html" }
    ]},
    { group: "평가", items: [
      { key: "assignment", label: "과제",       href: "/v2/lxp/trainee/assignments.html", badge: "2" },
      { key: "exam",       label: "온라인 시험", href: "/v2/lxp/trainee/exams.html" },
      { key: "survey",     label: "설문",        href: "/v2/lxp/trainee/surveys.html" }
    ]},
    { group: "기록", items: [
      { key: "attendance", label: "출결 현황",   href: "/v2/lxp/trainee/attendance.html" },
      { key: "completion", label: "이수 관리",   href: "/v2/lxp/trainee/completion.html" }
    ]},
    { group: "성장", items: [
      { key: "roadmap",    label: "직무 로드맵",     href: "/v2/lxp/trainee/ai-roadmap.html" },
      { key: "curriculum", label: "커리큘럼 추천",   href: "/v2/lxp/trainee/ai-curriculum.html" },
      { key: "aiqna",      label: "AI 학습 도우미",  href: "/v2/lxp/trainee/ai-qna.html" }
    ]},
    { group: "소통", items: [
      { key: "notice",   label: "공지사항",     href: "/v2/lxp/trainee/notices.html" },
      { key: "qna",      label: "Q&A",          href: "/v2/lxp/trainee/qna.html" },
      { key: "tutoring", label: "튜터링",       href: "/v2/lxp/trainee/tutoring.html" },
      { key: "alarm",    label: "알림함",       href: "/v2/lxp/trainee/alarm.html", badge: "5" }
    ]}
  ];

  var ADMIN_NAV = [
    { group: "관제", items: [
      { key: "dash",    label: "운영 관제",   href: "/v2/admin/index.html" },
      { key: "queue",   label: "개입 큐",     href: "/v2/admin/queue.html", badge: "4" },
      { key: "dropout", label: "이탈 예측",   href: "/v2/admin/analytics-dropout.html", level: "L2" }
    ]},
    { group: "과정·콘텐츠", items: [
      { key: "courses",  label: "과정 관리",     href: "/v2/admin/courses.html", level: "L2" },
      { key: "schedule", label: "차시·일정",     href: "/v2/admin/schedule.html", level: "L2" },
      { key: "contents", label: "콘텐츠 라이브러리", href: "/v2/admin/contents.html" },
      { key: "versions", label: "콘텐츠 버전",   href: "/v2/admin/content-versions.html" }
    ]},
    { group: "사용자", items: [
      { key: "trainees",    label: "훈련생 관리", href: "/v2/admin/trainees.html" },
      { key: "instructors", label: "강사 관리",   href: "/v2/admin/instructors.html", level: "L2" },
      { key: "pending",     label: "가입 승인",   href: "/v2/admin/pending-users.html", level: "L2", badge: "7" },
      { key: "enrollments", label: "수강 승인",   href: "/v2/admin/enrollments.html", level: "L2" },
      { key: "classes",     label: "분반 관리",   href: "/v2/admin/classes.html", level: "L2" }
    ]},
    { group: "출결·이수", items: [
      { key: "attendance",  label: "출결부",      href: "/v2/admin/attendance.html" },
      { key: "completion",  label: "이수 관리",   href: "/v2/admin/completion.html", level: "L2" },
      { key: "certificate", label: "이수증 편집", href: "/v2/admin/certificate-editor.html", level: "L2" }
    ]},
    { group: "평가·감독", items: [
      { key: "exams",       label: "시험 관리",   href: "/v2/admin/exams.html" },
      { key: "questions",   label: "문항 은행",   href: "/v2/admin/questions.html" },
      { key: "grading",     label: "채점",        href: "/v2/admin/grading.html", badge: "18" },
      { key: "assignments", label: "과제 관리",   href: "/v2/admin/assignments.html" },
      { key: "proctor",     label: "시험 감독",   href: "/v2/admin/proctor.html" }
    ]},
    { group: "소통", items: [
      { key: "notices",   label: "공지사항",     href: "/v2/admin/notices.html" },
      { key: "notify",    label: "알림 발송",    href: "/v2/admin/notifications.html", level: "L2" },
      { key: "reminder",  label: "리마인더 설정", href: "/v2/admin/reminder.html", level: "L2" },
      { key: "surveys",   label: "설문 관리",    href: "/v2/admin/surveys.html" },
      { key: "qna",       label: "Q&A 응답",     href: "/v2/admin/qna.html" },
      { key: "tutoring",  label: "튜터링",       href: "/v2/admin/tutoring.html" }
    ]},
    { group: "공개사이트 CMS", items: [
      { key: "cms-courses",  label: "과정 마스터", href: "/v2/admin/cms-courses.html", level: "L2" },
      { key: "cms-reviews",  label: "후기 관리",   href: "/v2/admin/cms-reviews.html", level: "L2" },
      { key: "cms-partners", label: "기업·기관",   href: "/v2/admin/cms-partners.html", level: "L2" },
      { key: "cms-inquiry",  label: "문의 통합",   href: "/v2/admin/cms-inquiries.html", level: "L2" },
      { key: "cms-site",     label: "메뉴·배너",   href: "/v2/admin/cms-site.html", level: "L2" },
      { key: "cms-seo",      label: "검색·SEO",    href: "/v2/admin/cms-seo.html", level: "L1" }
    ]},
    { group: "설정", items: [
      { key: "admins",   label: "관리자 계정", href: "/v2/admin/admins.html", level: "L1" },
      { key: "roles",    label: "권한 관리",   href: "/v2/admin/roles.html", level: "L1" },
      { key: "settings", label: "시스템 설정", href: "/v2/admin/settings.html", level: "L1" },
      { key: "audit",    label: "감사 로그",   href: "/v2/admin/audit-log.html", level: "L1" }
    ]}
  ];

  /* =========================================================
     공개사이트 셸
     ========================================================= */
  function site(opts) {
    opts = opts || {};
    var svc = opts.service || "campus";
    var nav = SITE_NAV[svc] || [];
    var home = SITE_HOME[svc] || "/v2/site/index.html";

    var serviceLinks = B.services.map(function (s) {
      return '<a href="' + s.href + '"' + (s.key === svc ? ' aria-current="true"' : "") + ">" + esc(s.label) + "</a>";
    }).join("");

    var menuLinks = nav.map(function (m) {
      return '<a href="' + m.href + '"' + (m.key === opts.nav ? ' aria-current="page"' : "") + ">" + esc(m.label) + "</a>";
    }).join("");

    // 본문을 <main id="main"> 으로 감싼다 (건너뛰기 링크 대상)
    var siteMain = document.createElement("main");
    siteMain.id = "main";
    while (document.body.firstChild) siteMain.appendChild(document.body.firstChild);
    document.body.appendChild(siteMain);

    var header = el(
      '<a class="skip-link" href="#main">본문 바로가기</a>' +
      '<div class="servicebar"><div class="container servicebar__inner">' +
        '<strong class="servicebar__brand"><span data-brand="name"></span></strong>' +
        serviceLinks +
      '</div></div>' +
      '<header class="gnb"><div class="container gnb__inner">' +
        '<nav class="gnb__menu" aria-label="주요 메뉴">' + menuLinks + '</nav>' +
        '<div class="gnb__actions">' +
          '<a class="btn btn--ghost btn--sm" href="/v2/login/index.html">로그인</a>' +
          '<a class="btn btn--primary btn--sm" href="/v2/site/campus/counsel.html">상담 신청</a>' +
          '<button class="btn btn--ghost btn--sm gnb__burger" type="button" aria-label="메뉴 열기">☰</button>' +
        '</div>' +
      '</div></header>'
    );
    document.body.insertBefore(header, document.body.firstChild);

    // 전체 과정 화면에서는 스크롤 위치에 맞춰 '전체 과정'과 '과정 상세' 표시를 전환한다.
    if (svc === "class" && /\/site\/class\/index\.html$/.test(location.pathname)) {
      var courseSection = document.querySelector("#course-list-all");
      var scrollTicking = false;
      var syncCourseMenu = function () {
        var isDetailList = courseSection && courseSection.getBoundingClientRect().top <= 150;
        document.querySelectorAll(".gnb__menu a").forEach(function (link) {
          var href = link.getAttribute("href") || "";
          var active = isDetailList ? href.indexOf("#course-list-all") > -1 : href === "/v2/site/class/index.html";
          if (active) link.setAttribute("aria-current", "page");
          else link.removeAttribute("aria-current");
        });
      };
      var requestCourseMenuSync = function () {
        if (scrollTicking) return;
        scrollTicking = true;
        window.requestAnimationFrame(function () {
          syncCourseMenu();
          scrollTicking = false;
        });
      };
      window.addEventListener("hashchange", syncCourseMenu);
      window.addEventListener("scroll", requestCourseMenuSync, { passive: true });
      window.addEventListener("resize", requestCourseMenuSync);
      syncCourseMenu();
    }

    var footer = el(
      '<footer class="footer"><div class="container">' +
        '<div class="footer__grid">' +
          '<div>' +
            '<h4 style="font-size:var(--fs-body-lg)"><span data-brand="name"></span></h4>' +
            '<p class="small" style="color:var(--ink-500);margin-top:var(--sp-2)"><span data-brand="tagline"></span></p>' +
            '<p class="xsmall" style="color:var(--ink-600);margin-top:var(--sp-4)"><span data-brand="heritage"></span>에서 시작해 지금에 이릅니다.</p>' +
          '</div>' +
          '<div><h4>서비스</h4><ul class="stack-sm">' +
            B.services.map(function (s) { return '<li><a href="' + s.href + '">' + esc(s.label) + "</a></li>"; }).join("") +
          '</ul></div>' +
          '<div><h4>지원</h4><ul class="stack-sm">' +
            '<li><a href="/v2/site/campus/counsel.html">상담 신청</a></li>' +
            '<li><a href="/v2/site/biz/contact.html">기업교육 문의</a></li>' +
            '<li><a href="/v2/site/faq.html">자주 묻는 질문</a></li>' +
            '<li><a href="/v2/site/search.html">통합 검색</a></li>' +
          '</ul></div>' +
          '<div><h4>문의</h4><ul class="stack-sm">' +
            '<li><span data-brand="tel"></span></li>' +
            '<li><span data-brand="email"></span></li>' +
            '<li class="xsmall" style="color:var(--ink-600)"><span data-brand="addr"></span></li>' +
          '</ul></div>' +
        '</div>' +
        '<div class="footer__bottom">' +
          '<span>© <span data-brand="legalName"></span></span>' +
          '<span><a href="/v2/site/terms.html">이용약관</a> · <a href="/v2/site/privacy.html">개인정보처리방침</a></span>' +
        '</div>' +
      '</div></footer>'
    );
    document.body.appendChild(footer);

    window.applyBrand();
  }

  /* =========================================================
     앱 셸 (LXP 수강생 / 관리자)
     ========================================================= */
  function app(opts) {
    opts = opts || {};
    var isAdmin = opts.role === "admin";
    var groups = isAdmin ? ADMIN_NAV : TRAINEE_NAV;
    var homeHref = isAdmin ? "/v2/admin/index.html" : "/v2/lxp/trainee/index.html";

    var navHtml = groups.map(function (g) {
      var items = g.items.map(function (it) {
        return '<a class="sidebar__link" href="' + it.href + '"' +
          (it.key === opts.nav ? ' aria-current="page"' : "") +
          (it.level ? ' data-level="' + it.level + '"' : "") + ">" +
          "<span>" + esc(it.label) + "</span>" +
          (it.badge && !it.level ? '<span class="badge badge--brand">' + esc(it.badge) + "</span>" : "") +
          "</a>";
      }).join("");
      return '<div class="sidebar__group"><p class="sidebar__grouplabel">' + esc(g.group) + "</p>" + items + "</div>";
    }).join("");

    var sidebar = el(
      '<a class="skip-link" href="#main">본문 바로가기</a>' +
      '<aside class="sidebar">' +
        '<a class="sidebar__brand" href="' + homeHref + '" style="display:block">' +
          '<span data-brand="short"></span>' +
          '<small>' + (isAdmin ? "ADMIN CONSOLE" : "LEARNING") + "</small>" +
        "</a>" +
        '<nav class="sidebar__nav" aria-label="' + (isAdmin ? "관리 메뉴" : "학습 메뉴") + '">' + navHtml + "</nav>" +
        '<div class="sidebar__foot">' +
          '<a class="sidebar__link" href="/v2/' + (isAdmin ? "admin" : "lxp/trainee") + '/my-info.html"><span>내 정보</span></a>' +
          '<a class="sidebar__link" href="/v2/login/index.html"><span>로그아웃</span></a>' +
        "</div>" +
      "</aside>"
    );

    var topbar = el(
      '<header class="topbar">' +
        '<button class="btn btn--ghost btn--sm" type="button" data-nav-toggle aria-label="메뉴 열기">☰</button>' +
        '<h1 class="topbar__title">' + esc(opts.title || "") + "</h1>" +
        (isAdmin
          ? '<div class="topbar__search"><input class="input" type="search" placeholder="훈련생·과정·시험 통합검색" aria-label="통합검색"></div>'
          : '<div class="spacer"></div>') +
        '<div class="topbar__user">' +
          '<span class="avatar avatar--sm">' + (isAdmin ? "박" : "최") + "</span>" +
          "<span>" + (isAdmin ? "박은정" : "최하늘") + '</span>' +
          '<span class="badge">' + (isAdmin ? "최고관리자" : "산업 데이터 분석 4기") + "</span>" +
        "</div>" +
      "</header>"
    );

    // 본문을 .main 으로 감싼다
    var wrap = document.createElement("div");
    wrap.className = "app";
    var main = document.createElement("div");
    main.className = "main";
    main.id = "main";

    while (document.body.firstChild) main.appendChild(document.body.firstChild);
    main.insertBefore(topbar, main.firstChild);
    wrap.appendChild(sidebar);
    wrap.appendChild(main);
    document.body.appendChild(wrap);

    // 모바일 사이드바 토글
    var t = document.querySelector("[data-nav-toggle]");
    if (t) t.addEventListener("click", function () {
      wrap.dataset.nav = wrap.dataset.nav === "open" ? "" : "open";
    });

    if (isAdmin) viewerSwitch();
    window.applyBrand();
  }

  /* 권한 등급 전환 위젯 — 프로토타입 시연 전용.
     실서비스에서는 로그인 계정의 등급이 서버에서 내려온다. */
  function viewerSwitch() {
    document.body.dataset.viewer = document.body.dataset.viewer || "L1";
    var box = el(
      '<div class="viewer-switch no-print" role="group" aria-label="관리자 등급 전환(시연용)">' +
        B.adminLevels.map(function (l) {
          return '<button type="button" data-viewer-set="' + l.key + '" title="' + esc(l.desc) + '">' + esc(l.label) + "</button>";
        }).join("") +
      "</div>"
    );
    document.body.appendChild(box);
    function sync() {
      document.querySelectorAll("[data-viewer-set]").forEach(function (b) {
        b.setAttribute("aria-pressed", String(b.dataset.viewerSet === document.body.dataset.viewer));
      });
    }
    document.querySelectorAll("[data-viewer-set]").forEach(function (b) {
      b.addEventListener("click", function () {
        document.body.dataset.viewer = b.dataset.viewerSet;
        sync();
      });
    });
    sync();
  }

  window.Shell = { site: site, app: app, NAV: { trainee: TRAINEE_NAV, admin: ADMIN_NAV, site: SITE_NAV } };
})();
