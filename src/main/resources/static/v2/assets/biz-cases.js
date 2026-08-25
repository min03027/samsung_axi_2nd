(function () {
  "use strict";
  var form = document.getElementById("biz-case-filters");
  var cards = Array.prototype.slice.call(document.querySelectorAll(".biz-case"));
  var count = document.getElementById("biz-case-count");
  var empty = document.getElementById("biz-case-empty");
  var detail = document.getElementById("case-detail");
  var detailContent = document.getElementById("case-detail-content");
  var cases = {
    manufacturing:{relation:"교육 수행 · 익명 공개",company:"제조 A사",title:"품질 검사 기록과 현장 보고서 자동화",meta:["제조","품질·생산 실무","16~24시간","데이터·자동화"],problem:"품질 검사 기록과 현장 보고 양식이 분리되어 반복 입력과 재정리가 필요한 업무를 교육 과제로 삼았습니다.",curriculum:["현장 데이터와 문서 흐름 점검","검사 항목과 보고 기준 구조화","반복 집계·보고 초안 실습","팀이 재사용할 수 있는 검토 기준 정리"],practice:"공개 가능한 예시 데이터로 검사 기록을 분류하고, 이상 신호를 설명하는 보고서 초안을 만드는 흐름을 실습합니다.",output:"품질 기록 정리 기준 · 현장 보고 템플릿 · 활용 가이드",follow:"실제 데이터 연결과 성과 수치 확인은 기업 보안·공개 범위 협의 후 별도로 설계합니다."},
    logistics:{relation:"교육 협의 · 익명 공개",company:"물류 B사",title:"운영 기록과 일일 보고 흐름 정리",meta:["물류","기획·운영 실무","8~16시간","생성형 AI·자동화"],problem:"현장별 운영 기록의 형식이 달라 일일 이슈와 조치 내용을 한 번에 파악하기 어려운 상황을 교육 과제로 정리했습니다.",curriculum:["운영 기록 유형 분류","보고 항목과 작성 기준 정의","일일 보고 초안 자동화 실습","사람이 확인해야 할 항목 구분"],practice:"가상의 현장 기록을 사용해 이슈·담당·조치·예정 업무를 구분하고 일일 보고 양식으로 변환합니다.",output:"운영 기록 분류표 · 일일 보고 템플릿 · 검토 체크리스트",follow:"실제 운영 시스템 연동은 포함하지 않으며, 협의 단계에서 데이터 형식과 보안 조건을 먼저 확인합니다."},
    finance:{relation:"PoC 교육 · 익명 공개",company:"금융 C사",title:"내부 규정 문서를 근거로 찾는 업무",meta:["금융","개발·업무혁신팀","24~40시간","RAG·생성형 AI"],problem:"내부 규정과 업무 지침에서 근거 문장을 찾고 답변을 검토하는 시간을 줄이는 PoC 교육 시나리오입니다.",curriculum:["문서 공개 범위와 보안 기준 설정","검색 단위와 메타데이터 설계","근거 기반 답변 PoC 구성","오답·권한·최신성 검토 기준 수립"],practice:"비식별 예시 규정으로 질문·검색·근거 표시·검토 과정을 구현하고 실패 사례까지 확인합니다.",output:"RAG PoC · 질문 평가표 · 운영·보안 체크리스트",follow:"실제 내부 문서와 시스템 적용은 보안 심사와 별도 개발 범위가 필요합니다."},
    medical:{relation:"직무교육 · 익명 공개",company:"의료 D재단",title:"의료 행정 문서의 반복 작업 줄이기",meta:["의료","행정 실무","8~16시간","생성형 AI"],problem:"개인정보를 제외한 반복 행정 문서의 초안 작성과 검토 절차를 표준화하는 교육 구성입니다.",curriculum:["민감정보와 입력 금지 기준","행정 문서 구조 분석","초안·요약·변환 실습","사람 중심의 최종 검토 절차"],practice:"비식별 예시 문서로 안내문·회의 요약·보고 초안을 만들고 위험 표현을 다시 검토합니다.",output:"안전 사용 기준 · 행정 문서 템플릿 · 검토 절차",follow:"환자정보·진료정보를 사용하는 실습은 포함하지 않으며 실제 적용 전 기관 보안 기준 확인이 필요합니다."},
    commerce:{relation:"직무교육 · 익명 공개",company:"커머스 E사",title:"브랜드 기준을 반영한 콘텐츠 제작",meta:["커머스","마케팅·콘텐츠팀","8~16시간","생성형 AI·자동화"],problem:"채널별 콘텐츠를 반복 제작하면서도 브랜드 표현과 검수 기준을 일관되게 유지해야 하는 업무를 다룹니다.",curriculum:["브랜드 표현 기준 구조화","채널별 카피 변형 실습","캠페인 콘텐츠 기획","성과 보고와 재사용 기준 정리"],practice:"가상 제품과 브랜드 기준을 사용해 채널별 카피를 만들고 금칙어·근거·톤을 검토합니다.",output:"브랜드 프롬프트 · 콘텐츠 검토표 · 캠페인 템플릿",follow:"실제 브랜드 데이터와 성과 수치는 공개 승인 후 과정에 반영할 수 있습니다."},
    public:{relation:"교육 수행 · 익명 공개",company:"공공 F기관",title:"행정 보고서와 자료 요약 실습",meta:["공공","행정 실무","4~8시간","생성형 AI"],problem:"여러 자료에서 핵심 내용을 분류하고 공공 문서 형식에 맞는 초안을 빠르게 준비하는 입문 실습입니다.",curriculum:["공공 업무의 AI 사용 기준","자료 분류와 핵심 내용 추출","행정 보고서 초안 실습","출처·근거·개인정보 검토"],practice:"공개 자료를 사용해 요약표와 보고서 초안을 만들고 근거 누락과 부정확한 표현을 점검합니다.",output:"자료 요약표 · 행정 보고 템플릿 · 안전 사용 체크리스트",follow:"기관별 보안·행정 기준과 실제 교육 인원은 공개 범위 확정 후 별도로 안내합니다."}
  };

  function applyFilters() {
    var filters = new FormData(form); var visible = 0;
    cards.forEach(function (card) {
      var matched = true;
      ["job","industry","size","time","tech","task"].forEach(function (key) {
        var value = filters.get(key);
        if (value && value !== "all" && card.dataset[key].split(" ").indexOf(value) === -1) matched = false;
      });
      card.hidden = !matched; if (matched) visible += 1;
    });
    count.textContent = "공개 사례 " + visible + "건"; empty.hidden = visible !== 0;
  }

  function detailRow(label, value) {
    return "<div><dt>" + label + "</dt><dd>" + value + "</dd></div>";
  }

  function openCase(id, updateUrl) {
    var item = cases[id]; if (!item) return;
    detailContent.innerHTML = "<header><span>" + item.relation + "</span><p>" + item.company + "</p><h2>" + item.title + "</h2><div class=\"biz-case-detail__meta\">" + item.meta.map(function (value) { return "<b>" + value + "</b>"; }).join("") + "</div></header><div class=\"biz-case-detail__body\"><dl>" + detailRow("업무 과제", item.problem) + detailRow("교육 구성", "<ol>" + item.curriculum.map(function (value) { return "<li>" + value + "</li>"; }).join("") + "</ol>") + detailRow("실습 방식", item.practice) + detailRow("예상 산출물", item.output) + detailRow("후속 범위", item.follow) + "</dl><aside><b>공개 기준</b><p>기업명과 세부 성과를 공개하지 않는 익명 사례입니다. 표기된 관계 유형을 공식 파트너십이나 성과 보장으로 해석하지 않습니다.</p><a class=\"btn btn--primary\" href=\"/v2/site/biz/index.html?case=" + id + "#biz-contact\">이 사례 기준으로 문의</a></aside></div>";
    detail.hidden = false;
    if (updateUrl) { var url = new URL(location.href); url.searchParams.set("case", id); history.pushState({caseId:id}, "", url); }
    detail.scrollIntoView({behavior:"smooth", block:"start"});
  }

  function closeCase() {
    detail.hidden = true; var url = new URL(location.href); url.searchParams.delete("case"); history.replaceState(null, "", url.pathname + url.search + "#case-list");
  }

  form.addEventListener("submit", function (event) { event.preventDefault(); applyFilters(); });
  form.addEventListener("reset", function () { window.setTimeout(applyFilters, 0); });
  cards.forEach(function (card) {
    card.addEventListener("click", function () { openCase(card.dataset.caseId, true); });
    card.addEventListener("keydown", function (event) { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); openCase(card.dataset.caseId, true); } });
  });
  document.querySelector("[data-close-case]").addEventListener("click", closeCase);
  window.addEventListener("popstate", function () { var id = new URLSearchParams(location.search).get("case"); if (id) openCase(id, false); else detail.hidden = true; });

  var params = new URLSearchParams(location.search); var initial = params.get("industry");
  if (initial && form.elements.industry) form.elements.industry.value = initial;
  applyFilters();
  if (params.get("case")) window.setTimeout(function () { openCase(params.get("case"), false); }, 0);
})();
