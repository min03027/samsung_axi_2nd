/* ============================================================
   presence-monitor.js — 학습 참여 모니터링 데모 (LXP-140 / LXP-141 / LXP-142)

   참가자·이탈 이력·이벤트는 전부 모의 데이터다. 실제 스트림도, 서버 호출도 없다 —
   "안내 보내기"는 모달 확인 후 이 브라우저의 이벤트 로그에만 남는다.
   ============================================================ */
(function () {
  "use strict";

  var C = window.LearningPresence;
  var D = window.LearningPresenceDemoData;

  var RANK = { focus: 0, warning: 1, normal: 2 };
  var TONE = { focus: "risk", warning: "warn", normal: "ok" };
  var LABEL_FALLBACK = { focus: "집중관리", warning: "주의", normal: "정상" };

  var selectedId = null;
  var keyword = "";
  var filterState = "all";
  var localEvents = [];   /* 안내 보내기로 추가된 이벤트 — 데모 데이터 원본은 건드리지 않는다 */

  var searchEl = document.getElementById("peopleSearch");
  var filterEl = document.getElementById("stateFilter");
  var scenarioEl = document.getElementById("scenarioSelect");
  var bodyEl = document.getElementById("peopleBody");
  var timelineBodyEl = document.getElementById("timelineBody");
  var countEl = document.getElementById("filterCount");
  var kpiTotalEl = document.getElementById("kpiTotal");
  var kpiNormalEl = document.getElementById("kpiNormal");
  var kpiWarningEl = document.getElementById("kpiWarning");
  var kpiFocusEl = document.getElementById("kpiFocus");
  var selNameEl = document.getElementById("selName");
  var selMetaEl = document.getElementById("selMeta");
  var selReasonEl = document.getElementById("selReason");
  var noticeBtn = document.getElementById("noticeBtn");
  var noticeDialog = document.getElementById("noticeDialog");
  var noticeConfirm = document.getElementById("noticeConfirm");
  var noticeCancel = document.getElementById("noticeCancel");

  document.getElementById("courseTitle").textContent = D.session.course;
  document.getElementById("policyLine").textContent =
    "점검 주기 " + D.policy.sampleIntervalSeconds + "초 · 자리 이탈 허용 " + D.policy.graceSeconds + "초 · " +
    "주의 기준 " + D.policy.warningSeconds + "초 · 집중관리 기준 " + D.policy.focusSeconds + "초";

  scenarioEl.innerHTML = D.monitorScenarios.map(function (s) {
    return '<option value="' + s.key + '" title="' + escText(s.desc) + '">' + escText(s.label) + "</option>";
  }).join("");

  function escText(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function pad(n) { return (n < 10 ? "0" : "") + n; }

  function nowHHMMSS() {
    var d = new Date();
    return pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
  }

  function formatHMS(totalSeconds) {
    var total = Math.max(0, Math.floor(totalSeconds || 0));
    var h = Math.floor(total / 3600);
    var m = Math.floor((total % 3600) / 60);
    var s = total % 60;
    return pad(h) + ":" + pad(m) + ":" + pad(s);
  }

  /* 시나리오는 원본 학습자 데이터를 바꾸지 않고 표시할 때만 이탈시간·횟수를 더한다.
     many-away  : 정상·주의였던 인원의 누적 이탈이 늘어나 상당수가 주의/집중관리로 넘어간다.
     camera-fault: 이미 이탈 이력이 있던(awayCount>0) 인원만 급격히 악화된다. */
  function withScenario(learner) {
    var key = scenarioEl.value;
    var awaySeconds = learner.awaySeconds;
    var awayCount = learner.awayCount;
    if (key === "many-away") {
      awaySeconds += 150;
      awayCount += 2;
    } else if (key === "camera-fault" && learner.awayCount > 0) {
      awaySeconds += 400;
      awayCount += 1;
    }
    return { awaySeconds: awaySeconds, awayCount: awayCount };
  }

  /** 정책 기준으로 분류하고, 시나리오가 반영된 시간 지표까지 한 번에 계산한다. */
  function classify(learner) {
    var v = withScenario(learner);
    var cls = C.classifyLearner({
      awaySeconds: v.awaySeconds, awayCount: v.awayCount,
      warningSeconds: D.policy.warningSeconds, focusSeconds: D.policy.focusSeconds
    });
    var time = C.calculateLearningTime({
      connectedSeconds: learner.connectedSeconds, verifiedSeconds: learner.verifiedSeconds, awaySeconds: v.awaySeconds
    });
    return { code: cls.code, label: cls.label, reason: cls.reason, awaySeconds: v.awaySeconds, awayCount: v.awayCount, time: time };
  }

  function visibleSorted() {
    var f = filterState;
    var k = keyword.toLowerCase();
    var rows = D.learners
      .map(function (p) { return { p: p, c: classify(p) }; })
      .filter(function (row) {
        if (f !== "all" && row.c.code !== f) return false;
        if (!k) return true;
        return row.p.name.toLowerCase().indexOf(k) > -1 || row.p.seat.toLowerCase().indexOf(k) > -1;
      });
    rows.sort(function (a, b) {
      var r = RANK[a.c.code] - RANK[b.c.code];
      if (r !== 0) return r;
      return b.c.awaySeconds - a.c.awaySeconds;
    });
    return rows;
  }

  /** 검색·필터·시나리오 변경으로 선택한 훈련생이 더 이상 보이지 않으면, 화면에 아무도
      선택되지 않은 채로 두지 않고 지금 표시된 첫 훈련생으로 옮긴다(목록이 비면 선택도 비운다). */
  function reconcileSelection(rows) {
    if (rows.some(function (r) { return r.p.id === selectedId; })) return;
    selectedId = rows.length ? rows[0].p.id : null;
  }

  function renderKPI() {
    var counts = { normal: 0, warning: 0, focus: 0 };
    D.learners.forEach(function (p) { counts[classify(p).code]++; });
    kpiTotalEl.textContent = D.learners.length;
    kpiNormalEl.textContent = counts.normal;
    kpiWarningEl.textContent = counts.warning;
    kpiFocusEl.textContent = counts.focus;
  }

  function renderTable(rows) {
    countEl.textContent = rows.length + "명 표시 중 (전체 " + D.learners.length + "명)";
    if (!rows.length) {
      bodyEl.innerHTML = '<tr><td colspan="7" class="hint-text">조건에 맞는 훈련생이 없습니다. 검색어나 필터를 바꿔 보세요.</td></tr>';
      return;
    }
    bodyEl.innerHTML = rows.map(function (row) {
      var p = row.p, c = row.c;
      return '<tr class="people-row' + (p.id === selectedId ? " is-selected" : "") + '" data-id="' + p.id +
        '" tabindex="0" role="button" aria-pressed="' + (p.id === selectedId) + '">' +
        "<td>" + escText(p.name) + "</td>" +
        "<td>" + escText(p.seat) + "</td>" +
        '<td class="mono">' + formatHMS(p.connectedSeconds) + "</td>" +
        '<td class="mono">' + formatHMS(c.time.verifiedSeconds) + "</td>" +
        '<td class="mono">' + formatHMS(c.awaySeconds) + "</td>" +
        '<td class="mono">' + formatHMS(c.time.recognizedSeconds) + "</td>" +
        '<td><span class="state-badge ' + (TONE[c.code] || "") + '">' + escText(c.label || LABEL_FALLBACK[c.code]) + "</span></td>" +
      "</tr>";
    }).join("");
  }

  function renderSelected(rows) {
    var row = rows.filter(function (r) { return r.p.id === selectedId; })[0];
    if (!row) {
      selNameEl.textContent = "훈련생을 선택하세요";
      selMetaEl.textContent = "";
      selReasonEl.textContent = "";
      noticeBtn.disabled = true;
      return;
    }
    selNameEl.textContent = row.p.name + " (" + row.p.seat + ")";
    selMetaEl.textContent = "연결 " + formatHMS(row.p.connectedSeconds) + " · 확인 " + formatHMS(row.c.time.verifiedSeconds) +
      " · 이탈 " + formatHMS(row.c.awaySeconds) + " · 인정 예정 " + formatHMS(row.c.time.recognizedSeconds);
    selReasonEl.textContent = row.c.reason;
    noticeBtn.disabled = false;
  }

  function allEvents() {
    return D.awayEvents.concat(localEvents).slice().sort(function (a, b) {
      return (a.at < b.at ? 1 : a.at > b.at ? -1 : 0);
    });
  }

  function renderTimeline() {
    var byId = {};
    D.learners.forEach(function (p) { byId[p.id] = p.name; });
    var rows = allEvents();
    if (!rows.length) {
      timelineBodyEl.innerHTML = '<tr><td colspan="4" class="hint-text">기록된 이벤트가 없습니다.</td></tr>';
      return;
    }
    timelineBodyEl.innerHTML = rows.map(function (ev) {
      return "<tr" + (ev.learnerId === selectedId ? ' class="is-selected"' : "") + ">" +
        '<td class="nowrap mono">' + escText(ev.at) + "</td>" +
        '<td class="nowrap">' + escText(byId[ev.learnerId] || ev.learnerId) + "</td>" +
        '<td class="mono">' + (ev.seconds != null ? formatHMS(ev.seconds) : "-") + "</td>" +
        "<td>" + escText(ev.desc) + "</td>" +
      "</tr>";
    }).join("");
  }

  function renderAll() {
    var rows = visibleSorted();
    reconcileSelection(rows);
    renderKPI();
    renderTable(rows);
    renderSelected(rows);
    renderTimeline();
  }

  /* ---------- 토스트(이 화면 전용 — 실제 발송 없음을 매번 확인시킨다) ---------- */
  var toastHost = null;
  function toast(message) {
    if (!toastHost) {
      toastHost = document.createElement("div");
      toastHost.className = "toast-host";
      document.body.appendChild(toastHost);
    }
    var t = document.createElement("div");
    t.className = "toast";
    t.setAttribute("role", "status");
    t.textContent = message;
    toastHost.appendChild(t);
    window.setTimeout(function () {
      t.classList.add("is-out");
      window.setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 240);
    }, 3200);
  }

  /* ---------- 이벤트 ---------- */
  function selectRow(id) { selectedId = id; renderAll(); }

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

  searchEl.addEventListener("input", function () { keyword = this.value.trim(); renderAll(); });
  filterEl.addEventListener("change", function () { filterState = this.value; renderAll(); });

  scenarioEl.addEventListener("change", function () {
    renderAll();
    var current = D.monitorScenarios.filter(function (s) { return s.key === scenarioEl.value; })[0];
    toast("[데모] " + (current ? current.label : scenarioEl.value) + " 시나리오를 적용했습니다.");
  });

  /* 확인 모달 — 네이티브 <dialog> 가 포커스 트랩·Escape 닫기·닫은 뒤 트리거 포커스 복원을
     전부 처리한다. 실제로 보내는 것은 confirm 버튼을 눌렀을 때뿐이다. */
  noticeBtn.addEventListener("click", function () {
    if (!selectedId) return;
    noticeDialog.showModal();
  });
  noticeCancel.addEventListener("click", function () { noticeDialog.close(); });
  noticeDialog.addEventListener("click", function (e) { if (e.target === noticeDialog) noticeDialog.close(); });

  noticeConfirm.addEventListener("click", function () {
    var p = D.learners.filter(function (x) { return x.id === selectedId; })[0];
    noticeDialog.close();
    if (!p) return;
    localEvents.push({
      id: "local-" + nowHHMMSS() + "-" + localEvents.length,
      at: nowHHMMSS(),
      learnerId: p.id,
      seconds: null,
      desc: "[데모] 안내 발송 기록 — " + p.name + "에게 안내를 보냈습니다(실제 발송·저장·출결 변경 없음)."
    });
    renderTimeline();
    toast("[데모] 실제 발송·저장·출결 변경 없음 — 화면 이벤트 로그에만 기록했습니다.");
  });

  /* ---------- 초기화 ---------- */
  renderAll();
})();
