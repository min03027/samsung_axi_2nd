/* ============================================================
   execution-infra.js — 개인 컨테이너·오토스케일링 관제 (LXP-013)

   시나리오(정상 / 급증 / 장애)에 따라 KPI·노드·컨테이너·증설 권고가
   함께 바뀐다. 증설은 대기 → 프로비저닝 → 준비 완료 단계를 보여주는
   시뮬레이션이며 실제 인프라 요청을 보내지 않는다.
   ============================================================ */

(function () {
  "use strict";

  var D = window.LXP_EXAM_DATA;
  var E = window.ExamDemo;

  var scenList  = document.getElementById("scenarioList");
  var scenKey   = "normal";                 /* select 대신 pill 버튼으로 고른다 */
  var scenSel   = { get value() { return scenKey; }, set value(v) { scenKey = v; } };
  var descEl    = document.getElementById("scenarioDesc");
  var adviceEl  = document.getElementById("advice");
  var stepsEl   = document.getElementById("scaleSteps");
  var scaleMsg  = document.getElementById("scaleMsg");
  var scaleBtn  = document.getElementById("scaleBtn");
  var retryBtn  = document.getElementById("retryBtn");
  var histEl    = document.getElementById("scaleHistory");
  var nodeGrid  = document.getElementById("nodeGrid");
  var boxBody   = document.getElementById("boxBody");
  var boxCount  = document.getElementById("boxCount");

  /* 라벨은 증설 단계 배지("2. 프로비저닝")와 같은 말을 쓴다 — 같은 상태를 두 이름으로
     부르면 표와 단계 표시가 따로 노는 것처럼 보인다. */
  var STATE_LABEL = { running: "가동", idle: "대기", provisioning: "프로비저닝", failed: "실패" };
  var STATE_BADGE = { running: "ok", idle: "", provisioning: "warn", failed: "risk" };

  var candName = {};
  D.candidates.forEach(function (c) { candName[c.id] = c.name; });

  var running = false;

  function scenario() {
    return D.scenarios.filter(function (s) { return s.key === scenSel.value; })[0] || D.scenarios[0];
  }

  /* 노드 장애 시나리오에서는 node-c 의 컨테이너를 실패로 바꿔서 보여준다.
     원본 데이터는 건드리지 않는다. */
  function boxes() {
    var s = scenSel.value;
    return D.containers.map(function (b) {
      var copy = { id: b.id, candidateId: b.candidateId, lang: b.lang, state: b.state,
                   cpu: b.cpu, mem: b.mem, lastRun: b.lastRun, node: b.node };
      if (s === "failure" && b.node === "node-c") { copy.state = "failed"; copy.cpu = 0; copy.mem = 0; }
      else if (s === "surge" && b.state === "idle") { copy.state = "running"; copy.cpu = 71; copy.mem = 620; }
      return copy;
    });
  }

  function renderKpi() {
    var k = scenario().kpi;
    document.getElementById("kpiActive").textContent = k.active;
    document.getElementById("kpiQueue").textContent = k.queue;
    document.getElementById("kpiRunning").textContent = k.running;
    document.getElementById("kpiAvg").textContent = k.avgMs.toLocaleString() + "ms";

    var s = scenario();
    descEl.textContent = s.desc;
    adviceEl.textContent = s.advice;
    adviceEl.style.color = s.adviceTone === "risk" ? "var(--color-danger)"
                          : s.adviceTone === "warn" ? "var(--color-warning)" : "var(--color-success)";
    scaleBtn.disabled = !s.needScale || running;
    if (!s.needScale) scaleMsg.textContent = "현재 시나리오에서는 증설이 필요하지 않습니다.";
  }

  function renderNodes() {
    var list = boxes();
    var nodes = ["node-a", "node-b", "node-c"];
    nodeGrid.innerHTML = nodes.map(function (n) {
      var mine = list.filter(function (b) { return b.node === n; });
      var down = scenSel.value === "failure" && n === "node-c";
      var cpu = mine.length ? Math.round(mine.reduce(function (a, b) { return a + b.cpu; }, 0) / mine.length) : 0;
      var tone = cpu > 75 ? "risk" : cpu > 55 ? "warn" : "ok";
      return '<article class="node-card" data-state="' + (down ? "down" : "up") + '">' +
        '<p class="node-name">' + n + "</p>" +
        '<p class="node-sub">' + (down ? "응답 없음" : "정상") + " · 컨테이너 " + mine.length + "개</p>" +
        '<div class="meter"><div class="meter-fill" data-tone="' + tone + '" style="width:' + (down ? 0 : cpu) + '%"></div></div>' +
        '<p class="hint-text">평균 CPU ' + (down ? "—" : cpu + "%") + "</p>" +
      "</article>";
    }).join("");
  }

  function renderBoxes() {
    var kw = document.getElementById("boxSearch").value.trim().toLowerCase();
    var st = document.getElementById("stateFilter").value;
    var lg = document.getElementById("langFilter").value;

    var rows = boxes().filter(function (b) {
      if (st !== "all" && b.state !== st) return false;
      if (lg !== "all" && b.lang !== lg) return false;
      if (!kw) return true;
      return b.id.toLowerCase().indexOf(kw) > -1 ||
             (candName[b.candidateId] || "").toLowerCase().indexOf(kw) > -1;
    });

    boxCount.textContent = rows.length + "개 표시 중 (전체 " + D.containers.length + "개)";

    if (!rows.length) {
      boxBody.innerHTML = '<tr><td colspan="8" class="hint-text">조건에 맞는 컨테이너가 없습니다.</td></tr>';
      return;
    }

    boxBody.innerHTML = rows.map(function (b) {
      return "<tr>" +
        '<td class="mono nowrap">' + E.esc(b.id) + "</td>" +
        "<td class=\"nowrap\">" + E.esc(candName[b.candidateId] || b.candidateId) + "</td>" +
        "<td class=\"nowrap\">" + E.esc(b.lang) + "</td>" +
        '<td><span class="state-badge ' + STATE_BADGE[b.state] + '">' + STATE_LABEL[b.state] + "</span></td>" +
        '<td class="mono">' + (b.state === "failed" ? "—" : b.cpu + "%") + "</td>" +
        '<td class="mono">' + (b.state === "failed" ? "—" : b.mem + "MB") + "</td>" +
        '<td class="nowrap">' + E.esc(b.lastRun) + "</td>" +
        '<td class="mono">' + E.esc(b.node) + "</td>" +
      "</tr>";
    }).join("");
  }

  /* ---------- 증설 시뮬레이션 ---------- */
  function setStep(key, value) {
    var el = stepsEl.querySelector('[data-step="' + key + '"]');
    if (el) el.dataset.state = value;
  }
  function clearSteps() {
    ["queue", "provision", "ready"].forEach(function (k) {
      stepsEl.querySelector('[data-step="' + k + '"]').removeAttribute("data-state");
    });
  }

  function addHistory(text, tone) {
    var now = new Date();
    var at = ("0" + now.getHours()).slice(-2) + ":" + ("0" + now.getMinutes()).slice(-2);
    if (histEl.dataset.seeded !== "1") { histEl.innerHTML = ""; histEl.dataset.seeded = "1"; }
    var li = document.createElement("li");
    li.innerHTML = '<span class="t">' + at + "</span><span>" + E.esc(text) + "</span>";
    histEl.insertBefore(li, histEl.firstChild);
  }

  function scale() {
    if (running) return;
    running = true;
    scaleBtn.disabled = true;
    retryBtn.hidden = true;
    clearSteps();

    var willFail = scenSel.value === "failure";

    setStep("queue", "active");
    scaleMsg.textContent = "[시뮬레이션] 증설 요청을 대기열에 넣었습니다…";

    window.setTimeout(function () {
      setStep("queue", "done");
      setStep("provision", "active");
      scaleMsg.textContent = "[시뮬레이션] 노드를 프로비저닝하는 중입니다… (예상 40초)";
    }, 700);

    window.setTimeout(function () {
      if (willFail) {
        setStep("provision", "failed");
        scaleMsg.textContent = "[시뮬레이션] 증설 실패 — node-c 가 응답하지 않아 대체 노드를 할당하지 못했습니다. " +
                               "가용 영역을 바꾸거나 수동으로 노드를 교체해야 합니다.";
        retryBtn.hidden = false;
        addHistory("증설 실패 — node-c 응답 없음", "risk");
        E.toast("증설 실패 (시뮬레이션)", "risk");
      } else {
        setStep("provision", "done");
        setStep("ready", "done");
        scaleMsg.textContent = "[시뮬레이션] 노드 2대가 추가되어 대기열이 해소되었습니다.";
        addHistory("노드 2대 증설 완료 (시뮬레이션)", "ok");
        E.toast("증설 완료 (시뮬레이션)", "ok");
      }
      running = false;
      scaleBtn.disabled = !scenario().needScale;
      E.patch(function (s) { s.infra.scaleState = willFail ? "failed" : "ready"; });
    }, 1800);
  }

  scaleBtn.addEventListener("click", scale);
  retryBtn.addEventListener("click", function () {
    retryBtn.hidden = true;
    E.toast("증설을 다시 시도합니다 (시뮬레이션)", "warn");
    scale();
  });

  function renderScenarioPills() {
    scenList.innerHTML = D.scenarios.map(function (x) {
      var dot = x.key === "normal" ? "" : (x.key === "surge" ? "warn" : "risk");
      return '<button type="button" class="student-btn' + (x.key === scenKey ? " active" : "") +
             '" data-scenario="' + x.key + '" aria-pressed="' + (x.key === scenKey) + '">' +
               '<span class="status-dot ' + dot + '"></span><span>' + E.esc(x.label) + "</span>" +
             "</button>";
    }).join("");
  }

  scenList.addEventListener("click", function (e) {
    var b = e.target.closest("[data-scenario]");
    if (!b) return;
    scenKey = b.dataset.scenario;
    E.patch(function (s) { s.infra.scenario = scenKey; });
    clearSteps();
    scaleMsg.textContent = "";
    retryBtn.hidden = true;
    renderScenarioPills();
    renderKpi(); renderNodes(); renderBoxes();
    E.toast("[시뮬레이션] " + b.textContent.trim() + " 시나리오를 적용했습니다",
            scenKey === "normal" ? "ok" : (scenKey === "surge" ? "warn" : "risk"));
  });

  ["boxSearch", "stateFilter", "langFilter"].forEach(function (id) {
    document.getElementById(id).addEventListener("input", renderBoxes);
    document.getElementById(id).addEventListener("change", renderBoxes);
  });

  /* ---------- 초기화 ---------- */
  scenKey = E.load().infra.scenario || "normal";
  renderScenarioPills();
  renderKpi(); renderNodes(); renderBoxes();
})();
