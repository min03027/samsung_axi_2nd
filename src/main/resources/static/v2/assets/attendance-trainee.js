/* ============================================================
   attendance-trainee.js — 출결·OTP·모바일 본인인증 데모(훈련생) (LXP-144/146/147)

   실제로 하는 일: OTP 입력·검증·재발급 타이머, 미수신 시 모바일 대체 인증(데모
   대화상자), 접속·인증 이후 체류시간 누적, 처리 이력 표시. 실제 OTP 발송·본인인증
   API 호출·HRD 전송은 하지 않는다 — 전부 이 화면의 메모리 상태만 바꾼다.

   상태 전이는 공용 순수 함수(AttendanceVerificationCommon)에서만 계산한다 — 이
   화면은 그 결과를 렌더링만 한다.
   ============================================================ */
(function () {
  "use strict";

  var C = window.AttendanceVerificationCommon;
  var D = window.AttendanceVerificationDemoData;

  var courseTitleEl = document.getElementById("courseTitle");
  var unitLineEl = document.getElementById("unitLine");
  var scheduleLineEl = document.getElementById("scheduleLine");
  var overallStageBadgeEl = document.getElementById("overallStageBadge");
  var overallStageMsgEl = document.getElementById("overallStageMsg");
  var connectedTimeEl = document.getElementById("connectedTime");
  var verifiedTimeEl = document.getElementById("verifiedTime");
  var sinceVerifiedTimeEl = document.getElementById("sinceVerifiedTime");
  var lastUpdatedAtEl = document.getElementById("lastUpdatedAt");
  var otpStateBadgeEl = document.getElementById("otpStateBadge");
  var otpMsgEl = document.getElementById("otpMsg");
  var otpDemoCodeHintEl = document.getElementById("otpDemoCodeHint");
  var otpInputEl = document.getElementById("otpInput");
  var otpIssueBtn = document.getElementById("otpIssueBtn");
  var otpVerifyBtn = document.getElementById("otpVerifyBtn");
  var otpReissueBtn = document.getElementById("otpReissueBtn");
  var otpUnreceivedBtn = document.getElementById("otpUnreceivedBtn");
  var otpRemainingTimeEl = document.getElementById("otpRemainingTime");
  var otpAttemptsLeftEl = document.getElementById("otpAttemptsLeft");
  var mobileCardEl = document.getElementById("mobileCard");
  var mobileStartBtn = document.getElementById("mobileStartBtn");
  var mobileResultMsgEl = document.getElementById("mobileResultMsg");
  var mobileDialog = document.getElementById("mobileDialog");
  var mobileDialogSuccessBtn = document.getElementById("mobileDialogSuccessBtn");
  var mobileDialogFailBtn = document.getElementById("mobileDialogFailBtn");
  var mobileDialogCancelBtn = document.getElementById("mobileDialogCancelBtn");
  var eventListEl = document.getElementById("eventList");

  courseTitleEl.textContent = D.session.course;
  unitLineEl.textContent = D.session.unit;
  scheduleLineEl.textContent = "일정 " + D.session.scheduledAt;
  otpDemoCodeHintEl.textContent = "데모 성공 번호: " + D.otpPolicy.demoSuccessCode + " (실제 인증값이 아닌 모의 값입니다)";
  otpDemoCodeHintEl.hidden = false;

  /* ---------- 시간 유틸 ---------- */
  function pad(n) { return (n < 10 ? "0" : "") + n; }
  function nowHHMMSS() {
    var d = new Date();
    return pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
  }

  /* ---------- 이벤트 로그 ---------- */
  var events = [];
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

  /* ---------- 상태 ---------- */
  var otp = {
    issued: false, verified: false, reportedUnreceived: false, lastMismatch: false,
    secondsRemaining: 0, attemptsLeft: D.otpPolicy.maxAttempts
  };
  var transfer = { code: "idle", retryCount: 0 };
  var stayClock = { connectedSeconds: 0, verifiedSeconds: 0 };
  var otpTimerHandle = null;
  var stayTimerHandle = null;

  function currentAuthState() {
    return C.deriveTraineeAuthState({
      issued: otp.issued, verified: otp.verified, reportedUnreceived: otp.reportedUnreceived,
      lastMismatch: otp.lastMismatch, secondsRemaining: otp.secondsRemaining, attemptsLeft: otp.attemptsLeft
    });
  }

  function setLastUpdated() { lastUpdatedAtEl.textContent = nowHHMMSS(); }

  /** "본인인증이 완료됐는가"를 한 곳에서만 판정한다 — 타이머(인정 체류시간)·전체 단계
      배지·버튼 활성화가 전부 이 함수 하나를 기준으로 움직인다. OTP 검증 완료
      경로(otp.verified)뿐 아니라 모바일 본인인증이 성공한 뒤의 모든 하류 상태
      (mobile_verified/fallback_queued/fallback_sent/fallback_failed)도 포함한다 —
      보완 전송 자체가 실패해도 "본인이 맞다"는 인증 사실은 이미 끝난 상태이기
      때문이다(재시도는 관리자 화면의 몫이다). */
  function isIdentityVerified() {
    return otp.verified || C.isMobileIdentityDone(transfer.code);
  }

  /* ---------- 렌더 ---------- */
  function renderOtp() {
    var state = currentAuthState();
    otpStateBadgeEl.textContent = state.label;
    otpStateBadgeEl.className = "state-badge" + (state.tone ? " " + state.tone : "");
    otpMsgEl.textContent = state.message;

    otpInputEl.disabled = state.code === "verified" || state.code === "locked" || state.code === "expired" || state.code === "unreceived" || !otp.issued;
    otpVerifyBtn.disabled = state.code !== "awaiting_input" && state.code !== "mismatch" || !C.validateOtpCode(otpInputEl.value);
    otpReissueBtn.hidden = !(state.code === "expired" || state.code === "locked" || state.code === "mismatch" || state.code === "unreceived");
    otpUnreceivedBtn.disabled = state.code === "verified" || state.code === "unreceived" || state.code === "not_issued";
    /* 모바일 본인인증이 이미 끝난 뒤에는 OTP 재발급으로 완료 상태를 되돌리지 않는다 —
       발급·재발급 버튼을 전부 잠근다. */
    var mobileDone = C.isMobileIdentityDone(transfer.code);
    otpIssueBtn.disabled = otp.issued || mobileDone;
    if (mobileDone) otpReissueBtn.hidden = true;

    otpRemainingTimeEl.textContent = otp.issued ? C.formatDuration(otp.secondsRemaining) : "-";
    otpAttemptsLeftEl.textContent = otp.issued ? (otp.attemptsLeft + "회") : "-";

    /* OTP 로 성공했으면(otp.verified) 모바일 대체 UI 는 더 볼 이유가 없다 — 인증성공
       후에도 카드가 남아 있던 것이 이번 보완의 핵심 결함이었다. 반대로 모바일 쪽으로
       진행 중이거나 이미 끝났으면(mobileFlowActive) 카드는 계속 보여야 한다 — 완료
       상태(성공/실패)를 표시하는 것 자체가 P1-H 요구사항이라 여기서 사라지면 안 된다. */
    var mobileFlowActive = otp.reportedUnreceived || mobileDone;
    mobileCardEl.hidden = otp.verified || !mobileFlowActive;
    renderMobileCard();
    renderOverallStage(state);
    setLastUpdated();
  }

  /** 모바일 카드 안의 시작 버튼·결과 문구 — 성공한 뒤에는 버튼을 다시 쓸 수 없게
      잠그고, fallback_queued(보완 전송 대기)와 fallback_sent(보완 전송 완료)를
      서로 다른 문구로 구분한다. */
  function renderMobileCard() {
    var mobileDone = C.isMobileIdentityDone(transfer.code);
    mobileStartBtn.disabled = mobileDone;
    if (transfer.code === "fallback_sent") {
      mobileResultMsgEl.textContent = "모바일 본인인증에 성공했습니다(데모). 보완 전송이 완료되었습니다.";
    } else if (transfer.code === "fallback_queued") {
      mobileResultMsgEl.textContent = "모바일 본인인증에 성공했습니다(데모). 보완 전송 대기 상태입니다.";
    } else if (transfer.code === "fallback_failed") {
      mobileResultMsgEl.textContent = "모바일 본인인증은 완료했지만 보완 전송에는 실패했습니다(데모). 관리자 화면에서 재시도합니다.";
    } else if (transfer.code === "mobile_verified") {
      mobileResultMsgEl.textContent = "모바일 본인인증에 성공했습니다(데모).";
    }
  }

  function renderOverallStage(authState) {
    var badge = overallStageBadgeEl, msg = overallStageMsgEl;
    if (transfer.code === "hrd_success") {
      badge.textContent = "OTP 인증 및 전송 완료(데모)"; badge.className = "state-badge ok"; msg.textContent = "";
      return;
    }
    if (transfer.code === "fallback_sent") {
      badge.textContent = "모바일 보완 전송 완료(데모)"; badge.className = "state-badge ok"; msg.textContent = "";
      return;
    }
    if (transfer.code === "fallback_queued") {
      badge.textContent = "모바일 인증 완료 — 보완 전송 대기(데모)"; badge.className = "state-badge warn"; msg.textContent = "";
      return;
    }
    if (transfer.code === "fallback_failed") {
      badge.textContent = "모바일 보완 전송 실패 — 재시도 필요(데모)"; badge.className = "state-badge risk"; msg.textContent = "";
      return;
    }
    if (authState.code === "verified") {
      badge.textContent = "OTP 인증 완료 — 전송 대기(데모)";
      badge.className = "state-badge warn";
      msg.textContent = "관리자 화면에서 HRD 전송을 진행하는 데모입니다.";
      return;
    }
    badge.textContent = "본인인증 진행 중";
    badge.className = "state-badge";
    msg.textContent = "";
  }

  function renderTimes() {
    connectedTimeEl.textContent = C.formatDuration(stayClock.connectedSeconds);
    verifiedTimeEl.textContent = C.formatDuration(stayClock.verifiedSeconds);
    /* "인증 이후 확인시간"도 같은 stayClock.verifiedSeconds 를 보여준다 — verified 는
       한 번 참이 되면 되돌아가지 않는 단방향 상태라 "인증 이후 누적 시간"과 "확인된
       체류시간"이 이 데모에서는 수학적으로 항상 같다. 라벨은 지시서가 요구한 대로
       둘 다 남겨둔다. */
    sinceVerifiedTimeEl.textContent = C.formatDuration(stayClock.verifiedSeconds);
  }

  /* ---------- OTP 타이머 ---------- */
  function stopOtpTimer() {
    if (otpTimerHandle) { window.clearInterval(otpTimerHandle); otpTimerHandle = null; }
  }
  function startOtpTimer() {
    stopOtpTimer();
    otpTimerHandle = window.setInterval(function () {
      if (otp.secondsRemaining > 0) otp.secondsRemaining -= 1;
      if (otp.secondsRemaining <= 0) stopOtpTimer();
      renderOtp();
    }, 1000);
  }

  /* ---------- 체류시간 타이머 — 페이지에 머무는 동안 항상 흐른다 ---------- */
  function startStayTimer() {
    stayTimerHandle = window.setInterval(function () {
      /* 인정 체류시간은 OTP 로 성공했을 때만이 아니라 "본인인증이 끝났다"는 판정
         (isIdentityVerified) 하나로만 늘어난다 — 모바일 대체 인증만으로 끝난
         경우에도 확인시간이 0에 머무르던 것이 이번 보완의 핵심 결함이었다. */
      stayClock = C.advanceStayClock(stayClock, isIdentityVerified());
      renderTimes();
    }, 1000);
  }
  function stopStayTimer() {
    if (stayTimerHandle) { window.clearInterval(stayTimerHandle); stayTimerHandle = null; }
  }

  /* ---------- OTP 이벤트 ---------- */
  function issueOtp(isReissue) {
    /* 모바일 본인인증이 이미 끝난 뒤에는 재발급으로 그 완료 상태를 되돌리지 않는다. */
    if (isReissue && C.isMobileIdentityDone(transfer.code)) return;
    /* OTP 를 못 받았다고 보고한(otp_unreceived) 뒤 다시 발급하면, 그 낡은 미수신
       기록을 새 OTP 흐름에 맞게 명시적으로 되돌린다 — 안 그러면 나중에 올바른
       코드를 입력해도 전송 상태 기계가 여전히 otp_unreceived 에 머물러 있어
       OTP_VERIFIED 전이 자체가 거부된다(그 실패를 무시하면 UI 만 성공처럼 보인다). */
    if (transfer.code === "otp_unreceived") transfer = { code: "idle", retryCount: transfer.retryCount };
    otp.issued = true;
    otp.verified = false;
    otp.reportedUnreceived = false;
    otp.lastMismatch = false;
    otp.secondsRemaining = D.otpPolicy.validSeconds;
    otp.attemptsLeft = D.otpPolicy.maxAttempts;
    otpInputEl.value = "";
    startOtpTimer();   /* 재발급이어도 기존 타이머를 정리하고 단일 타이머만 유지한다(startOtpTimer 내부에서 stopOtpTimer 호출) */
    renderOtp();
    addEvent(isReissue ? "OTP 를 재발급했습니다(데모)." : "OTP 를 발급했습니다(데모).");
  }

  otpIssueBtn.addEventListener("click", function () { issueOtp(false); });
  otpReissueBtn.addEventListener("click", function () { issueOtp(true); });

  otpInputEl.addEventListener("input", function () {
    var normalized = C.normalizeOtpCode(otpInputEl.value);
    if (otpInputEl.value !== normalized) otpInputEl.value = normalized;
    renderOtp();
  });

  otpVerifyBtn.addEventListener("click", function () {
    var entered = C.normalizeOtpCode(otpInputEl.value);
    if (!C.validateOtpCode(entered)) return;
    if (entered === D.otpPolicy.demoSuccessCode) {
      /* otp.verified 와 전송 상태가 원자적으로 일치할 때만 성공 UI 를 보여준다 —
         전이가 거부되면(t.ok===false) 절대 조용히 무시하지 않고, otp.verified 도
         true 로 만들지 않는다(전이 실패를 숨기면 화면만 성공으로 둔갑한다). */
      var t = C.transitionTransferState(transfer, "OTP_VERIFIED");
      if (!t.ok) {
        addEvent("OTP 코드는 일치했지만 상태 전이가 거부되어 인증을 완료하지 못했습니다: " + t.reason);
        renderOtp();
        return;
      }
      transfer = t;
      otp.verified = true;
      otp.lastMismatch = false;
      stopOtpTimer();
      addEvent("OTP 인증에 성공했습니다.");
    } else {
      otp.attemptsLeft = Math.max(0, otp.attemptsLeft - 1);
      otp.lastMismatch = true;
      otpInputEl.value = "";
      addEvent("OTP 가 일치하지 않았습니다(" + otp.attemptsLeft + "회 남음).");
    }
    renderOtp();
  });

  otpUnreceivedBtn.addEventListener("click", function () {
    otp.reportedUnreceived = true;
    stopOtpTimer();
    var t = C.transitionTransferState(transfer, "OTP_UNRECEIVED");
    if (t.ok) transfer = t;
    renderOtp();
    addEvent("OTP 를 받지 못한 것으로 표시했습니다.");
  });

  /* ---------- 모바일 본인인증 대체(데모 대화상자) ---------- */
  mobileStartBtn.addEventListener("click", function () {
    if (mobileStartBtn.disabled) return;   /* 이미 완료된 뒤 강제로 다시 불려도 막는다 */
    mobileResultMsgEl.textContent = "";
    mobileDialog.showModal();
  });
  mobileDialogCancelBtn.addEventListener("click", function () { mobileDialog.close(); });
  mobileDialog.addEventListener("click", function (e) { if (e.target === mobileDialog) mobileDialog.close(); });

  mobileDialogFailBtn.addEventListener("click", function () {
    mobileDialog.close();
    mobileResultMsgEl.textContent = "모바일 본인인증에 실패했습니다(데모). 다시 시도해 주세요.";
    addEvent("모바일 본인인증을 시도했지만 실패했습니다(데모).");
  });

  mobileDialogSuccessBtn.addEventListener("click", function () {
    mobileDialog.close();
    /* 이미 한 번 성공한 뒤 대화상자가 강제로 다시 실행돼도(예: 테스트가 disabled
       버튼을 무시하고 핸들러를 직접 호출) 전이가 거부되면 절대 성공 메시지·이벤트를
       추가하지 않는다 — 반복 클릭이 중복 성공 이벤트를 만들던 것이 이번 결함이었다. */
    var t1 = C.transitionTransferState(transfer, "MOBILE_VERIFIED");
    if (!t1.ok) { renderOtp(); return; }
    transfer = t1;
    var t2 = C.transitionTransferState(transfer, "QUEUE_FALLBACK");
    if (!t2.ok) { renderOtp(); return; }
    transfer = t2;
    renderOtp();   /* mobileStartBtn.disabled 와 완료 문구를 renderMobileCard() 가 여기서 갱신한다 */
    addEvent("모바일 본인인증에 성공해 보완 전송 대기로 전환했습니다(데모).");
  });

  /* ---------- 정리 ---------- */
  function cleanupAll() { stopOtpTimer(); stopStayTimer(); }
  window.addEventListener("pagehide", cleanupAll);
  window.addEventListener("beforeunload", cleanupAll);

  /* ---------- 초기화 ---------- */
  renderOtp();
  renderTimes();
  startStayTimer();

  /* 테스트 전용 훅 — 훈련생 화면과 관리자 화면은 메모리를 공유하지 않으므로, 실제
     클릭만으로는 fallback_sent(관리자가 보완 전송을 실행한 결과)에 훈련생 화면에서
     도달할 방법이 없다(운영 연동 시에는 이 결과를 훈련생 화면에 반영할 API/폴링/SSE
     경로가 필요하다). 화면과 다른 문구를 별도로 재구현하는 대신, 실제 렌더 경로
     (renderOtp → renderMobileCard)를 그대로 실행해 fallback_queued/fallback_sent
     문구 차이만 테스트가 검증할 수 있게 transfer.code 를 바꾸는 통로 하나만 연다. */
  if (typeof window !== "undefined") {
    window.__attendanceTraineeTest = {
      setTransferCodeForTest: function (code) {
        transfer = { code: code, retryCount: transfer.retryCount, otpVerified: transfer.otpVerified };
        renderOtp();
      }
    };
  }
})();
