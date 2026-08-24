/* ============================================================
   exam-demo-data.js — LXP 시험 데모 중앙 모의 데이터

   체크리스트 LXP-005/009/010/013/015/016/018/019/020 프론트엔드
   데모가 공유하는 유일한 데이터 소스다. 화면 파일에 데이터를
   흩어 놓지 않는다.

   ⚠ 전부 가상 값이다. 실제 훈련생 이름·연락처·신분증 번호·얼굴
     사진을 넣지 않는다. 이름은 가상 한국어 이름, 연락처와 생년월일은
     마스킹 형태만 쓴다.
   ============================================================ */

(function () {
  "use strict";

  /* ---------- 시험 2건 ---------- */
  var exams = [
    {
      id: "frontend-2026",
      title: "산업 데이터 분석 4기 — 2차 코딩 역량평가",
      course: "산업 데이터 분석 4기",
      startsAt: "2026-08-24 14:00",
      durationMin: 90,
      questionCount: 3,
      proctorMode: "웹캠 · 화면공유 · 모바일 3면 감독",
      requiresIdCheck: true,
      status: "precheck",       // upcoming | precheck | ready | done
      statusLabel: "사전점검 필요"
    },
    {
      id: "midterm-2026",
      title: "산업 데이터 분석 4기 — 중간 이론평가",
      course: "산업 데이터 분석 4기",
      startsAt: "2026-08-18 10:00",
      durationMin: 60,
      questionCount: 25,
      proctorMode: "웹캠 감독",
      requiresIdCheck: false,
      status: "done",
      statusLabel: "응시 완료",
      score: 84
    }
  ];

  /* ---------- 코딩 문제 3건 ---------- */
  var questions = [
    {
      id: "q1",
      no: 1,
      title: "결측 구간 보간",
      points: 40,
      brief: "센서 로그의 결측 구간을 앞뒤 값의 선형 보간으로 채우는 함수를 작성한다.",
      body: [
        "설비 센서가 일정 주기로 값을 기록하지만 통신 장애로 일부 구간이 비어 있다.",
        "비어 있는 구간(null)을 앞뒤 유효값의 선형 보간으로 채워라.",
        "맨 앞이나 맨 뒤가 비어 있으면 가장 가까운 유효값으로 채운다."
      ],
      constraints: ["1 ≤ 길이 ≤ 100,000", "값은 -10,000 이상 10,000 이하의 정수 또는 null"],
      samples: [
        { input: "[1, null, 3]", output: "[1, 2, 3]" },
        { input: "[null, 5, null]", output: "[5, 5, 5]" }
      ],
      tests: [
        { name: "기본 보간", input: "[1, null, 3]", expected: "[1, 2, 3]" },
        { name: "양끝 결측", input: "[null, 5, null]", expected: "[5, 5, 5]" },
        { name: "연속 결측", input: "[0, null, null, 9]", expected: "[0, 3, 6, 9]" },
        { name: "결측 없음", input: "[2, 4, 6]", expected: "[2, 4, 6]" }
      ]
    },
    {
      id: "q2",
      no: 2,
      title: "이상치 구간 탐지",
      points: 30,
      brief: "이동평균에서 임계치 이상 벗어난 구간의 시작·끝 인덱스를 반환한다.",
      body: [
        "윈도우 크기 k의 이동평균을 구하고, 관측값이 이동평균에서 임계치 t 이상 벗어난 지점을 이상치로 본다.",
        "연속된 이상치는 하나의 구간으로 묶어 [시작, 끝] 형태로 반환하라."
      ],
      constraints: ["1 ≤ k ≤ 길이 ≤ 50,000", "t 는 0 이상의 실수"],
      samples: [{ input: "values=[1,1,9,9,1], k=2, t=3", output: "[[2, 3]]" }],
      tests: [
        { name: "단일 구간", input: "[1,1,9,9,1], k=2, t=3", expected: "[[2, 3]]" },
        { name: "이상치 없음", input: "[1,1,1,1], k=2, t=3", expected: "[]" },
        { name: "구간 2개", input: "[9,1,1,9], k=2, t=3", expected: "[[0, 0], [3, 3]]" }
      ]
    },
    {
      id: "q3",
      no: 3,
      title: "설비별 가동률 집계",
      points: 30,
      brief: "로그를 설비별로 묶어 가동률을 계산하고 낮은 순으로 정렬한다.",
      body: [
        "각 로그는 설비ID, 상태(run/stop), 지속시간(분)으로 구성된다.",
        "설비별 가동률 = run 시간 합 / 전체 시간 합. 소수점 셋째 자리에서 반올림한다.",
        "가동률이 낮은 순으로, 같으면 설비ID 오름차순으로 정렬해 반환하라."
      ],
      constraints: ["1 ≤ 로그 수 ≤ 200,000", "지속시간은 1 이상의 정수"],
      samples: [{ input: "[[\"A\",\"run\",60], [\"A\",\"stop\",40]]", output: "[[\"A\", 0.6]]" }],
      tests: [
        { name: "단일 설비", input: "[[\"A\",\"run\",60],[\"A\",\"stop\",40]]", expected: "[[\"A\", 0.6]]" },
        { name: "정렬 확인", input: "A=0.9, B=0.4", expected: "[[\"B\", 0.4], [\"A\", 0.9]]" },
        { name: "전부 가동", input: "[[\"C\",\"run\",10]]", expected: "[[\"C\", 1.0]]" }
      ]
    }
  ];

  /* ---------- 언어별 시작 코드 ---------- */
  var starterCode = {
    python: {
      label: "Python 3.11",
      mode: "python",
      q1: "def solve(values):\n    # values: list[int | None]\n    # TODO: 선형 보간으로 결측 구간을 채운다\n    return values\n",
      q2: "def solve(values, k, t):\n    # TODO: 이동평균 기준 이상치 구간을 찾는다\n    return []\n",
      q3: "def solve(logs):\n    # TODO: 설비별 가동률을 집계한다\n    return []\n"
    },
    java: {
      label: "Java 17",
      mode: "java",
      q1: "import java.util.*;\n\npublic class Solution {\n    public int[] solve(Integer[] values) {\n        // TODO: 선형 보간으로 결측 구간을 채운다\n        return new int[0];\n    }\n}\n",
      q2: "import java.util.*;\n\npublic class Solution {\n    public int[][] solve(int[] values, int k, double t) {\n        // TODO: 이동평균 기준 이상치 구간을 찾는다\n        return new int[0][];\n    }\n}\n",
      q3: "import java.util.*;\n\npublic class Solution {\n    public List<Object[]> solve(List<Object[]> logs) {\n        // TODO: 설비별 가동률을 집계한다\n        return new ArrayList<>();\n    }\n}\n"
    },
    javascript: {
      label: "JavaScript (Node 20)",
      mode: "javascript",
      q1: "function solve(values) {\n  // TODO: 선형 보간으로 결측 구간을 채운다\n  return values;\n}\n",
      q2: "function solve(values, k, t) {\n  // TODO: 이동평균 기준 이상치 구간을 찾는다\n  return [];\n}\n",
      q3: "function solve(logs) {\n  // TODO: 설비별 가동률을 집계한다\n  return [];\n}\n"
    }
  };

  /* ---------- 응시자 12명 ----------
     name 은 가상 한국어 이름, phone·birth 는 마스킹 값만 쓴다. */
  function cand(id, name, seat, state, cam, screen, mobile, lastEventMin, note) {
    return {
      id: id, name: name, seat: seat, course: "산업 데이터 분석 4기",
      phone: "010-****-" + seat.slice(-4),
      birth: "20**-**-**",
      state: state,                 // ok | warn | risk | offline
      feeds: { camera: cam, screen: screen, mobile: mobile },  // live | weak | off
      lastEventMin: lastEventMin,
      idStatus: note === "id-pending" ? "검토 중" : (note === "id-none" ? "미제출" : "승인"),
      progress: Math.min(100, 20 + (lastEventMin * 7) % 80),
      reviewStatus: "미검토"
    };
  }

  var candidates = [
    cand("c01", "김도윤", "A-1041", "ok",      "live", "live", "live", 12),
    cand("c02", "이서준", "A-1042", "ok",      "live", "live", "live", 21),
    cand("c03", "박하은", "A-1043", "warn",    "live", "live", "off",   3),
    cand("c04", "최민서", "A-1044", "ok",      "live", "live", "live", 34),
    cand("c05", "정예린", "A-1045", "risk",    "live", "off",  "live",  1),
    cand("c06", "강시우", "A-1046", "ok",      "live", "live", "live", 27),
    cand("c07", "윤지호", "A-1047", "warn",    "weak", "live", "live",  6),
    cand("c08", "임채원", "A-1048", "ok",      "live", "live", "live", 41),
    cand("c09", "한소율", "A-1049", "offline", "off",  "off",  "off",   2, "id-pending"),
    cand("c10", "오건우", "A-1050", "ok",      "live", "live", "live", 18),
    cand("c11", "신아린", "A-1051", "risk",    "off",  "live", "live",  1),
    cand("c12", "배준혁", "A-1052", "ok",      "live", "live", "weak", 9, "id-none")
  ];

  /* ---------- 감독 이벤트 8건 ---------- */
  var events = [
    { id: "e1", at: "14:03:11", sec:  191, candidateId: "c05", type: "화면공유 중단",     severity: "risk", desc: "화면 공유 track 이 종료됨. 재공유 요청 발송." },
    { id: "e2", at: "14:07:45", sec:  465, candidateId: "c03", type: "모바일 연결 끊김",   severity: "warn", desc: "모바일 보조 카메라 응답 없음(30초 이상)." },
    { id: "e3", at: "14:12:02", sec:  722, candidateId: "c11", type: "웹캠 미검출",        severity: "risk", desc: "웹캠 장치가 제거되었거나 다른 앱이 점유 중." },
    { id: "e4", at: "14:15:38", sec:  938, candidateId: "c07", type: "전체화면 해제",      severity: "warn", desc: "시험 창 전체화면이 해제됨. 3초 후 복귀." },
    { id: "e5", at: "14:19:20", sec: 1160, candidateId: "c05", type: "다중 모니터 감지",   severity: "risk", desc: "확장 디스플레이 연결 감지(screen.isExtended = true)." },
    { id: "e6", at: "14:24:57", sec: 1497, candidateId: "c09", type: "네트워크 재연결",    severity: "warn", desc: "세션 재연결 3회. 응시 시간 보정 검토 필요." },
    { id: "e7", at: "14:31:12", sec: 1872, candidateId: "c11", type: "붙여넣기 시도",      severity: "warn", desc: "코드 영역에 외부 클립보드 붙여넣기 차단됨." },
    { id: "e8", at: "14:38:04", sec: 2284, candidateId: "c03", type: "자리 이탈 의심",     severity: "warn", desc: "웹캠 프레임에서 얼굴 미검출 45초 지속." }
  ];

  var warnReasons = [
    "화면 공유를 다시 시작하세요",
    "웹캠이 얼굴을 향하도록 조정하세요",
    "모바일 보조 카메라를 확인하세요",
    "전체화면을 유지하세요",
    "확장 모니터 연결을 해제하세요",
    "자리를 비우지 마세요"
  ];

  /* ---------- 실행 컨테이너 10건 ---------- */
  function box(id, candidateId, lang, state, cpu, mem, lastRun, node) {
    return { id: id, candidateId: candidateId, lang: lang, state: state,
             cpu: cpu, mem: mem, lastRun: lastRun, node: node };
  }
  var containers = [
    box("ex-4101", "c01", "Python 3.11", "running",      34, 412, "12초 전",  "node-a"),
    box("ex-4102", "c02", "Python 3.11", "running",      28, 388, "44초 전",  "node-a"),
    box("ex-4103", "c03", "Java 17",     "running",      61, 742, "8초 전",   "node-a"),
    box("ex-4104", "c04", "JavaScript",  "idle",          4, 196, "6분 전",   "node-b"),
    box("ex-4105", "c05", "Python 3.11", "running",      47, 455, "3초 전",   "node-b"),
    box("ex-4106", "c06", "Java 17",     "provisioning",  0,   0, "—",        "node-b"),
    box("ex-4107", "c07", "Python 3.11", "running",      52, 501, "19초 전",  "node-b"),
    box("ex-4108", "c08", "JavaScript",  "idle",          6, 210, "4분 전",   "node-c"),
    box("ex-4109", "c09", "Python 3.11", "failed",        0,   0, "2분 전",   "node-c"),
    box("ex-4110", "c10", "Java 17",     "running",      58, 690, "31초 전",  "node-c")
  ];

  /* ---------- 부하 시나리오 3건 ---------- */
  var scenarios = [
    {
      key: "normal", label: "정상",
      desc: "응시 인원이 예상 범위 안에 있고 대기열이 비어 있다.",
      kpi: { active: 12, queue: 0, running: 7, avgMs: 820 },
      advice: "증설 없이 현재 용량으로 충분합니다.",
      adviceTone: "ok", needScale: false
    },
    {
      key: "surge", label: "트래픽 급증",
      desc: "동시 제출이 몰려 실행 대기열이 쌓이고 평균 실행시간이 늘어난다.",
      kpi: { active: 48, queue: 23, running: 10, avgMs: 4120 },
      advice: "대기열 23건. 노드 2대 증설을 권고합니다.",
      adviceTone: "warn", needScale: true
    },
    {
      key: "failure", label: "노드 장애",
      desc: "node-c 가 응답하지 않아 해당 컨테이너가 모두 실패 상태다.",
      kpi: { active: 41, queue: 31, running: 5, avgMs: 7350 },
      advice: "node-c 응답 없음. 대체 노드 증설이 필요합니다.",
      adviceTone: "risk", needScale: true
    }
  ];

  window.LXP_EXAM_DATA = {
    exams: exams,
    questions: questions,
    starterCode: starterCode,
    candidates: candidates,
    events: events,
    warnReasons: warnReasons,
    containers: containers,
    scenarios: scenarios,
    /* 사전점검에서 확인하는 다섯 항목 */
    checkKeys: ["camera", "display", "fullscreen", "multiMonitor", "identity"]
  };
})();
