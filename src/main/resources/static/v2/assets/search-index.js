(function () {
  "use strict";

  var ITEMS = [
    {type:"course",title:"개인 맞춤형 예측 자동화 서비스",description:"Hadoop·Tableau·TensorFlow로 데이터를 분석하고 예측 서비스를 구현하는 KDT 과정",tags:["데이터 분석","Hadoop","TensorFlow","KDT"],url:"/v2/site/class/course.html?course=data"},
    {type:"course",title:"산업 자율주행·서비스 협동로봇",description:"라이다와 뎁스카메라를 활용해 영상 인식과 로봇 시스템을 설계합니다.",tags:["로봇 AI","자율주행","LiDAR"],url:"/v2/site/class/course.html?course=robot"},
    {type:"course",title:"빅데이터 분석 산업솔루션",description:"인공지능과 IoT 데이터를 연결해 산업 현장의 문제를 해결합니다.",tags:["AIoT","빅데이터","산업 AI"],url:"/v2/site/class/course.html?course=aiot"},
    {type:"course",title:"ESG 스마트 환경공정 제어",description:"공정 데이터를 수집하고 제조 시스템의 제어와 운영을 자동화합니다.",tags:["스마트팩토리","MES","PLC"],url:"/v2/site/class/course.html?course=factory"},
    {type:"course",title:"클라우드 기반 웹&앱 개발",description:"프론트엔드부터 서버·배포·보안까지 서비스 전 과정을 경험합니다.",tags:["웹 개발","클라우드","Docker","Java"],url:"/v2/site/class/course.html?course=cloud"},
    {type:"review",title:"무역회사 대신 선택한 IT, 데이터와 로봇",description:"데이터 분석과 로봇 AI 과정을 연달아 수강하고 AI·AIgent 개발자가 된 김유신 수료생 이야기",tags:["브레인크루","AI 개발","커리어 전환"],url:"/v2/site/class/review.html?id=20037"},
    {type:"review",title:"웹 개발을 넘어, 스스로 판단하는 AI로",description:"웹 개발 경험 위에 데이터와 모델 학습을 쌓아 AI 개발자로 방향을 전환한 수료생 인터뷰",tags:["유티정보","AI 개발","취업"],url:"/v2/site/class/reviews.html"},
    {type:"review",title:"하고 싶은 게 없던 제가, 가고 싶은 곳을 찾기까지",description:"기업 솔루션 실무와 IoT 과정에서 진로를 발견하고 취업한 수료생의 경험",tags:["IoT","기업 솔루션","취업"],url:"/v2/site/class/reviews.html"},
    {type:"case",title:"내부 규정 문서를 근거로 찾는 업무",description:"금융사 내부 규정과 업무 지침에서 근거 문장을 찾는 RAG PoC 기업교육 사례",tags:["금융","RAG","생성형 AI","문서 검색"],url:"/v2/site/biz/cases.html?case=finance"},
    {type:"case",title:"품질 검사 기록과 현장 보고서 자동화",description:"제조 현장의 품질 기록과 보고 흐름을 데이터·AI로 개선하는 프로젝트형 교육 사례",tags:["제조","품질","자동화"],url:"/v2/site/biz/cases.html?case=manufacturing"},
    {type:"case",title:"콘텐츠와 캠페인 업무 자동화",description:"브랜드 기준을 반영한 콘텐츠 기획과 성과 리포트 업무를 실습하는 기업교육 사례",tags:["마케팅","콘텐츠","업무 자동화"],url:"/v2/site/biz/cases.html?case=commerce"},
    {type:"notice",title:"2026 하반기 KDT 과정 모집 안내",description:"데이터·로봇 AI·AIoT·스마트팩토리·클라우드 과정의 일정과 지원 조건을 확인하세요.",tags:["모집","KDT","국비지원"],url:"/v2/site/class/index.html#all-courses"},
    {type:"notice",title:"취업캠퍼스 1:1 상담 안내",description:"과정 선택과 취업 준비, 숙식·통학 지원에 관해 현재 상황에 맞는 상담을 신청할 수 있습니다.",tags:["상담","취업","과정 추천"],url:"/v2/site/campus/counsel.html"},
    {type:"notice",title:"기업교육 도입 문의 안내",description:"조직의 업무와 목표를 진단하고 교육 과정과 AX 실행 방안을 함께 설계합니다.",tags:["기업교육","AX","도입 문의"],url:"/v2/site/biz/index.html#biz-contact"}
  ];

  var labels = {course:"과정",review:"수료생 후기",case:"기업교육 사례",notice:"공지·안내"};
  var form = document.querySelector("[data-integrated-search]");
  if (!form) return;
  var input = form.querySelector("input[name=q]");
  var list = document.querySelector("[data-search-results]");
  var empty = document.querySelector("[data-search-empty]");
  var total = document.querySelector("[data-result-count]");
  var tabs = Array.prototype.slice.call(document.querySelectorAll("[data-search-type]"));
  var activeType = "all";

  function normalized(value) { return String(value || "").toLocaleLowerCase("ko-KR").replace(/\s+/g, " ").trim(); }
  function escapeHtml(value) { return String(value).replace(/[&<>"']/g, function (char) { return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[char]; }); }
  function highlight(value, query) {
    if (!query) return escapeHtml(value);
    var safe = query.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    return escapeHtml(value).replace(new RegExp("(" + safe + ")", "ig"), "<mark>$1</mark>");
  }
  function matches(item, query) {
    if (!query) return true;
    return normalized([item.title,item.description].concat(item.tags).join(" ")).indexOf(query) >= 0;
  }
  function counts(query) {
    var filtered = ITEMS.filter(function (item) { return matches(item, query); });
    ["all","course","review","case","notice"].forEach(function (type) {
      var node = document.querySelector('[data-search-count="' + type + '"]');
      if (node) node.textContent = type === "all" ? filtered.length : filtered.filter(function (item) { return item.type === type; }).length;
    });
  }
  function render(pushState) {
    var query = normalized(input.value);
    var results = ITEMS.filter(function (item) { return matches(item, query) && (activeType === "all" || item.type === activeType); });
    counts(query);
    total.textContent = results.length;
    list.innerHTML = results.map(function (item) {
      return '<a class="search-result" href="' + item.url + '">' +
        '<span class="search-result__type">' + labels[item.type] + '</span>' +
        '<div><h2>' + highlight(item.title, query) + '</h2><p>' + highlight(item.description, query) + '</p>' +
        '<div class="search-result__meta">' + item.tags.map(function (tag) { return '<span>' + highlight(tag, query) + '</span>'; }).join("") + '</div></div>' +
        '<span class="search-result__arrow" aria-hidden="true">→</span></a>';
    }).join("");
    empty.hidden = results.length > 0;
    tabs.forEach(function (tab) { tab.setAttribute("aria-selected", String(tab.dataset.searchType === activeType)); });
    if (pushState && window.history && window.history.replaceState) {
      var params = new URLSearchParams();
      if (input.value.trim()) params.set("q", input.value.trim());
      if (activeType !== "all") params.set("type", activeType);
      history.replaceState(null, "", location.pathname + (params.toString() ? "?" + params.toString() : ""));
    }
  }

  form.addEventListener("submit", function (event) { event.preventDefault(); render(true); });
  input.addEventListener("input", function () { render(true); });
  tabs.forEach(function (tab) { tab.addEventListener("click", function () { activeType = tab.dataset.searchType; render(true); }); });
  document.querySelectorAll("[data-search-suggestion]").forEach(function (button) {
    button.addEventListener("click", function () { input.value = button.dataset.searchSuggestion; activeType = "all"; render(true); input.focus(); });
  });

  var params = new URLSearchParams(location.search);
  input.value = params.get("q") || "";
  activeType = ["course","review","case","notice"].indexOf(params.get("type")) >= 0 ? params.get("type") : "all";
  render(false);
})();
