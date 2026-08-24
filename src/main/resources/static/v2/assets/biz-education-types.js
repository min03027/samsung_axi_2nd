(function () {
  "use strict";

  var tabs = Array.prototype.slice.call(document.querySelectorAll("[data-biz-type-tab]"));
  var panels = Array.prototype.slice.call(document.querySelectorAll("[data-biz-type-panel]"));
  if (!tabs.length || !panels.length) return;

  function selectType(key, moveFocus) {
    tabs.forEach(function (tab) {
      var active = tab.getAttribute("data-biz-type-tab") === key;
      tab.setAttribute("aria-selected", active ? "true" : "false");
      tab.tabIndex = active ? 0 : -1;
      if (active && moveFocus) tab.focus();
    });
    panels.forEach(function (panel) {
      panel.hidden = panel.getAttribute("data-biz-type-panel") !== key;
    });
  }

  tabs.forEach(function (tab, index) {
    tab.addEventListener("click", function () {
      selectType(tab.getAttribute("data-biz-type-tab"), false);
    });
    tab.addEventListener("keydown", function (event) {
      var next = index;
      if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
      else if (event.key === "ArrowLeft") next = (index - 1 + tabs.length) % tabs.length;
      else if (event.key === "Home") next = 0;
      else if (event.key === "End") next = tabs.length - 1;
      else return;
      event.preventDefault();
      selectType(tabs[next].getAttribute("data-biz-type-tab"), true);
    });
  });

  document.querySelectorAll("[data-education-type]").forEach(function (link) {
    link.addEventListener("click", function () {
      var issue = document.getElementById("issue");
      var interest = document.getElementById("interest");
      var educationType = link.getAttribute("data-education-type");
      if (issue) issue.value = educationType + "의 대상·일정·운영 방식을 문의합니다.";
      if (interest) {
        Array.prototype.some.call(interest.options, function (option) {
          if (option.value !== educationType) return false;
          interest.value = educationType;
          return true;
        });
      }
    });
  });

  var diagnosisForm = document.getElementById("biz-diagnosis-form");
  var diagnosisResult = document.getElementById("biz-diagnosis-result");
  if (diagnosisForm && diagnosisResult) {
    diagnosisForm.addEventListener("submit", function (event) {
      event.preventDefault();
      var values = new FormData(diagnosisForm);
      var task = values.get("task");
      var skill = values.get("skill");
      var data = values.get("data");
      var security = values.get("security");
      var course = skill === "low" ? "4~8시간 기초·직무 실습" : (data === "high" ? "24~40시간 프로젝트형 과정" : "8~16시간 직무 적용 과정");
      var priorities = [];
      if (data === "low") priorities.push("업무 자료와 데이터 정리 기준 수립");
      if (security !== "low") priorities.push("사용 가능 도구와 보안 가이드 확정");
      if (skill === "low") priorities.push("공통 AI 리터러시와 안전한 사용법 교육");
      if (!priorities.length) priorities.push("실제 업무 기반 PoC와 성과 지표 설정");
      var taskLabels = {document:"문서·보고 자동화",data:"데이터 분석·집계",content:"콘텐츠 제작",development:"개발·서비스 구축"};
      diagnosisResult.innerHTML = "<span>진단 결과</span><h4>" + taskLabels[task] + "부터 시작하는 것이 좋습니다.</h4><p><b>권장 구성</b> " + course + "</p><ul>" + priorities.map(function (item) { return "<li>" + item + "</li>"; }).join("") + "</ul><a class=\"btn btn--primary\" href=\"#biz-contact\" data-diagnosis-inquiry>이 결과로 상담하기</a>";
      diagnosisResult.hidden = false;
      diagnosisResult.scrollIntoView({behavior:"smooth", block:"nearest"});
      var inquiry = diagnosisResult.querySelector("[data-diagnosis-inquiry]");
      inquiry.addEventListener("click", function () {
        var issue = document.getElementById("issue");
        if (issue) issue.value = "AX 간편 진단 결과: " + taskLabels[task] + " / " + course + " / 우선 확인: " + priorities.join(", ");
      });
    });
  }
})();
