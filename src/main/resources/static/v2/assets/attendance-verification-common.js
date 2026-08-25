/* ============================================================
   attendance-verification-common.js — 출결·OTP·본인인증·HRD 전송 데모 공통 유틸리티
   (LXP-144 / LXP-146 / LXP-147)

   브라우저에서는 window.AttendanceVerificationCommon, Node 에서는 module.exports 로
   같은 API 를 노출한다 — 계약 테스트가 문자열 검사가 아니라 실제로 require 해
   판정·전이 로직을 실행한다.

   여기서 하는 일은 여섯이다:
     ① OTP 입력값 정규화·6자리 검증 (normalizeOtpCode / validateOtpCode)
     ② 발급·입력대기·성공·불일치·만료·시도초과·미수신 상태 파생 (deriveTraineeAuthState)
     ③ HRD 전송·모바일 보완 전송 상태 전이 (transitionTransferState)
     ④ 접속·인증 이후 확인 체류시간 누적 (advanceStayClock)
     ⑤ 초 단위 시간 포맷 (formatDuration)
   실제 OTP 발송·본인인증 API 호출·HRD 전문 송수신 코드는 이 파일에 없다 —
   전부 입력값과 데모 이벤트만으로 계산되는 순수 함수다.
   ============================================================ */
(function () {
  "use strict";

  /** 음수·NaN·문자열 입력을 0 이상의 정수로 정규화한다. */
  function toNonNegInt(v) {
    var n = Number(v);
    if (!isFinite(n)) return 0;
    n = Math.floor(n);
    return n < 0 ? 0 : n;
  }

  /** 숫자가 아닌 문자를 전부 지우고 6자리를 넘으면 잘라낸다. */
  function normalizeOtpCode(value) {
    var digits = String(value == null ? "" : value).replace(/\D/g, "");
    return digits.slice(0, 6);
  }

  /** 정규화한 값이 정확히 6자리일 때만 제출 가능하다고 본다. */
  function validateOtpCode(value) {
    return normalizeOtpCode(value).length === 6;
  }

  /** 훈련생의 현재 OTP 인증 상태를 파생한다 — 우선순위는 다음과 같다.
        1) 인증 성공은 그 뒤로 시간·시도 횟수가 어떻게 바뀌어도 유지되는 종료 상태다.
        2) 사용자가 "받지 못했어요"로 직접 보고했으면(reportedUnreceived) 미수신으로 본다.
        3) 발급 자체를 안 했으면 미발급이다.
        4) 시도 횟수를 다 썼으면(0회 남음) 시간이 남아 있어도 잠김(시도초과)이다.
        5) 유효시간이 다 됐으면 만료다.
        6) 마지막 제출이 틀렸지만 아직 시도 횟수가 남아 있으면 불일치다.
        7) 그 외에는 입력을 기다리는 중이다. */
  function deriveTraineeAuthState(input) {
    input = input || {};
    var issued = !!input.issued;
    var verified = !!input.verified;
    var reportedUnreceived = !!input.reportedUnreceived;
    var lastMismatch = !!input.lastMismatch;
    var secondsRemaining = toNonNegInt(input.secondsRemaining);
    var attemptsLeft = toNonNegInt(input.attemptsLeft);

    if (verified) {
      return { code: "verified", label: "인증성공", tone: "ok", message: "OTP 인증에 성공했습니다." };
    }
    if (reportedUnreceived) {
      return {
        code: "unreceived", label: "미수신", tone: "warn",
        message: "OTP 를 받지 못한 것으로 표시했습니다. 모바일 본인인증으로 진행할 수 있습니다."
      };
    }
    if (!issued) {
      return { code: "not_issued", label: "미발급", tone: "", message: "OTP 발급(데모) 버튼을 눌러 시작하세요." };
    }
    if (attemptsLeft <= 0) {
      return {
        code: "locked", label: "시도 초과", tone: "risk",
        message: "최대 시도 횟수에 도달했습니다. 재발급 후 다시 시도하세요."
      };
    }
    if (secondsRemaining <= 0) {
      return { code: "expired", label: "만료", tone: "risk", message: "유효시간이 만료되었습니다. 재발급 후 다시 시도하세요." };
    }
    if (lastMismatch) {
      return {
        code: "mismatch", label: "불일치", tone: "risk",
        message: "입력한 번호가 일치하지 않습니다. 다시 시도하세요(" + attemptsLeft + "회 남음)."
      };
    }
    return { code: "awaiting_input", label: "입력대기", tone: "warn", message: "발급된 6자리 번호를 입력하세요." };
  }

  /* ---------- HRD 전송 / 모바일 보완 전송 상태 전이 ----------
     하나의 유한 상태 기계로 일반 전송 경로와 모바일 보완 경로를 모두 다룬다.
       idle          → OTP 인증 성공(OTP_VERIFIED) 또는 미수신 보고(OTP_UNRECEIVED) 전까지의 시작 상태.
                        이 상태에서는 QUEUE 를 직접 받아들이지 않는다 — "인증성공 전에는 HRD
                        전송 대기로 이동할 수 없다"는 요구가 상태 기계 구조 자체로 보장된다.
       otp_verified  → QUEUE          → hrd_queued
       hrd_queued    → SEND           → hrd_sending
       hrd_sending   → SUCCEED/FAIL   → hrd_success / hrd_failed
       hrd_failed    → RETRY          → retrying (retryCount 증가)
       retrying      → SUCCEED/FAIL   → hrd_success / hrd_failed (재시도 반복 가능)
       otp_unreceived→ MOBILE_VERIFIED→ mobile_verified
       hrd_success   → MARK_HRD_UNRECEIVED → hrd_unreceived
                        (OTP 는 이미 성공했지만 공단이 나중에 못 받았다고 알려온 경우다 —
                        "OTP 를 못 받았다"는 otp_unreceived 와는 원인이 다르므로 별도 상태로
                        분리한다. OTP 인증 성공 사실 자체는 이 전이로 사라지지 않는다.)
       hrd_unreceived → MOBILE_VERIFIED → mobile_verified
                        (공단 미수신도 결국 모바일 본인인증으로 보완하는 같은 경로를 탄다)
       mobile_verified→ QUEUE_FALLBACK→ fallback_queued
       fallback_queued→ SEND_FALLBACK → fallback_sent
                       FAIL_FALLBACK  → fallback_failed
       fallback_failed→ RETRY_FALLBACK→ fallback_queued (retryCount 증가)
     표에 없는 (state, event) 조합은 전부 거부한다 — 상태는 그대로, ok:false, reason 반환. */
  var TRANSFER_TRANSITIONS = {
    idle: { OTP_VERIFIED: "otp_verified", OTP_UNRECEIVED: "otp_unreceived" },
    otp_verified: { QUEUE: "hrd_queued" },
    hrd_queued: { SEND: "hrd_sending" },
    hrd_sending: { SUCCEED: "hrd_success", FAIL: "hrd_failed" },
    hrd_failed: { RETRY: "retrying" },
    retrying: { SUCCEED: "hrd_success", FAIL: "hrd_failed" },
    hrd_success: { MARK_HRD_UNRECEIVED: "hrd_unreceived" },
    hrd_unreceived: { MOBILE_VERIFIED: "mobile_verified" },
    otp_unreceived: { MOBILE_VERIFIED: "mobile_verified" },
    mobile_verified: { QUEUE_FALLBACK: "fallback_queued" },
    fallback_queued: { SEND_FALLBACK: "fallback_sent", FAIL_FALLBACK: "fallback_failed" },
    fallback_failed: { RETRY_FALLBACK: "fallback_queued" },
    fallback_sent: {}
  };

  var RETRY_EVENTS = { RETRY: true, RETRY_FALLBACK: true };

  /* mobile_verified 이후의 코드(mobile_verified/fallback_queued/fallback_sent/
     fallback_failed)는 코드 하나만 보고는 "본인인증이 끝났다"는 사실만 알 수 있을 뿐,
     그 이전에 OTP 인증까지 성공했었는지(hrd_unreceived 경유)는 코드만으로 구분할 수
     없다 — 같은 mobile_verified 라도 otp_unreceived 경유(OTP 는 못 받음)와 hrd_unreceived
     경유(OTP 는 이미 성공, 공단만 못 받음)가 있기 때문이다. 그래서 otpVerified 는
     mobileVerified 와 달리 코드에서 역산할 수 없고, 상태 객체에 별도로 실어 모든
     전이를 거치며 그대로 보존해야 한다. */
  var MOBILE_IDENTITY_DONE_CODES = { mobile_verified: true, fallback_queued: true, fallback_sent: true, fallback_failed: true };
  function isMobileIdentityDone(code) { return !!MOBILE_IDENTITY_DONE_CODES[code]; }

  function transitionTransferState(state, event) {
    state = state || {};
    var code = state.code || "idle";
    var retryCount = toNonNegInt(state.retryCount);
    var otpVerified = !!state.otpVerified;
    var table = TRANSFER_TRANSITIONS[code] || {};
    var nextCode = table[event];
    if (!nextCode) {
      return {
        code: code,
        retryCount: retryCount,
        otpVerified: otpVerified,
        ok: false,
        reason: "\"" + code + "\" 상태에서는 \"" + event + "\" 이벤트를 처리할 수 없습니다."
      };
    }
    if (event === "OTP_VERIFIED") otpVerified = true;
    return {
      code: nextCode,
      retryCount: RETRY_EVENTS[event] ? retryCount + 1 : retryCount,
      otpVerified: otpVerified,
      ok: true,
      reason: null
    };
  }

  /** 접속시간은 항상 흐르고, 인증 이후 확인시간(verified===true 인 동안)만 같이 늘어난다.
      확인시간이 접속시간을 넘지 않게 자른다(부동소수·순서 오류 방어). */
  function advanceStayClock(clock, verified) {
    clock = clock || {};
    var connectedSeconds = toNonNegInt(clock.connectedSeconds) + 1;
    var verifiedSeconds = toNonNegInt(clock.verifiedSeconds) + (verified ? 1 : 0);
    if (verifiedSeconds > connectedSeconds) verifiedSeconds = connectedSeconds;
    return { connectedSeconds: connectedSeconds, verifiedSeconds: verifiedSeconds };
  }

  function pad2(n) { return (n < 10 ? "0" : "") + n; }

  /** 초를 HH:MM:SS 로 통일해 표기한다. */
  function formatDuration(seconds) {
    var total = toNonNegInt(seconds);
    var h = Math.floor(total / 3600);
    var m = Math.floor((total % 3600) / 60);
    var s = total % 60;
    return pad2(h) + ":" + pad2(m) + ":" + pad2(s);
  }

  var api = {
    normalizeOtpCode: normalizeOtpCode,
    validateOtpCode: validateOtpCode,
    deriveTraineeAuthState: deriveTraineeAuthState,
    transitionTransferState: transitionTransferState,
    isMobileIdentityDone: isMobileIdentityDone,
    advanceStayClock: advanceStayClock,
    formatDuration: formatDuration
  };

  if (typeof window !== "undefined") window.AttendanceVerificationCommon = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})();
