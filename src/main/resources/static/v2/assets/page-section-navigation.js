/* ============================================================
   section-navigation.js — 공용 셸(GNB·푸터·사이드바·상단바) 렌더러

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
      { key: "about",   label: "브랜드 스토리", href: "/v2/site/campus/about.html", todo: true },
      { key: "outcome", label: "취업 성과",     href: "/v2/site/campus/outcome.html", todo: true },
      { key: "review",  label: "취업 후기",     href: "/v2/site/class/reviews.html" },
      { key: "support", label: "취업 지원",     href: "/v2/site/campus/support.html" },
      { key: "facility",label: "캠퍼스·기숙사", href: "/v2/site/campus/facility.html" }
    ],
    class: [
      { key: "catalog", label: "전체 과정",   href: "/v2/site/class/index.html" },
      { key: "detail",  label: "과정 상세",   href: "/v2/site/class/index.html#course-list-all" },
      { key: "career",  label: "취업클라쓰",  href: "/v2/site/class/reviews.html" },
      { key: "apply",   label: "수강 신청",   href: "/v2/site/class/apply.html" }
    ],
    biz: [
      { key: "diagnosis", label: "조직 진단",   href: "/v2/site/biz/diagnosis.html", todo: true },
      { key: "flow",      label: "AX Flow",     href: "/v2/site/biz/flow.html", todo: true },
      { key: "programs",  label: "직무별 과정", href: "/v2/site/biz/programs.html" },
      { key: "cases",     label: "기업 사례",   href: "/v2/site/biz/cases.html" },
      { key: "contact",   label: "도입 문의",   href: "/v2/site/biz/contact.html", todo: true }
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
      { key: "home",     label: "학습 홈",      href: "/trainee" },
      { key: "mycourse", label: "내 과정",      href: "/trainee/my-course" },
      { key: "contents", label: "학습 콘텐츠",  href: "/trainee/contents" }
    ]},
    { group: "평가", items: [
      { key: "assignment", label: "과제",       href: "/trainee/assignment", badge: "2" },
      { key: "exam",       label: "온라인 시험", href: "/trainee/exam" },
      { key: "survey",     label: "설문",        href: "/trainee/survey" }
    ]},
    { group: "기록", items: [
      { key: "attendance", label: "출결 현황",   href: "/trainee/attendance" },
      { key: "completion", label: "이수 관리",   href: "/trainee/completion-management" }
    ]},
    { group: "성장", items: [
      { key: "roadmap",    label: "직무 로드맵",     href: "/trainee/ai/roadmap" },
      { key: "curriculum", label: "커리큘럼 추천",   href: "/trainee/ai/curriculum" },
      { key: "aiqna",      label: "AI 학습 도우미",  href: "/trainee/ai/qna" }
    ]},
    { group: "소통", items: [
      { key: "notice",   label: "공지사항",     href: "/trainee/notice" },
      { key: "qna",      label: "Q&A",          href: "/trainee/qna" },
      { key: "tutoring", label: "튜터링",       href: "/trainee/qna/tutoring" },
      { key: "alarm",    label: "알림함",       href: "/trainee/alarm", badge: "5" }
    ]}
  ];

  var ADMIN_NAV = [
    { group: "관제", items: [
      { key: "dash",    label: "운영 관제",   href: "/admin" },
      { key: "queue",   label: "개입 큐",     href: "/admin/analytics/dropout", badge: "4" },
      { key: "dropout", label: "이탈 예측",   href: "/admin/analytics/dropout", level: "L2" }
    ]},
    { group: "과정·콘텐츠", items: [
      { key: "courses",  label: "과정 관리",     href: "/admin/courses", level: "L2" },
      { key: "schedule", label: "차시·일정",     href: "/admin/courses/schedule", level: "L2" },
      { key: "contents", label: "콘텐츠 라이브러리", href: "/instructor/contents" },
      { key: "versions", label: "콘텐츠 버전",   href: null /* 화면 미구현 */ }
    ]},
    { group: "사용자", items: [
      { key: "trainees",    label: "훈련생 관리", href: "/admin/users/trainees" },
      { key: "instructors", label: "강사 관리",   href: "/admin/users/instructors", level: "L2" },
      { key: "pending",     label: "가입 승인",   href: "/admin/users/pending", level: "L2", badge: "7" },
      { key: "enrollments", label: "수강 승인",   href: "/admin/enrollments/pending", level: "L2" },
      { key: "classes",     label: "분반 관리",   href: null /* 화면 미구현 */, level: "L2" }
    ]},
    { group: "출결·이수", items: [
      { key: "attendance",  label: "출결부",      href: "/admin/attendance" },
      { key: "completion",  label: "이수 관리",   href: "/admin/completion", level: "L2" },
      { key: "certificate", label: "이수증 편집", href: null /* 화면 미구현 */, level: "L2" }
    ]},
    { group: "평가·감독", items: [
      { key: "exams",       label: "시험 관리",   href: "/admin/evaluation/exams" },
      { key: "questions",   label: "문항 은행",   href: "/admin/evaluation/questions" },
      { key: "grading",     label: "채점",        href: "/admin/evaluation/grading", badge: "18" },
      { key: "assignments", label: "과제 관리",   href: "/admin/evaluation/assignments" },
      { key: "proctor",     label: "시험 감독",   href: "/admin/evaluation/monitoring" }
    ]},
    { group: "소통", items: [
      { key: "notices",   label: "공지사항",     href: "/admin/notice" },
      { key: "notify",    label: "알림 발송",    href: "/admin/notice/alarms", level: "L2" },
      { key: "reminder",  label: "리마인더 설정", href: "/admin/settings/reminder", level: "L2" },
      { key: "surveys",   label: "설문 관리",    href: "/admin/survey" },
      { key: "qna",       label: "Q&A 응답",     href: "/admin/support/qna" },
      { key: "tutoring",  label: "튜터링",       href: "/admin/support/tutoring" }
    ]},
    { group: "공개사이트 CMS", items: [
      { key: "cms-courses",  label: "과정 마스터", href: null /* 화면 미구현 */, level: "L2" },
      { key: "cms-reviews",  label: "후기 관리",   href: null /* 화면 미구현 */, level: "L2" },
      { key: "cms-partners", label: "기업·기관",   href: null /* 화면 미구현 */, level: "L2" },
      { key: "cms-inquiry",  label: "문의 통합",   href: null /* 화면 미구현 */, level: "L2" },
      { key: "cms-site",     label: "메뉴·배너",   href: null /* 화면 미구현 */, level: "L2" },
      { key: "cms-seo",      label: "검색·SEO",    href: null /* 화면 미구현 */, level: "L1" }
    ]},
    { group: "설정", items: [
      { key: "admins",   label: "관리자 계정", href: "/admin/admins", level: "L1" },
      { key: "roles",    label: "권한 관리",   href: null /* 화면 미구현 */, level: "L1" },
      { key: "settings", label: "시스템 설정", href: null /* 화면 미구현 */, level: "L1" },
      { key: "audit",    label: "감사 로그",   href: null /* 화면 미구현 */, level: "L1" }
    ]}
  ];

  /* =========================================================
     공개사이트 셸
     ========================================================= */
  /* ---------------------------------------------------------
     공개사이트 내비게이션 — 서비스 카테고리는 상단, 페이지 메뉴는 오른쪽 패널.
     로그인 버튼은 두지 않는다 — 로그인은 LXP 소개 페이지에서만 한다.
     --------------------------------------------------------- */
  function sideNav(opts) {
    opts = opts || {};
    var svc = opts.service || "";
    var sections = opts.sections || [];
    var usePublicJourney = sections.length && svc !== "home" &&
      !document.body.matches(".sales-course,.application-page,.recommend-page");

    var serviceLinks = B.services.map(function (s) {
      return '<a href="' + s.href + '"' + (s.key === svc ? ' aria-current="page"' : "") + ">" + esc(s.label) + "</a>";
    }).join("");

    var sectionBlock = "";
    if (sections.length) {
      sectionBlock =
        '<div>' + (opts.hideSectionLabel ? '' : '<p class="sidenav__label">이 페이지</p>') + '<div class="sidenav__list">' +
        sections.map(function (m) {
          if (m.todo) {  // 아직 화면이 없다 — 404 대신 안내 (CLAUDE.md 규칙 8)
            return '<a href="#" onclick="alert(\'준비 중인 기능입니다.\');return false">' + esc(m.label) + "</a>";
          }
          return '<a href="' + m.href + '"' + (m.key && m.key === opts.nav ? ' aria-current="page"' : "") + ">" + esc(m.label) + "</a>";
        }).join("") +
        "</div></div>";
    }

    var journeyBlock = "";
    if (usePublicJourney) {
      journeyBlock = '<nav class="course-journey public-journey" aria-label="현재 페이지 빠른 이동">' +
        sections.map(function (m, index) {
          var current = (m.key && m.key === opts.nav) || (!opts.nav && index === 0);
          var attrs = current ? ' aria-current="step"' : '';
          if (m.todo) {
            return '<a href="#"' + attrs + ' onclick="alert(\'준비 중인 기능입니다.\');return false"><i></i><span>' + esc(m.label) + '</span></a>';
          }
          return '<a href="' + m.href + '"' + attrs + '><i></i><span>' + esc(m.label) + '</span></a>';
        }).join("") + '</nav>';
    }

    var node = el(
      '<a class="skip-link" href="#main">본문 바로가기</a>' +
      '<nav class="home-categorybar" aria-label="서비스 카테고리"><div class="container home-categorybar__inner">' +
        '<strong><span data-brand="name"></span></strong><div>' + serviceLinks + '</div>' +
      '</div></nav>' +
      '<div class="sitebar">' +
        '<a class="sitebar__brand" href="/v2/index.html"><span data-brand="name"></span></a>' +
        '<button class="sitebar__toggle" type="button" aria-label="메뉴 열기" aria-expanded="false" aria-controls="sidenav"><i></i><i></i><i></i></button>' +
      '</div>' +
      '<div class="sidenav-scrim" data-sidenav-scrim hidden></div>' +
      '<nav class="sidenav" id="sidenav" aria-label="사이트 메뉴">' +
        '<div class="sidenav__head"><b data-brand="name"></b>' +
          '<button class="sidenav__close" type="button" aria-label="메뉴 닫기">&times;</button></div>' +
        sectionBlock +
        '<div class="sidenav__cta"><a class="btn btn--primary btn--lg btn--block" href="' + esc(opts.ctaHref || '/v2/site/class/index.html') + '">' + esc(opts.ctaLabel || '모집 과정 보기') + '</a></div>' +
      '</nav>' +
      journeyBlock
    );

    // 신청서처럼 입력에 집중해야 하는 화면은 상단 서비스 메뉴만 유지하고
    // 우측 과정 네비게이터와 햄버거 메뉴를 렌더링하지 않는다.
    if (opts.hideNavigator) {
      var navigatorSelectors = [".sitebar", ".sidenav-scrim", ".sidenav", ".public-journey"];
      navigatorSelectors.forEach(function (selector) {
        var target = node.querySelector(selector);
        if (target) target.remove();
      });
    }
    document.body.insertBefore(node, document.body.firstChild);

    if (opts.hideNavigator) return;

    var panel  = document.getElementById("sidenav");
    var scrim  = document.querySelector("[data-sidenav-scrim]");
    var toggle = document.querySelector(".sitebar__toggle");
    var closeBtn = panel.querySelector(".sidenav__close");

    function setOpen(open) {
      panel.dataset.open = open ? "true" : "";
      scrim.dataset.open = open ? "true" : "";
      scrim.hidden = !open;
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
      toggle.setAttribute("aria-label", open ? "메뉴 닫기" : "메뉴 열기");
      document.body.dataset.sidenav = open ? "open" : "";
      if (open) closeBtn.focus();
    }
    toggle.addEventListener("click", function () { setOpen(panel.dataset.open !== "true"); });
    closeBtn.addEventListener("click", function () { setOpen(false); toggle.focus(); });
    scrim.addEventListener("click", function () { setOpen(false); });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && panel.dataset.open === "true") { setOpen(false); toggle.focus(); }
    });
    // 같은 페이지 안 앵커로 이동하면 패널을 닫는다
    panel.addEventListener("click", function (e) {
      var a = e.target.closest("a");
      if (a && (a.getAttribute("href") || "").charAt(0) === "#") setOpen(false);
    });

    // 오른쪽 네비게이터가 현재 보고 있는 구간을 주황 표시로 따라간다.
    var localLinks = Array.prototype.slice.call(panel.querySelectorAll('.sidenav__list a[href^="#"]'));
    if (localLinks.length) {
      var syncSection = function () {
        var current = localLinks[0];
        localLinks.forEach(function (link) {
          var target = document.querySelector(link.getAttribute("href"));
          if (target && target.getBoundingClientRect().top <= window.innerHeight * 0.42) current = link;
        });
        localLinks.forEach(function (link) {
          if (link === current) link.setAttribute("aria-current", "location");
          else link.removeAttribute("aria-current");
        });
      };
      window.addEventListener("scroll", syncSection, { passive: true });
      window.addEventListener("resize", syncSection);
      syncSection();
    }

    var publicJourney = document.querySelector(".public-journey");
    if (publicJourney) {
      document.body.classList.add("has-public-journey");
      document.body.dataset.publicNavPhase = "intro";
      var publicLinks = Array.prototype.slice.call(publicJourney.querySelectorAll("a"));
      var firstSection = document.querySelector("#main section, main section");
      var syncPublicJourney = function () {
        document.body.dataset.publicNavPhase = firstSection && firstSection.getBoundingClientRect().bottom <= 72 ? "content" : "intro";
        var anchorLinks = publicLinks.filter(function (link) {
          return (link.getAttribute("href") || "").charAt(0) === "#" && link.getAttribute("href").length > 1;
        });
        if (!anchorLinks.length) return;
        var current = anchorLinks[0];
        anchorLinks.forEach(function (link) {
          var target = document.querySelector(link.getAttribute("href"));
          if (target && target.getBoundingClientRect().top <= window.innerHeight * 0.42) current = link;
        });
        if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 8) {
          current = anchorLinks[anchorLinks.length - 1];
        }
        anchorLinks.forEach(function (link) {
          link.setAttribute("aria-current", link === current ? "step" : "false");
        });
      };
      window.addEventListener("scroll", syncPublicJourney, { passive: true });
      window.addEventListener("resize", syncPublicJourney);
      syncPublicJourney();
    }
  }

  function site(opts) {
    opts = opts || {};
    var svc = opts.service || "campus";
    var nav = opts.sections || SITE_NAV[svc] || [];
    var home = SITE_HOME[svc] || "/v2/site/index.html";

    // 본문을 <main id="main"> 으로 감싼다 (건너뛰기 링크 대상)
    var siteMain = document.createElement("main");
    siteMain.id = "main";
    while (document.body.firstChild) siteMain.appendChild(document.body.firstChild);
    document.body.appendChild(siteMain);

    sideNav({
      service: svc,
      sections: nav,
      nav: opts.nav,
      hideNavigator: opts.hideNavigator,
      hideSectionLabel: opts.hideSectionLabel,
      ctaLabel: opts.ctaLabel,
      ctaHref: opts.ctaHref
    });

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
    var homeHref = window.lxpUrl(isAdmin ? "/admin" : "/trainee");

    var navHtml = groups.map(function (g) {
      var items = g.items.map(function (it) {
        var to = it.href
          ? ' href="' + (window.lxpUrl ? window.lxpUrl(it.href) : it.href) + '"'
          : ' href="#" onclick="alert(\'준비 중인 기능입니다.\');return false"';
        return '<a class="sidebar__link"' + to +
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
          '<a class="sidebar__link" href="' + window.lxpUrl(isAdmin ? "/admin/my-info" : "/trainee/my-info") + '"><span>내 정보</span></a>' +
          // 로그아웃하면 통합 홈으로 (실제 세션 종료는 LXP 화면의 POST /logout 이 담당한다.
          // 여기는 정적 프로토타입이라 세션이 없어 링크로 둔다.)
          '<a class="sidebar__link" href="/v2/index.html"><span>로그아웃</span></a>' +
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

  window.Shell = { site: site, app: app, nav: sideNav, NAV: { trainee: TRAINEE_NAV, admin: ADMIN_NAV, site: SITE_NAV } };
})();
