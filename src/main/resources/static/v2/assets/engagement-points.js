(function () {
  "use strict";
  var storageKey = "lxp-demo-point-rules";
  var defaults = [
    { id: "lesson", title: "학습 콘텐츠 완료", note: "차시 진도 100% 달성", point: 30, limit: 4, active: true },
    { id: "assignment", title: "과제 기한 내 제출", note: "재제출은 최초 1회만 인정", point: 60, limit: 2, active: true },
    { id: "quiz", title: "퀴즈 목표 점수 달성", note: "80점 이상 완료", point: 40, limit: 3, active: true },
    { id: "feedback", title: "동료 피드백 작성", note: "50자 이상 구체적 의견", point: 20, limit: 3, active: true },
    { id: "streak", title: "연속 학습 달성", note: "하루 30분 이상 학습", point: 15, limit: 1, active: true }
  ];

  function copyRules(rules) { return rules.map(function (rule) { return Object.assign({}, rule); }); }
  function loadRules() {
    try {
      var stored = JSON.parse(window.localStorage.getItem(storageKey));
      return Array.isArray(stored) && stored.length === defaults.length ? stored : copyRules(defaults);
    } catch (e) { return copyRules(defaults); }
  }
  function toast(message) {
    var old = document.querySelector(".ops-toast"); if (old) old.remove();
    var node = document.createElement("div"); node.className = "ops-toast"; node.setAttribute("role", "status"); node.textContent = message;
    document.body.appendChild(node); window.setTimeout(function () { node.remove(); }, 2500);
  }

  function initAdmin() {
    var list = document.getElementById("criteriaList");
    if (!list) return;
    var rules = loadRules();

    function render() {
      list.innerHTML = rules.map(function (rule, index) {
        return '<div class="criteria-row" data-index="' + index + '"><div><strong>' + rule.title + '</strong><small>' + rule.note +
          '</small></div><label>활동 점수<input class="rule-point" type="number" min="0" max="500" value="' + rule.point + '"></label>' +
          '<label>일일 인정 횟수<input class="rule-limit" type="number" min="1" max="20" value="' + rule.limit + '"></label>' +
          '<label><span class="sr-only">활성 여부</span><input class="ops-switch rule-active" type="checkbox" ' + (rule.active ? "checked" : "") + ' aria-label="' + rule.title + ' 활성 여부"></label></div>';
      }).join("");
      updateSummary();
    }
    function sync() {
      list.querySelectorAll(".criteria-row").forEach(function (row) {
        var index = Number(row.dataset.index);
        rules[index].point = Math.max(0, Number(row.querySelector(".rule-point").value) || 0);
        rules[index].limit = Math.max(1, Number(row.querySelector(".rule-limit").value) || 1);
        rules[index].active = row.querySelector(".rule-active").checked;
      });
      updateSummary();
    }
    function updateSummary() {
      var active = rules.filter(function (rule) { return rule.active; });
      document.getElementById("activeRuleCount").textContent = active.length;
      document.getElementById("weeklyMax").textContent = active.reduce(function (sum, rule) { return sum + rule.point * rule.limit * 5; }, 0).toLocaleString("ko-KR") + "점";
    }
    list.addEventListener("input", sync); list.addEventListener("change", sync);
    document.getElementById("savePointRules").addEventListener("click", function () {
      sync(); window.localStorage.setItem(storageKey, JSON.stringify(rules));
      toast("점수 기준을 이 브라우저에 저장했습니다. 훈련생 미리보기에서도 적용 기준을 확인할 수 있습니다.");
    });
    document.getElementById("resetPointRules").addEventListener("click", function () { rules = copyRules(defaults); window.localStorage.removeItem(storageKey); render(); toast("기본 점수 기준으로 복원했습니다."); });
    document.getElementById("pointCourse").addEventListener("change", function () { toast("선택한 과정의 화면 예시를 불러왔습니다. 실제 과정별 저장은 2차 범위입니다."); });
    render();
  }

  function initTrainee() {
    var historyBody = document.getElementById("pointHistoryBody");
    if (!historyBody) return;
    var rules = loadRules();
    var byId = {}; rules.forEach(function (rule) { byId[rule.id] = rule; });
    var base = [
      ["2026.08.26", "lesson", "데이터 전처리 실습", 1, "반영 완료"],
      ["2026.08.25", "assignment", "AI 기반 데이터 분석", 1, "반영 완료"],
      ["2026.08.24", "feedback", "프로젝트 리뷰", 2, "반영 완료"],
      ["2026.08.23", "quiz", "머신러닝 기초", 1, "반영 완료"],
      ["2026.08.22", "streak", "AI 기반 데이터 분석", 1, "반영 완료"]
    ];
    function renderHistory(month) {
      var rows = month ? base.concat([["2026.08.18", "lesson", "Python 데이터 처리", 2, "반영 완료"], ["2026.08.15", "quiz", "통계 기초", 1, "반영 완료"]]) : base;
      historyBody.innerHTML = rows.map(function (row) {
        var rule = byId[row[1]] || defaults[0]; var score = rule.active ? rule.point * row[3] : 0;
        return "<tr><td>" + row[0] + "</td><td><strong>" + rule.title + "</strong></td><td>" + row[2] + "</td><td>+" + score + "점</td><td><span class=\"ops-status valid\">" + row[4] + "</span></td></tr>";
      }).join("");
    }
    var ranking = [[1,"학습자A",610,"12일"],[2,"학습자B",560,"8일"],[3,"학습자C",510,"10일"],[4,"학습자D",455,"7일"],[5,"나",420,"6일"],[6,"학습자F",390,"5일"],[7,"학습자G",355,"5일"]];
    document.getElementById("rankingBody").innerHTML = ranking.map(function (row) { return '<tr' + (row[1] === "나" ? ' class="rank-me"' : '') + '><td>' + row[0] + '</td><td>' + row[1] + '</td><td>' + row[2] + '점</td><td>' + row[3] + '</td></tr>'; }).join("");
    document.getElementById("pointPeriod").addEventListener("change", function (event) { renderHistory(event.target.value === "month"); });
    renderHistory(false);
  }
  document.addEventListener("DOMContentLoaded", function () { initAdmin(); initTrainee(); });
})();
