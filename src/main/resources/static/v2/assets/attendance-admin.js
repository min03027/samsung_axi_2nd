/* ============================================================
   attendance-admin.js — 출결부·HRD 전송 모니터링 데모 (LXP-144 / LXP-146 / LXP-147)

   훈련생·인증 상태·전송 이력은 전부 모의 데이터다. 실제 통신은 없다 — 전송·재시도·
   보완 전송은 전부 AttendanceVerificationCommon.transitionTransferState() 를
   호출해 이 화면의 메모리 상태만 바꾼다.
   ============================================================ */
(function () {
  "use strict";

  var C = window.AttendanceVerificationCommon;
  var D = window.AttendanceVerificationDemoData;

  var RANK = { failed: 0, fallback_needed: 1, pending: 2, otp_verified: 3, success: 4 };
  var TONE = { failed: "risk", fallback_needed: "warn", pending: "warn", otp_verified: "ok", success: "ok" };
  var BUCKET_LABEL = {
    failed: "전송실패", fallback_needed: "모바일 보완 필요", pending: "전송대기",
    otp_verified: "OTP 인증완료", success: "전송성공"
  };
  /* "공단 미수신"(hrd_unreceived — OTP 는 성공했지만 공단이 나중에 못 받았다고 알려온
     경우)과 "OTP 미수신"(otp_unreceived — 훈련생이 OTP 자체를 못 받은 경우)은 원인이
     달라 같은 버킷(fallback_needed) 안에서도 문구는 코드별로 구분해야 한다 —
     BUCKET_LABEL 만 쓰면 전부 "모바일 보완 필요"로 뭉개져 원인이 뒤바뀐다. */
  var CODE_LABEL = { hrd_unreceived: "공단 미수신" };
  function transferLabelOf(c) { return CODE_LABEL[c.code] || BUCKET_LABEL[c.bucket] || c.bucket; }

  /* 인증상태는 "OTP 인증완료 여부"와 "모바일 보완 인증완료 여부" 두 독립된 축이다 —
     hrd_unreceived 를 거쳐 모바일 보완 인증까지 마치면 둘 다 참이 될 수 있으므로,
     하나의 authPath 값으로 뭉개면 먼저 있던 인증 이력이 사라진다(2차보완 결함1). */
  function authLabelOf(c) {
    if (c.otpVerified && c.mobileVerified) return "OTP 인증완료 · 모바일 보완완료";
    if (c.otpVerified) return "OTP 인증완료";
    if (c.mobileVerified) return "모바일 인증완료";
    return "OTP 미수신";
  }
  function matchesAuthFilter(c, filterValue) {
    if (filterValue === "all") return true;
    if (filterValue === "otp") return c.otpVerified === true;
    if (filterValue === "mobile") return c.mobileVerified === true;
    if (filterValue === "unreceived") return c.otpVerified !== true && c.mobileVerified !== true;
    return true;
  }

  /* 모바일 보완 경로의 3단계(공단 미수신 → 모바일 보완 인증 완료 처리 → 모바일 보완
     전송 → [실패 시] 재전송)는 버튼·모달을 그대로 재사용하되 각 단계의 의미가 서로
     달라, 문구가 뭉개지면 관리자가 지금 무엇을 확정하는지 알 수 없다(2차보완 결함2). */
  var FALLBACK_STAGE = {
    hrd_unreceived: {
      btnLabel: "모바일 보완 인증 완료 처리(데모)",
      dialogTitle: "모바일 보완 인증 완료로 반영하시겠습니까?",
      dialogBody: "실제 모바일 본인인증 연동이 아니라, 화면 상태만 '모바일 보완 인증 완료'로 바꾸는 데모입니다. OTP 인증 완료 이력은 그대로 유지됩니다.",
      confirmLabel: "인증 완료 반영(데모)"
    },
    mobile_verified: {
      btnLabel: "모바일 보완 전송(데모)",
      dialogTitle: "모바일 보완 전송을 진행하시겠습니까?",
      dialogBody: "모바일 본인인증이 완료된 훈련생의 데이터를 보완 전송하는 데모입니다. 실제 공단 전송은 없습니다.",
      confirmLabel: "보완 전송(데모)"
    },
    fallback_failed: {
      btnLabel: "모바일 보완 재전송(데모)",
      dialogTitle: "모바일 보완 전송을 재시도하시겠습니까?",
      dialogBody: "실패했던 모바일 보완 전송을 다시 시도하는 데모입니다. 실제 공단 전송은 없습니다.",
      confirmLabel: "보완 재전송(데모)"
    }
  };

  var selectedId = null;
  var keyword = "";
  var authFilterValue = "all";
  var transferFilterValue = "all";
  var overrides = {};      /* learnerId -> { code, retryCount } — 시나리오/액션이 원본 위에 덮어쓰는 값 */
  var localEvents = [];

  var searchEl = document.getElementById("peopleSearch");
  var authFilterEl = document.getElementById("authFilter");
  var transferFilterEl = document.getElementById("transferFilter");
  var scenarioEl = document.getElementById("scenarioSelect");
  var bodyEl = document.getElementById("peopleBody");
  var timelineBodyEl = document.getElementById("timelineBody");
  var countEl = document.getElementById("filterCount");
  var kpiTotalEl = document.getElementById("kpiTotal");
  var kpiOtpVerifiedEl = document.getElementById("kpiOtpVerified");
  var kpiPendingEl = document.getElementById("kpiPending");
  var kpiSuccessEl = document.getElementById("kpiSuccess");
  var kpiFailedEl = document.getElementById("kpiFailed");
  var kpiFallbackEl = document.getElementById("kpiFallback");
  var selNameEl = document.getElementById("selName");
  var selMetaEl = document.getElementById("selMeta");
  var selDetailEl = document.getElementById("selDetail");
  var sendBtn = document.getElementById("sendBtn");
  var retryBtn = document.getElementById("retryBtn");
  var fallbackBtn = document.getElementById("fallbackBtn");
  var retryDialog = document.getElementById("retryDialog");
  var retryConfirmBtn = document.getElementById("retryConfirmBtn");
  var retryCancelBtn = document.getElementById("retryCancelBtn");
  var fallbackDialog = document.getElementById("fallbackDialog");
  var fallbackDialogTitleEl = document.getElementById("fallbackDialogTitle");
  var fallbackDialogBodyEl = document.getElementById("fallbackDialogBody");
  var fallbackConfirmBtn = document.getElementById("fallbackConfirmBtn");
  var fallbackCancelBtn = document.getElementById("fallbackCancelBtn");

  document.getElementById("courseTitle").textContent = D.session.course;
  document.getElementById("unitLine").textContent = D.session.unit + " · " + D.session.scheduledAt;

  scenarioEl.innerHTML = D.adminScenarios.map(function (s) {
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

  function bucketOf(code) {
    if (code === "otp_verified") return "otp_verified";
    if (code === "hrd_queued" || code === "hrd_sending") return "pending";
    if (code === "hrd_success" || code === "fallback_sent") return "success";
    if (code === "hrd_failed" || code === "retrying") return "failed";
    if (code === "otp_unreceived" || code === "hrd_unreceived" || code === "mobile_verified" || code === "fallback_queued" || code === "fallback_failed") return "fallback_needed";
    return "pending";
  }

  /* 시나리오는 원본 학습자 데이터를 바꾸지 않고, "표시용 전송 상태"만 override 로 겹쳐
     보여준다 — 실제 상태 전이(transitionTransferState)는 이 override 를 시작점으로
     삼아 계속 이어간다. otp_verified/hrd_* 계열 baseline 코드는 구조적으로 항상 OTP
     성공을 거쳐야만 도달하므로, 이 시나리오들이 만드는 override 도 항상
     otpVerified: true 를 그대로 이어받는다(2차보완 결함1 — 시나리오를 적용해도 이미
     있던 OTP 인증 이력이 사라지면 안 된다). */
  function scenarioOverrideFor(learner) {
    var key = scenarioEl.value;
    if (key === "partial-failure" && (learner.transferCode === "hrd_queued" || learner.transferCode === "hrd_sending")) {
      /* 아직 전송을 시작하지 않은 otp_verified 는 건드리지 않는다 — "전송 대기·전송
         중"이었던 상태만 실패로 바뀐다. otp_verified 까지 실패로 바꾸면 아직 보내지도
         않은 인원이 재시도 가능한 실패 상태처럼 보이는 역행이 생긴다. */
      return { code: "hrd_failed", retryCount: learner.retryCount, otpVerified: learner.otpVerified };
    }
    if (key === "hrd-unreceived" && learner.transferCode === "hrd_success") {
      /* 실제 전이표를 거쳐 hrd_unreceived 로 보낸다 — otp_unreceived 로 잘못 매핑하면
         "OTP 자체를 못 받았다"는 것으로 원인이 뒤바뀐다(OTP 는 이미 성공했었다). */
      var t = C.transitionTransferState({ code: "hrd_success", retryCount: learner.retryCount, otpVerified: learner.otpVerified }, "MARK_HRD_UNRECEIVED");
      if (t.ok) return { code: t.code, retryCount: t.retryCount, otpVerified: t.otpVerified };
    }
    /* "fallback-failure" 시나리오는 여기서 즉시 아무도 바꾸지 않는다 — baseline
       학습자 중 fallback_queued 로 시작하는 사람이 없어 그런 분기는 절대 실행되지
       않는 죽은 코드였다(2차보완 결함7). 실제 실패는 fallbackConfirmBtn 클릭 시
       shouldFailNow("fallback") 이 결정한다 — 시나리오 선택 자체는 아무것도 바꾸지
       않고, 실제로 보완 전송을 실행해야 비로소 실패로 이어진다. */
    return null;
  }

  function effectiveState(learner) {
    if (overrides[learner.id]) return overrides[learner.id];
    var sc = scenarioOverrideFor(learner);
    if (sc) return sc;
    return { code: learner.transferCode, retryCount: learner.retryCount, otpVerified: learner.otpVerified };
  }

  function classify(learner) {
    var st = effectiveState(learner);
    /* override 가 있으면 관리자가 실제로 조치한 시각을, 없으면(시나리오만 적용됐거나
       원본 그대로면) 데모 데이터의 baseline 시각을 그대로 쓴다 — 조치 후에는 이 값과
       타임라인 최신 이벤트 시각이 항상 같아야 한다(§P1-E). */
    var lastProcessedAt = st.lastProcessedAt || learner.lastProcessedAt;
    return {
      code: st.code, retryCount: st.retryCount, lastProcessedAt: lastProcessedAt, bucket: bucketOf(st.code),
      otpVerified: !!st.otpVerified, mobileVerified: C.isMobileIdentityDone(st.code)
    };
  }

  function visibleSorted() {
    var k = keyword.toLowerCase();
    var rows = D.learners
      .map(function (p) { return { p: p, c: classify(p) }; })
      .filter(function (row) {
        if (!matchesAuthFilter(row.c, authFilterValue)) return false;
        if (transferFilterValue !== "all" && row.c.bucket !== transferFilterValue) return false;
        if (!k) return true;
        return row.p.name.toLowerCase().indexOf(k) > -1 || row.p.seat.toLowerCase().indexOf(k) > -1;
      });
    rows.sort(function (a, b) {
      var r = RANK[a.c.bucket] - RANK[b.c.bucket];
      if (r !== 0) return r;
      return a.p.name < b.p.name ? -1 : a.p.name > b.p.name ? 1 : 0;
    });
    return rows;
  }

  function reconcileSelection(rows) {
    if (rows.some(function (r) { return r.p.id === selectedId; })) return;
    selectedId = rows.length ? rows[0].p.id : null;
  }

  /* "OTP 인증완료" KPI 와 전송상태 버킷(전송대기/성공/실패/보완필요)은 서로 다른 축이다
     — OTP 로 인증에 성공한 사람은 이후 HRD 전송이 대기·성공·실패 어디로 가든 여전히
     "OTP 로 인증을 완료한 사람"이다. bucket 만으로 세면(예전 방식) HRD 로 넘어간
     사람이 전부 빠져 인증상태 필터(=OTP 인증완료)와 어긋난다 — KPI 는 전송상태와
     겹치는 중첩 지표일 수 있으므로 합이 전체 인원과 같다고 가정하지 않는다. */
  function renderKPI() {
    var counts = { pending: 0, success: 0, failed: 0, fallback_needed: 0 };
    var otpCount = 0;
    D.learners.forEach(function (p) {
      var c = classify(p);
      counts[c.bucket]++;
      if (c.otpVerified) otpCount++;
    });
    kpiTotalEl.textContent = D.learners.length;
    kpiOtpVerifiedEl.textContent = otpCount;
    kpiPendingEl.textContent = counts.pending;
    kpiSuccessEl.textContent = counts.success;
    kpiFailedEl.textContent = counts.failed;
    kpiFallbackEl.textContent = counts.fallback_needed;
  }

  function renderTable(rows) {
    countEl.textContent = rows.length + "명 표시 중 (전체 " + D.learners.length + "명)";
    /* innerHTML 재대입은 기존 자식을 통째로 지웠다 새로 만든다 — 키보드로 표 안에
       포커스가 있었다면 그 포커스가 사라진다. 재렌더링 뒤 같은 훈련생 행에 포커스를
       복원해 키보드 사용자가 위치를 잃지 않게 한다(§ 결과 보고에 한계를 기록). */
    var hadFocusInTable = document.activeElement && bodyEl.contains(document.activeElement);
    if (!rows.length) {
      bodyEl.innerHTML = '<tr><td colspan="8" class="hint-text">조건에 맞는 훈련생이 없습니다. 검색어나 필터를 바꿔 보세요.</td></tr>';
      return;
    }
    bodyEl.innerHTML = rows.map(function (row) {
      var p = row.p, c = row.c;
      return '<tr class="people-row' + (p.id === selectedId ? " is-selected" : "") + '" data-id="' + p.id +
        '" tabindex="0" role="button" aria-pressed="' + (p.id === selectedId) + '">' +
        "<td>" + escText(p.name) + "</td>" +
        "<td>" + escText(p.seat) + "</td>" +
        '<td class="mono">' + C.formatDuration(p.connectedSeconds) + "</td>" +
        '<td class="mono">' + C.formatDuration(p.verifiedSeconds) + "</td>" +
        "<td>" + escText(authLabelOf(c)) + "</td>" +
        '<td><span class="state-badge ' + (TONE[c.bucket] || "") + '">' + escText(transferLabelOf(c)) + "</span></td>" +
        '<td class="mono">' + c.retryCount + "회</td>" +
        '<td class="nowrap">' + escText(c.lastProcessedAt) + "</td>" +
      "</tr>";
    }).join("");
    if (hadFocusInTable && selectedId) {
      var row = bodyEl.querySelector('[data-id="' + selectedId + '"]');
      if (row && row.focus) row.focus();
    }
  }

  function renderSelected(rows) {
    var row = rows.filter(function (r) { return r.p.id === selectedId; })[0];
    if (!row) {
      selNameEl.textContent = "훈련생을 선택하세요";
      selMetaEl.textContent = "";
      selDetailEl.textContent = "";
      sendBtn.disabled = true; retryBtn.disabled = true; fallbackBtn.disabled = true;
      return;
    }
    var p = row.p, c = row.c;
    selNameEl.textContent = p.name + " (" + p.seat + ")";
    selMetaEl.textContent = "접속 " + C.formatDuration(p.connectedSeconds) + " · 확인된 체류시간 " + C.formatDuration(p.verifiedSeconds) +
      " · 인증상태 " + authLabelOf(c) + " · 전송상태 " + transferLabelOf(c);
    selDetailEl.textContent = "재시도 " + c.retryCount + "회 · 마지막 처리 " + c.lastProcessedAt;

    sendBtn.disabled = c.code !== "otp_verified";
    retryBtn.disabled = c.code !== "hrd_failed";
    /* 모바일 보완 경로는 hrd_unreceived(인증 완료 처리) → mobile_verified(최초 전송)
       → fallback_failed(재전송) 3단계를 같은 버튼·모달로 받되, 단계마다 버튼·제목·
       본문·확인 문구를 전부 다르게 보여준다(2차보완 결함2). */
    var stage = FALLBACK_STAGE[c.code];
    fallbackBtn.disabled = !stage;
    fallbackBtn.textContent = stage ? stage.btnLabel : "모바일 보완 전송(데모)";
    fallbackDialogTitleEl.textContent = stage ? stage.dialogTitle : "모바일 보완 전송을 진행하시겠습니까?";
    fallbackDialogBodyEl.textContent = stage ? stage.dialogBody : "";
    fallbackConfirmBtn.textContent = stage ? stage.confirmLabel : "보완 전송(데모)";
  }

  /* 이벤트 시각(at)은 "HH:MM:SS" 문자열이라 자정을 넘나들면(예: 모의 데이터는
     19시대 고정, 실제 관리자 조치는 nowHHMMSS() 로 자정 이후 실행) 문자열 비교만으로는
     방금 일어난 조치가 오래된 모의 이벤트보다 "이르다"고 잘못 판정될 수 있다 — 발생
     순서(모의 데이터 원본 순서 뒤에 조치가 실제로 일어난 순서)로만 최신순을 매긴다. */
  function allEvents() {
    var seq = 0;
    var withSeq = D.transferEvents.map(function (ev) { return { ev: ev, seq: seq++ }; })
      .concat(localEvents.map(function (ev) { return { ev: ev, seq: seq++ }; }));
    return withSeq.sort(function (a, b) { return b.seq - a.seq; }).map(function (x) { return x.ev; });
  }

  function renderTimeline() {
    var byId = {};
    D.learners.forEach(function (p) { byId[p.id] = p.name; });
    var rows = allEvents();
    if (!rows.length) {
      timelineBodyEl.innerHTML = '<tr><td colspan="3" class="hint-text">기록된 이벤트가 없습니다.</td></tr>';
      return;
    }
    timelineBodyEl.innerHTML = rows.map(function (ev) {
      return "<tr" + (ev.learnerId === selectedId ? ' class="is-selected"' : "") + ">" +
        '<td class="nowrap mono">' + escText(ev.at) + "</td>" +
        '<td class="nowrap">' + escText(byId[ev.learnerId] || ev.learnerId) + "</td>" +
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

  /* ---------- 토스트 ---------- */
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

  /* ---------- 시나리오에 따른 성공/실패 결정 ----------
     "성공·실패 결과는 선택한 데모 시나리오로 결정" — 시나리오별로 실패를 유도해야
     하는 흐름을 각각 골라 하나의 규칙으로 묶는다. */
  function shouldFailNow(kind) {
    var key = scenarioEl.value;
    if (kind === "send" || kind === "retry") return key === "partial-failure";
    if (kind === "fallback") return key === "fallback-failure";
    return false;
  }

  function findLearner(id) { return D.learners.filter(function (x) { return x.id === id; })[0]; }

  /** 관리자 조치 한 건은 override 의 lastProcessedAt 과 타임라인 이벤트가 반드시 같은
      시각을 가리켜야 한다 — 호출부가 각자 nowHHMMSS() 를 다시 부르면 초 경계에서
      두 값이 어긋날 수 있어, 이 함수 하나가 만든 시각을 그대로 override 에도 넣는다. */
  function applyOutcome(learner, st, desc) {
    var ts = nowHHMMSS();
    overrides[learner.id] = { code: st.code, retryCount: st.retryCount, otpVerified: st.otpVerified, lastProcessedAt: ts };
    localEvents.push({ id: "local-" + ts + "-" + localEvents.length, at: ts, learnerId: learner.id, desc: desc });
  }

  /* ---------- 액션: 전송 시작(확인 dialog 없음 — 정상 경로) ---------- */
  sendBtn.addEventListener("click", function () {
    var learner = findLearner(selectedId);
    if (!learner) return;
    var st = effectiveState(learner);
    if (st.code !== "otp_verified") return;
    st = C.transitionTransferState(st, "QUEUE");
    st = C.transitionTransferState(st, "SEND");
    st = C.transitionTransferState(st, shouldFailNow("send") ? "FAIL" : "SUCCEED");
    applyOutcome(learner, st, st.code === "hrd_success" ? "[데모] HRD 전송 성공." : "[데모] HRD 전송 실패(모의).");
    toast("[데모] 실제 공단 전송 없음 — 화면 상태만 갱신했습니다.");
    renderAll();
  });

  /* ---------- 액션: 재시도(확인 dialog) — HRD 일반 전송 전용 ---------- */
  retryBtn.addEventListener("click", function () {
    var learner = findLearner(selectedId);
    if (!learner) return;
    var st = effectiveState(learner);
    if (st.code !== "hrd_failed") return;
    retryDialog.showModal();
  });
  retryCancelBtn.addEventListener("click", function () { retryDialog.close(); });
  retryDialog.addEventListener("click", function (e) { if (e.target === retryDialog) retryDialog.close(); });
  retryConfirmBtn.addEventListener("click", function () {
    retryDialog.close();
    var learner = findLearner(selectedId);
    if (!learner) return;
    var st = effectiveState(learner);
    if (st.code !== "hrd_failed") return;
    st = C.transitionTransferState(st, "RETRY");
    st = C.transitionTransferState(st, shouldFailNow("retry") ? "FAIL" : "SUCCEED");
    applyOutcome(learner, st, st.code === "hrd_success" ? "[데모] 재시도 후 전송 성공." : "[데모] 재시도했지만 다시 실패(모의).");
    toast("[데모] 실제 재전송 없음 — 화면 상태만 갱신했습니다.");
    renderAll();
  });

  /* ---------- 액션: 모바일 보완 경로(확인 dialog) — hrd_unreceived(인증 완료 처리) →
     mobile_verified(최초 전송) → fallback_failed(재전송) 3단계를 같은 버튼으로
     받되, 단계마다 실행하는 전이·이벤트 문구를 다르게 남긴다(2차보완 결함1·2). ---------- */
  fallbackBtn.addEventListener("click", function () {
    var learner = findLearner(selectedId);
    if (!learner) return;
    var st = effectiveState(learner);
    if (!FALLBACK_STAGE[st.code]) return;
    fallbackDialog.showModal();
  });
  fallbackCancelBtn.addEventListener("click", function () { fallbackDialog.close(); });
  fallbackDialog.addEventListener("click", function (e) { if (e.target === fallbackDialog) fallbackDialog.close(); });
  fallbackConfirmBtn.addEventListener("click", function () {
    fallbackDialog.close();
    var learner = findLearner(selectedId);
    if (!learner) return;
    var st = effectiveState(learner);

    if (st.code === "hrd_unreceived") {
      /* 공단 미수신 → 모바일 보완 인증 완료로만 반영한다 — 전송까지 한 번에 밀어붙이지
         않는다. 전이가 거부되면(ok:false) override·타임라인·토스트를 전혀 건드리지
         않는다. */
      var marked = C.transitionTransferState(st, "MOBILE_VERIFIED");
      if (!marked.ok) { renderAll(); return; }
      applyOutcome(learner, marked, "[데모] 모바일 보완 인증 완료로 반영했습니다(공단 미수신 → 모바일 보완, OTP 인증 이력 유지).");
      toast("[데모] 실제 인증 연동 없음 — 화면 상태만 갱신했습니다.");
      renderAll();
      return;
    }

    var isRetry = st.code === "fallback_failed";
    if (st.code !== "mobile_verified" && !isRetry) { renderAll(); return; }
    var queued = C.transitionTransferState(st, isRetry ? "RETRY_FALLBACK" : "QUEUE_FALLBACK");
    if (!queued.ok) { renderAll(); return; }
    var sent = C.transitionTransferState(queued, shouldFailNow("fallback") ? "FAIL_FALLBACK" : "SEND_FALLBACK");
    var successDesc = isRetry ? "[데모] 모바일 보완 재전송 성공." : "[데모] 모바일 보완 전송 성공.";
    var failDesc = isRetry ? "[데모] 모바일 보완 재전송도 다시 실패(모의)." : "[데모] 모바일 보완 전송 실패(모의).";
    applyOutcome(learner, sent, sent.code === "fallback_sent" ? successDesc : failDesc);
    toast("[데모] 실제 공단 전송 없음 — 화면 상태만 갱신했습니다.");
    renderAll();
  });

  /* ---------- 검색·필터·시나리오 ---------- */
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
  authFilterEl.addEventListener("change", function () { authFilterValue = this.value; renderAll(); });
  transferFilterEl.addEventListener("change", function () { transferFilterValue = this.value; renderAll(); });

  scenarioEl.addEventListener("change", function () {
    renderAll();
    var current = D.adminScenarios.filter(function (s) { return s.key === scenarioEl.value; })[0];
    toast("[데모] " + (current ? current.label : scenarioEl.value) + " 시나리오를 적용했습니다.");
  });

  /* ---------- 초기화 ---------- */
  renderAll();

  /* 테스트 전용 훅 — 행 선택은 실제 브라우저에서 클릭 위임(closest())으로 이뤄지는데
     가벼운 Node 스텁은 innerHTML 문자열을 실제 자식 엘리먼트 트리로 만들지 않아
     클릭 위임을 그대로 재현할 수 없다. 화면 동작과 다른 별도 로직을 새로 만드는
     대신, 클릭 핸들러가 실제로 쓰는 selectRow() 그 자체를 노출해 테스트가 "행을
     고른다"는 사용자 동작을 동일한 코드 경로로 실행하게 한다. 운영 전역 오염을
     막기 위해 이름 하나만 노출한다. */
  if (typeof window !== "undefined") {
    window.__attendanceAdminTest = { selectLearner: selectRow };
  }
})();
