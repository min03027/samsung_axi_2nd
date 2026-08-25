/* ============================================================
   live-class-common.js — 화상강의 데모 공통 유틸리티 (LXP-125 / LXP-127)

   브라우저에서는 window.LiveClassCommon, Node 에서는 module.exports 로
   같은 API 를 노출한다 — 계약 테스트가 문자열 검사가 아니라 실제로
   require 해 판정 로직을 실행한다.

   여기서 하는 일은 딱 둘이다:
     ① 보안 컨텍스트·브라우저 지원 여부 판정 (supportState)
     ② 장치 오류(getUserMedia/getDisplayMedia 의 DOMException)를
        사용자에게 다른 안내가 필요한 원인별로 구분 (mediaErrorInfo)
   실제 MediaStream 을 저장·전송하는 코드는 이 파일에 없다.
   ============================================================ */
(function () {
  "use strict";

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  /* 보안 컨텍스트가 아니거나 API 자체가 없으면 장치 오류보다 먼저 이 이유로 막힌다. */
  function supportState(secureContext, mediaDevices) {
    if (secureContext === false) {
      return {
        ok: false, reason: "INSECURE_CONTEXT",
        message: "이 페이지가 보안 연결이 아니어서 브라우저가 카메라·마이크·화면 공유를 차단합니다. localhost 또는 HTTPS에서 열어 주세요."
      };
    }
    if (!mediaDevices) {
      return {
        ok: false, reason: "UNSUPPORTED",
        message: "이 브라우저는 카메라·마이크 API를 지원하지 않습니다. 최신 Chrome, Edge, Safari를 사용하세요."
      };
    }
    return { ok: true, reason: "OK", message: "" };
  }

  /* DOMException.name 별 원인 — 사용자가 다음에 뭘 해야 하는지가 달라진다. */
  var ERROR_MAP = {
    NotAllowedError: function (kind) {
      return {
        reason: "PERMISSION_DENIED",
        message: kind + " 권한이 거부되었거나 브라우저 설정에서 차단되어 있습니다. 주소창의 권한 아이콘에서 허용으로 바꾼 뒤 다시 시도하세요."
      };
    },
    NotFoundError: function (kind) {
      return { reason: "DEVICE_NOT_FOUND", message: kind + " 장치를 찾을 수 없습니다. 연결 상태를 확인하세요." };
    },
    NotReadableError: function (kind) {
      return {
        reason: "DEVICE_BUSY",
        message: "다른 프로그램이 " + kind + "을(를) 사용 중이거나 장치에 접근할 수 없습니다. 해당 프로그램을 종료한 뒤 다시 시도하세요."
      };
    },
    OverconstrainedError: function () {
      return { reason: "CONSTRAINT_FAILED", message: "선택한 장치 또는 조건을 사용할 수 없습니다. 다른 장치를 선택해 보세요." };
    },
    AbortError: function (kind) {
      return { reason: "ABORTED", message: kind + " 시작이 중단되었습니다." };
    },
    TypeError: function () {
      return { reason: "CONTEXT_ERROR", message: "보안 컨텍스트와 지원 조건을 확인하세요." };
    }
  };

  /** 목록에 없는 오류 이름은 "알 수 없는 장치 오류" 로 묶는다. */
  function mediaErrorInfo(name, kind) {
    kind = kind || "장치";
    var handler = ERROR_MAP[name];
    if (handler) return handler(kind);
    return {
      reason: "UNKNOWN_ERROR",
      message: kind + " 확인 중 알 수 없는 오류가 발생했습니다." + (name ? " (" + name + ")" : "")
    };
  }

  /** 값이 하나도 없거나 하나라도 "pass" 가 아니면 통과가 아니다 — 자동 통과를 만들지 않는다. */
  function allChecksPassed(checks) {
    if (!checks || typeof checks !== "object") return false;
    var keys = Object.keys(checks);
    if (!keys.length) return false;
    return keys.every(function (k) { return checks[k] === "pass"; });
  }

  function stopStream(stream) {
    if (!stream || !stream.getTracks) return;
    stream.getTracks().forEach(function (t) { t.stop(); });
  }

  function attachStream(videoElement, stream) {
    videoElement.srcObject = stream;
    videoElement.hidden = false;
    return videoElement.play().catch(function () { /* 자동재생 차단은 미리보기에만 영향 */ });
  }

  /* ---------- 스트림 슬롯 ----------
     빠른 재클릭·장치 재선택에서 "먼저 보낸 요청이 나중에 도착"하는 역전이 실제로 일어난다.
     세대 번호(generation) 하나로 이 문제를 전부 해결한다:

       begin()          호출마다 세대를 올리고 새 토큰을 준다 — 그 순간 이전 토큰은 전부 낡은 것이 된다.
       resolve(tok, s)  토큰이 아직 최신이면(= isCurrent) 기존 스트림을 정지하고 s 를 채택한다.
                        토큰이 이미 낡았으면(= 더 최신 요청이 있었거나 dispose 된 뒤) s 를 그 자리에서
                        바로 정지하고 채택하지 않는다 — 화면에는 절대 반영되지 않는다.
       dispose()        이후의 모든 resolve() 를 영구히 거부 상태로 만든다. pagehide 이후 늦게 도착하는
                        스트림도 이 규칙 하나로 자동 정지된다 — 별도의 "떠났는지" 플래그가 필요 없다.

     카메라 "장치 점검"과 "장치 전환"이 같은 슬롯을 공유하면, 전환이 점검보다 늦게 끝나도
     전환 쪽 토큰이 더 최신이라 점검의 뒤늦은 결과가 자동으로 버려진다 — 화면마다 슬롯을
     하나씩만 두면 된다. */
  function createStreamSlot() {
    var current = null;
    var generation = 0;
    var disposed = false;

    function begin() {
      generation += 1;
      return generation;
    }

    function isCurrent(token) {
      return !disposed && token === generation;
    }

    function resolve(token, stream) {
      if (!isCurrent(token)) {
        stopStream(stream);
        return false;
      }
      if (current && current !== stream) stopStream(current);
      current = stream;
      return true;
    }

    function stop() {
      stopStream(current);
      current = null;
    }

    function dispose() {
      disposed = true;
      stopStream(current);
      current = null;
    }

    function getStream() { return current; }

    return { begin: begin, isCurrent: isCurrent, resolve: resolve, stop: stop, dispose: dispose, getStream: getStream };
  }

  /** 활성 장치가 목록에서 사라졌는지 판정한다. select 옵션 재구성 뒤 남은 값이 아니라
      "성공 시점에 실제로 잡은 deviceId"를 직접 대조해야 한다 — 안 그러면 브라우저가
      제거된 장치 대신 남은 첫 옵션을 자동 선택해서 그걸 정상으로 오인하게 된다. */
  function isDevicePresent(deviceId, deviceList) {
    if (!deviceId || !deviceList) return false;
    for (var i = 0; i < deviceList.length; i++) {
      if (deviceList[i] && deviceList[i].deviceId === deviceId) return true;
    }
    return false;
  }

  /** 검색·필터·시나리오 변경으로 선택이 더 이상 보이지 않으면 null 을 돌려준다.
      선택 유지 여부를 화면마다 다시 판단하지 않도록 판정만 여기서 순수 함수로 뽑았다. */
  function reconcileSelection(selectedId, visibleIds) {
    if (!selectedId || !visibleIds) return null;
    for (var i = 0; i < visibleIds.length; i++) {
      if (visibleIds[i] === selectedId) return selectedId;
    }
    return null;
  }

  /** getUserMedia/getDisplayMedia 성공 콜백에서 슬롯에 채택하기 "전에" 반드시 거친다.
      요청한 종류(kind: "video"|"audio")의 트랙이 없으면 반환된 스트림의 모든 트랙을 그
      자리에서 정지하고 null 을 돌려준다 — 슬롯은 손대지 않으므로 기존 스트림이 살아있다.
      트랙이 있으면 그제서야 slot.resolve() 를 호출한다 — 이 순서가 중요하다. 먼저
      resolve() 부터 하면 "쓸 수 없는 빈 스트림"이 기존 정상 스트림을 밀어내고 슬롯을
      차지해 버린다. */
  function adoptTrack(slot, token, stream, kind) {
    var tracks = kind === "audio" ? stream.getAudioTracks() : stream.getVideoTracks();
    var track = tracks && tracks[0];
    if (!track) {
      stopStream(stream);
      return null;
    }
    if (!slot.resolve(token, stream)) return null;
    return track;
  }

  /** select 는 버튼과 달리 비활성 사유가 두 가지(요청 중 / 옵션 없음)이고 서로 독립적이다.
      요청이 끝났다고 disabled 를 그냥 false 로 덮으면, 애초에 옵션이 없던(또는 권한 거부로
      한 번도 채워지지 못한) select 가 열려 버린다. 두 사유를 OR 로만 합친다. */
  function computeSelectDisabled(busy, hasOptions) {
    return !!busy || !hasOptions;
  }

  /** 재점검·장치 전환이 "실패"했을 때만 쓰는 정책 결정. 성공 시엔 항상 새 스트림으로
      교체하므로 이 함수를 거치지 않는다.
        wasPass=true  → 기존 스트림·pass 배지·입장 게이트를 그대로 두고 안내만 한다.
        wasPass=false → 원래 통과한 적이 없으므로 그냥 fail 로 떨어진다.
      화면 스크립트는 이 결과의 keepStream 이 true 면 setState 를 호출하지 않고(따라서
      배지·게이트를 건드리지 않고) 안내 메시지만 띄운다. */
  function switchFailurePolicy(wasPass) {
    return wasPass
      ? { nextState: "pass", keepStream: true }
      : { nextState: "fail", keepStream: false };
  }

  /* ---------- 토스트 (exam-common.js 의 ExamDemo.toast 와 같은 문법) ----------
     3개 화면이 공용으로 쓴다 — 화면마다 따로 만들지 않는다. */
  var toastHost = null;
  function toast(message, tone) {
    if (typeof document === "undefined") return;
    if (!toastHost) {
      toastHost = document.createElement("div");
      toastHost.className = "toast-host";
      document.body.appendChild(toastHost);
    }
    var t = document.createElement("div");
    t.className = "toast" + (tone ? " " + tone : "");
    t.setAttribute("role", tone === "risk" ? "alert" : "status");
    t.textContent = message;
    toastHost.appendChild(t);
    window.setTimeout(function () {
      t.classList.add("is-out");
      window.setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 240);
    }, 3200);
  }

  var api = {
    esc: esc,
    supportState: supportState,
    mediaErrorInfo: mediaErrorInfo,
    allChecksPassed: allChecksPassed,
    stopStream: stopStream,
    attachStream: attachStream,
    createStreamSlot: createStreamSlot,
    isDevicePresent: isDevicePresent,
    reconcileSelection: reconcileSelection,
    adoptTrack: adoptTrack,
    computeSelectDisabled: computeSelectDisabled,
    switchFailurePolicy: switchFailurePolicy,
    toast: toast
  };

  if (typeof window !== "undefined") window.LiveClassCommon = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})();
