/* ============================================================
   learning-presence-demo-data.js — 학습 참여 확인 데모 전용 모의 데이터 (LXP-140/141/142)

   learning-presence-check.html 과 presence-monitor.html 이 공유하는 유일한
   데이터 소스다.

   ⚠ 전부 가상 값이다. 실제 훈련생 이름·연락처·이메일·주민등록번호·얼굴 이미지를
     넣지 않는다. 이름은 "훈련생 01" 형식의 가상 표기만 쓴다.
   ============================================================ */
(function () {
  "use strict";

  var session = {
    title: "프론트엔드 실전 프로젝트 4기 — 학습 참여 모니터링(데모)",
    course: "프론트엔드 실전 프로젝트 4기"
  };

  /** 훈련생 화면의 "데모 시나리오" — 실제 얼굴 검출 대신 이 값을 그대로
      derivePresenceState() 의 입력(faceCount/cameraConnected)으로 쓴다. */
  var traineeScenarios = [
    { key: "normal", label: "정상 (얼굴 1명 인식)", faceCount: 1, cameraConnected: true,
      desc: "얼굴 1명이 정상적으로 인식된 상태를 흉내냅니다(모의 값 — 실제 얼굴 분석 결과가 아닙니다)." },
    { key: "no-face", label: "얼굴 없음", faceCount: 0, cameraConnected: true,
      desc: "카메라에는 연결되어 있지만 얼굴이 감지되지 않는 상태를 흉내냅니다(모의 값 — 실제 얼굴 분석 결과가 아닙니다)." },
    { key: "multiple-faces", label: "복수 얼굴 감지", faceCount: 2, cameraConnected: true,
      desc: "카메라에 얼굴이 2명 이상 감지되는 상태를 흉내냅니다(모의 값 — 실제 얼굴 분석 결과가 아닙니다)." },
    { key: "camera-lost", label: "카메라 연결 끊김", faceCount: 0, cameraConnected: false,
      desc: "카메라 연결 자체가 끊어진 상태를 흉내냅니다(모의 값 — 실제 얼굴 분석 결과가 아닙니다)." }
  ];

  /** 관리자 모니터링 화면의 상태 시나리오 — 원본 학습자 데이터는 바꾸지 않고
      표시할 때만 규칙으로 이탈시간·횟수를 더해 재분류시킨다. */
  var monitorScenarios = [
    { key: "normal", label: "정상 운영", desc: "모의 데이터 원본 그대로 표시합니다." },
    { key: "many-away", label: "다수 이탈", desc: "여러 훈련생의 누적 이탈시간이 늘어나 주의·집중관리 인원이 증가합니다." },
    { key: "camera-fault", label: "카메라 장애", desc: "이미 이탈 이력이 있던 훈련생 일부의 카메라 연결 장애로 이탈시간이 급증합니다." }
  ];

  function learner(id, name, seat, connectedSeconds, verifiedSeconds, awaySeconds, awayCount, lastAwayAt) {
    return {
      id: id, name: name, seat: seat,
      connectedSeconds: connectedSeconds, verifiedSeconds: verifiedSeconds,
      awaySeconds: awaySeconds, awayCount: awayCount, lastAwayAt: lastAwayAt
    };
  }

  /* 정책 기준(경고 180초, 집중관리 300초)을 기준으로 정상 4명·주의 2명·집중관리 2명이
     모두 나오도록 구성했다. */
  var learners = [
    learner("l01", "훈련생 01", "A-01", 3600, 3550, 0,   0, ""),
    learner("l02", "훈련생 02", "A-02", 3600, 3520, 60,  1, "09:12:05"),
    learner("l03", "훈련생 03", "A-03", 3600, 3400, 200, 2, "09:20:41"),
    learner("l04", "훈련생 04", "A-04", 3600, 3200, 350, 4, "09:35:10"),
    learner("l05", "훈련생 05", "A-05", 3600, 3480, 90,  3, "09:18:22"),
    learner("l06", "훈련생 06", "A-06", 3600, 3390, 190, 1, "09:29:55"),
    learner("l07", "훈련생 07", "A-07", 3600, 3600, 0,   0, ""),
    learner("l08", "훈련생 08", "A-08", 3600, 3150, 310, 5, "09:40:03")
  ];

  /* 시간순 자리 이탈 이벤트 — 관리자 화면 타임라인에 그대로 표시한다. */
  var awayEvents = [
    { id: "ae1", at: "09:12:05", learnerId: "l02", seconds: 60,  desc: "[데모] 자리 이탈이 감지되었습니다." },
    { id: "ae2", at: "09:18:22", learnerId: "l05", seconds: 90,  desc: "[데모] 자리 이탈이 감지되었습니다." },
    { id: "ae3", at: "09:20:41", learnerId: "l03", seconds: 120, desc: "[데모] 자리 이탈이 감지되었습니다." },
    { id: "ae4", at: "09:20:41", learnerId: "l03", seconds: 80,  desc: "[데모] 두 번째 자리 이탈이 감지되었습니다." },
    { id: "ae5", at: "09:29:55", learnerId: "l06", seconds: 190, desc: "[데모] 자리 이탈이 감지되었습니다." },
    { id: "ae6", at: "09:35:10", learnerId: "l04", seconds: 350, desc: "[데모] 장시간 자리 이탈이 감지되었습니다." },
    { id: "ae7", at: "09:40:03", learnerId: "l08", seconds: 310, desc: "[데모] 장시간 자리 이탈이 감지되었습니다." }
  ];

  var api = {
    session: session,
    policy: {
      sampleIntervalSeconds: 30,
      graceSeconds: 120,
      warningSeconds: 180,
      focusSeconds: 300
    },
    traineeScenarios: traineeScenarios,
    monitorScenarios: monitorScenarios,
    learners: learners,
    awayEvents: awayEvents
  };

  if (typeof window !== "undefined") window.LearningPresenceDemoData = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})();
