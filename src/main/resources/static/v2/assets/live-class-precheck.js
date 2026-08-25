/* ============================================================
   live-class-precheck.js — 화상강의 입장 전 장치 점검 (LXP-125 / LXP-127)

   실제 브라우저 API 를 쓰는 부분:
     - navigator.mediaDevices.getUserMedia    (카메라+마이크, 카메라만, 마이크만)
     - navigator.mediaDevices.enumerateDevices
     - navigator.mediaDevices.getDisplayMedia (화면 공유)
     - Web Audio AnalyserNode                 (마이크 입력 레벨)

   저장·녹화·서버 전송은 없다 — 전부 이 브라우저 안에서만 재생된다.

   비동기 경합 방지: 카메라·마이크·화면공유 각각 LiveClassCommon.createStreamSlot() 을
   하나씩 가진다. 재클릭·장치 재선택으로 여러 getUserMedia 요청이 겹쳐도 슬롯이 "가장
   최근 요청"만 채택하고 나머지는 도착 즉시 정지한다(자세한 이유는 live-class-common.js
   참고). 장치 제거 판정은 select 의 남은 값이 아니라 성공 시점에 잡은 deviceId 를
   직접 대조한다 — 안 그러면 브라우저가 자동 선택한 다른 장치를 정상으로 오인한다.

   재점검·전환 실패 정책(switchFailurePolicy): 이미 pass 였던 항목은 요청 시작 시점부터
   끝까지 checks[key] 를 건드리지 않는다. "요청 중"은 disabled·aria-busy·안내 문구로만
   드러내고, 성공해야만 setState(pass) 로 스트림을 교체한다. 실패하면 아무것도 하지 않은
   것과 같아서 기존 스트림·배지·입장 게이트가 자동으로 그대로 남는다.
   ============================================================ */
(function () {
  "use strict";

  var C = window.LiveClassCommon;

  var cameraSlot = C.createStreamSlot();
  var micSlot = C.createStreamSlot();
  var screenSlot = C.createStreamSlot();

  /* 성공 시점에 실제로 잡은 deviceId — select.value 가 아니라 이 값으로 "아직 있는가"를 본다. */
  var activeDeviceId = { camera: null, mic: null };
  var checks = { camera: "idle", mic: "idle", screen: "idle" };

  var audioCtx = null, analyser = null, rafId = null;

  var LABEL = { idle: "미확인", checking: "확인 중", pass: "통과", fail: "실패", unsupported: "브라우저 확인 불가" };
  var TONE = { pass: "ok", fail: "risk", checking: "warn", unsupported: "warn", idle: "" };

  function cardOf(key) { return document.querySelector('[data-check="' + key + '"]'); }

  function setState(key, state, message, tone) {
    checks[key] = state;
    var card = cardOf(key);
    if (!card) return;
    card.dataset.state = state;

    var badge = card.querySelector('[data-role="badge"]');
    badge.textContent = LABEL[state] || state;
    badge.className = "state-badge" + (TONE[state] ? " " + TONE[state] : "");

    setCardMessage(key, message, tone);
    syncGate();
  }

  /* checks[key]·배지·게이트는 건드리지 않고 카드 안내문만 바꾼다. "이미 pass 인데 처리
     중"이거나 "실패했지만 기존 연결을 유지"할 때 쓴다. */
  function setCardMessage(key, message, tone) {
    var card = cardOf(key);
    var msg = card.querySelector('[data-role="msg"]');
    if (!msg) return;
    msg.textContent = message || "";
    if (tone) msg.dataset.tone = tone; else msg.removeAttribute("data-tone");
  }

  function showAdvisory(key, message) {
    setCardMessage(key, message, "warn");
    C.toast(message, "warn");
  }

  function setBusy(el, busy) {
    if (!el) return;
    el.disabled = busy;
    if (busy) el.setAttribute("aria-busy", "true"); else el.removeAttribute("aria-busy");
  }

  /* ---------- 미디어 미리보기 공통 ---------- */
  function attachPreview(key, stream) {
    var card = cardOf(key);
    var video = card.querySelector('[data-role="preview"]');
    if (!video) return;
    var ph = card.querySelector('[data-role="placeholder"]');
    C.attachStream(video, stream);
    if (ph) ph.hidden = true;
  }

  function detachPreview(key) {
    var card = cardOf(key);
    var video = card.querySelector('[data-role="preview"]');
    if (video) { video.srcObject = null; video.hidden = true; }
    var ph = card.querySelector('[data-role="placeholder"]');
    if (ph) ph.hidden = false;
  }

  /* 슬롯이 아직 이 스트림을 들고 있을 때만 UI 를 끈다 — 이미 다른 스트림으로 교체된
     뒤에 이전 트랙이 뒤늦게 ended 되어도(자연 종료) 방금 켠 화면을 지우지 않는다.
     세 종류(카메라·마이크·화면공유)를 각자의 정리 경로로 보낸다 — 화면공유를 빠뜨리면
     "브라우저 자체 공유 중지" 이후 미리보기·버튼이 정지 상태로 안 돌아온다. */
  function bindTrackEnded(key, track, slot, ownStream) {
    track.addEventListener("ended", function () {
      if (slot.getStream() !== ownStream) return;
      if (key === "camera") {
        slot.stop();
        detachPreview("camera");
        activeDeviceId.camera = null;
        setState("camera", "fail", "카메라 연결이 끊겼습니다. 다시 점검해 주세요.", "risk");
      } else if (key === "mic") {
        slot.stop();
        stopMicMeter();
        activeDeviceId.mic = null;
        setState("mic", "fail", "마이크 연결이 끊겼습니다. 다시 점검해 주세요.", "risk");
      } else if (key === "screen") {
        stopScreenShare();
        setState("screen", "fail", "화면 공유가 중단되었습니다. 다시 시작해 주세요.", "risk");
      }
    });
  }

  /* ---------- 마이크 레벨 미터 ---------- */
  function startMicMeter(stream) {
    stopMicMeter();
    try {
      var AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      audioCtx = new AudioCtx();
      var source = audioCtx.createMediaStreamSource(stream);
      analyser = audioCtx.createAnalyser();
      analyser.fftSize = 512;
      source.connect(analyser);
      var data = new Uint8Array(analyser.frequencyBinCount);
      var fill = document.querySelector('[data-role="level-fill"]');
      (function tick() {
        analyser.getByteTimeDomainData(data);
        var sum = 0;
        for (var i = 0; i < data.length; i++) { var v = (data[i] - 128) / 128; sum += v * v; }
        var rms = Math.sqrt(sum / data.length);
        var pct = Math.min(100, Math.round(rms * 220));
        if (fill) fill.style.width = pct + "%";
        rafId = window.requestAnimationFrame(tick);
      })();
    } catch (e) { /* AnalyserNode 를 못 만들면 레벨 표시만 건너뛴다 — 마이크 자체 판정에는 영향 없다 */ }
  }

  function stopMicMeter() {
    if (rafId) window.cancelAnimationFrame(rafId);
    rafId = null;
    if (audioCtx) { audioCtx.close().catch(function () {}); audioCtx = null; }
    analyser = null;
    var fill = document.querySelector('[data-role="level-fill"]');
    if (fill) fill.style.width = "0%";
  }

  /* ---------- 장치 목록 ---------- */
  var cameraSelectEl = document.getElementById("cameraSelect");
  var micSelectEl = document.getElementById("micSelect");

  /* select 는 "요청 중"과 "옵션 있음"을 각자 data 속성으로 들고, 실제 disabled 는
     computeSelectDisabled() 로만 계산한다. 요청이 끝났다는 이유만으로 옵션 없는
     select 가 열리는 사고를 막는다. */
  function setSelectBusy(sel, busy) {
    sel.dataset.busy = busy ? "1" : "";
    if (busy) sel.setAttribute("aria-busy", "true"); else sel.removeAttribute("aria-busy");
    syncSelectDisabled(sel);
  }

  function setSelectHasOptions(sel, hasOptions) {
    sel.dataset.hasOptions = hasOptions ? "1" : "";
    syncSelectDisabled(sel);
  }

  function syncSelectDisabled(sel) {
    sel.disabled = C.computeSelectDisabled(!!sel.dataset.busy, !!sel.dataset.hasOptions);
  }

  function refreshDeviceLists() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) return Promise.resolve();
    return navigator.mediaDevices.enumerateDevices()
      .then(function (devices) {
        var byKind = {
          camera: devices.filter(function (d) { return d.kind === "videoinput"; }),
          mic: devices.filter(function (d) { return d.kind === "audioinput"; })
        };

        ["camera", "mic"].forEach(function (key) {
          var selectEl = key === "camera" ? cameraSelectEl : micSelectEl;
          var list = byKind[key];
          fillSelect(selectEl, list, key === "camera" ? "카메라" : "마이크");

          /* 브라우저가 제거된 장치 대신 남은 옵션을 자동 선택해도, 실제로 살아있는 스트림의
             deviceId 를 직접 대조하므로 그 자동 선택에 속지 않는다. */
          if (activeDeviceId[key] && C.isDevicePresent(activeDeviceId[key], list)) {
            selectEl.value = activeDeviceId[key];
          } else if (checks[key] === "pass") {
            var slot = key === "camera" ? cameraSlot : micSlot;
            slot.stop();
            activeDeviceId[key] = null;
            if (key === "camera") detachPreview("camera"); else stopMicMeter();
            setState(key, "fail", (key === "camera" ? "카메라" : "마이크") + " 장치 연결이 끊겼습니다. 다시 점검해 주세요.", "risk");
          }
        });
      })
      .catch(function () {
        /* 목록 갱신 실패는 기존 스트림을 건드릴 이유가 아니다 — 안내만 하고 계속 쓰게 둔다. */
        C.toast("장치 목록을 다시 확인하지 못했습니다. 연결은 유지됩니다.", "warn");
      });
  }

  function fillSelect(sel, list, kindLabel) {
    var current = sel.value;
    sel.innerHTML = list.length
      ? list.map(function (d, i) {
          return '<option value="' + C.esc(d.deviceId) + '">' + C.esc(d.label || (kindLabel + " " + (i + 1))) + "</option>";
        }).join("")
      : '<option value="">사용 가능한 ' + kindLabel + " 장치가 없습니다</option>";
    if (current && list.some(function (d) { return d.deviceId === current; })) sel.value = current;
    setSelectHasOptions(sel, list.length > 0);
  }

  /* ---------- 카메라·마이크: "장치 점검 시작" 한 번으로 함께 요청 ---------- */
  var deviceStartBtn = document.querySelector('[data-action="device-start"]');

  function startDeviceCheck() {
    var support = C.supportState(window.isSecureContext, navigator.mediaDevices);
    if (!support.ok) {
      setState("camera", "fail", support.message, "risk");
      setState("mic", "fail", support.message, "risk");
      return;
    }

    var wasCameraPass = checks.camera === "pass";
    var wasMicPass = checks.mic === "pass";
    var camToken = cameraSlot.begin();
    var micToken = micSlot.begin();

    setBusy(deviceStartBtn, true);
    setSelectBusy(cameraSelectEl, true);
    setSelectBusy(micSelectEl, true);
    if (wasCameraPass) setCardMessage("camera", "카메라·마이크 권한을 다시 확인하는 중입니다…");
    else setState("camera", "checking", "카메라·마이크 권한을 요청하는 중입니다…");
    if (wasMicPass) setCardMessage("mic", "카메라·마이크 권한을 다시 확인하는 중입니다…");
    else setState("mic", "checking", "카메라·마이크 권한을 요청하는 중입니다…");

    navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 360 }, audio: true })
      .then(function (stream) {
        /* adoptTrack 에 넘긴 바로 그 wrapper 를 attachPreview/bindTrackEnded 에도 그대로
           써야 한다 — resolve() 가 슬롯에 저장하는 것도 이 wrapper 이므로, bindTrackEnded
           의 "slot.getStream() === ownStream" 비교가 항상 참이려면 같은 객체여야 한다.
           새 MediaStream 을 두 번 만들면 트랙은 같아도 객체가 달라 그 비교가 영원히
           거짓이 되어 정상 종료 처리가 죽는다. */
        var camWrapper = new MediaStream(stream.getVideoTracks());
        var camTrack = C.adoptTrack(cameraSlot, camToken, camWrapper, "video");
        if (cameraSlot.isCurrent(camToken)) {
          if (camTrack) {
            activeDeviceId.camera = camTrack.getSettings().deviceId || null;
            attachPreview("camera", camWrapper);
            bindTrackEnded("camera", camTrack, cameraSlot, camWrapper);
            setState("camera", "pass", "카메라가 연결되었습니다. 저장되지 않습니다.", "ok");
          } else {
            var camOutcome = C.switchFailurePolicy(wasCameraPass);
            if (camOutcome.keepStream) showAdvisory("camera", "재점검 실패 — 카메라 트랙을 가져오지 못했습니다. 기존 연결을 유지합니다.");
            else setState("camera", "fail", "카메라 트랙을 가져오지 못했습니다.", "risk");
          }
        }

        var micWrapper = new MediaStream(stream.getAudioTracks());
        var micTrack = C.adoptTrack(micSlot, micToken, micWrapper, "audio");
        if (micSlot.isCurrent(micToken)) {
          if (micTrack) {
            activeDeviceId.mic = micTrack.getSettings().deviceId || null;
            bindTrackEnded("mic", micTrack, micSlot, micWrapper);
            startMicMeter(micWrapper);
            setState("mic", "pass", "마이크가 연결되었습니다. 음성은 저장되지 않습니다.", "ok");
          } else {
            var micOutcome = C.switchFailurePolicy(wasMicPass);
            if (micOutcome.keepStream) showAdvisory("mic", "재점검 실패 — 마이크 트랙을 가져오지 못했습니다. 기존 연결을 유지합니다.");
            else setState("mic", "fail", "마이크 트랙을 가져오지 못했습니다.", "risk");
          }
        }

        return refreshDeviceLists();
      })
      .catch(function (err) {
        var info = C.mediaErrorInfo(err && err.name, "카메라·마이크");
        if (cameraSlot.isCurrent(camToken)) {
          var camOutcome = C.switchFailurePolicy(wasCameraPass);
          if (camOutcome.keepStream) showAdvisory("camera", "재점검 실패 — 기존 카메라 연결을 유지합니다: " + info.message);
          else setState("camera", "fail", info.message, "risk");
        }
        if (micSlot.isCurrent(micToken)) {
          var micOutcome = C.switchFailurePolicy(wasMicPass);
          if (micOutcome.keepStream) showAdvisory("mic", "재점검 실패 — 기존 마이크 연결을 유지합니다: " + info.message);
          else setState("mic", "fail", info.message, "risk");
        }
      })
      .then(function () {
        /* 두 슬롯 모두 이 요청을 disable 창 동안 어떤 다른 요청도 끼어들 수 없었으므로
           토큰은 항상 최신이지만, dispose(pagehide) 이후에는 여기서 UI 를 만지지 않는다. */
        if (cameraSlot.isCurrent(camToken)) {
          setBusy(deviceStartBtn, false);
          setSelectBusy(cameraSelectEl, false);
          setSelectBusy(micSelectEl, false);
        }
      });
  }

  /* ---------- 카메라·마이크 장치 재선택 ---------- */
  document.addEventListener("change", function (e) {
    if (e.target.id === "cameraSelect") switchCamera(e.target.value);
    else if (e.target.id === "micSelect") switchMic(e.target.value);
  });

  /* 전환(장치 재선택)이 실패한 두 경로 — Promise 자체가 reject 되는 경우와, resolve 는 됐지만
     필요한 트랙이 없어 adoptTrack() 이 null 을 돌려주는 경우 — 가 같은 정책을 쓰게 하나로 묶는다.
     기존 스트림·pass 상태는 이미 손대지 않았으므로(둘 다 setState 를 안 부름), 여기서는
     "안내 + select 를 실제 활성 장치로 복원"만 한다. wasPass 가 아니면(원래 idle) 여기로
     오지 않고 각자 setState(fail) 로 떨어진다 — 이 함수는 "기존 연결 유지" 케이스 전용이다. */
  function restoreSelectAfterFailedSwitch(selectEl, activeId, message) {
    showAdvisory(selectEl === cameraSelectEl ? "camera" : "mic", message);
    if (activeId) selectEl.value = activeId;
  }

  function switchCamera(deviceId) {
    if (!deviceId) return;
    var wasPass = checks.camera === "pass";
    var token = cameraSlot.begin();
    setSelectBusy(cameraSelectEl, true);
    if (!wasPass) setState("camera", "checking", "선택한 카메라로 다시 확인하는 중입니다…");
    else setCardMessage("camera", "선택한 카메라로 다시 확인하는 중입니다…");

    navigator.mediaDevices.getUserMedia({ video: { deviceId: { exact: deviceId } }, audio: false })
      .then(function (stream) {
        var track = C.adoptTrack(cameraSlot, token, stream, "video");
        if (!cameraSlot.isCurrent(token)) return;
        if (!track) {
          var outcome = C.switchFailurePolicy(wasPass);
          if (outcome.keepStream) {
            restoreSelectAfterFailedSwitch(cameraSelectEl, activeDeviceId.camera,
              "전환 실패 — 카메라 트랙을 가져오지 못했습니다. 이전 연결을 유지합니다.");
          } else {
            setState("camera", "fail", "카메라 트랙을 가져오지 못했습니다.", "risk");
          }
          return;
        }
        activeDeviceId.camera = track.getSettings().deviceId || deviceId;
        attachPreview("camera", stream);
        bindTrackEnded("camera", track, cameraSlot, stream);
        setState("camera", "pass", "선택한 카메라로 연결되었습니다.", "ok");
      })
      .catch(function (err) {
        if (!cameraSlot.isCurrent(token)) return;
        var info = C.mediaErrorInfo(err && err.name, "카메라");
        var outcome = C.switchFailurePolicy(wasPass);
        if (outcome.keepStream) {
          restoreSelectAfterFailedSwitch(cameraSelectEl, activeDeviceId.camera,
            "전환 실패 — 이전 카메라 연결을 유지합니다: " + info.message);
        } else {
          setState("camera", "fail", info.message, "risk");
        }
      })
      .then(function () { if (cameraSlot.isCurrent(token)) setSelectBusy(cameraSelectEl, false); });
  }

  function switchMic(deviceId) {
    if (!deviceId) return;
    var wasPass = checks.mic === "pass";
    var token = micSlot.begin();
    setSelectBusy(micSelectEl, true);
    if (!wasPass) setState("mic", "checking", "선택한 마이크로 다시 확인하는 중입니다…");
    else setCardMessage("mic", "선택한 마이크로 다시 확인하는 중입니다…");

    navigator.mediaDevices.getUserMedia({ audio: { deviceId: { exact: deviceId } }, video: false })
      .then(function (stream) {
        var track = C.adoptTrack(micSlot, token, stream, "audio");
        if (!micSlot.isCurrent(token)) return;
        if (!track) {
          var outcome = C.switchFailurePolicy(wasPass);
          if (outcome.keepStream) {
            restoreSelectAfterFailedSwitch(micSelectEl, activeDeviceId.mic,
              "전환 실패 — 마이크 트랙을 가져오지 못했습니다. 이전 연결을 유지합니다.");
          } else {
            setState("mic", "fail", "마이크 트랙을 가져오지 못했습니다.", "risk");
          }
          return;
        }
        activeDeviceId.mic = track.getSettings().deviceId || deviceId;
        bindTrackEnded("mic", track, micSlot, stream);
        startMicMeter(stream);
        setState("mic", "pass", "선택한 마이크로 연결되었습니다.", "ok");
      })
      .catch(function (err) {
        if (!micSlot.isCurrent(token)) return;
        var info = C.mediaErrorInfo(err && err.name, "마이크");
        var outcome = C.switchFailurePolicy(wasPass);
        if (outcome.keepStream) {
          restoreSelectAfterFailedSwitch(micSelectEl, activeDeviceId.mic,
            "전환 실패 — 이전 마이크 연결을 유지합니다: " + info.message);
        } else {
          setState("mic", "fail", info.message, "risk");
        }
      })
      .then(function () { if (micSlot.isCurrent(token)) setSelectBusy(micSelectEl, false); });
  }

  /* ---------- 화면 공유 ---------- */
  function toggleScreenButtons(state) {
    var card = cardOf("screen");
    var start = card.querySelector('[data-action="screen-start"]');
    var stop = card.querySelector('[data-action="screen-stop"]');
    if (state === "busy") {
      start.disabled = true;
      stop.disabled = true;
      start.setAttribute("aria-busy", "true");
      return;
    }
    start.removeAttribute("aria-busy");
    start.disabled = state;
    stop.disabled = !state;
  }

  /* 수동 중지와 트랙 ended(브라우저 자체 "공유 중지") 가 항상 같은 정리 경로를 타게
     하나로 모았다 — 두 경로가 따로 놀면 한쪽만 고치고 잊어버리기 쉽다. */
  function stopScreenShare() {
    screenSlot.stop();
    detachPreview("screen");
    toggleScreenButtons(false);
  }

  function startScreenShare() {
    var support = C.supportState(window.isSecureContext, navigator.mediaDevices);
    if (!support.ok) { setState("screen", "fail", support.message, "risk"); return; }
    if (!navigator.mediaDevices.getDisplayMedia) {
      setState("screen", "unsupported", "이 브라우저는 화면 공유 API를 지원하지 않습니다. 데스크톱 Chrome, Edge를 사용하세요.", "risk");
      return;
    }
    var wasPass = checks.screen === "pass";
    var token = screenSlot.begin();
    toggleScreenButtons("busy");
    if (!wasPass) setState("screen", "checking", "공유할 화면을 선택해 주세요…");
    else setCardMessage("screen", "공유할 화면을 다시 선택해 주세요…");

    navigator.mediaDevices.getDisplayMedia({ video: true, audio: false })
      .then(function (stream) {
        var track = C.adoptTrack(screenSlot, token, stream, "video");
        if (!screenSlot.isCurrent(token)) return;
        if (!track) {
          var outcome = C.switchFailurePolicy(wasPass);
          if (outcome.keepStream) { showAdvisory("screen", "재공유 실패 — 화면 트랙을 가져오지 못했습니다. 기존 공유를 유지합니다."); toggleScreenButtons(true); }
          else { setState("screen", "fail", "화면 트랙을 가져오지 못했습니다.", "risk"); toggleScreenButtons(false); }
          return;
        }
        attachPreview("screen", stream);
        toggleScreenButtons(true);
        bindTrackEnded("screen", track, screenSlot, stream);
        setState("screen", "pass", "화면이 공유되었습니다. 저장되지 않습니다.", "ok");
      })
      .catch(function (err) {
        if (!screenSlot.isCurrent(token)) return;
        var info = C.mediaErrorInfo(err && err.name, "화면 공유");
        var outcome = C.switchFailurePolicy(wasPass);
        if (outcome.keepStream) {
          showAdvisory("screen", "재공유 실패 — 기존 화면 공유를 유지합니다: " + info.message);
          toggleScreenButtons(true);
        } else {
          setState("screen", "fail", info.message, "risk");
          toggleScreenButtons(false);
        }
      });
  }

  /* ---------- 입장 게이트 ---------- */
  function syncGate() {
    var passed = Object.keys(checks).filter(function (k) { return checks[k] === "pass"; });
    document.getElementById("progressLine").textContent = "통과 " + passed.length + " / 3개 항목";

    var btn = document.getElementById("enterBtn");
    var msg = document.getElementById("gateMsg");
    if (C.allChecksPassed(checks)) {
      btn.removeAttribute("aria-disabled");
      btn.removeAttribute("tabindex");
      btn.classList.remove("btn-secondary");
      btn.classList.add("btn-primary");
      msg.textContent = "카메라·마이크·화면 공유를 모두 확인했습니다. 강의실 데모에 입장할 수 있습니다.";
    } else {
      btn.setAttribute("aria-disabled", "true");
      btn.setAttribute("tabindex", "-1");
      btn.classList.add("btn-secondary");
      btn.classList.remove("btn-primary");
      var names = { camera: "카메라", mic: "마이크", screen: "화면 공유" };
      var pending = Object.keys(checks).filter(function (k) { return checks[k] !== "pass"; })
        .map(function (k) { return names[k]; });
      msg.textContent = "남은 항목: " + pending.join(", ");
    }
  }

  document.getElementById("enterBtn").addEventListener("click", function (e) {
    if (this.getAttribute("aria-disabled") === "true") {
      e.preventDefault();
      C.toast("아직 통과하지 못한 점검 항목이 있습니다", "warn");
    }
  });

  document.addEventListener("click", function (e) {
    var btn = e.target.closest && e.target.closest("[data-action]");
    if (!btn) return;
    var a = btn.dataset.action;
    if (a === "device-start") startDeviceCheck();
    else if (a === "screen-start") startScreenShare();
    else if (a === "screen-stop") { stopScreenShare(); setState("screen", "idle", "화면 공유를 중지했습니다."); }
  });

  /* 페이지를 떠날 때 모든 슬롯을 폐기한다 — 이미 나간 요청은 물론, 이 시점 이후 늦게
     도착하는 getUserMedia 결과도 slot.resolve() 가 자동으로 즉시 정지시킨다. */
  window.addEventListener("pagehide", function () {
    cameraSlot.dispose();
    micSlot.dispose();
    screenSlot.dispose();
    stopMicMeter();
  });

  navigator.mediaDevices && navigator.mediaDevices.addEventListener &&
    navigator.mediaDevices.addEventListener("devicechange", refreshDeviceLists);

  syncGate();
})();
