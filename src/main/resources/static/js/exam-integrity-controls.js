/* =============================================================
   LXP-014 / LXP-017 / LXP-021 응시 중 무결성 UI

   <b>기존 do-test.html 의 이벤트 전송을 대체하지 않는다.</b>
   기존 스크립트는 blockTabSwitch 일 때 TAB_BLUR/TAB_FOCUS, blockCopyPaste 일 때
   COPY/PASTE 를 이미 보낸다. 이 파일은 그 위에 <b>전체화면 감지와 사용자 안내</b>를 얹는다.
   중복 전송을 만들지 않으려고 TAB_BLUR·TAB_FOCUS·COPY·PASTE 는 여기서 다시 보내지 않는다.

   서버가 받는 이벤트 유형은 ExamEventLog.EventType 에 있는 것만이다:
   ENTER, EXIT, RESUME, TAB_BLUR, TAB_FOCUS, FULLSCREEN_EXIT, COPY, PASTE,
   MULTI_FACE, NO_FACE, NETWORK_DROP
   → 이 파일이 새로 보내는 것은 FULLSCREEN_EXIT 하나뿐이다.

   답안·타이머·자동 저장에 손대지 않는다. 경고를 띄우는 것 외에 아무 상태도 지우지 않는다.
   ============================================================= */
(function () {
  "use strict";

  /* 같은 이벤트를 짧은 시간에 반복 전송·토스트하지 않는다. */
  var DEBOUNCE_MS = 1500;
  var lastSent = Object.create(null);

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  function fullscreenElement() {
    return document.fullscreenElement || document.webkitFullscreenElement || null;
  }

  function fullscreenSupported() {
    return !!(document.documentElement.requestFullscreen
        || document.documentElement.webkitRequestFullscreen);
  }

  /**
   * @param {Object} opt
   *   attempt      window._serverAttempt (blockTabSwitch/blockCopyPaste/proctorEnabled 사용)
   *   eventsUrl    이벤트 POST URL (없으면 전송하지 않는다)
   *   post         (url, params) => Promise — 기존 페이지의 post 를 재사용한다
   *   mount        UI 를 붙일 요소
   *   isSubmitting () => boolean — 제출 중이면 경고를 띄우지 않는다
   */
  function init(opt) {
    opt = opt || {};
    var attempt = opt.attempt || {};
    var mount = opt.mount;
    if (!mount) return null;

    var submitting = typeof opt.isSubmitting === "function" ? opt.isSubmitting : function () { return false; };

    /* ---------- 상태 막대 ---------- */

    var bar = el("div", "integrity-bar");
    bar.appendChild(el("p", "integrity-bar__title", "응시 환경"));

    var fsFlag = el("span", "integrity-flag", "전체화면");
    fsFlag.dataset.state = "off";

    var tabFlag = el("span", "integrity-flag",
        attempt.blockTabSwitch ? "탭 이탈 기록 켜짐" : "탭 이탈 기록 꺼짐");
    tabFlag.dataset.state = attempt.blockTabSwitch ? "on" : "unknown";

    var copyFlag = el("span", "integrity-flag",
        attempt.blockCopyPaste ? "복사·붙여넣기 차단" : "복사·붙여넣기 허용");
    copyFlag.dataset.state = attempt.blockCopyPaste ? "on" : "unknown";

    var fsBtn = el("button", "btn btn-gray", "전체화면 시작");
    fsBtn.type = "button";

    bar.appendChild(fsFlag);
    bar.appendChild(tabFlag);
    bar.appendChild(copyFlag);
    bar.appendChild(fsBtn);

    /* ---------- 경고 배너 ---------- */

    var warn = el("div", "integrity-warn");
    warn.hidden = true;
    warn.setAttribute("role", "alert");
    var warnMsg = el("p", "integrity-warn__msg", "");
    var backBtn = el("button", "btn btn-secondary", "전체화면으로 돌아가기");
    backBtn.type = "button";
    var dismissBtn = el("button", "btn btn-gray", "닫기");
    dismissBtn.type = "button";
    warn.appendChild(warnMsg);
    warn.appendChild(backBtn);
    warn.appendChild(dismissBtn);

    /* ---------- 한계 고지 ---------- */

    var limits = el("p", "integrity-limits");
    limits.textContent =
      "감지 한계: 브라우저는 전체화면 해제, 탭 이탈, 복사·붙여넣기, 우클릭까지만 알 수 있습니다. " +
      "F12·개발자도구 단축키, 운영체제 화면 전환(예: Command+Tab), 화면 캡처, " +
      "다른 기기 사용은 이 화면에서 차단하거나 확인할 수 없습니다. " +
      "감독관 확인과 함께 사용해야 합니다.";

    /* 토스트 컨테이너 */
    var toastBox = el("div", "integrity-toast");
    toastBox.setAttribute("aria-live", "polite");

    mount.appendChild(bar);
    mount.appendChild(warn);
    mount.appendChild(limits);
    document.body.appendChild(toastBox);

    /* ---------- 토스트 ---------- */

    function toast(msg) {
      var t = el("div", null, msg);
      toastBox.appendChild(t);
      setTimeout(function () {
        if (t.parentNode) t.parentNode.removeChild(t);
      }, 3200);
    }

    /* ---------- 이벤트 전송 ---------- */

    function send(type, detail) {
      if (!opt.eventsUrl || typeof opt.post !== "function") return;
      var now = Date.now();
      if (lastSent[type] && now - lastSent[type] < DEBOUNCE_MS) return;
      lastSent[type] = now;
      /* 실패해도 응시를 막지 않는다 — 기록은 부가 기능이다. */
      try {
        opt.post(opt.eventsUrl, { eventType: type, detail: detail || "" });
      } catch (e) { /* 무시 */ }
    }

    /* ---------- 전체화면 ---------- */

    if (!fullscreenSupported()) {
      fsFlag.dataset.state = "unknown";
      fsFlag.textContent = "전체화면 미지원";
      fsBtn.disabled = true;
      fsBtn.title = "이 브라우저는 전체화면 API 를 지원하지 않습니다.";
    }

    function requestFs() {
      var root = document.documentElement;
      var fn = root.requestFullscreen || root.webkitRequestFullscreen;
      if (!fn) return;
      try {
        var r = fn.call(root);
        if (r && typeof r.catch === "function") {
          r.catch(function () {
            /* 사용자 제스처 없이 호출됐거나 권한이 없다. 조용히 실패하되 안내한다. */
            toast("전체화면 전환이 거부되었습니다. 버튼을 다시 눌러 주세요.");
          });
        }
      } catch (e) {
        toast("전체화면 전환에 실패했습니다.");
      }
    }

    fsBtn.addEventListener("click", requestFs);
    backBtn.addEventListener("click", function () {
      requestFs();
      warn.hidden = true;
    });
    dismissBtn.addEventListener("click", function () { warn.hidden = true; });

    var wasFullscreen = false;

    function onFsChange() {
      var on = !!fullscreenElement();
      fsFlag.dataset.state = on ? "on" : "off";
      fsFlag.textContent = on ? "전체화면" : "전체화면 아님";
      fsBtn.hidden = on;

      if (wasFullscreen && !on) {
        /* 전체화면에서 <b>빠져나왔을 때만</b> 경고한다. 처음부터 아닌 상태는 경고가 아니다. */
        if (!submitting()) {
          warnMsg.textContent =
            "전체화면이 해제되었습니다. 이 사실은 감독 기록에 남습니다. "
            + "답안과 남은 시간은 그대로 유지됩니다.";
          warn.hidden = false;
          toast("전체화면 해제가 기록되었습니다.");
          send("FULLSCREEN_EXIT", "전체화면 해제");
        }
      }
      wasFullscreen = on;
    }

    document.addEventListener("fullscreenchange", onFsChange);
    document.addEventListener("webkitfullscreenchange", onFsChange);
    onFsChange();

    /* ---------- 사용자에게 상태를 설명하는 리스너 ----------
       ★ 기존 스크립트가 이미 서버로 보내는 이벤트는 여기서 다시 보내지 않는다.
          토스트로 "무엇이 기록되는지" 만 알려 준다. */

    if (attempt.blockCopyPaste) {
      document.addEventListener("copy", function () {
        if (!submitting()) toast("복사가 차단되었고 기록되었습니다.");
      });
      document.addEventListener("paste", function () {
        if (!submitting()) toast("붙여넣기가 차단되었고 기록되었습니다.");
      });
      document.addEventListener("cut", function (e) {
        /* 기존 스크립트는 cut 을 막지 않는다 — 복사와 같은 취지이므로 여기서 막고 안내한다.
           서버 이벤트 유형에 CUT 이 없으므로 전송하지 않는다. */
        e.preventDefault();
        if (!submitting()) toast("잘라내기가 차단되었습니다. (별도 기록 유형은 없습니다)");
      });
      document.addEventListener("contextmenu", function () {
        if (!submitting()) toast("우클릭 메뉴가 차단되었습니다.");
      });
    }

    if (attempt.blockTabSwitch) {
      document.addEventListener("visibilitychange", function () {
        if (submitting()) return;
        toast(document.hidden
          ? "화면을 벗어난 사실이 기록되었습니다."
          : "화면으로 돌아온 사실이 기록되었습니다.");
      });
    }

    return {
      toast: toast,
      isFullscreen: function () { return !!fullscreenElement(); }
    };
  }

  window.ExamIntegrity = { init: init };
})();
