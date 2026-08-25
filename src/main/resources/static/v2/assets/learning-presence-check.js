/* ============================================================
   learning-presence-check.js — 학습 참여 확인 데모 (LXP-140 / LXP-141 / LXP-142)

   실제로 하는 일: 카메라 로컬 미리보기, 데모 시나리오에 따른 참여 상태·학습시간
   계산과 화면 갱신. 실제 얼굴 검출·특징값 생성·영상 프레임 읽기·저장·업로드는
   하지 않는다 — "참여 상태"는 오직 데모 시나리오 select 의 값으로만 계산된다.

   비동기 경합 방지: 카메라는 LiveClassCommon 과 같은 원리의
   LearningPresence.createMediaSlot() 을 쓴다. 재클릭·장치 재선택으로 여러
   getUserMedia 요청이 겹쳐도 가장 최근 요청만 채택되고 나머지는 도착 즉시 정지된다.
   ============================================================ */
(function () {
  "use strict";

  var C = window.LearningPresence;
  var D = window.LearningPresenceDemoData;

  var consentCheckbox = document.getElementById("consentCheckbox");
  var cameraSelect = document.getElementById("cameraSelect");
  var startBtn = document.getElementById("startBtn");
  var stopBtn = document.getElementById("stopBtn");
  var retryBtn = document.getElementById("retryBtn");
  var selfPreview = document.getElementById("selfPreview");
  var selfPlaceholder = document.getElementById("selfPlaceholder");
  var cameraMsgEl = document.getElementById("cameraMsg");
  var scenarioSelect = document.getElementById("scenarioSelect");
  var scenarioDescEl = document.getElementById("scenarioDesc");
  var stateBadgeEl = document.getElementById("stateBadge");
  var stateMsgEl = document.getElementById("stateMsg");
  var connectedTimeEl = document.getElementById("connectedTime");
  var verifiedTimeEl = document.getElementById("verifiedTime");
  var awayTimeEl = document.getElementById("awayTime");
  var recognizedTimeEl = document.getElementById("recognizedTime");
  var lastCheckedAtEl = document.getElementById("lastCheckedAt");
  var nextCheckedAtEl = document.getElementById("nextCheckedAt");
  var eventListEl = document.getElementById("eventList");

  var mediaSlot = C.createMediaSlot();
  var currentStream = null;
  var activeDeviceId = null;
  var cameraState = "idle";   /* idle | checking | active | fail */

  /* connectedSeconds/verifiedSeconds/cumulativeAwaySeconds 는 이 페이지 세션 전체의
     누적값이라 카메라를 중지했다가 다시 시작해도 줄지 않는다. currentAwaySeconds 만
     "지금 이어지고 있는 자리 이탈 구간"의 길이라 정상으로 돌아오는 순간 0 이 된다.
     실제 갱신은 매 초 C.advanceLearningClock() 하나로만 한다(여기서 +1 하지 않는다). */
  var connectedSeconds = 0, verifiedSeconds = 0, cumulativeAwaySeconds = 0, currentAwaySeconds = 0;
  var ticksSinceCheck = 0;
  var timerHandle = null;
  var warnedThisEpisode = false, focusedThisEpisode = false;
  var events = [];

  function pad(n) { return (n < 10 ? "0" : "") + n; }

  function nowHHMMSS() {
    var d = new Date();
    return pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
  }

  function formatHMS(totalSeconds) {
    var total = Math.max(0, Math.floor(totalSeconds));
    var h = Math.floor(total / 3600);
    var m = Math.floor((total % 3600) / 60);
    var s = total % 60;
    return pad(h) + ":" + pad(m) + ":" + pad(s);
  }

  function addEvent(text) {
    events.unshift({ at: nowHHMMSS(), text: text });
    if (events.length > 20) events.length = 20;
    renderEvents();
  }

  function renderEvents() {
    eventListEl.innerHTML = "";
    events.forEach(function (e) {
      var li = document.createElement("li");
      var time = document.createElement("span");
      time.className = "mono";
      time.textContent = e.at;
      li.appendChild(time);
      li.appendChild(document.createTextNode(" " + e.text));   /* textContent 계열만 쓴다 — HTML 삽입 없음 */
      eventListEl.appendChild(li);
    });
  }

  /* ---------- 지원 여부 ---------- */
  var supported = (function checkSupport() {
    if (!window.isSecureContext) {
      cameraMsgEl.textContent = "이 페이지가 보안 연결이 아니어서 브라우저가 카메라를 차단합니다. localhost 또는 HTTPS에서 열어 주세요.";
      cameraMsgEl.dataset.tone = "risk";
      return false;
    }
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      cameraMsgEl.textContent = "이 브라우저는 카메라 API를 지원하지 않습니다. 최신 Chrome, Edge, Safari를 사용하세요.";
      cameraMsgEl.dataset.tone = "risk";
      return false;
    }
    return true;
  })();

  /* ---------- 버튼·select 상태 동기화 ---------- */
  function syncButtons() {
    var consented = consentCheckbox.checked;
    startBtn.disabled = !supported || !consented || cameraState === "checking" || cameraState === "active";
    stopBtn.disabled = cameraState !== "active";
    retryBtn.hidden = cameraState !== "fail";
    retryBtn.disabled = !supported || !consented;
    cameraSelect.disabled = cameraState === "checking" || cameraSelect.options.length === 0;
  }

  function setCameraState(state) {
    cameraState = state;
    syncButtons();
  }

  function setCameraMsg(text, tone) {
    cameraMsgEl.textContent = text || "";
    if (tone) cameraMsgEl.dataset.tone = tone; else cameraMsgEl.removeAttribute("data-tone");
  }

  /* reject 경로와 no-track 경로가 "기존 연결 유지 + select 를 실제 활성 장치로 복원" 이라는
     같은 정책을 쓰게 하나로 묶는다(LXP-125/127 precheck.js 와 동일 정책). */
  function selectHasOption(value) {
    for (var i = 0; i < cameraSelect.options.length; i++) {
      if (cameraSelect.options[i].value === value) return true;
    }
    return false;
  }

  /* activeDeviceId 는 track.getSettings().deviceId 를 그대로 담을 수 있는데, 이 값이
     select 의 실제 옵션과 항상 일치한다는 보장이 없다(예: canvas.captureStream() 처럼
     enumerateDevices() 목록에 없는 합성 트랙은 브라우저 내부용 임의의 deviceId 를
     돌려준다). 존재하지 않는 값을 그대로 대입하면 select 가 선택 없음(value="")
     상태로 무너진다 — 반드시 지금 목록에 있는 값인지 확인한 뒤에만 대입한다. */
  function restoreSelectAfterFailedSwitch(message) {
    setCameraMsg(message, "warn");
    if (activeDeviceId && selectHasOption(activeDeviceId)) cameraSelect.value = activeDeviceId;
  }

  /* ---------- 장치 목록 ---------- */
  function fillCameraSelect(list) {
    cameraSelect.innerHTML = "";
    list.forEach(function (d, i) {
      var opt = document.createElement("option");
      opt.value = d.deviceId;
      opt.textContent = d.label || ("카메라 " + (i + 1));
      cameraSelect.appendChild(opt);
    });
    syncButtons();
  }

  /* enumerateDevices() 요청도 겹칠 수 있다(카메라 연결 직후 자동 호출 + devicechange
     가 거의 동시에 온다) — 세대 토큰으로 늦게 도착한 낡은 결과가 최신 결과를 덮어쓰지
     않게 막는다. reject 도 반드시 여기서 소비한다 — 그냥 두면 unhandled rejection 이
     되고, 호출부(카메라 연결 성공 직후·devicechange)마다 매번 따로 처리해야 한다. */
  var deviceListToken = 0;

  function refreshDeviceList() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) return Promise.resolve();
    var token = ++deviceListToken;
    return navigator.mediaDevices.enumerateDevices()
      .then(function (devices) {
        if (token !== deviceListToken) return;   /* 더 최신 조회가 이미 있었다 — 낡은 결과는 버린다 */
        var cams = devices.filter(function (d) { return d.kind === "videoinput"; });
        fillCameraSelect(cams);
        if (activeDeviceId && cams.some(function (d) { return d.deviceId === activeDeviceId; })) {
          cameraSelect.value = activeDeviceId;
        }
      })
      .catch(function () {
        if (token !== deviceListToken) return;   /* 이미 낡은 요청 — 화면에 영향을 주지 않는다 */
        /* select·activeDeviceId 는 그대로 둔다 — 이미 연결된 카메라나 기존 목록을
           지우지 않는다. idle 이면 이 안내조차 띄우지 않아 "시작"·"재시도"를 막지 않는다. */
        if (cameraState === "active") {
          setCameraMsg("카메라는 연결됐지만 장치 목록을 새로 읽지 못했습니다.", "warn");
        }
      });
  }

  /* ---------- 카메라 오류 메시지(이 화면 전용 — 카메라 종류 1개만 다룬다) ---------- */
  var ERROR_MESSAGE = {
    NotAllowedError: "카메라 권한이 거부되었거나 브라우저 설정에서 차단되어 있습니다. 주소창의 권한 아이콘에서 허용으로 바꾼 뒤 다시 시도하세요.",
    NotFoundError: "카메라 장치를 찾을 수 없습니다. 연결 상태를 확인하세요.",
    NotReadableError: "다른 프로그램이 카메라를 사용 중이거나 접근할 수 없습니다. 해당 프로그램을 종료한 뒤 다시 시도하세요.",
    OverconstrainedError: "선택한 카메라를 사용할 수 없습니다. 다른 장치를 선택해 보세요.",
    AbortError: "카메라 시작이 중단되었습니다."
  };

  function cameraErrorMessage(err) {
    var name = err && err.name;
    return ERROR_MESSAGE[name] || ("카메라 확인 중 알 수 없는 오류가 발생했습니다." + (name ? " (" + name + ")" : ""));
  }

  function attachPreview(stream) {
    selfPreview.srcObject = stream;
    selfPreview.hidden = false;
    selfPlaceholder.hidden = true;
    selfPreview.play().catch(function () { /* 자동재생 차단은 미리보기에만 영향 */ });
  }

  function handleTrackEnded(stream) {
    if (currentStream !== stream) return;   /* 이미 교체된 이전 스트림의 뒤늦은 ended 는 무시 */
    currentStream = null;
    selfPreview.srcObject = null;
    selfPreview.hidden = true;
    selfPlaceholder.hidden = false;
    activeDeviceId = null;
    stopTimer();
    setCameraState("fail");
    renderState();   /* cameraState 가 바뀌었으니 참여 배지도 즉시 "미확인" 으로 되돌린다 */
    setCameraMsg("카메라 연결이 끊겼습니다. 다시 시도해 주세요.", "risk");
    addEvent("카메라 연결이 끊겼습니다.");
  }

  /* ---------- 카메라 시작/전환 ---------- */
  function startCamera() {
    if (!supported || !consentCheckbox.checked) return;
    var wasActive = cameraState === "active";
    var requestedDeviceId = cameraSelect.value;   /* select 에 실제로 있는 값 — 신뢰할 수 있다 */
    var token = mediaSlot.request();
    setCameraState("checking");
    setCameraMsg(wasActive ? "선택한 카메라로 다시 확인하는 중입니다…" : "카메라를 확인하는 중입니다…");

    var constraints = { video: requestedDeviceId ? { deviceId: { exact: requestedDeviceId } } : true };
    navigator.mediaDevices.getUserMedia(constraints)
      .then(function (stream) {
        var track = stream.getVideoTracks()[0];
        if (!track) {
          stream.getTracks().forEach(function (t) { t.stop(); });
          if (!mediaSlot.isCurrent(token)) return;   /* 낡은 요청 — 침묵 */
          if (wasActive) {
            restoreSelectAfterFailedSwitch("전환 실패 — 카메라 트랙을 가져오지 못했습니다. 이전 연결을 유지합니다.");
            setCameraState("active");
          } else {
            setCameraMsg("카메라 트랙을 가져오지 못했습니다. 다시 시도해 주세요.", "risk");
            setCameraState("fail");
          }
          return;
        }
        var adopted = mediaSlot.adopt(token, stream);
        if (!adopted) return;   /* 낡은 요청 — adopt() 가 이미 스트림을 정지했다 */
        currentStream = adopted;
        activeDeviceId = requestedDeviceId || null;
        attachPreview(adopted);
        track.addEventListener("ended", function () { handleTrackEnded(adopted); });
        setCameraState("active");
        setCameraMsg("");
        /* 최초 연결처럼 요청 시점엔 select 에 아무 값도 없었을 수 있다(브라우저가 기본
           장치를 알아서 골랐다). track.getSettings().deviceId 는 canvas.captureStream()
           같은 합성 트랙에서 목록에 없는 임의의 값을 돌려줄 수 있어 신뢰하지 않는다 —
           대신 장치 목록을 다시 읽은 뒤 select 가 실제로 보여주는 값(비어 있으면 그대로
           두고, 있으면 그 값)을 기준으로 삼는다. */
        refreshDeviceList().then(function () {
          if (mediaSlot.isCurrent(token) && cameraSelect.value) activeDeviceId = cameraSelect.value;
        });
        startTimer();
        addEvent(wasActive ? "카메라를 전환했습니다." : "카메라가 연결되었습니다.");
      })
      .catch(function (err) {
        if (!mediaSlot.isCurrent(token)) return;   /* 낡은 요청 — 침묵 */
        var msg = cameraErrorMessage(err);
        if (wasActive) {
          restoreSelectAfterFailedSwitch("전환 실패 — 이전 카메라 연결을 유지합니다: " + msg);
          setCameraState("active");
        } else {
          setCameraMsg(msg, "risk");
          setCameraState("fail");
        }
      });
  }

  function stopCamera(reason) {
    mediaSlot.stop();
    currentStream = null;
    selfPreview.srcObject = null;
    selfPreview.hidden = true;
    selfPlaceholder.hidden = false;
    activeDeviceId = null;
    stopTimer();
    setCameraState("idle");
    renderState();   /* cameraState 가 바뀌었으니 참여 배지도 즉시 "미확인" 으로 되돌린다 —
                         안 부르면 다음 tick 이 없어(타이머를 멈췄다) 이전 배지가 그대로 남는다 */
    if (reason) addEvent(reason);
  }

  /* ---------- 데모 시나리오 ---------- */
  function currentScenario() {
    var found = D.traineeScenarios.filter(function (s) { return s.key === scenarioSelect.value; })[0];
    return found || D.traineeScenarios[0];
  }

  function renderScenarioDesc() {
    scenarioDescEl.textContent = currentScenario().desc;
  }

  scenarioSelect.innerHTML = D.traineeScenarios.map(function (s) {
    return '<option value="' + s.key + '">' + s.label + "</option>";
  }).join("");
  renderScenarioDesc();

  scenarioSelect.addEventListener("change", function () {
    renderScenarioDesc();
    addEvent('데모 시나리오를 "' + currentScenario().label + '"(으)로 변경했습니다.');
    renderState();
  });

  /* ---------- 참여 상태·학습시간 ----------
     판정에는 "지금 이어지는" currentAwaySeconds 를 쓴다(연속 이탈 구간). 관리자
     화면의 집중관리 판정과 달리, 이 화면은 정상으로 돌아오면 이 값이 0 이 되어
     안내 단계도 같이 내려간다 — 대신 cumulativeAwaySeconds(전체 세션 누적)는
     별도로 계속 쌓인다(§ tick 참고). */
  function computeState() {
    var scenario = currentScenario();
    return C.derivePresenceState({
      faceCount: scenario.faceCount,
      cameraConnected: scenario.cameraConnected,
      currentAwaySeconds: currentAwaySeconds,
      graceSeconds: D.policy.graceSeconds,
      warningSeconds: D.policy.warningSeconds,
      focusSeconds: D.policy.focusSeconds
    });
  }

  function renderState() {
    if (cameraState !== "active") {
      stateBadgeEl.textContent = "미확인";
      stateBadgeEl.className = "state-badge";
      stateMsgEl.textContent = "카메라를 시작하면 참여 상태 확인이 시작됩니다.";
      return;
    }
    var state = computeState();
    stateBadgeEl.textContent = state.label;
    stateBadgeEl.className = "state-badge " + state.tone;
    stateMsgEl.textContent = (state.code === "away_return_needed" || state.code === "away_warning" || state.code === "away_focus")
      ? "자리 이탈이 감지되어 그 시간만큼 학습시간이 인정되지 않습니다."
      : "";
  }

  function renderTimes() {
    var t = C.calculateLearningTime({
      connectedSeconds: connectedSeconds,
      verifiedSeconds: verifiedSeconds,
      cumulativeAwaySeconds: cumulativeAwaySeconds
    });
    connectedTimeEl.textContent = formatHMS(t.connectedSeconds);
    verifiedTimeEl.textContent = formatHMS(t.verifiedSeconds);
    awayTimeEl.textContent = formatHMS(t.cumulativeAwaySeconds);
    recognizedTimeEl.textContent = formatHMS(t.recognizedSeconds);
  }

  function tick() {
    /* 이번 초가 시작되기 직전 상태로 "이번 한 초를 어떻게 셀지" 정한 뒤,
       그 판정 그대로 C.advanceLearningClock() 에 넘겨 네 누적값을 한 칸 전진시킨다 —
       화면(tick)에서 직접 +1 하지 않는다. */
    var preState = computeState();
    var clock = C.advanceLearningClock(
      { connectedSeconds: connectedSeconds, verifiedSeconds: verifiedSeconds,
        cumulativeAwaySeconds: cumulativeAwaySeconds, currentAwaySeconds: currentAwaySeconds },
      preState
    );
    connectedSeconds = clock.connectedSeconds;
    verifiedSeconds = clock.verifiedSeconds;
    cumulativeAwaySeconds = clock.cumulativeAwaySeconds;
    currentAwaySeconds = clock.currentAwaySeconds;

    /* 갱신된 currentAwaySeconds 로 다시 판정해야 이번 초에 막 경계를 넘은 안내를
       지연 없이 보여준다(예: 마지막 tick 에서 grace 를 넘겼다면 이번에는 바로
       "자리 복귀 필요"로 보여야 한다 — 넘기기 직전 값으로는 한 틱 늦게 보인다). */
    var state = computeState();

    ticksSinceCheck += 1;
    if (ticksSinceCheck >= D.policy.sampleIntervalSeconds) {
      ticksSinceCheck = 0;
      lastCheckedAtEl.textContent = nowHHMMSS();
    }
    nextCheckedAtEl.textContent = "약 " + (D.policy.sampleIntervalSeconds - ticksSinceCheck) + "초 후";

    renderState();
    renderTimes();

    if (state.code === "away_warning" && !warnedThisEpisode) {
      warnedThisEpisode = true;
      addEvent("자리 이탈 경고 — " + state.label);
    } else if (state.code === "away_focus" && !focusedThisEpisode) {
      focusedThisEpisode = true;
      addEvent("집중관리 대상 전환 — " + state.label);
    } else if (state.code === "present") {
      warnedThisEpisode = false;
      focusedThisEpisode = false;
    }
  }

  function startTimer() {
    if (timerHandle) return;
    timerHandle = window.setInterval(tick, 1000);
  }

  function stopTimer() {
    if (timerHandle) { window.clearInterval(timerHandle); timerHandle = null; }
  }

  /* ---------- 이벤트 바인딩 ---------- */
  consentCheckbox.addEventListener("change", function () {
    if (!consentCheckbox.checked) {
      stopCamera("동의를 해제하여 카메라와 학습시간 측정을 즉시 중지했습니다.");
    }
    syncButtons();
  });

  startBtn.addEventListener("click", startCamera);
  retryBtn.addEventListener("click", startCamera);
  stopBtn.addEventListener("click", function () { stopCamera("사용자가 카메라를 중지했습니다."); });

  /* 카메라가 켜져 있는 동안 select 를 바꾸면 그 장치로 다시 연결을 시도한다.
     시작 전에는(cameraState !== "active") select 를 미리 골라만 두고, 실제 요청은
     "시작" 버튼을 눌렀을 때 그 값으로 나간다. */
  cameraSelect.addEventListener("change", function () {
    if (cameraState === "active") startCamera();
  });

  if (navigator.mediaDevices && navigator.mediaDevices.addEventListener) {
    navigator.mediaDevices.addEventListener("devicechange", function () {
      if (cameraState === "active" || cameraState === "idle") refreshDeviceList();
    });
  }

  function cleanupAll() { mediaSlot.stop(); stopTimer(); }
  window.addEventListener("pagehide", cleanupAll);
  window.addEventListener("beforeunload", cleanupAll);

  /* ---------- 초기화 ---------- */
  syncButtons();
  renderState();
  renderTimes();
  nextCheckedAtEl.textContent = "약 " + D.policy.sampleIntervalSeconds + "초 후";
})();
