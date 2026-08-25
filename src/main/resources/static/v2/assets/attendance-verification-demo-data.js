/* ============================================================
   attendance-verification-demo-data.js — 출결·OTP·본인인증·HRD 전송 데모 전용
   모의 데이터 (LXP-144 / LXP-146 / LXP-147)

   attendance.html(훈련생) 과 attendance.html(관리자) 이 공유하는 유일한 데이터
   소스다.

   ⚠ 전부 가상 값이다. 실제 휴대전화 번호·이메일·생년월일·주민등록번호·얼굴 이미지를
     넣지 않는다. 이름은 "훈련생 01" 형식의 가상 표기만 쓴다. 모바일 번호 자체를
     표시하지 않는다(마스킹한 값도 넣지 않는다).
   ============================================================ */
(function () {
  "use strict";

  var session = {
    course: "프론트엔드 실전 프로젝트 4기",
    unit: "3차시 — 상태관리 실습",
    scheduledAt: "2026-08-26 19:00 ~ 21:00"
  };

  var otpPolicy = {
    validSeconds: 180,
    maxAttempts: 3,
    /* 실제 서비스라면 서버가 무작위로 발급하지만, 이 데모는 통신이 전혀 없으므로
       발급 즉시 "정답"을 화면에 그대로 보여준다 — 사용자가 이 값이 실제 인증값이
       아니라 모의 값임을 알 수 있게 문구를 반드시 함께 표시한다. */
    demoSuccessCode: "123456"
  };

  /* 훈련생 화면은 시나리오를 강제로 고르지 않고 실제 입력·대기·버튼 클릭으로 모든
     상태에 도달할 수 있다(카메라처럼 흉내낼 대상이 없어 실제 흐름 자체가 데모다).
     이 목록은 "이렇게 체험해 보세요" 안내 문구로만 쓰인다. */
  var traineeTryIt = [
    { key: "success", label: "정상 인증", desc: "발급 후 화면에 표시된 데모 성공 번호를 그대로 입력하면 인증에 성공합니다." },
    { key: "mismatch", label: "OTP 불일치", desc: "데모 성공 번호가 아닌 임의의 6자리를 입력하면 불일치로 표시됩니다." },
    { key: "expired", label: "OTP 만료", desc: "유효시간이 다 지나도록 기다리면 만료로 바뀝니다." },
    { key: "unreceived", label: "OTP 미수신", desc: "\"OTP 를 받지 못했어요\" 버튼을 누르면 모바일 본인인증 카드가 열립니다." },
    { key: "mobile-success", label: "모바일 대체 인증 성공", desc: "모바일 본인인증 대화상자에서 \"성공(데모)\"을 선택하면 보완 전송 대기로 넘어갑니다." },
    { key: "mobile-fail", label: "모바일 대체 인증 실패", desc: "모바일 본인인증 대화상자에서 \"실패(데모)\"를 선택하면 다시 시도할 수 있는 안내가 표시됩니다." }
  ];

  /* 관리자 화면의 상태 시나리오 — 원본 훈련생 데이터는 바꾸지 않고 표시할 때만
     일부 인원의 전송 상태·재시도 횟수를 규칙으로 바꿔 재분류시킨다. */
  var adminScenarios = [
    { key: "normal", label: "정상 전송", desc: "모의 데이터 원본 그대로 표시합니다." },
    { key: "partial-failure", label: "일부 전송 실패", desc: "전송 대기·전송 중이던 일부 인원의 HRD 전송이 실패로 바뀝니다(아직 전송을 시작하지 않은 OTP 인증완료 인원은 그대로 유지됩니다)." },
    { key: "hrd-unreceived", label: "공단 미수신", desc: "전송 성공이던 일부 인원을 공단이 아직 받지 못한 것으로 되돌립니다(OTP 인증 성공 사실은 유지되며, 모바일 본인인증으로 보완 전송이 필요합니다)." },
    { key: "fallback-failure", label: "모바일 보완 전송 실패", desc: "이 시나리오를 선택한 뒤 모바일 보완 전송을 실행하면 실패 결과가 발생합니다(선택 자체만으로는 아무도 바뀌지 않습니다)." }
  ];

  /** transferCode: idle | otp_verified | hrd_queued | hrd_sending | hrd_success |
      hrd_failed | retrying | otp_unreceived | hrd_unreceived | mobile_verified |
      fallback_queued | fallback_failed | fallback_sent
      (= transitionTransferState() 의 code 값과 동일)

      otpVerified: 이 학습자가 "OTP 인증 자체"를 성공한 적이 있는지를 나타낸다.
      mobile_verified 이후 코드들은 otp_unreceived 경유(otpVerified=false)와
      hrd_unreceived 경유(otpVerified=true, 공단만 못 받음) 둘 다로 도달할 수 있어
      코드만으로는 구분되지 않는다 — 그래서 baseline 데이터에 명시적으로 싣는다.
      otp_verified/hrd_* 계열 코드는 구조적으로 항상 OTP 성공을 거쳐야만 도달하므로
      이 값이 항상 true 다. */
  function learner(id, name, seat, connectedSeconds, verifiedSeconds, transferCode, retryCount, lastProcessedAt, otpVerified) {
    return {
      id: id, name: name, seat: seat,
      connectedSeconds: connectedSeconds, verifiedSeconds: verifiedSeconds,
      transferCode: transferCode, retryCount: retryCount, lastProcessedAt: lastProcessedAt,
      otpVerified: otpVerified
    };
  }

  var learners = [
    learner("l01", "훈련생 01", "A-01", 3600, 3550, "otp_verified", 0, "19:02:10", true),
    learner("l02", "훈련생 02", "A-02", 3600, 3520, "hrd_queued",   0, "19:03:05", true),
    learner("l03", "훈련생 03", "A-03", 3600, 3500, "hrd_success",  0, "19:04:40", true),
    learner("l04", "훈련생 04", "A-04", 3600, 3480, "hrd_success",  0, "19:05:12", true),
    learner("l05", "훈련생 05", "A-05", 3600, 3400, "hrd_failed",   1, "19:07:33", true),
    learner("l06", "훈련생 06", "A-06", 3600, 3350, "retrying",     2, "19:10:02", true),
    learner("l07", "훈련생 07", "A-07", 3600, 3100, "otp_unreceived", 0, "19:12:45", false),
    learner("l08", "훈련생 08", "A-08", 3600, 3200, "mobile_verified", 0, "19:14:18", false)
  ];

  /* 전송·재시도·보완 전송 이벤트 — 관리자 화면 타임라인에 그대로 표시한다. */
  var transferEvents = [
    { id: "te1", at: "19:04:40", learnerId: "l03", desc: "[데모] HRD 전송 성공." },
    { id: "te2", at: "19:05:12", learnerId: "l04", desc: "[데모] HRD 전송 성공." },
    { id: "te3", at: "19:06:50", learnerId: "l05", desc: "[데모] HRD 전송 실패 — 공단 응답 지연(모의)." },
    { id: "te4", at: "19:07:33", learnerId: "l05", desc: "[데모] 재시도 대기로 전환." },
    { id: "te5", at: "19:09:20", learnerId: "l06", desc: "[데모] 1차 재시도 실패(모의)." },
    { id: "te6", at: "19:10:02", learnerId: "l06", desc: "[데모] 2차 재시도 진행 중." },
    { id: "te7", at: "19:12:45", learnerId: "l07", desc: "[데모] OTP 미수신으로 보고됨." },
    { id: "te8", at: "19:14:18", learnerId: "l08", desc: "[데모] 모바일 본인인증 성공 — 보완 전송 대기." }
  ];

  var api = {
    session: session,
    otpPolicy: otpPolicy,
    traineeTryIt: traineeTryIt,
    adminScenarios: adminScenarios,
    learners: learners,
    transferEvents: transferEvents
  };

  if (typeof window !== "undefined") window.AttendanceVerificationDemoData = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})();
