/* ============================================================
   proctor-demo.js — 실시간 3면 감독관제 (LXP-005 / LXP-020)

   운영 admin-evaluation-monitoring-live.html 의 문법을 따른다:
     응시자 pill(.student-btn) · 요약 줄(.proctor-summary) ·
     타일(.video-item) · 감독 패널(.proctor-table)

   모든 데이터는 모의값이고 서버 호출은 없다.
   ============================================================ */

(function () {
  "use strict";

  var D = window.LXP_EXAM_DATA;
  var E = window.ExamDemo;

  var state = E.load();
  var selectedId = state.proctor.selectedId || D.candidates[0].id;
  var keyword = "";

  var listEl   = document.getElementById("studentList");
  var countEl  = document.getElementById("filterCount");
  var eventEl  = document.getElementById("eventBody");
  var nameEl   = document.getElementById("selName");
  var metaEl   = document.getElementById("selMeta");
  var scenSel  = document.getElementById("scenario");
  var stateSel = document.getElementById("stateFilter");
  var feedTabs = document.getElementById("feedTabs");

  var STATE_LABEL = { ok: "정상", warn: "주의", risk: "위험", offline: "연결 끊김" };
  var DOT_CLASS   = { ok: "", warn: "warn", risk: "risk", offline: "off" };
  var FEED_LABEL  = { live: "정상", weak: "불안정", off: "끊김" };

  /* 시나리오는 원본 데이터를 바꾸지 않고 표시할 때만 덮어쓴다 */
  function withScenario(c) {
    var s = scenSel.value;
    var feeds = { camera: c.feeds.camera, screen: c.feeds.screen, mobile: c.feeds.mobile };
    var st = c.state;
    if (s === "screen-drop") { feeds.screen = "off"; st = "risk"; }
    else if (s === "camera-drop") { feeds.camera = "off"; st = "risk"; }
    else if (s === "mobile-drop") { feeds.mobile = "off"; st = st === "ok" ? "warn" : st; }
    return { feeds: feeds, state: st };
  }

  function visible() {
    var f = stateSel.value;
    return D.candidates.filter(function (c) {
      var v = withScenario(c);
      if (f !== "all" && v.state !== f) return false;
      if (!keyword) return true;
      var k = keyword.toLowerCase();
      return c.name.toLowerCase().indexOf(k) > -1 || c.seat.toLowerCase().indexOf(k) > -1;
    });
  }

  function renderSummary() {
    var counts = { ok: 0, warn: 0, risk: 0, offline: 0 };
    D.candidates.forEach(function (c) { counts[withScenario(c).state]++; });
    document.getElementById("kpiTotal").textContent = D.candidates.length;
    document.getElementById("kpiOk").textContent = counts.ok;
    document.getElementById("kpiWarn").textContent = counts.warn;
    document.getElementById("kpiRisk").textContent = counts.risk;
    document.getElementById("kpiOffline").textContent = counts.offline;
  }

  function renderList() {
    var rows = visible();
    listEl.innerHTML = rows.map(function (c) {
      var v = withScenario(c);
      return '<button type="button" class="student-btn' + (c.id === selectedId ? " active" : "") +
             '" data-id="' + c.id + '" aria-pressed="' + (c.id === selectedId) +
             '" title="' + E.esc(STATE_LABEL[v.state]) + '">' +
               '<span class="status-dot ' + DOT_CLASS[v.state] + '"></span>' +
               "<span>" + E.esc(c.name) + "</span>" +
             "</button>";
    }).join("");

    countEl.textContent = rows.length + "명 표시 중 (전체 " + D.candidates.length + "명)";
    if (!rows.length) {
      listEl.innerHTML = '<span class="hint-text">조건에 맞는 응시자가 없습니다. 검색어나 필터를 바꿔 보세요.</span>';
    }
  }

  function renderSelected() {
    var c = D.candidates.filter(function (x) { return x.id === selectedId; })[0];
    if (!c) { nameEl.textContent = "응시자를 선택하세요"; metaEl.textContent = ""; return; }
    var v = withScenario(c);

    nameEl.textContent = c.name + " (" + c.seat + ")";
    metaEl.textContent = c.course + " · 진행률 " + c.progress + "% · 신분 확인 " + c.idStatus +
                         " · 상태 " + STATE_LABEL[v.state];

    ["camera", "screen", "mobile"].forEach(function (k) {
      var el = document.querySelector('[data-role="' + k + '"]');
      if (el) el.textContent = FEED_LABEL[v.feeds[k]];
      var item = el && el.closest(".video-item");
      if (item) {
        item.classList.toggle("severity-critical", v.feeds[k] === "off");
        item.classList.toggle("severity-warn", v.feeds[k] === "weak");
      }
    });
  }

  function allEvents() {
    return D.events.concat(E.load().proctor.extraEvents || [])
      .slice()
      .sort(function (a, b) { return (b.sec || 0) - (a.sec || 0); });
  }

  function renderEvents() {
    var byName = {};
    D.candidates.forEach(function (c) { byName[c.id] = c.name; });

    eventEl.innerHTML = allEvents().map(function (ev) {
      var cls = ev.severity === "risk" ? "sev-critical" : "sev-warn";
      var label = ev.severity === "risk" ? "심각" : "주의";
      return "<tr" + (ev.candidateId === selectedId ? ' class="is-selected"' : "") + ">" +
        '<td class="nowrap mono">' + E.esc(ev.at) + "</td>" +
        '<td class="nowrap">' + E.esc(byName[ev.candidateId] || ev.candidateId) + "</td>" +
        "<td>" + E.esc(ev.type) + "</td>" +
        '<td class="' + cls + '">' + label + "</td>" +
        "<td>" + E.esc(ev.desc) + "</td>" +
      "</tr>";
    }).join("");
  }

  function renderAll() { renderSummary(); renderList(); renderSelected(); renderEvents(); }

  /* ---------- 이벤트 ---------- */
  listEl.addEventListener("click", function (e) {
    var b = e.target.closest("[data-id]");
    if (!b) return;
    selectedId = b.dataset.id;
    E.patch(function (s) { s.proctor.selectedId = selectedId; });
    renderAll();
  });

  document.getElementById("candSearch").addEventListener("input", function () {
    keyword = this.value.trim();
    renderList();
  });

  stateSel.addEventListener("change", renderList);

  scenSel.addEventListener("change", function () {
    E.patch(function (s) { s.proctor.scenario = scenSel.value; });
    renderAll();
    var msg = {
      "normal": "정상 시나리오로 되돌렸습니다",
      "screen-drop": "[시뮬레이션] 화면 공유 끊김을 적용했습니다",
      "camera-drop": "[시뮬레이션] 카메라 끊김을 적용했습니다",
      "mobile-drop": "[시뮬레이션] 모바일 끊김을 적용했습니다"
    }[scenSel.value];
    E.toast(msg, scenSel.value === "normal" ? "ok" : "warn");
  });

  /* ---------- 3면 탭 (1024px 미만) ---------- */
  var mq = window.matchMedia("(max-width: 1023px)");
  function syncFeedMode() {
    feedTabs.hidden = !mq.matches;
    var panes = document.querySelectorAll(".video-pane");
    if (!mq.matches) {
      panes.forEach(function (p) { p.classList.add("is-active"); });
      return;
    }
    var active = feedTabs.querySelector(".is-active").dataset.feedTab;
    panes.forEach(function (p) { p.classList.toggle("is-active", p.id === active); });
  }
  feedTabs.addEventListener("click", function (e) {
    var b = e.target.closest("[data-feed-tab]");
    if (!b) return;
    Array.prototype.forEach.call(feedTabs.children, function (x) {
      var on = x === b;
      x.classList.toggle("is-active", on);
      x.classList.toggle("btn-secondary", on);
      x.classList.toggle("btn-gray", !on);
    });
    syncFeedMode();
  });
  mq.addEventListener("change", syncFeedMode);

  /* ---------- 경고 발송 ---------- */
  var warnModal = document.getElementById("warnModal");
  var reasonSel = document.getElementById("warnReason");
  var msgEl = document.getElementById("warnMessage");
  var errEl = document.getElementById("warnError");

  reasonSel.innerHTML = D.warnReasons.map(function (r) {
    return "<option>" + E.esc(r) + "</option>";
  }).join("");

  document.getElementById("warnBtn").addEventListener("click", function () {
    var c = D.candidates.filter(function (x) { return x.id === selectedId; })[0];
    if (!c) { E.toast("먼저 응시자를 선택하세요", "warn"); return; }
    document.getElementById("warnTarget").textContent = c.name + " (" + c.seat + ") 에게 보냅니다.";
    errEl.hidden = true;
    E.openModal(warnModal, this);
  });

  document.getElementById("warnSend").addEventListener("click", function () {
    var text = msgEl.value.trim();
    if (!text) {
      errEl.textContent = "보낼 메시지를 입력하세요.";
      errEl.hidden = false;
      msgEl.focus();
      return;
    }
    var now = new Date();
    var at = ("0" + now.getHours()).slice(-2) + ":" + ("0" + now.getMinutes()).slice(-2) +
             ":" + ("0" + now.getSeconds()).slice(-2);
    var ev = {
      id: "w" + now.getTime(),
      at: at,
      sec: 9999,                       /* 최신 이벤트가 위로 오도록 */
      candidateId: selectedId,
      type: "감독관 경고 발송",
      severity: "risk",
      desc: reasonSel.value + " — " + text
    };
    E.patch(function (s) { s.proctor.extraEvents.push(ev); });
    msgEl.value = "";
    E.closeModal(warnModal);
    renderEvents();
    E.toast("경고를 기록했습니다 (데모)", "risk");
  });

  /* ---------- 초기화 ---------- */
  scenSel.value = state.proctor.scenario || "normal";
  syncFeedMode();
  renderAll();
})();
