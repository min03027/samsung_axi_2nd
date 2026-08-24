/* ============================================================
   exam-precheck.js — PC 응시 준비(사전점검)

   두 가지를 다룬다.
     1) QR 신분확인 — 서버가 발급한 일회용 토큰을 QR 로 그리고, 상태를 폴링한다.
     2) 웹캠 연결 확인 — 권한 요청 → 장치 선택 → 미리보기 → 실제 프레임 수신 확인.

   입장 가능 판정은 화면이 하지 않는다. 서버가 내려주는 canEnter 만 본다
   (버튼을 열어 줘도 최종 차단은 POST /start 에서 서버가 다시 한다).
   ============================================================ */

(function () {
  "use strict";

  function meta(name) {
    var el = document.querySelector('meta[name="' + name + '"]');
    return el ? el.getAttribute("content") : "";
  }
  var cfgEl = document.getElementById("precheckConfig");
  var CFG = {
    sessionId: cfgEl ? cfgEl.dataset.sessionId : "",
    examId: cfgEl ? cfgEl.dataset.examId : "",
    csrfHeader: meta("_csrf_header"),
    csrfToken: meta("_csrf")
  };
  var $ = function (id) { return document.getElementById(id); };

  /* ---------- 공통 ---------- */

  function post(url, body, isForm) {
    var headers = {};
    if (CFG.csrfHeader) headers[CFG.csrfHeader] = CFG.csrfToken;
    if (!isForm) headers["Content-Type"] = "application/json";
    return fetch(url, {
      method: "POST",
      headers: headers,
      body: isForm ? body : (body ? JSON.stringify(body) : null)
    }).then(function (r) { return r.json().catch(function () { return { ok: false, message: "서버 응답을 읽지 못했습니다." }; }); });
  }

  function setBadge(el, text, tone) {
    el.textContent = text;
    el.className = "state-badge" + (tone ? " " + tone : "");
  }

  /* ============================================================
     1. 신분 확인 (QR)
     ============================================================ */

  var idBadge = $("idBadge"), idMessage = $("idMessage"), idReason = $("idReason");
  var qrBox = $("qrBox"), qrEmpty = $("qrEmpty"), qrExpire = $("qrExpire"), qrCountdown = $("qrCountdown");
  var identityApproved = false;
  var sharePassed = false;    /* 화면 공유 확인 (LXP-018) */
  var monitorPassed = false;  /* 모니터 구성 확인 (LXP-019) */
  var qrDeadline = null;
  var idSubmitted = false;   /* 신분증이 이미 올라갔는가 — 서버 폴링 값으로만 판단한다 */
  var qrIssuable = true;     /* 지금 새 업로드 QR 을 받을 수 있는가 (P1-5) */

  function drawQr(url) {
    /* qrcode-generator(MIT, vendored): typeNumber 0 = 자동, 오류정정 M.
       화면에서 스캔하는 용도라 M 이면 충분하다. */
    var qr = qrcode(0, "M");
    qr.addData(url);
    qr.make();
    qrBox.innerHTML = qr.createImgTag(6, 8);
    var img = qrBox.querySelector("img");
    if (img) img.alt = "신분증 제출용 QR 코드";
    if (qrEmpty) qrEmpty.hidden = true;
  }

  function tickQr() {
    if (!qrDeadline) return;
    var left = Math.max(0, Math.round((qrDeadline - Date.now()) / 1000));
    var m = String(Math.floor(left / 60)).padStart(2, "0");
    var s = String(left % 60).padStart(2, "0");
    qrCountdown.textContent = m + ":" + s;
    if (left === 0) {
      qrBox.replaceChildren();
      var p = document.createElement("p");
      p.className = "qr-empty";
      /* 제출이 끝난 뒤의 만료는 "다시 발급" 이 아니다 — 더 올릴 것이 없다 (P1-5). */
      p.textContent = idSubmitted
        ? "QR이 만료되었습니다. 신분증은 이미 제출되었습니다."
        : "QR이 만료되었습니다. 다시 발급하세요.";
      qrBox.appendChild(p);
      qrExpire.hidden = true;
      qrDeadline = null;
      syncQrButton();
    }
  }

  /**
   * QR 발급 버튼 상태 (P1-5).
   *
   * <p>서버는 PENDING·RESUBMIT_REQUIRED 에서만 새 업로드 QR 을 발급한다. 신분증이 이미
   * 올라간 뒤에는 발급할 이유가 없으므로 버튼을 숨긴다. 다만 <b>영구 disabled 로 두지 않는다</b> —
   * 재제출 요청이 오면 다시 필요해지고, 그때는 버튼이 살아나야 한다.</p>
   */
  function syncQrButton() {
    var btn = $("issueQrBtn");
    if (!btn) return;
    btn.hidden = !qrIssuable;
    btn.disabled = !qrIssuable;
    btn.textContent = qrDeadline ? "QR 재발급" : "QR 발급";
  }

  $("issueQrBtn").addEventListener("click", function () {
    var btn = this;
    btn.disabled = true;
    post("/trainee/exam/precheck/" + CFG.sessionId + "/identity/qr", null)
      .then(function (res) {
        if (!res.ok) { idMessage.textContent = res.message || "QR 발급에 실패했습니다."; return; }
        drawQr(res.url);
        qrDeadline = Date.now() + (res.remainingSeconds * 1000);
        qrExpire.hidden = false;
        tickQr();
        idMessage.textContent = "휴대폰으로 QR을 스캔해 신분증을 제출하세요. " + res.expiresAt + " 까지 유효합니다.";
      })
      .catch(function () { idMessage.textContent = "QR 발급 중 오류가 발생했습니다."; })
      .then(function () { syncQrButton(); });
  });

  function pollIdentity() {
    fetch("/trainee/exam/precheck/" + CFG.sessionId + "/identity/status")
      .then(function (r) { return r.json(); })
      .then(function (res) {
        if (!res.ok) return;
        var tone = res.status === "APPROVED" ? "ok"
                 : res.status === "REJECTED" || res.status === "EXPIRED" ? "risk"
                 : res.status === "PENDING" ? "" : "warn";
        setBadge(idBadge, res.statusLabel, tone);
        $("identityCard").dataset.state = res.status.toLowerCase();

        if (res.status === "REJECTED" && res.reason) {
          idReason.hidden = false;
          idReason.textContent = "반려 사유 — " + res.reason + " (휴대폰에서 다시 제출해 주세요)";
        } else {
          idReason.hidden = true;
        }
        /* 서버가 알려 준 두 포인터로 부분 제출을 구분한다 (P1-2). */
        idSubmitted = !!res.hasIdCard;
        qrIssuable = !res.hasIdCard
          && (res.status === "PENDING" || res.status === "RESUBMIT_REQUIRED");
        syncQrButton();

        if (res.status === "PENDING" || res.status === "RESUBMIT_REQUIRED") {
          idMessage.textContent = res.hasIdCard
            ? "신분증이 제출되었습니다. 아래에서 웹캠 얼굴 사진을 제출해 주세요."
            : "휴대폰으로 QR을 스캔해 신분증을 제출하세요.";
        } else if (res.status === "SUBMITTED" || res.status === "UNDER_REVIEW") {
          idMessage.textContent = "신분증과 얼굴 사진이 모두 제출되었습니다. 운영진 검토를 기다리는 중입니다.";
        } else if (res.status === "APPROVED") {
          idMessage.textContent = "신분 확인이 승인되었습니다."
            + (res.approvalExpiresAt ? " (" + res.approvalExpiresAt + " 까지 유효)" : "");
        }

        identityApproved = !!res.canEnter;
        syncGate();
      })
      .catch(function () { /* 일시적 네트워크 오류는 다음 폴링에서 회복된다 */ });
  }

  /* ============================================================
     2. 웹캠 연결 확인
     ============================================================ */

  var camBadge = $("camBadge"), camMessage = $("camMessage"), permHint = $("permHint");
  var camSelect = $("camSelect"), video = $("camPreview"), previewEmpty = $("previewEmpty");
  var startBtn = $("camStartBtn"), stopBtn = $("camStopBtn");
  var stream = null, camPassed = false, frameSeen = 0, fpsTimer = null;

  function camState(state, message, tone) {
    $("webcamCard").dataset.state = state;
    setBadge(camBadge, {
      idle: "미확인", checking: "확인 중", pass: "통과", fail: "실패", unsupported: "브라우저 확인 불가"
    }[state] || state, tone);
    if (message !== undefined) camMessage.textContent = message;
    camPassed = (state === "pass");
    syncGate();
  }

  function resetInfo() {
    ["infoName", "infoState", "infoRes", "infoFps", "infoFrames"].forEach(function (id) {
      $(id).textContent = "—";
    });
  }

  /** getUserMedia 실패 원인을 구분해 해결 방법이 다르게 보이도록 한다. */
  function mediaError(err) {
    var n = err && err.name;
    if (n === "NotAllowedError" || n === "SecurityError") {
      return "카메라 권한이 거부되었습니다. 주소창의 카메라 아이콘에서 '허용'으로 바꾼 뒤 다시 시도하세요.";
    }
    if (n === "NotFoundError" || n === "DevicesNotFoundError") {
      return "카메라 장치를 찾을 수 없습니다. 연결을 확인하세요.";
    }
    if (n === "NotReadableError") {
      return "다른 프로그램이 카메라를 사용 중입니다. 해당 프로그램을 종료한 뒤 다시 시도하세요.";
    }
    if (n === "OverconstrainedError") {
      return "선택한 카메라를 사용할 수 없습니다. 다른 카메라를 선택해 주세요.";
    }
    if (n === "AbortError") return "카메라 요청이 취소되었습니다.";
    return "카메라 확인에 실패했습니다: " + (n || "알 수 없는 오류");
  }

  function stopStream() {
    if (fpsTimer) { clearInterval(fpsTimer); fpsTimer = null; }
    if (stream) { stream.getTracks().forEach(function (t) { t.stop(); }); stream = null; }
    video.srcObject = null;
    video.hidden = true;
    previewEmpty.hidden = false;
    startBtn.disabled = false;
    stopBtn.disabled = true;
    frameSeen = 0;
    /* 카메라가 멈췄는데 아직 안 보낸 캡처가 남아 있으면, 지금 화면에 보이는 얼굴과
       제출될 얼굴이 달라질 수 있다. 미제출 캡처·동의는 여기서 확실히 버린다 (P1-8). */
    if (typeof discardUnsentFace === "function") {
      discardUnsentFace("카메라 연결이 끊겨 촬영본을 지웠습니다. 다시 연결한 뒤 촬영해 주세요.");
    }
  }

  /**
   * 통과 기준은 "권한이 떨어졌다" 가 아니다.
   * 트랙이 live 이고, muted/ended 가 아니고, videoWidth 가 있고,
   * 실제 프레임이 들어와야 통과로 본다.
   */
  function verifyLive(track) {
    if (!track) return "비디오 트랙이 없습니다.";
    if (track.readyState !== "live") return "카메라 트랙이 활성 상태가 아닙니다.";
    if (track.muted) return "카메라가 음소거(차단) 상태입니다.";
    if (!video.videoWidth || !video.videoHeight) return "카메라 화면 크기를 읽지 못했습니다.";
    if (frameSeen < 3) return "카메라에서 영상이 들어오지 않습니다.";
    return null;
  }

  function watchFrames() {
    frameSeen = 0;
    if (typeof video.requestVideoFrameCallback === "function") {
      var step = function () {
        frameSeen++;
        $("infoFrames").textContent = frameSeen + "프레임 수신";
        if (stream) video.requestVideoFrameCallback(step);
      };
      video.requestVideoFrameCallback(step);
    } else {
      /* 미지원 브라우저 폴백 — currentTime 이 흐르면 프레임이 오는 것으로 본다. */
      var last = -1;
      fpsTimer = setInterval(function () {
        if (!stream) return;
        if (video.currentTime > last) { frameSeen++; last = video.currentTime; }
        $("infoFrames").textContent = frameSeen + "회 갱신 확인 (폴백)";
      }, 200);
    }
  }

  function renderInfo(track) {
    var s = (track.getSettings && track.getSettings()) || {};
    $("infoName").textContent = track.label || "(이름 없음)";
    $("infoState").textContent = track.readyState === "live" ? "연결됨" : track.readyState;
    $("infoRes").textContent = (s.width && s.height) ? (s.width + " × " + s.height) : "확인 불가";
    $("infoFps").textContent = s.frameRate ? (Math.round(s.frameRate) + " fps") : "확인 불가";
  }

  function bindTrack(track) {
    track.addEventListener("ended", function () {
      stopStream(); resetInfo();
      camState("fail", "카메라 연결이 끊겼습니다. 다시 연결해 주세요.", "risk");
    });
    track.addEventListener("mute", function () {
      camState("fail", "카메라 영상이 중단되었습니다. 장치를 확인해 주세요.", "risk");
      /* mute 는 stopStream 을 타지 않으므로 여기서 직접 버린다 (P1-8). */
      discardUnsentFace("카메라 영상이 중단되어 촬영본을 지웠습니다. 다시 촬영해 주세요.");
    });
  }

  function openCamera(deviceId) {
    if (!window.isSecureContext) {
      camState("fail", "보안 연결(HTTPS 또는 localhost)이 아니어서 브라우저가 카메라를 차단합니다.", "risk");
      return Promise.resolve();
    }
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      camState("unsupported", "이 브라우저는 카메라 API를 지원하지 않습니다. 최신 Chrome·Edge·Safari를 사용하세요.", "warn");
      return Promise.resolve();
    }

    stopStream();
    permHint.hidden = false;
    camState("checking", "카메라 권한을 요청하는 중입니다…", "warn");

    /* 영상만 필요하다. 마이크 권한까지 요구하면 거부율만 올라간다. */
    var constraints = { audio: false, video: deviceId ? { deviceId: { exact: deviceId } } : true };

    return navigator.mediaDevices.getUserMedia(constraints)
      .then(function (s) {
        stream = s;
        permHint.hidden = true;
        video.srcObject = s;
        video.hidden = false;
        previewEmpty.hidden = true;
        startBtn.disabled = true;
        stopBtn.disabled = false;

        var track = s.getVideoTracks()[0];
        bindTrack(track);
        renderInfo(track);
        watchFrames();

        return video.play().catch(function () { /* 자동재생 차단은 미리보기에만 영향 */ })
          .then(function () { return listDevices(track); })
          .then(function () {
            /* 프레임이 실제로 들어올 시간을 준 뒤 판정한다. */
            return new Promise(function (r) { setTimeout(r, 700); });
          })
          .then(function () {
            var problem = verifyLive(track);
            if (problem) { camState("fail", problem, "risk"); return; }

            /* 브라우저 확인만으로 pass 로 바꾸지 않는다.
               서버가 점검 시각을 받아 적어야 입장 판정이 성립하므로, 응답을 기다린다. */
            camState("checking", "점검 결과를 서버에 기록하는 중입니다…", "warn");
            return post("/trainee/exam/precheck/" + CFG.sessionId + "/webcam-checked", null)
              .then(function (res) {
                if (!res || res.ok !== true) {
                  camState("fail",
                    "점검 결과를 서버에 기록하지 못했습니다. " + ((res && res.message) || "다시 시도해 주세요."),
                    "risk");
                  $("retryWebcam").hidden = false;
                  return;
                }
                camState("pass",
                  "이 브라우저에서 카메라 연결이 확인되었고 서버에 점검 시각이 기록되었습니다. "
                  + "(서버가 영상을 받거나 검증하지는 않습니다)", "ok");
                $("retryWebcam").hidden = true;
                $("faceCheckArea").hidden = false;
              })
              .catch(function () {
                camState("fail", "네트워크 오류로 점검 결과를 기록하지 못했습니다.", "risk");
                $("retryWebcam").hidden = false;
              });
          });
      })
      .catch(function (err) {
        permHint.hidden = true;
        stopStream(); resetInfo();
        camState("fail", mediaError(err), "risk");
      });
  }

  /** 권한 허용 전에는 label 이 비어 있으므로, 최초 성공 후에 목록을 읽는다. */
  function listDevices(currentTrack) {
    if (!navigator.mediaDevices.enumerateDevices) return Promise.resolve();
    return navigator.mediaDevices.enumerateDevices().then(function (list) {
      var cams = list.filter(function (d) { return d.kind === "videoinput"; });
      var currentId = (currentTrack.getSettings && currentTrack.getSettings().deviceId) || "";
      /* 장치 이름·deviceId 를 innerHTML 로 넣지 않는다 — 값에 따옴표나 태그가 섞이면
         마크업이 깨지거나 주입이 된다. DOM API + textContent 로 만든다 (지적 10). */
      camSelect.replaceChildren();
      if (!cams.length) {
        var none = document.createElement("option");
        none.textContent = "사용 가능한 카메라 없음";
        camSelect.appendChild(none);
        camSelect.disabled = true;
        return;
      }
      cams.forEach(function (d, i) {
        var opt = document.createElement("option");
        opt.value = d.deviceId;
        opt.textContent = d.label || ("카메라 " + (i + 1));
        opt.selected = d.deviceId === currentId;
        camSelect.appendChild(opt);
      });
      camSelect.disabled = false;
    });
  }

  startBtn.addEventListener("click", function () { openCamera(null); });

  /* 서버 기록만 실패한 경우 — 카메라는 살아 있으므로 기록만 다시 시도한다. */
  $("retryWebcam").addEventListener("click", function () {
    if (!stream) { camState("fail", "카메라부터 다시 연결해 주세요.", "risk"); return; }
    var btn = this;
    btn.disabled = true;
    camState("checking", "점검 결과를 서버에 기록하는 중입니다…", "warn");
    post("/trainee/exam/precheck/" + CFG.sessionId + "/webcam-checked", null)
      .then(function (res) {
        if (res && res.ok === true) {
          camState("pass",
            "이 브라우저에서 카메라 연결이 확인되었고 서버에 점검 시각이 기록되었습니다. "
            + "(서버가 영상을 받거나 검증하지는 않습니다)", "ok");
          btn.hidden = true;
          $("faceCheckArea").hidden = false;
        } else {
          camState("fail", "여전히 기록하지 못했습니다. " + ((res && res.message) || ""), "risk");
        }
      })
      .catch(function () { camState("fail", "네트워크 오류로 기록하지 못했습니다.", "risk"); })
      .then(function () { btn.disabled = false; });
  });

  stopBtn.addEventListener("click", function () {
    stopStream(); resetInfo();
    camState("idle", "카메라 연결을 끊었습니다.", "");
    $("faceCheckArea").hidden = true;
  });

  camSelect.addEventListener("change", function () {
    if (!this.value) return;
    /* 장치를 바꾸면 이전 카메라로 찍은 미제출 사진은 버린다 — 화면과 제출본이 어긋난다 (P1-8). */
    discardUnsentFace("카메라를 변경해 촬영본을 지웠습니다. 새 카메라로 다시 촬영해 주세요.");
    openCamera(this.value);
  });

  $("mirrorToggle").addEventListener("change", function () {
    video.classList.toggle("mirrored", this.checked);
  });

  /* 장치가 뽑히면 목록을 다시 읽고, 쓰던 카메라가 사라졌으면 즉시 실패로 돌린다. */
  if (navigator.mediaDevices && navigator.mediaDevices.addEventListener) {
    navigator.mediaDevices.addEventListener("devicechange", function () {
      if (!stream) return;
      var track = stream.getVideoTracks()[0];
      var usingId = (track && track.getSettings && track.getSettings().deviceId) || "";
      navigator.mediaDevices.enumerateDevices().then(function (list) {
        var still = list.some(function (d) { return d.kind === "videoinput" && d.deviceId === usingId; });
        if (!still) {
          stopStream(); resetInfo();
          camState("fail", "사용 중이던 카메라가 제거되었습니다. 다시 연결해 주세요.", "risk");
          $("faceCheckArea").hidden = true;
        } else {
          listDevices(track);
        }
      });
    });
  }

  /* ---------- 얼굴 확인용 사진 ---------- */

  var faceCanvas = $("faceCanvas"), faceEmpty = $("faceEmpty"), faceMessage = $("faceMessage");
  var faceConsent = $("faceConsent"), faceSubmitBtn = $("faceSubmitBtn");
  var faceBlob = null;
  var faceBlobPending = false;   /* toBlob 은 비동기다 — 준비 전에 제출을 열면 안 된다 (P1-8) */
  var faceSubmitted = false;

  /** 촬영·동의·전송상태를 모두 반영해 제출 버튼을 켠다. 한 군데서만 결정한다. */
  function syncFaceSubmit() {
    faceSubmitBtn.disabled = !(faceBlob && faceConsent.checked && !faceBlobPending && !faceSubmitted);
  }

  /**
   * 카메라 정지·장치 변경·트랙 종료에서 부르는 진입점.
   * 이미 제출이 끝난 세션은 건드리지 않는다 — 서버에 있는 사진까지 지울 이유가 없다.
   */
  function discardUnsentFace(message) {
    if (faceSubmitted) return;
    if (!faceBlob && !faceBlobPending && faceCanvas.hidden) return;
    resetFaceCapture(message);
  }

  /** 미제출 캡처·Blob·동의를 모두 되돌린다. 카메라가 끊기면 이 상태를 남겨 두면 안 된다. */
  function resetFaceCapture(message) {
    faceBlob = null;
    faceBlobPending = false;
    if (faceCanvas.width && faceCanvas.height) {
      faceCanvas.getContext("2d").clearRect(0, 0, faceCanvas.width, faceCanvas.height);
    }
    faceCanvas.hidden = true;
    faceEmpty.hidden = false;
    $("faceRetakeBtn").hidden = true;
    $("faceShotBtn").hidden = false;
    $("faceConsentLine").hidden = true;
    faceSubmitBtn.hidden = true;
    faceConsent.checked = false;
    syncFaceSubmit();
    if (message !== undefined) faceMessage.textContent = message;
  }

  $("faceShotBtn").addEventListener("click", function () {
    if (!stream || !video.videoWidth) {
      faceMessage.textContent = "먼저 웹캠 연결을 완료해 주세요.";
      return;
    }
    faceCanvas.width = video.videoWidth;
    faceCanvas.height = video.videoHeight;
    /* 저장본은 반전하지 않는다 — 화면 미리보기만 보기 편하라고 뒤집는다. */
    faceCanvas.getContext("2d").drawImage(video, 0, 0);
    faceCanvas.hidden = false;
    faceEmpty.hidden = true;

    /* toBlob 은 비동기다. 콜백이 오기 전에는 제출을 못 하게 잠가 둔다 (P1-8). */
    faceBlob = null;
    faceBlobPending = true;
    syncFaceSubmit();
    faceCanvas.toBlob(function (b) {
      faceBlobPending = false;
      if (!b) {
        /* 캔버스를 이미지로 만들지 못했다. 조용히 넘어가면 제출 버튼만 계속 잠긴 채 이유가 안 보인다. */
        faceMessage.textContent = "사진을 준비하지 못했습니다. 다시 촬영해 주세요.";
        syncFaceSubmit();
        return;
      }
      faceBlob = b;
      faceMessage.textContent = "사진을 확인한 뒤 동의에 체크하고 제출하세요.";
      syncFaceSubmit();
    }, "image/jpeg", 0.9);

    this.hidden = true;
    $("faceRetakeBtn").hidden = false;
    $("faceConsentLine").hidden = false;
    faceSubmitBtn.hidden = false;
    faceMessage.textContent = "사진을 준비하는 중입니다…";
  });

  $("faceRetakeBtn").addEventListener("click", function () {
    resetFaceCapture("");
  });

  faceConsent.addEventListener("change", syncFaceSubmit);

  faceSubmitBtn.addEventListener("click", function () {
    /* Blob 이 아직 없거나 준비 중이면 절대 보내지 않는다 (P1-8). */
    if (!faceBlob || faceBlobPending || !faceConsent.checked) return;
    var btn = this;
    btn.disabled = true;
    faceMessage.textContent = "제출하는 중입니다…";
    var fd = new FormData();
    fd.append("file", faceBlob, "face-check.jpg");
    fd.append("consent", "true");   /* 서버가 다시 확인한다 — 화면 체크박스만으로 저장하지 않는다 */
    post("/trainee/exam/precheck/" + CFG.sessionId + "/face-check", fd, true)
      .then(function (res) {
        if (!res || !res.ok) {
          /* 서버 4xx/5xx·JSON 파싱 실패. 화면을 되살려 다시 시도할 수 있게 한다. */
          faceMessage.textContent = (res && res.message) || "제출에 실패했습니다. 다시 시도해 주세요.";
          syncFaceSubmit();
          btn.disabled = false;
          return;
        }
        /* ★ 서버가 성공을 확인한 뒤에만 "제출 완료" 로 바꾼다 (P1-8). */
        faceSubmitted = true;
        faceMessage.textContent = res.message || "제출했습니다.";
        faceCanvas.getContext("2d").clearRect(0, 0, faceCanvas.width, faceCanvas.height);
        faceCanvas.hidden = true;
        faceEmpty.hidden = false;
        faceEmpty.textContent = "제출 완료";
        faceBlob = null;
        $("faceRetakeBtn").hidden = true;
        $("faceConsentLine").hidden = true;
        btn.hidden = true;
      })
      .catch(function () {
        /* 네트워크 자체가 실패한 경우. catch 가 없으면 버튼이 잠긴 채 아무 안내도 없다. */
        faceMessage.textContent = "네트워크 오류로 제출하지 못했습니다. 다시 시도해 주세요.";
        syncFaceSubmit();
        btn.disabled = false;
      });
  });

  /* ============================================================
     3. 화면 공유 확인 (LXP-018)
     사용자가 공유 대상을 직접 고른다. 서버는 이 화면을 받지 않는다.
     ============================================================ */

  var shareStream = null;

  function shareState(state, msg, tone) {
    $("shareCard").dataset.state = state;
    setBadge($("shareBadge"), {
      idle: "\ubbf8\ud655\uc778", checking: "\ud655\uc778 \uc911",
      pass: "\ud655\uc778\ub428", fail: "\ubbf8\ud1b5\uacfc"
    }[state] || state, tone || "");
    if (msg !== undefined) $("shareMessage").textContent = msg;
  }

  function stopShare() {
    if (shareStream) {
      shareStream.getTracks().forEach(function (t) { t.stop(); });
      shareStream = null;
    }
    var v = $("sharePreview");
    if (v) { v.srcObject = null; v.hidden = true; }
    var em = $("shareEmpty"); if (em) em.hidden = false;
    $("shareStartBtn").disabled = false;
    $("shareStopBtn").disabled = true;
    $("shareState").textContent = "\u2014";
    $("shareTarget").textContent = "\u2014";
    $("shareRes").textContent = "\u2014";
    sharePassed = false;
    syncGate();
  }

  $("shareStartBtn").addEventListener("click", function () {
    if (!window.isSecureContext) {
      shareState("fail",
        "\ubcf4\uc548 \uc5f0\uacb0(HTTPS \ub610\ub294 localhost)\uc774 \uc544\ub2c8\uc5b4\uc11c \ubc0f\ub77c\uc6b0\uc7b0\uac00 \ud654\uba74 \uacf5\uc720\ub97c \uc0c1\ub2e8\ud569\ub2c8\ub2e4. localhost \uc8fc\uc18c\ub85c \uc5ec\uc5b4 \uc8fc\uc138\uc694.",
        "risk");
      return;
    }
    if (!navigator.mediaDevices || !navigator.mediaDevices.getDisplayMedia) {
      shareState("fail",
        "\uc774 \ubc0f\ub77c\uc6b0\uc7b0\ub294 \ud654\uba74 \uacf5\uc720(getDisplayMedia)\ub97c \uc9c0\uc6d0\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4.",
        "risk");
      return;
    }

    shareState("checking", "\uacf5\uc720\ud560 \ud654\uba74\uc744 \uc120\ud0dd\ud574 \uc8fc\uc138\uc694\u2026", "warn");
    navigator.mediaDevices.getDisplayMedia({ video: true, audio: false })
      .then(function (stream) {
        shareStream = stream;
        var track = stream.getVideoTracks()[0];
        if (!track) {
          stopShare();
          shareState("fail", "\uacf5\uc720 \ud2b8\ub799\uc744 \ubc1b\uc9c0 \ubabb\ud588\uc2b5\ub2c8\ub2e4.", "risk");
          return;
        }
        var v = $("sharePreview");
        v.srcObject = stream;
        v.hidden = false;
        $("shareEmpty").hidden = true;
        v.play().catch(function () { /* \uc790\ub3d9\uc7ac\uc0dd \ucc28\ub2e8\uc740 \ubbf8\ub9ac\ubcf4\uae30\uc5d0\ub9cc \uc5f0\uad00 */ });

        var st = track.getSettings ? track.getSettings() : {};
        $("shareState").textContent = track.readyState === "live"
          ? "\uacf5\uc720 \uc911" : track.readyState;
        /* displaySurface \ub294 \ubc0f\ub77c\uc6b0\uc7b0\uac00 \uc8fc\ub294 \uacbd\uc6b0\uc5d0\ub9cc \uc788\ub2e4 \u2014 \uc5c6\uc73c\uba74 \ub9cc\ub4e4\uc9c0 \uc54a\ub294\ub2e4. */
        var surf = { monitor: "\uc804\uccb4 \ud654\uba74", window: "\ucc3d", browser: "\ubc0f\ub77c\uc6b0\uc7b0 \ud0ed" };
        $("shareTarget").textContent = st.displaySurface
          ? (surf[st.displaySurface] || st.displaySurface)
          : (track.label || "\ubc0f\ub77c\uc6b0\uc7b0\uac00 \uc54c\ub824\uc8fc\uc9c0 \uc54a\uc74c");
        $("shareRes").textContent = (st.width && st.height)
          ? (st.width + " \u00d7 " + st.height) : "\u2014";

        $("shareStartBtn").disabled = true;
        $("shareStopBtn").disabled = false;
        sharePassed = true;
        shareState("pass",
          "\uc774 \ubc0f\ub77c\uc6b0\uc7b0\uc5d0\uc11c \ud654\uba74 \uacf5\uc720\uac00 \ud655\uc778\ub418\uc5c8\uc2b5\ub2c8\ub2e4. (\uc11c\ubc84\uac00 \ud654\uba74\uc744 \ubc1b\uac70\ub098 \uac80\uc99d\ud558\uc9c0\ub294 \uc54a\uc2b5\ub2c8\ub2e4)",
          "ok");
        syncGate();

        /* \uc0ac\uc6a9\uc790\uac00 \ubc0f\ub77c\uc6b0\uc7b0 UI \ub85c \uacf5\uc720\ub97c \uc911\uc9c0\ud558\uba74 \uc989\uc2dc \ubbf8\ud1b5\uacfc\ub85c \ub418\ub3cc\ub9b0\ub2e4. */
        track.addEventListener("ended", function () {
          stopShare();
          shareState("fail",
            "\ud654\uba74 \uacf5\uc720\uac00 \uc911\uc9c0\ub418\uc5c8\uc2b5\ub2c8\ub2e4. \ub2e4\uc2dc \uc2dc\uc791\ud574 \uc8fc\uc138\uc694.", "risk");
        });
      })
      .catch(function (err) {
        var n = err && err.name;
        var msg;
        if (n === "NotAllowedError") {
          msg = "\ud654\uba74 \uacf5\uc720\uac00 \ucd94\uc18c\ub418\uc5c8\uac70\ub098 \uac70\ubd80\ub418\uc5c8\uc2b5\ub2c8\ub2e4. \ubc84\ud2bc\uc744 \ub2e4\uc2dc \ub20c\ub7ec \ub300\uc0c1\uc744 \uc120\ud0dd\ud574 \uc8fc\uc138\uc694.";
        } else if (n === "NotFoundError") {
          msg = "\uacf5\uc720\ud560 \uc218 \uc788\ub294 \ud654\uba74\uc744 \ucc3e\uc9c0 \ubabb\ud588\uc2b5\ub2c8\ub2e4.";
        } else if (n === "NotSupportedError") {
          msg = "\uc774 \ubc0f\ub77c\uc6b0\uc7b0\u00b7\ud658\uacbd\uc5d0\uc11c\ub294 \ud654\uba74 \uacf5\uc720\ub97c \uc9c0\uc6d0\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4.";
        } else {
          msg = "\ud654\uba74 \uacf5\uc720\uc5d0 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4: " + (n || "\uc54c \uc218 \uc5c6\ub294 \uc624\ub958");
        }
        stopShare();
        shareState("fail", msg, "risk");
      });
  });

  $("shareStopBtn").addEventListener("click", function () {
    stopShare();
    shareState("idle", "\ud654\uba74 \uacf5\uc720\ub97c \uc911\uc9c0\ud588\uc2b5\ub2c8\ub2e4.", "");
  });

  window.addEventListener("pagehide", stopShare);

  /* ============================================================
     4. \ubaa8\ub2c8\ud130 \uad6c\uc131 \ud655\uc778 (LXP-019)
     screen.isExtended \ubbf8\uc9c0\uc6d0\uc744 \ud1b5\uacfc\ub85c \uac00\uc7a5\ud558\uc9c0 \uc54a\ub294\ub2e4.
     \uad8c\ud55c \ud31d\uc5c5\uc744 \uc790\ub3d9\uc73c\ub85c \ub744\uc6b0\uc9c0 \uc54a\ub294\ub2e4 \u2014 \ubc84\ud2bc\uc744 \ub20c\ub800\uc744 \ub54c\ub9cc \ud655\uc778\ud55c\ub2e4.
     ============================================================ */

  function monitorState(state, msg, tone) {
    $("monitorCard").dataset.state = state;
    setBadge($("monitorBadge"), {
      idle: "\ubbf8\ud655\uc778", pass: "\ub2e8\uc77c \ubaa8\ub2c8\ud130",
      fail: "\ud655\uc7a5 \ub514\uc2a4\ud50c\ub808\uc774", unknown: "\ud655\uc778 \ubd88\uac00"
    }[state] || state, tone || "");
    if (msg !== undefined) $("monitorMessage").textContent = msg;
  }

  $("monitorCheckBtn").addEventListener("click", function () {
    var sc = window.screen;
    if (!sc || typeof sc.isExtended !== "boolean") {
      monitorPassed = false;
      $("monitorExtended").textContent = "\ud655\uc778 \ubd88\uac00";
      $("monitorSource").textContent = "screen.isExtended \ubbf8\uc9c0\uc6d0";
      monitorState("unknown",
        "\uc774 \ubc0f\ub77c\uc6b0\uc7b0\ub294 \ud655\uc7a5 \ub514\uc2a4\ud50c\ub808\uc774 \uc5ec\ubd80\ub97c \uc54c\ub824\uc8fc\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. \ud655\uc778 \ubd88\uac00\ub294 \ud1b5\uacfc\ub85c \ucc98\ub9ac\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. \uac10\ub3c5\uad00\uc5d0\uac8c \ubaa8\ub2c8\ud130 \uad6c\uc131\uc744 \uc9c1\uc811 \ud655\uc778\ubc1b\uc544 \uc8fc\uc138\uc694.",
        "warn");
      syncGate();
      return;
    }
    $("monitorSource").textContent = "screen.isExtended";
    if (sc.isExtended) {
      monitorPassed = false;
      $("monitorExtended").textContent = "\uc0ac\uc6a9 \uc911 (2\ub300 \uc774\uc0c1)";
      monitorState("fail",
        "\ud655\uc7a5 \ub514\uc2a4\ud50c\ub808\uc774\uac00 \uac10\uc9c0\ub418\uc5c8\uc2b5\ub2c8\ub2e4. \uc2dc\ud5d8 \uc911\uc5d0\ub294 \ubaa8\ub2c8\ud130\ub97c \ud55c \ub300\ub9cc \uc0ac\uc6a9\ud574\uc57c \ud569\ub2c8\ub2e4. \ucd94\uac00 \ubaa8\ub2c8\ud130 \uc5f0\uacb0\uc744 \ud574\uc81c\ud55c \ub4a4 \ub2e4\uc2dc \ud655\uc778\ud574 \uc8fc\uc138\uc694.",
        "risk");
    } else {
      monitorPassed = true;
      $("monitorExtended").textContent = "\uc0ac\uc6a9\ud558\uc9c0 \uc54a\uc74c (1\ub300)";
      monitorState("pass", "\ub2e8\uc77c \ubaa8\ub2c8\ud130 \uad6c\uc131\uc774 \ud655\uc778\ub418\uc5c8\uc2b5\ub2c8\ub2e4.", "ok");
    }
    syncGate();
  });

  /* ============================================================
     입장 게이트
     ============================================================ */

  function syncGate() {
    /* 프론트 시작 버튼은 <b>표시된 필수 항목이 모두 통과할 때만</b> 열린다 (LXP-018).
       단, 서버가 실제로 강제하는 것은 신분확인 승인과 웹캠 점검 기록뿐이다 —
       화면 공유·모니터는 서버 검증 API 가 없어 프론트 확인 단계다. */
    var ok = identityApproved && camPassed && sharePassed && monitorPassed;
    var btn = $("enterBtn");
    btn.disabled = !ok;
    btn.classList.toggle("btn-primary", ok);
    btn.classList.toggle("btn-secondary", !ok);

    if (ok) {
      $("gateMsg").textContent = "모든 조건을 만족했습니다. 시험을 시작할 수 있습니다.";
    } else {
      var left = [];
      if (!identityApproved) left.push("신분 확인 승인");
      if (!camPassed) left.push("웹캠 연결");
      if (!sharePassed) left.push("화면 공유 확인");
      if (!monitorPassed) left.push("모니터 구성 확인");
      $("gateMsg").textContent = "남은 항목: " + left.join(", ");
    }
  }

  /* 페이지를 떠날 때 카메라를 반드시 끈다 — 표시등이 켜진 채로 남지 않도록. */
  window.addEventListener("pagehide", stopStream);

  /* ---------- 시작 ---------- */
  resetInfo();
  syncGate();
  pollIdentity();
  setInterval(pollIdentity, 3000);
  setInterval(tickQr, 1000);
})();
