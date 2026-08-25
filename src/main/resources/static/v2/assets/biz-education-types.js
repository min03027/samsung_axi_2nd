(function () {
  "use strict";

  var DRAFT_KEY = "tomorrow-ai-biz-contact-draft-v1";
  var tabs = Array.prototype.slice.call(document.querySelectorAll("[data-biz-type-tab]"));
  var panels = Array.prototype.slice.call(document.querySelectorAll("[data-biz-type-panel]"));

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
    tab.addEventListener("click", function () { selectType(tab.getAttribute("data-biz-type-tab"), false); });
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

  var contactForm = document.getElementById("biz-contact-form");
  var review = document.getElementById("biz-contact-review");
  var complete = document.getElementById("biz-contact-complete");
  var summary = document.getElementById("biz-contact-summary");
  var draftStatus = document.getElementById("biz-draft-status");
  var currentDiagnosis = null;
  var draftAttachmentName = "";
  var saveTimer = 0;

  function readStoredDraft() {
    try { return JSON.parse(localStorage.getItem(DRAFT_KEY) || "null"); }
    catch (_) { return null; }
  }

  function formValue(id) {
    var element = document.getElementById(id);
    return element ? String(element.value || "").trim() : "";
  }

  function collectContact() {
    var attachment = document.getElementById("attachment");
    return {
      company: formValue("co"), name: formValue("nm"), department: formValue("dep"), count: formValue("cnt"),
      phone: formValue("tel2"), timing: formValue("when"), interest: formValue("interest"),
      contactTime: formValue("contact-time"), issue: formValue("issue"),
      attachmentName: attachment && attachment.files && attachment.files[0] ? attachment.files[0].name : draftAttachmentName,
      diagnosis: currentDiagnosis
    };
  }

  function updateDraftStatus(message) {
    if (!draftStatus) return;
    draftStatus.hidden = false;
    draftStatus.querySelector("span").textContent = message;
  }

  function saveDraft(stage, notify) {
    if (!contactForm) return null;
    var stored = readStoredDraft() || {};
    var draft = {data:collectContact(), stage:stage || stored.stage || "editing", reference:stored.reference || "", updatedAt:new Date().toISOString()};
    try {
      localStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
      if (notify) updateDraftStatus("이 브라우저에 임시 저장했습니다. 개인정보 동의와 첨부파일은 저장하지 않습니다.");
    } catch (_) { updateDraftStatus("브라우저 저장 공간을 사용할 수 없어 임시저장하지 못했습니다."); }
    return draft;
  }

  function setField(id, value) {
    var element = document.getElementById(id);
    if (element && value !== undefined && value !== null) element.value = value;
  }

  function restoreDraft() {
    var draft = readStoredDraft();
    if (!draft || !draft.data) return;
    setField("co", draft.data.company); setField("nm", draft.data.name); setField("dep", draft.data.department);
    setField("cnt", draft.data.count); setField("tel2", draft.data.phone); setField("when", draft.data.timing);
    setField("interest", draft.data.interest); setField("contact-time", draft.data.contactTime); setField("issue", draft.data.issue);
    currentDiagnosis = draft.data.diagnosis || null;
    draftAttachmentName = draft.data.attachmentName || "";
    var time = draft.updatedAt ? new Date(draft.updatedAt).toLocaleString("ko-KR") : "이전 방문";
    updateDraftStatus(time + "에 저장한 작성 내용을 불러왔습니다. 첨부파일은 다시 선택해 주세요.");
    if (currentDiagnosis) {
      var restoredResult = document.getElementById("biz-diagnosis-result");
      restoredResult.innerHTML = "<span>저장된 진단 결과</span><h4>" + currentDiagnosis.task + "부터 시작하는 것이 좋습니다.</h4><p><b>권장 구성</b> " + currentDiagnosis.course + "</p><ul>" + currentDiagnosis.priorities.map(function (item) { return "<li>" + item + "</li>"; }).join("") + "</ul><a class=\"btn btn--primary\" href=\"#biz-contact\" data-diagnosis-inquiry>이 결과를 문의에 담기</a>";
      restoredResult.hidden = false;
      restoredResult.querySelector("[data-diagnosis-inquiry]").addEventListener("click", function () {
        setField("issue", "AX 간편 진단 결과: " + currentDiagnosis.task + " / " + currentDiagnosis.course + " / 우선 확인: " + currentDiagnosis.priorities.join(", "));
        saveDraft("editing", true);
      });
    }
    if (draft.stage === "ready" && draft.reference) {
      document.getElementById("biz-contact-reference").textContent = "임시 작성번호 " + draft.reference + " · 실제 접수번호가 아닙니다";
      contactForm.hidden = true; complete.hidden = false;
    }
  }

  function addSummaryItem(label, value) {
    var dt = document.createElement("dt"); var dd = document.createElement("dd");
    dt.textContent = label; dd.textContent = value || "미입력"; summary.appendChild(dt); summary.appendChild(dd);
  }

  function summaryText(data) {
    var lines = ["[비즈워크래프트 기업교육 상담 요약]", "회사·기관: " + (data.company || "미입력"),
      "담당자: " + (data.name || "미입력"), "부서·직급: " + (data.department || "미입력"),
      "예상 인원: " + (data.count ? data.count + "명" : "미입력"), "연락처: " + (data.phone || "미입력"),
      "희망 시기: " + (data.timing || "미입력"), "관심 과정: " + (data.interest || "미입력"),
      "연락 가능 시간: " + (data.contactTime || "미입력"), "해결 과제: " + (data.issue || "미입력")];
    if (data.diagnosis) lines.push("AX 진단: " + data.diagnosis.task + " / " + data.diagnosis.course + " / " + data.diagnosis.priorities.join(", "));
    if (data.attachmentName) lines.push("참고 자료: " + data.attachmentName + " (실제 전달 시 다시 첨부 필요)");
    lines.push("※ 이 요약은 브라우저에서 작성되었으며 서버에 접수되지 않았습니다.");
    return lines.join("\n");
  }

  function renderReview() {
    var data = collectContact(); summary.textContent = "";
    addSummaryItem("회사·기관", data.company); addSummaryItem("담당자", data.name); addSummaryItem("부서·직급", data.department);
    addSummaryItem("예상 인원", data.count ? data.count + "명" : ""); addSummaryItem("연락처", data.phone);
    addSummaryItem("희망 시기", data.timing); addSummaryItem("관심 과정", data.interest);
    addSummaryItem("연락 가능 시간", data.contactTime); addSummaryItem("해결 과제", data.issue);
    if (data.diagnosis) addSummaryItem("AX 진단", data.diagnosis.task + " · " + data.diagnosis.course);
    if (data.attachmentName) addSummaryItem("참고 자료", data.attachmentName + " · 실제 전달 시 다시 첨부 필요");
    contactForm.hidden = true; complete.hidden = true; review.hidden = false; saveDraft("review", false);
    review.scrollIntoView({behavior:"smooth", block:"start"});
  }

  function editContact() {
    review.hidden = true; complete.hidden = true; contactForm.hidden = false; saveDraft("editing", false);
    contactForm.scrollIntoView({behavior:"smooth", block:"start"});
  }

  function localReference() {
    var now = new Date();
    var date = String(now.getFullYear()).slice(-2) + String(now.getMonth() + 1).padStart(2, "0") + String(now.getDate()).padStart(2, "0");
    return "LOCAL-" + date + "-" + Math.random().toString(36).slice(2, 6).toUpperCase();
  }

  function finishContact() {
    var draft = saveDraft("ready", false) || {};
    if (!draft.reference) { draft.reference = localReference(); try { localStorage.setItem(DRAFT_KEY, JSON.stringify(draft)); } catch (_) {} }
    document.getElementById("biz-contact-reference").textContent = "임시 작성번호 " + draft.reference + " · 실제 접수번호가 아닙니다";
    review.hidden = true; contactForm.hidden = true; complete.hidden = false;
    complete.scrollIntoView({behavior:"smooth", block:"start"});
  }

  if (contactForm) {
    restoreDraft();
    contactForm.addEventListener("submit", function (event) { event.preventDefault(); if (contactForm.reportValidity()) renderReview(); });
    contactForm.addEventListener("input", function (event) {
      if (event.target.id === "privacy-consent" || event.target.id === "attachment") return;
      window.clearTimeout(saveTimer); saveTimer = window.setTimeout(function () { saveDraft("editing", false); }, 500);
    });
    contactForm.addEventListener("change", function (event) {
      if (event.target.id === "attachment") draftAttachmentName = event.target.files && event.target.files[0] ? event.target.files[0].name : "";
      if (event.target.id !== "privacy-consent") saveDraft("editing", false);
    });
    document.querySelector("[data-save-draft]").addEventListener("click", function () { saveDraft("editing", true); });
    document.querySelector("[data-clear-draft]").addEventListener("click", function () {
      localStorage.removeItem(DRAFT_KEY); contactForm.reset(); currentDiagnosis = null; draftAttachmentName = ""; draftStatus.hidden = true;
    });
    document.querySelectorAll("[data-edit-contact]").forEach(function (button) { button.addEventListener("click", editContact); });
    document.querySelector("[data-finish-contact]").addEventListener("click", finishContact);
    document.querySelector("[data-copy-contact]").addEventListener("click", function () {
      var status = document.querySelector(".biz-copy-status");
      navigator.clipboard.writeText(summaryText(collectContact())).then(function () { status.textContent = "상담 요약을 복사했습니다."; })
        .catch(function () { status.textContent = "자동 복사가 차단됐습니다. 브라우저 권한을 확인해 주세요."; });
    });
  }

  document.querySelectorAll("[data-education-type]").forEach(function (link) {
    link.addEventListener("click", function () {
      var issue = document.getElementById("issue"); var interest = document.getElementById("interest");
      var educationType = link.getAttribute("data-education-type");
      if (issue) issue.value = educationType + "의 대상·일정·운영 방식을 문의합니다.";
      if (interest) Array.prototype.some.call(interest.options, function (option) {
        if (option.value !== educationType) return false; interest.value = educationType; return true;
      });
      saveDraft("editing", false);
    });
  });

  var diagnosisForm = document.getElementById("biz-diagnosis-form"); var diagnosisResult = document.getElementById("biz-diagnosis-result");
  if (diagnosisForm && diagnosisResult) diagnosisForm.addEventListener("submit", function (event) {
    event.preventDefault(); var values = new FormData(diagnosisForm); var task = values.get("task");
    var skill = values.get("skill"); var data = values.get("data"); var security = values.get("security");
    var course = skill === "low" ? "4~8시간 기초·직무 실습" : (data === "high" ? "24~40시간 프로젝트형 과정" : "8~16시간 직무 적용 과정");
    var priorities = [];
    if (data === "low") priorities.push("업무 자료와 데이터 정리 기준 수립");
    if (security !== "low") priorities.push("사용 가능 도구와 보안 가이드 확정");
    if (skill === "low") priorities.push("공통 AI 리터러시와 안전한 사용법 교육");
    if (!priorities.length) priorities.push("실제 업무 기반 PoC와 성과 지표 설정");
    var taskLabels = {document:"문서·보고 자동화", data:"데이터 분석·집계", content:"콘텐츠 제작", development:"개발·서비스 구축"};
    currentDiagnosis = {task:taskLabels[task], course:course, priorities:priorities, answers:{task:task, data:data, skill:skill, security:security}};
    diagnosisResult.innerHTML = "<span>진단 결과</span><h4>" + taskLabels[task] + "부터 시작하는 것이 좋습니다.</h4><p><b>권장 구성</b> " + course + "</p><ul>" + priorities.map(function (item) { return "<li>" + item + "</li>"; }).join("") + "</ul><a class=\"btn btn--primary\" href=\"#biz-contact\" data-diagnosis-inquiry>이 결과를 문의에 담기</a>";
    diagnosisResult.hidden = false; saveDraft("editing", false); diagnosisResult.scrollIntoView({behavior:"smooth", block:"nearest"});
    diagnosisResult.querySelector("[data-diagnosis-inquiry]").addEventListener("click", function () {
      var issue = document.getElementById("issue");
      if (issue) issue.value = "AX 간편 진단 결과: " + taskLabels[task] + " / " + course + " / 우선 확인: " + priorities.join(", ");
      saveDraft("editing", true);
    });
  });
})();
