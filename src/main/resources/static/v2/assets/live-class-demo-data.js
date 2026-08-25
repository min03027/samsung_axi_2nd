/* ============================================================
   live-class-demo-data.js — 화상강의 데모 전용 모의 데이터 (LXP-125 / LXP-127)

   live-classroom.html 과 live-class-monitor.html 이 공유하는 유일한
   데이터 소스다. live-class-precheck.html 은 참가자 데이터가 필요
   없어 이 파일을 로드하지 않는다.

   ⚠ 전부 가상 값이다. 실제 훈련생 이름·연락처·이메일·주민등록번호를
     넣지 않는다. 이름은 가상 한국어 이름만 쓴다.
   ============================================================ */
(function () {
  "use strict";

  var session = {
    id: "live-2026-fe",
    title: "프론트엔드 실전 프로젝트 라이브 세션",
    course: "프론트엔드 실전 프로젝트 4기",
    instructor: "박서준 강사",
    startedAt: "2026-08-24 19:00"
  };

  /** state: ok | warn | offline. devices 값: live | weak | off. */
  function participant(id, name, seat, role, state, camera, mic, screen) {
    return {
      id: id, name: name, seat: seat, role: role,
      state: state,
      devices: { camera: camera, mic: mic, screen: screen }
    };
  }

  var participants = [
    participant("p01", "김도윤", "A-01", "훈련생", "ok",      "live", "live", "off"),
    participant("p02", "이서준", "A-02", "훈련생", "ok",      "live", "live", "off"),
    participant("p03", "박하은", "A-03", "훈련생", "warn",    "weak", "live", "off"),
    participant("p04", "최민서", "A-04", "훈련생", "ok",      "live", "live", "live"),
    participant("p05", "정예린", "A-05", "훈련생", "offline", "off",  "off",  "off"),
    participant("p06", "강시우", "A-06", "훈련생", "ok",      "live", "live", "off"),
    participant("p07", "윤지호", "A-07", "훈련생", "warn",    "live", "weak", "off"),
    participant("p08", "임채원", "A-08", "훈련생", "ok",      "live", "live", "off")
  ];

  /* 강의실 화면의 로컬 채팅 초기 메시지 — 새로고침하면 이 3건으로 되돌아간다. */
  var chatSeed = [
    { id: "m1", author: "박서준 강사", text: "모두 접속 확인했습니다. 화면 잘 보이시나요?", at: "19:00" },
    { id: "m2", author: "김도윤", text: "네 잘 보입니다!", at: "19:00" },
    { id: "m3", author: "이서준", text: "소리도 잘 들려요.", at: "19:01" }
  ];

  /* 관리자 모니터링 화면의 최근 데모 이벤트 — 참가자별로 필터링해 보여준다. */
  var events = [
    { id: "e1", at: "19:03", participantId: "p03", type: "카메라 화질 저하", severity: "warn", desc: "[데모] 네트워크 대역폭 부족으로 추정됩니다." },
    { id: "e2", at: "19:05", participantId: "p05", type: "연결 끊김",       severity: "risk", desc: "[데모] 참가자 연결이 끊어졌습니다." },
    { id: "e3", at: "19:08", participantId: "p07", type: "마이크 음질 저하", severity: "warn", desc: "[데모] 마이크 입력이 불안정합니다." },
    { id: "e4", at: "19:11", participantId: "p05", type: "재연결 시도",     severity: "warn", desc: "[데모] 자동 재연결을 시도하는 중입니다." }
  ];

  /* 모니터링 화면의 상태 시나리오 — 실제 상태 변화는 live-class-monitor.js 가 규칙으로 계산한다. */
  var scenarios = [
    { key: "normal", label: "정상", desc: "기본 데모 상태입니다. 정상 5명, 주의 2명, 끊김 1명을 표시합니다." },
    { key: "device-warning", label: "장치 경고", desc: "정상 참가자의 카메라·마이크 화질/음질이 저하됩니다." },
    { key: "disconnected", label: "연결 끊김", desc: "주의 상태였던 참가자의 연결이 끊어집니다." }
  ];

  var api = {
    session: session,
    participants: participants,
    chatSeed: chatSeed,
    events: events,
    scenarios: scenarios
  };

  if (typeof window !== "undefined") window.LiveClassDemoData = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})();
