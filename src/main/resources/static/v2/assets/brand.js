/* ============================================================
   brand.js — 브랜드 문자열 단일 출처

   브랜드명이 아직 확정이 아니다("내일의AI"도 바뀔 수 있음).
   그래서 화면 파일에 브랜드 문자열을 절대 직접 쓰지 않는다.
   전부 여기서 읽는다. 확정되면 이 파일만 고치면 전 화면이 따라온다.

   검증법: 아래 name 을 아무 문자열로 바꾸고 전 화면을 돌아본다.
           옛 이름이 남아 있으면 그 화면은 하드코딩된 것이다.
   ============================================================ */

/* LXP 로그인 진입점.
   '수강생 로그인'은 v2 프로토타입 로그인 화면이 아니라 실제 LXP 로그인으로 보낸다.
   주소는 v2 안의 "/v2/login" 이고(AuthController 가 LXP 로그인 템플릿을 그대로 렌더),
   화면 내용은 templates/01-login/login.html 이 그대로 담당한다(여기서 다시 그리지 않는다).
   화면 파일에 이 경로를 직접 쓰지 말고 BRAND.lxpLogin 을 읽는다.

   주의 — v2 화면은 두 곳에서 서빙된다:
     (1) 스프링 앱 (lms.samsungax.com, 로컬 8080) — /login 이 실제로 있다 → 상대경로
     (2) Cloudflare 정적 배포 (wrangler.jsonc) — 앱이 없어서 /login 은 404 → 운영 절대주소
   그래서 호스트를 보고 고른다. 앱이 서빙하는 곳 목록만 관리하면 된다. */
var LXP_ORIGIN = "https://lms.samsungax.com";   // 운영 LXP
var LXP_APP_HOSTS = ["lms.samsungax.com", "localhost", "127.0.0.1"];
/* LXP 화면 주소를 만든다. 화면 파일에는 실제 경로("/trainee/my-course")만 쓰고
   여기서 배포 위치에 맞게 상대/절대를 고른다. */
function lxpUrl(path) {
  var host = (typeof location !== "undefined" && location.hostname) || "";
  return LXP_APP_HOSTS.indexOf(host) !== -1 ? path : LXP_ORIGIN + path;
}
var LXP_LOGIN_HREF = lxpUrl("/v2/login");

window.BRAND = {
  /* --- 정체 --- */
  name:     "내일의AI",              // 국문 브랜드명 (미확정)
  nameEn:   "Tomorrow's AI",          // 영문
  short:    "내일의AI",               // 좁은 자리용
  tagline:  "공부하는 오늘이 취업하는 내일이 되도록",
  legalName: "(주)내일의AI",          // 사업자명 — 확정 필요
  domain:   "example.com",            // 최종 도메인 미정

  /* --- 연혁 --- */
  foundedYear: 1982,
  heritage: "1982 세종교육",

  /* --- 연락처 (확정 전 자리표시) --- */
  tel:   "1600-0000",
  email: "help@example.com",
  addr:  "주소 확정 예정",

  /* --- LXP 진입 --- */
  lxpLogin: LXP_LOGIN_HREF,

  /* --- 서비스 6종 (공통-001 통합 내비게이션) ---
     LXP 학습은 로그인으로 바로 보내지 않고 소개 페이지를 먼저 거친다.
     기숙사·수영센터는 "시설 소개" 하나로 합쳤다.
     current 는 각 화면에서 Shell.gnb({ service: "campus" }) 로 지정한다. */
  services: [
    { key: "home",    label: "통합 홈",       href: "/v2/index.html" },
    { key: "campus",  label: "취업캠퍼스",     href: "/v2/site/campus/index.html" },
    { key: "class",   label: "몰입클라쓰",     href: "/v2/site/class/index.html" },
    { key: "biz",     label: "비즈워크넥트",   href: "/v2/site/biz/index.html" },
    { key: "lxp",     label: "LXP 학습",       href: "/v2/site/lxp/index.html" },
    { key: "facility",label: "시설 소개",      href: "/v2/site/campus/facility.html" }
  ],

  /* --- 관리자 권한 3단계 --- */
  adminLevels: [
    { key: "L1", label: "최고관리자", desc: "전체 + 설정·권한·계정·감사로그" },
    { key: "L2", label: "운영관리자", desc: "과정·훈련생·출결·이수·공지·설문·승인" },
    { key: "L3", label: "강사",       desc: "담당 과정의 콘텐츠·채점·감독·튜터링" }
  ]
};

/* 화면에서 문자열을 심는 헬퍼.
   <span data-brand="name"></span> 형태로 쓰면 자동 치환된다. */
window.lxpUrl = lxpUrl;

window.applyBrand = function applyBrand(root) {
  var scope = root || document;
  scope.querySelectorAll("[data-brand]").forEach(function (el) {
    var key = el.getAttribute("data-brand");
    var val = window.BRAND[key];
    if (typeof val === "string" || typeof val === "number") el.textContent = val;
  });
  // <a data-brand-href="lxpLogin"> 형태로 링크 주소도 심는다.
  // HTML 에는 상대경로를 그대로 두어 JS 가 죽어도 앱에서는 동작하게 하고,
  // 정적 배포처럼 앱이 없는 곳에서만 절대주소로 바뀐다.
  scope.querySelectorAll("[data-brand-href]").forEach(function (el) {
    var val = window.BRAND[el.getAttribute("data-brand-href")];
    if (typeof val === "string") el.setAttribute("href", val);
  });
  // <a href="/trainee/my-course" data-lxp> — 실제 LXP 화면으로 가는 링크.
  // href 에 진짜 경로를 그대로 두고, 정적 배포에서만 절대주소로 바꾼다.
  scope.querySelectorAll("[data-lxp]").forEach(function (el) {
    el.setAttribute("href", window.lxpUrl(el.getAttribute("href")));
  });
  // <title> 안의 {brand} 치환
  if (document.title.indexOf("{brand}") !== -1) {
    document.title = document.title.replace(/\{brand\}/g, window.BRAND.name);
  }
};
