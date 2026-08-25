/* ============================================================
   live-class-monitor.js — 라이브 강의 모니터링 데모 (LXP-125 / LXP-127)

   운영 admin-evaluation-monitoring-live.html 의 문법(검색·요약·타일·표)을
   따르지만 참가자·장치·이벤트는 전부 모의 데이터다. 실제 스트림도,
   서버 호출도 없다 — "안내 보내기"는 이 브라우저의 이벤트 로그에만 남는다.
   ============================================================ */
(function () {
  "use strict";

  var C = window.LiveClassCommon;
  var D = window.LiveClassDemoData;

  var selectedId = null;
  var keyword = "";
  var localEvents = [];    /* 안내 보내기로 추가된 이벤트 — 데모 데이터 원본은 건드리지 않는다 */

  var STATE_LABEL = { ok: "정상", warn: "주의", offline: "연결 끊김" };
  var STATE_TONE = { ok: "ok", warn: "warn", offline: "risk" };
  var DEVICE_LABEL = { live: "정상", weak: "불안정", off: "없음" };
  var DEVICE_TONE = { live: "ok", weak: "warn", off: "risk" };

  var searchEl = document.getElementById("peopleSearch");
  var filterEl = document.getElementById("stateFilter");
  var scenarioEl = document.getElementById("scenarioSelect");
  var bodyEl = document.getElementById("peopleBody");
  var eventBodyEl = document.getElementById("eventBody");
  var countEl = document.getElementById("filterCount");
  var selNameEl = document.getElementById("selName");
  var selMetaEl = document.getElementById("selMeta");
  var noticeBtn = document.getElementById("noticeBtn");

  document.getElementById("sessionTitle").textContent = D.session.title;
  document.getElementById("sessionMeta").textContent =
    D.session.course + " · " + D.session.instructor + " · 시작 " + D.session.startedAt;

  scenarioEl.innerHTML = D.scenarios.map(function (s) {
    return '<option value="' + s.key + '" title="' + C.esc(s.desc) + '">' + C.esc(s.label) + "</option>";
  }).join("");

  /* 시나리오는 원본 참가자 데이터를 바꾸지 않고 표시할 때만 규칙으로 덮어쓴다.
     device-warning: 정상이던 카메라·마이크가 불안정해지고 상태도 주의로 내려간다.
     disconnected  : 이미 주의 상태였던 참가자가 연결 끊김으로 넘어간다. */
  function withScenario(p) {
    var s = scenarioEl.value;
    var devices = { camera: p.devices.camera, mic: p.devices.mic, screen: p.devices.screen };
    var state = p.state;
    if (s === "device-warning") {
      if (devices.camera === "live") devices.camera = "weak";
      if (devices.mic === "live") devices.mic = "weak";
      if (state === "ok") state = "warn";
    } else if (s === "disconnected") {
      if (state === "warn") { state = "offline"; devices.camera = "off"; devices.mic = "off"; devices.screen = "off"; }
    }
    return { devices: devices, state: state };
  }

  function visible() {
    var f = filterEl.value;
    return D.participants.filter(function (p) {
      var v = withScenario(p);
      if (f !== "all" && v.state !== f) return false;
      if (!keyword) return true;
      var k = keyword.toLowerCase();
      return p.name.toLowerCase().indexOf(k) > -1 || p.seat.toLowerCase().indexOf(k) > -1;
    });
  }

  function renderSummary() {
    var counts = { ok: 0, warn: 0, offline: 0 };
    D.participants.forEach(function (p) { counts[withScenario(p).state]++; });
    document.getElementById("kpiTotal").textContent = D.participants.length;
    document.getElementById("kpiOk").textContent = counts.ok;
    document.getElementById("kpiWarn").textContent = counts.warn;
    document.getElementById("kpiOffline").textContent = counts.offline;
  }

  function renderPeople() {
    var rows = visible();
    countEl.textContent = rows.length + "명 표시 중 (전체 " + D.participants.length + "명)";
    if (!rows.length) {
      bodyEl.innerHTML = '<tr><td colspan="4" class="hint-text">조건에 맞는 참가자가 없습니다. 검색어나 필터를 바꿔 보세요.</td></tr>';
      return;
    }
    bodyEl.innerHTML = rows.map(function (p) {
      var v = withScenario(p);
      return '<tr class="people-row' + (p.id === selectedId ? " is-selected" : "") + '" data-id="' + p.id + '" tabindex="0" role="button" aria-pressed="' + (p.id === selectedId) + '">' +
        "<td>" + C.esc(p.name) + "</td>" +
        "<td>" + C.esc(p.seat) + "</td>" +
        "<td>" + C.esc(p.role) + "</td>" +
        '<td><span class="state-badge ' + STATE_TONE[v.state] + '">' + C.esc(STATE_LABEL[v.state]) + "</span></td>" +
      "</tr>";
    }).join("");
  }

  function renderSelected() {
    var p = D.participants.filter(function (x) { return x.id === selectedId; })[0];
    var camBadge = document.querySelector('[data-role="camera-badge"]');
    var micBadge = document.querySelector('[data-role="mic-badge"]');
    var screenBadge = document.querySelector('[data-role="screen-badge"]');

    if (!p) {
      selNameEl.textContent = "참가자를 선택하세요";
      selMetaEl.textContent = "";
      [camBadge, micBadge, screenBadge].forEach(function (b) { b.textContent = "-"; b.className = "state-badge"; });
      noticeBtn.disabled = true;
      return;
    }
    var v = withScenario(p);
    selNameEl.textContent = p.name + " (" + p.seat + ")";
    selMetaEl.textContent = p.role + " · 상태 " + STATE_LABEL[v.state];
    noticeBtn.disabled = false;

    [["camera", camBadge], ["mic", micBadge], ["screen", screenBadge]].forEach(function (pair) {
      var key = pair[0], el = pair[1];
      var dv = v.devices[key];
      el.textContent = DEVICE_LABEL[dv];
      el.className = "state-badge " + DEVICE_TONE[dv];
    });
  }

  function allEvents() {
    return D.events.concat(localEvents).slice().sort(function (a, b) {
      return (a.at < b.at ? 1 : a.at > b.at ? -1 : 0);
    });
  }

  function renderEvents() {
    var byId = {};
    D.participants.forEach(function (p) { byId[p.id] = p.name; });
    var rows = allEvents();
    if (!rows.length) {
      eventBodyEl.innerHTML = '<tr><td colspan="5" class="hint-text">기록된 이벤트가 없습니다.</td></tr>';
      return;
    }
    eventBodyEl.innerHTML = rows.map(function (ev) {
      var cls = ev.severity === "risk" ? "sev-risk" : "sev-warn";
      var label = ev.severity === "risk" ? "심각" : "주의";
      return "<tr" + (ev.participantId === selectedId ? ' class="is-selected"' : "") + ">" +
        '<td class="nowrap mono">' + C.esc(ev.at) + "</td>" +
        '<td class="nowrap">' + C.esc(byId[ev.participantId] || ev.participantId) + "</td>" +
        "<td>" + C.esc(ev.type) + "</td>" +
        '<td class="' + cls + '">' + label + "</td>" +
        "<td>" + C.esc(ev.desc) + "</td>" +
      "</tr>";
    }).join("");
  }

  /* 검색·필터·시나리오 변경으로 선택한 참가자가 더 이상 목록에 안 보이면 선택을 비운다.
     안 그러면 화면에 없는 사람에게 "안내 보내기"를 누를 수 있는 모순된 상태가 남는다.
     판정 자체는 live-class-common.js 의 순수 함수(reconcileSelection)를 그대로 쓴다. */
  function reconcileSelectionIfNeeded() {
    if (!selectedId) return;
    var visibleIds = visible().map(function (p) { return p.id; });
    selectedId = C.reconcileSelection(selectedId, visibleIds);
  }

  function renderAll() {
    reconcileSelectionIfNeeded();
    renderSummary(); renderPeople(); renderSelected(); renderEvents();
  }

  /* 검색·필터는 요약(전체 집계)까지 다시 셀 필요가 없어 renderAll() 을 쓰지 않지만,
     선택 초기화·목록·상세·이벤트 강조는 한 흐름에서 같이 갱신해야 모순이 안 생긴다. */
  function applyFiltersAndRender() {
    reconcileSelectionIfNeeded();
    renderPeople();
    renderSelected();
    renderEvents();
  }

  /* ---------- 이벤트 ---------- */
  function selectRow(id) { selectedId = id; renderPeople(); renderSelected(); renderEvents(); }

  bodyEl.addEventListener("click", function (e) {
    var row = e.target.closest("[data-id]");
    if (row) selectRow(row.dataset.id);
  });
  bodyEl.addEventListener("keydown", function (e) {
    if (e.key !== "Enter" && e.key !== " ") return;
    var row = e.target.closest("[data-id]");
    if (!row) return;
    e.preventDefault();
    selectRow(row.dataset.id);
  });

  searchEl.addEventListener("input", function () { keyword = this.value.trim(); applyFiltersAndRender(); });
  filterEl.addEventListener("change", applyFiltersAndRender);

  scenarioEl.addEventListener("change", function () {
    renderAll();
    var current = D.scenarios.filter(function (s) { return s.key === scenarioEl.value; })[0];
    C.toast("[데모] " + (current ? current.label : scenarioEl.value) + " 시나리오를 적용했습니다",
      scenarioEl.value === "normal" ? "ok" : "warn");
  });

  function pad(n) { return (n < 10 ? "0" : "") + n; }

  noticeBtn.addEventListener("click", function () {
    var p = D.participants.filter(function (x) { return x.id === selectedId; })[0];
    if (!p) return;
    var now = new Date();
    localEvents.push({
      id: "local-" + now.getTime(),
      at: pad(now.getHours()) + ":" + pad(now.getMinutes()) + ":" + pad(now.getSeconds()),
      participantId: p.id,
      type: "운영진 안내 발송",
      severity: "warn",
      desc: "[데모] 안내 기록 — " + p.name + "에게 안내를 보냈습니다(서버로 전송되지 않음)."
    });
    renderEvents();
    C.toast("[데모] 안내 기록을 추가했습니다", "warn");
  });

  /* ---------- 초기화 ---------- */
  renderAll();
})();
