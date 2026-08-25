/* ============================================================
   exam-hub.js — 시험 허브 화면

   운영 online-test.js 의 카드 렌더 방식(상태 배지 + meta + 액션 버튼)을 따른다.
   시험 상태는 데이터의 초기값이 아니라 ExamDemo 세션 상태로 결정한다.
   ============================================================ */

(function () {
  "use strict";

  var D = window.LXP_EXAM_DATA;
  var E = window.ExamDemo;

  var listEl = document.getElementById("cardList");
  var hintEl = document.getElementById("listHint");

  var PRECHECK_URL = "/v2/lxp/trainee/exam-precheck.html?exam=frontend-2026";
  var WORKSPACE_URL = "/v2/lxp/trainee/exam-workspace.html?exam=frontend-2026";

  /* 상태 키는 online-test.css 의 배지 클래스와 같은 이름을 쓴다.
     scheduled | available | in_progress | completed | ended */
  function resolve(exam, state) {
    if (exam.status === "done") {
      return { key: "completed", label: "응시완료",
               note: "점수 " + exam.score + "점", cta: null, ctaLabel: "" };
    }
    if (state.workspace && state.workspace.submitted) {
      return { key: "completed", label: "제출완료",
               note: "답안이 제출되었습니다(데모)", cta: null, ctaLabel: "" };
    }
    var checks = state.checks || {};
    var allPass = D.checkKeys.every(function (k) { return checks[k] === "pass"; });
    if (allPass) {
      return { key: "available", label: "응시가능",
               note: "사전점검을 통과했습니다", cta: WORKSPACE_URL, ctaLabel: "시험장 입장" };
    }
    var started = D.checkKeys.some(function (k) { return checks[k] && checks[k] !== "idle"; });
    return { key: "in_progress", label: "사전점검 필요",
             note: started ? "점검이 진행 중입니다" : "다섯 항목을 통과해야 입장할 수 있습니다",
             cta: PRECHECK_URL, ctaLabel: started ? "사전점검 이어하기" : "사전점검 시작" };
  }

  function card(exam, state) {
    var r = resolve(exam, state);
    var esc = E.esc;

    var actions = "";
    if (r.cta) {
      actions += '<a class="btn btn-primary" href="' + r.cta + '">' + esc(r.ctaLabel) + "</a>";
    }
    if (exam.requiresIdCheck && r.key !== "completed") {
      actions += '<a class="btn btn-secondary" href="/v2/lxp/trainee/exam-id-upload.html?exam=' +
                 esc(exam.id) + '">신분 확인</a>';
    }
    if (!actions) {
      actions = '<a class="btn btn-gray" href="/trainee/exam">운영 시험 목록에서 결과 보기</a>';
    }

    return '' +
      '<article class="exam-card" data-exam="' + esc(exam.id) + '">' +
        '<div class="card-top">' +
          '<span class="exam-badge ' + r.key + '">' + esc(r.label) + "</span>" +
          '<span class="demo-chip">데모</span>' +
        "</div>" +
        '<p class="card-title">' + esc(exam.title) + "</p>" +
        '<div class="meta">' +
          "<span><span class=\"dim\">과정</span> " + esc(exam.course) + "</span>" +
          "<span><span class=\"dim\">응시기간</span> " + esc(exam.startsAt) + "</span>" +
          "<span><span class=\"dim\">제한시간</span> " + exam.durationMin + "분</span>" +
          "<span><span class=\"dim\">문항</span> " + exam.questionCount + "문항</span>" +
        "</div>" +
        '<p class="submeta">감독 방식 · ' + esc(exam.proctorMode) +
          (exam.requiresIdCheck ? " / 신분 확인 " + esc(state.identity.status) : "") + "</p>" +
        '<p class="submeta">' + esc(r.note) + "</p>" +
        '<div class="card-actions">' + actions + "</div>" +
      "</article>";
  }

  function render() {
    var state = E.load();
    listEl.innerHTML = D.exams.map(function (x) { return card(x, state); }).join("");
    hintEl.textContent = "전체 " + D.exams.length + "건";
  }

  /* ---------- 데모 초기화 ---------- */
  var modal = document.getElementById("resetModal");
  var resetBtn = document.getElementById("resetDemo");

  resetBtn.addEventListener("click", function () { E.openModal(modal, resetBtn); });
  document.getElementById("resetConfirm").addEventListener("click", function () {
    E.reset();
    E.closeModal(modal);
    render();
    E.toast("데모 상태를 초기화했습니다", "ok");
  });

  render();
})();
