/* ============================================================
   exam-precheck.js — 응시환경 사전점검 (LXP-018 / LXP-019)

   실제 브라우저 API 를 쓰는 부분:
     - navigator.mediaDevices.getUserMedia   (카메라)
     - navigator.mediaDevices.getDisplayMedia(화면 공유)
     - Fullscreen API
     - screen.isExtended                     (다중 모니터)

   시뮬레이션인 부분:
     - 감독 서버 연결, 녹화 저장, 신분증 심사

   권한이 도중에 끊기면(track ended) 즉시 실패로 되돌리고 입장을 다시 잠근다.
   ============================================================ */

(function () {
  "use strict";

  var D = window.LXP_EXAM_DATA;
  var E = window.ExamDemo;

  var streams = { camera: null, display: null };

  var LABEL = {
    idle: "미확인", checking: "확인 중", pass: "통과",
    fail: "실패", unsupported: "브라우저 확인 불가"
  };
  var TONE = { pass: "ok", fail: "risk", checking: "warn", unsupported: "warn", idle: "" };
  /* 배지 클래스는 exam.css 의 .state-badge 문법을 쓴다 (운영 화면과 같은 상태색). */

  function cardOf(key) { return document.querySelector('[data-check="' + key + '"]'); }

  function setState(key, state, message, tone) {
    var card = cardOf(key);
    if (!card) return;
    card.dataset.state = state;

    var badge = card.querySelector('[data-role="badge"]');
    badge.textContent = LABEL[state] || state;
    badge.className = "state-badge" + (TONE[state] ? " " + TONE[state] : "");

    var msg = card.querySelector('[data-role="msg"]');
    if (msg) {
      msg.textContent = message || "";
      if (tone) msg.dataset.tone = tone; else msg.removeAttribute("data-tone");
    }

    E.patch(function (s) { s.checks[key] = state; });
    syncGate();
  }

  /* ---------- 미디어 공통 ---------- */
  function attach(key, stream) {
    var card = cardOf(key);
    var video = card.querySelector('[data-role="preview"]');
    var ph = card.querySelector('[data-role="placeholder"]');
    video.srcObject = stream;
    video.hidden = false;
    if (ph) ph.hidden = true;
    video.play().catch(function () { /* 자동재생 차단은 미리보기에만 영향 */ });

    /* 사용자가 브라우저 UI 로 공유를 중단하면 track 이 ended 된다 */
    stream.getTracks().forEach(function (t) {
      t.addEventListener("ended", function () {
        detach(key);
        setState(key, "fail",
          key === "camera" ? "카메라 연결이 끊겼습니다. 다시 켜 주세요."
                           : "화면 공유가 중단되었습니다. 다시 시작해 주세요.", "risk");
        E.toast(key === "camera" ? "카메라가 꺼졌습니다" : "화면 공유가 중단되었습니다", "risk");
      });
    });
  }

  function detach(key) {
    var s = streams[key];
    if (s) { s.getTracks().forEach(function (t) { t.stop(); }); streams[key] = null; }
    var card = cardOf(key);
    var video = card.querySelector('[data-role="preview"]');
    var ph = card.querySelector('[data-role="placeholder"]');
    video.srcObject = null;
    video.hidden = true;
    if (ph) ph.hidden = false;
    toggle(key, false);
  }

  function toggle(key, on) {
    var card = cardOf(key);
    var start = card.querySelector('[data-action="' + key + '-start"]');
    var stop = card.querySelector('[data-action="' + key + '-stop"]');
    if (start) start.disabled = on;
    if (stop) stop.disabled = !on;
  }

  /* 권한 실패 원인을 구분해 안내한다 — 무엇을 해야 하는지가 달라진다 */
  function mediaError(err, kind) {
    var name = err && err.name;
    if (name === "NotAllowedError" || name === "SecurityError") {
      return kind + " 권한이 거부되었습니다. 주소창의 권한 아이콘에서 허용으로 바꾼 뒤 다시 시도하세요.";
    }
    if (name === "NotFoundError" || name === "DevicesNotFoundError") {
      return kind + " 장치를 찾을 수 없습니다. 기기 연결을 확인하세요.";
    }
    if (name === "NotReadableError") {
      return "다른 프로그램이 " + kind + "을(를) 사용 중입니다. 해당 프로그램을 종료한 뒤 다시 시도하세요.";
    }
    if (name === "AbortError") return kind + " 요청이 취소되었습니다.";
    return kind + " 확인에 실패했습니다: " + (name || "알 수 없는 오류");
  }

  function secureContextProblem() {
    if (window.isSecureContext) return null;
    return "이 페이지가 보안 연결(HTTPS)이 아니어서 브라우저가 카메라·화면 공유를 차단합니다. https 주소 또는 localhost 에서 열어 주세요.";
  }

  /* ---------- 카메라 ---------- */
  function startCamera() {
    var blocked = secureContextProblem();
    if (blocked) { setState("camera", "fail", blocked, "risk"); return; }
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      setState("camera", "unsupported", "이 브라우저는 카메라 API 를 지원하지 않습니다. 최신 Chrome·Edge·Safari 를 사용하세요.", "risk");
      return;
    }
    setState("camera", "checking", "카메라 권한을 요청하는 중입니다…");
    navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 360 }, audio: false })
      .then(function (stream) {
        streams.camera = stream;
        attach("camera", stream);
        toggle("camera", true);
        setState("camera", "pass", "카메라가 연결되었습니다. 미리보기는 저장되지 않습니다.", "ok");
      })
      .catch(function (err) { setState("camera", "fail", mediaError(err, "카메라"), "risk"); });
  }

  /* ---------- 화면 공유 ---------- */
  function startDisplay() {
    var blocked = secureContextProblem();
    if (blocked) { setState("display", "fail", blocked, "risk"); return; }
    if (!navigator.mediaDevices || !navigator.mediaDevices.getDisplayMedia) {
      setState("display", "unsupported", "이 브라우저는 화면 공유 API 를 지원하지 않습니다. 데스크톱 Chrome·Edge 를 사용하세요.", "risk");
      return;
    }
    setState("display", "checking", "공유할 화면을 선택해 주세요…");
    navigator.mediaDevices.getDisplayMedia({ video: true, audio: false })
      .then(function (stream) {
        streams.display = stream;
        attach("display", stream);
        toggle("display", true);

        /* 전체 화면이 아닌 창·탭 공유는 감독 요건을 만족하지 않는다 */
        var track = stream.getVideoTracks()[0];
        var surface = track && track.getSettings && track.getSettings().displaySurface;
        if (surface && surface !== "monitor") {
          /* 실패한 공유를 먼저 정리한다 — 안 그러면 시작 버튼이 disabled 로 남아
             안내대로 "다시 공유"를 눌러도 아무 일이 없다(중지를 먼저 눌러야 했다). */
          detach("display");
          setState("display", "fail",
            "창 또는 탭만 공유되었습니다. 전체 화면(모니터)을 선택해 다시 공유해 주세요.", "risk");
          return;
        }
        setState("display", "pass",
          surface ? "전체 화면이 공유되었습니다. 미리보기는 저장되지 않습니다."
                  : "화면이 공유되었습니다. 브라우저가 공유 범위를 알려주지 않아 전체 화면 여부는 확인하지 못했습니다.",
          "ok");
      })
      .catch(function (err) { setState("display", "fail", mediaError(err, "화면 공유"), "risk"); });
  }

  /* ---------- 전체화면 ---------- */
  function enterFullscreen() {
    var el = document.documentElement;
    if (!el.requestFullscreen) {
      setState("fullscreen", "unsupported", "이 브라우저는 전체화면 API 를 지원하지 않습니다.", "risk");
      return;
    }
    setState("fullscreen", "checking", "전체화면 전환을 요청하는 중입니다…");
    el.requestFullscreen().then(function () {
      setState("fullscreen", "pass", "전체화면이 적용되었습니다.", "ok");
    }).catch(function (err) {
      setState("fullscreen", "fail", "전체화면 전환이 거부되었습니다: " + (err && err.name ? err.name : "알 수 없는 오류"), "risk");
    });
  }

  document.addEventListener("fullscreenchange", function () {
    var card = cardOf("fullscreen");
    var exit = card.querySelector('[data-action="fullscreen-exit"]');
    var enter = card.querySelector('[data-action="fullscreen-enter"]');
    if (document.fullscreenElement) {
      exit.disabled = false; enter.disabled = true;
    } else {
      exit.disabled = true; enter.disabled = false;
      if (E.load().checks.fullscreen === "pass") {
        setState("fullscreen", "fail", "전체화면이 해제되었습니다. 다시 확인해 주세요.", "risk");
      }
    }
  });

  /* ---------- 다중 모니터 (LXP-019) ---------- */
  function checkMonitor() {
    var card = cardOf("multiMonitor");
    var fallback = card.querySelector('[data-role="monitor-fallback"]');

    if (typeof window.screen !== "undefined" && typeof window.screen.isExtended === "boolean") {
      E.patch(function (s) { s.multiMonitorSource = "api"; });
      fallback.hidden = true;
      if (window.screen.isExtended) {
        setState("multiMonitor", "fail",
          "확장 디스플레이가 감지되었습니다. 보조 모니터 연결을 해제한 뒤 다시 확인해 주세요.", "risk");
      } else {
        setState("multiMonitor", "pass", "단일 모니터만 연결되어 있습니다.", "ok");
      }
      return;
    }

    /* 값이 없으면 통과시키지 않는다 — 자동 통과는 감독 허점이 된다 */
    E.patch(function (s) { s.multiMonitorSource = "unknown"; });
    fallback.hidden = false;
    setState("multiMonitor", "unsupported",
      "이 브라우저는 확장 디스플레이 여부를 제공하지 않습니다(screen.isExtended 미지원). 실제 운영에서는 감독관 육안 확인이 필요합니다.", "warn");
  }

  function monitorDemo(value) {
    E.patch(function (s) { s.multiMonitorSource = "demo"; });
    if (value === "multi") {
      setState("multiMonitor", "fail", "[데모] 확장 디스플레이 연결 상태를 선택했습니다. 입장이 차단됩니다.", "risk");
    } else {
      setState("multiMonitor", "pass", "[데모] 단일 모니터 상태를 선택했습니다.", "ok");
    }
  }

  /* ---------- 신분 확인 (LXP-015) ---------- */
  function refreshIdentity(quiet) {
    var st = E.load();
    var status = st.identity.status;
    if (status === "승인") {
      setState("identity", "pass", "신분증이 승인되었습니다(데모 심사). 제출 파일: " + (st.identity.fileName || "이름 없음"), "ok");
    } else if (status === "검토 중") {
      setState("identity", "checking", "제출된 신분증을 검토하는 중입니다(데모).");
    } else if (status === "재제출") {
      setState("identity", "fail", "신분증을 다시 제출해 주세요.", "risk");
    } else {
      setState("identity", "idle", "아직 제출되지 않았습니다. 휴대폰에서 신분증 화면을 열어 제출하세요.");
    }
    if (!quiet) E.toast("신분 확인 상태를 새로 읽었습니다");
  }

  /* ---------- 입장 게이트 (LXP-018) ---------- */
  function syncGate() {
    var st = E.load();
    var passed = D.checkKeys.filter(function (k) { return st.checks[k] === "pass"; });
    var ok = passed.length === D.checkKeys.length;

    var btn = document.getElementById("enterBtn");
    var msg = document.getElementById("gateMsg");
    var line = document.getElementById("progressLine");

    line.textContent = "통과 " + passed.length + " / " + D.checkKeys.length + "개 항목";

    if (ok) {
      btn.removeAttribute("aria-disabled");
      btn.removeAttribute("tabindex");
      btn.classList.remove("btn-secondary");
      btn.classList.add("btn-primary");
      msg.textContent = "모든 조건을 만족했습니다. 시험장에 입장할 수 있습니다.";
      E.patch(function (s) { s.entered = false; });
    } else {
      btn.setAttribute("aria-disabled", "true");
      btn.setAttribute("tabindex", "-1");
      btn.classList.add("btn-secondary");
      btn.classList.remove("btn-primary");
      var pending = D.checkKeys.filter(function (k) { return st.checks[k] !== "pass"; });
      var names = { camera: "카메라", display: "화면 공유", fullscreen: "전체화면", multiMonitor: "다중 모니터", identity: "신분 확인" };
      msg.textContent = "남은 항목: " + pending.map(function (k) { return names[k]; }).join(", ");
    }
  }

  /* 비활성 상태에서 클릭·엔터가 들어와도 이동을 막는다 */
  document.getElementById("enterBtn").addEventListener("click", function (e) {
    if (this.getAttribute("aria-disabled") === "true") {
      e.preventDefault();
      E.toast("아직 통과하지 못한 점검 항목이 있습니다", "warn");
    }
  });

  document.getElementById("simulateAll").addEventListener("click", function () {
    D.checkKeys.forEach(function (k) {
      setState(k, "pass", "[데모] 시뮬레이션으로 통과 처리했습니다.", "ok");
    });
    E.patch(function (s) { s.identity.status = "승인"; s.multiMonitorSource = "demo"; });
    E.toast("[데모] 전체 조건을 통과 상태로 만들었습니다", "warn");
  });

  /* ---------- 이벤트 바인딩 ---------- */
  document.addEventListener("click", function (e) {
    var btn = e.target.closest && e.target.closest("[data-action]");
    if (!btn) return;
    var a = btn.dataset.action;
    if (a === "camera-start") startCamera();
    else if (a === "camera-stop") { detach("camera"); setState("camera", "idle", "카메라를 껐습니다."); }
    else if (a === "display-start") startDisplay();
    else if (a === "display-stop") { detach("display"); setState("display", "idle", "화면 공유를 중지했습니다."); }
    else if (a === "fullscreen-enter") enterFullscreen();
    else if (a === "fullscreen-exit" && document.exitFullscreen) document.exitFullscreen();
    else if (a === "monitor-check") checkMonitor();
    else if (a === "monitor-demo") monitorDemo(btn.dataset.value);
    else if (a === "identity-refresh") refreshIdentity(false);
  });

  /* 페이지를 떠날 때 track 을 반드시 정리한다 — 카메라 표시등이 켜진 채 남지 않도록 */
  window.addEventListener("pagehide", function () {
    Object.keys(streams).forEach(function (k) {
      if (streams[k]) streams[k].getTracks().forEach(function (t) { t.stop(); });
    });
  });

  /* ---------- 초기 상태 복원 ----------
     미디어 권한은 페이지를 새로 열면 유지되지 않으므로 idle 로 되돌린다. */
  (function init() {
    E.patch(function (s) {
      if (s.checks.camera === "pass") s.checks.camera = "idle";
      if (s.checks.display === "pass") s.checks.display = "idle";
      if (s.checks.fullscreen === "pass" && !document.fullscreenElement) s.checks.fullscreen = "idle";
    });
    var st = E.load();
    ["camera", "display", "fullscreen", "multiMonitor"].forEach(function (k) {
      var v = st.checks[k] || "idle";
      setState(k, v, v === "idle" ? "" : undefined);
    });
    refreshIdentity(true);
    syncGate();
  })();
})();
