/* ============================================================
   learning-presence-common.js — 학습 참여 확인 데모 공통 유틸리티 (LXP-140 / LXP-141 / LXP-142)

   브라우저에서는 window.LearningPresence, Node 에서는 module.exports 로
   같은 API 를 노출한다 — 계약 테스트가 문자열 검사가 아니라 실제로
   require 해 판정 로직을 실행한다.

   여기서 하는 일은 다섯이다:
     ① 얼굴 검출·카메라 연결·연속 이탈시간으로 현재 참여 상태 판정 (derivePresenceState)
     ② 접속/확인/누적 이탈/인정 시간의 불변식 계산 (calculateLearningTime)
     ③ 매 초 시계를 한 칸 전진시키며 위 누적값을 갱신 (advanceLearningClock)
     ④ 누적 이탈시간·횟수로 훈련생을 정상/주의/집중관리로 분류 (classifyLearner)
     ⑤ 카메라 스트림 수명주기 — 낡은 요청의 스트림을 자동 폐기 (createMediaSlot)
   실제 얼굴 검출·특징값·생체정보를 다루는 코드는 이 파일에 없다.
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

  /** 현재 순간의 참여 상태. 카메라 연결이 끊기면 얼굴 여부 자체를 알 수 없으므로
      다른 무엇보다 먼저 본다. 얼굴이 없는 상태가 이어지면(자리 이탈) "이번에 자리를
      비운 연속 시간"(currentAwaySeconds)을 grace → warning → focus 세 기준과 비교해
      안내 단계를 올린다 — 정상으로 돌아오면 이 값만 0 으로 초기화된다(누적값이 아니다). */
  function derivePresenceState(input) {
    input = input || {};
    var faceCount = toNonNegInt(input.faceCount);
    var cameraConnected = !!input.cameraConnected;
    var currentAwaySeconds = toNonNegInt(input.currentAwaySeconds);
    var graceSeconds = toNonNegInt(input.graceSeconds);
    var warningSeconds = toNonNegInt(input.warningSeconds);
    var focusSeconds = toNonNegInt(input.focusSeconds);

    if (!cameraConnected) {
      return { code: "camera_disconnected", label: "카메라 연결 끊김", tone: "risk", countsAsPresent: false };
    }
    if (faceCount === 0) {
      if (focusSeconds > 0 && currentAwaySeconds >= focusSeconds) {
        return { code: "away_focus", label: "장시간 자리 이탈 — 집중관리 확인 필요", tone: "risk", countsAsPresent: false };
      }
      if (warningSeconds > 0 && currentAwaySeconds >= warningSeconds) {
        return { code: "away_warning", label: "자리 이탈 경고", tone: "warn", countsAsPresent: false };
      }
      if (graceSeconds > 0 && currentAwaySeconds >= graceSeconds) {
        return { code: "away_return_needed", label: "자리 복귀 필요", tone: "warn", countsAsPresent: false };
      }
      return { code: "no_face", label: "얼굴 미검출 — 허용시간 내 확인 중", tone: "warn", countsAsPresent: false };
    }
    if (faceCount > 1) {
      return { code: "multiple_faces", label: "여러 얼굴 감지 — 본인 확인 필요", tone: "warn", countsAsPresent: false };
    }
    return { code: "present", label: "정상 참여 중", tone: "ok", countsAsPresent: true };
  }

  /** 0 <= verifiedSeconds <= connectedSeconds, recognizedSeconds === verifiedSeconds,
      cumulativeAwaySeconds >= 0 을 항상 보장한다. 프론트엔드 데모에서는 실제 출결 인정
      정책이 백엔드에 없으므로 확인된 학습시간을 그대로 인정 예정 시간으로 쓴다 —
      자리 이탈은 애초에 verifiedSeconds 를 늘리지 않은 시점에 이미 반영되어 있으므로
      여기서 다시 빼면(이중 차감) 정상 복귀 시 인정 예정이 소급 증가하는 역전이 생긴다.
      입력은 새 이름 cumulativeAwaySeconds 를 쓰지만, 이 함수를 쓰는 다른 화면이 예전
      이름 awaySeconds 로 호출해도 그대로 받아들인다(하위 호환 — 그 화면은 이번 차수
      수정 대상이 아니다). */
  function calculateLearningTime(input) {
    input = input || {};
    var connectedSeconds = toNonNegInt(input.connectedSeconds);
    var verifiedSeconds = Math.min(toNonNegInt(input.verifiedSeconds), connectedSeconds);
    var cumulativeAwaySeconds = toNonNegInt(
      input.cumulativeAwaySeconds !== undefined ? input.cumulativeAwaySeconds : input.awaySeconds
    );
    return {
      connectedSeconds: connectedSeconds,
      verifiedSeconds: verifiedSeconds,
      cumulativeAwaySeconds: cumulativeAwaySeconds,
      recognizedSeconds: verifiedSeconds
    };
  }

  /** 매 초(tick)마다 학습 시계를 한 칸 전진시킨다 — 화면(learning-presence-check.js)은
      이 함수 하나로만 네 누적값을 바꾼다(직접 +1 하지 않는다).
        presenceState.countsAsPresent 이면: 확인된 학습시간 +1, 연속 이탈시간은 0 으로
          초기화(정상 복귀).
        그 외 자리 이탈류 코드(camera_disconnected/no_face/away_return_needed/
          away_warning/away_focus, 즉 countsAsPresent 가 아니면서 multiple_faces 도
          아닌 경우)면: 누적 이탈시간과 연속 이탈시간 모두 +1.
        multiple_faces 는 정상 확인도, 자리 이탈 확정도 아니므로 두 종류 누적값과
          연속 이탈시간을 전부 그대로 둔다(접속시간만 흐른다). */
  function advanceLearningClock(clock, presenceState) {
    clock = clock || {};
    presenceState = presenceState || {};
    var connectedSeconds = toNonNegInt(clock.connectedSeconds) + 1;
    var verifiedSeconds = toNonNegInt(clock.verifiedSeconds);
    var cumulativeAwaySeconds = toNonNegInt(clock.cumulativeAwaySeconds);
    var currentAwaySeconds = toNonNegInt(clock.currentAwaySeconds);

    if (presenceState.countsAsPresent) {
      verifiedSeconds += 1;
      currentAwaySeconds = 0;
    } else if (presenceState.code !== "multiple_faces") {
      cumulativeAwaySeconds += 1;
      currentAwaySeconds += 1;
    }

    var time = calculateLearningTime({
      connectedSeconds: connectedSeconds,
      verifiedSeconds: verifiedSeconds,
      cumulativeAwaySeconds: cumulativeAwaySeconds
    });
    return {
      connectedSeconds: time.connectedSeconds,
      verifiedSeconds: time.verifiedSeconds,
      cumulativeAwaySeconds: time.cumulativeAwaySeconds,
      currentAwaySeconds: currentAwaySeconds,
      recognizedSeconds: time.recognizedSeconds
    };
  }

  /** 누적 자리 이탈시간·횟수를 기준(warningSeconds/focusSeconds)과 비교해
      정상/주의/집중관리로 분류한다. 횟수는 판정 사유 문구에만 반영한다 —
      기준 자체는 누적 시간 하나로만 판단해 애매한 임계값을 늘리지 않는다. */
  function classifyLearner(input) {
    input = input || {};
    var awaySeconds = toNonNegInt(input.awaySeconds);
    var awayCount = toNonNegInt(input.awayCount);
    var warningSeconds = toNonNegInt(input.warningSeconds);
    var focusSeconds = toNonNegInt(input.focusSeconds);

    if (focusSeconds > 0 && awaySeconds >= focusSeconds) {
      return {
        code: "focus",
        label: "집중관리",
        reason: "누적 자리 이탈 " + awaySeconds + "초(" + awayCount + "회)가 집중관리 기준 " + focusSeconds + "초 이상입니다."
      };
    }
    if (warningSeconds > 0 && awaySeconds >= warningSeconds) {
      return {
        code: "warning",
        label: "주의",
        reason: "누적 자리 이탈 " + awaySeconds + "초(" + awayCount + "회)가 주의 기준 " + warningSeconds + "초 이상입니다."
      };
    }
    return {
      code: "normal",
      label: "정상",
      reason: "누적 자리 이탈 " + awaySeconds + "초(" + awayCount + "회)가 기준 이내입니다."
    };
  }

  /* ---------- 카메라 스트림 슬롯 ----------
     request() 호출마다 세대를 올리고 새 토큰을 준다 — 그 순간 이전 토큰은 낡은 것이 된다.
     adopt(tok, stream) 은 토큰이 아직 최신이면 기존 스트림을 정지하고 채택해 그 스트림을
     돌려준다. 토큰이 낡았으면 받은 스트림의 모든 트랙을 그 자리에서 정지하고 null 을
     돌려준다 — 화면에는 절대 반영되지 않는다.
     stop() 은 현재 스트림을 정지하면서 세대도 같이 올린다 — 그래서 stop() 을 부른 뒤에
     뒤늦게 도착하는, stop() 이전에 이미 보낸 요청의 결과도 자동으로 낡은 토큰이 되어
     adopt() 에서 거부된다. 여러 번 불러도 안전하다(두 번째 호출은 이미 비어 있는
     스트림을 다시 정지하려 시도할 뿐, 아무 부작용이 없다). */
  function createMediaSlot() {
    var generation = 0;
    var current = null;

    function stopStream(stream) {
      if (!stream || !stream.getTracks) return;
      stream.getTracks().forEach(function (t) { t.stop(); });
    }

    function request() {
      generation += 1;
      return generation;
    }

    function isCurrent(token) {
      return token === generation;
    }

    function adopt(token, stream) {
      if (!isCurrent(token)) {
        stopStream(stream);
        return null;
      }
      if (current && current !== stream) stopStream(current);
      current = stream;
      return stream;
    }

    function stop() {
      stopStream(current);
      current = null;
      generation += 1;
    }

    return { request: request, isCurrent: isCurrent, adopt: adopt, stop: stop };
  }

  var api = {
    derivePresenceState: derivePresenceState,
    calculateLearningTime: calculateLearningTime,
    advanceLearningClock: advanceLearningClock,
    classifyLearner: classifyLearner,
    createMediaSlot: createMediaSlot
  };

  if (typeof window !== "undefined") window.LearningPresence = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})();
