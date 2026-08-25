/* ============================================================
   live-classroom.js — 화상 라이브 강의실 데모 (LXP-125 / LXP-127)

   실제로 하는 일: 본인 카메라·마이크·화면 공유를 로컬 브라우저에서 켜고 끈다.
   데모로 남겨두는 일: 강사 영상, 참가자 목록, 채팅 — 전부 화면에 "데모"라고
   표시하고 서버로 아무것도 보내지 않는다.

   비동기 경합 방지: 카메라·마이크·화면공유 각각 LiveClassCommon.createStreamSlot() 을
   쓴다. 버튼을 빠르게 두 번 눌러도 슬롯이 가장 최근 요청만 채택하고, 늦게 도착한
   이전 요청은 즉시 정지한다 — 자세한 이유는 live-class-common.js 참고.
   ============================================================ */
(function () {
  "use strict";

  var C = window.LiveClassCommon;
  var D = window.LiveClassDemoData;

  var cameraSlot = C.createStreamSlot();
  var micSlot = C.createStreamSlot();
  var screenSlot = C.createStreamSlot();
  var micEnabled = false;

  var STATE_LABEL = { ok: "정상", warn: "주의", offline: "연결 끊김" };

  /* ---------- 세션 정보 ---------- */
  document.getElementById("sessionTitle").textContent = D.session.title;
  document.getElementById("sessionMeta").textContent =
    D.session.course + " · " + D.session.instructor + " · 시작 " + D.session.startedAt + " (데모)";

  /* ---------- 참가자 패널 ---------- */
  function renderPeople() {
    var list = document.getElementById("peopleList");
    document.getElementById("peopleCount").textContent = D.participants.length + "명";
    list.innerHTML = D.participants.map(function (p) {
      return '<li class="people-item" data-state="' + p.state + '">' +
        '<span class="people-name">' + C.esc(p.name) + " (" + C.esc(p.seat) + ")</span>" +
        '<span class="state-badge ' + (p.state === "ok" ? "ok" : p.state === "warn" ? "warn" : "risk") + '">' +
          C.esc(STATE_LABEL[p.state] || p.state) +
        "</span>" +
      "</li>";
    }).join("");
  }
  renderPeople();

  /* ---------- 채팅 (로컬 전용, 새로고침하면 초기 메시지로 되돌아간다) ---------- */
  var chat = D.chatSeed.map(function (m) { return { id: m.id, author: m.author, text: m.text, at: m.at }; });
  var chatListEl = document.getElementById("chatList");

  function renderChat() {
    chatListEl.innerHTML = "";
    chat.forEach(function (m) {
      var li = document.createElement("li");
      li.className = "chat-item";
      var head = document.createElement("p");
      head.className = "chat-meta";
      head.textContent = m.author + " · " + m.at;
      var body = document.createElement("p");
      body.className = "chat-text";
      body.textContent = m.text;              /* textContent 만 쓴다 — 서버에도, DOM 에도 HTML 삽입이 없다 */
      li.appendChild(head);
      li.appendChild(body);
      chatListEl.appendChild(li);
    });
    chatListEl.scrollTop = chatListEl.scrollHeight;
  }
  renderChat();

  function pad(n) { return (n < 10 ? "0" : "") + n; }

  document.getElementById("chatForm").addEventListener("submit", function (e) {
    e.preventDefault();
    var input = document.getElementById("chatInput");
    var text = input.value.trim().slice(0, 300);
    if (!text) return;                          /* 빈 메시지는 보내지 않는다 */
    var now = new Date();
    chat.push({ id: "local-" + now.getTime(), author: "나", text: text, at: pad(now.getHours()) + ":" + pad(now.getMinutes()) });
    input.value = "";
    renderChat();
  });

  /* ---------- 미디어 버튼 공통 ---------- */
  function setBusy(btn, busy) {
    btn.disabled = busy;
    if (busy) btn.setAttribute("aria-busy", "true"); else btn.removeAttribute("aria-busy");
  }

  function setPressed(btn, on) { btn.setAttribute("aria-pressed", String(on)); }

  var stageMsg = document.getElementById("stageMsg");
  function setStageMsg(text, tone) {
    stageMsg.textContent = text || "";
    if (tone) stageMsg.dataset.tone = tone; else stageMsg.removeAttribute("data-tone");
  }

  /* ---------- 본인 카메라 (전체 시작/종료 토글) ---------- */
  var camBtn = document.getElementById("camBtn");
  var camVideo = document.querySelector('[data-role="self-preview"]');
  var camPh = document.querySelector('[data-role="self-placeholder"]');
  setPressed(camBtn, false);

  function turnCameraOff() {
    cameraSlot.stop();
    camVideo.srcObject = null;
    camVideo.hidden = true;
    camPh.hidden = false;
    camBtn.textContent = "카메라 켜기";
    camBtn.dataset.state = "off";
    setPressed(camBtn, false);
  }

  camBtn.addEventListener("click", function () {
    if (cameraSlot.getStream()) { turnCameraOff(); return; }
    var support = C.supportState(window.isSecureContext, navigator.mediaDevices);
    if (!support.ok) { setStageMsg(support.message, "risk"); return; }
    var token = cameraSlot.begin();
    setBusy(camBtn, true);
    navigator.mediaDevices.getUserMedia({ video: true, audio: false })
      .then(function (stream) {
        var track = C.adoptTrack(cameraSlot, token, stream, "video");
        if (!track) {
          /* adoptTrack 이 이미 스트림을 정리했다. 다만 낡은 요청(더 최신 요청이 있어 버려짐)과
             지금 요청인데 트랙이 없었던 경우는 사용자에게 다른 의미다 — 낡은 요청은 화면에
             보일 이유가 없어 조용히 무시하고, 지금 요청의 실패만 안내한다. */
          if (cameraSlot.isCurrent(token)) setStageMsg("카메라 트랙을 가져오지 못했습니다. 다시 시도해 주세요.", "risk");
          return;
        }
        C.attachStream(camVideo, stream);
        camPh.hidden = true;
        camBtn.textContent = "카메라 끄기";
        camBtn.dataset.state = "on";
        setPressed(camBtn, true);
        track.addEventListener("ended", function () {
          if (cameraSlot.getStream() !== stream) return;   /* 이미 교체된 이전 스트림의 뒤늦은 ended 는 무시 */
          turnCameraOff();
          setStageMsg("카메라 연결이 끊겼습니다.", "risk");
        });
        setStageMsg("");
      })
      .catch(function (err) {
        if (!cameraSlot.isCurrent(token)) return;
        setStageMsg(C.mediaErrorInfo(err && err.name, "카메라").message, "risk");
      })
      .then(function () { if (cameraSlot.isCurrent(token)) setBusy(camBtn, false); });
  });

  /* ---------- 본인 마이크 (한 번 얻은 트랙을 enabled 로만 토글) ---------- */
  var micBtn = document.getElementById("micBtn");
  setPressed(micBtn, false);

  micBtn.addEventListener("click", function () {
    if (micSlot.getStream()) {
      micEnabled = !micEnabled;
      micSlot.getStream().getAudioTracks().forEach(function (t) { t.enabled = micEnabled; });
      micBtn.textContent = micEnabled ? "마이크 끄기" : "마이크 켜기";
      micBtn.dataset.state = micEnabled ? "on" : "off";
      setPressed(micBtn, micEnabled);
      return;
    }
    var support = C.supportState(window.isSecureContext, navigator.mediaDevices);
    if (!support.ok) { setStageMsg(support.message, "risk"); return; }
    var token = micSlot.begin();
    setBusy(micBtn, true);
    navigator.mediaDevices.getUserMedia({ audio: true, video: false })
      .then(function (stream) {
        var track = C.adoptTrack(micSlot, token, stream, "audio");
        if (!track) {
          if (micSlot.isCurrent(token)) setStageMsg("마이크 트랙을 가져오지 못했습니다. 다시 시도해 주세요.", "risk");
          return;
        }
        micEnabled = true;
        micBtn.textContent = "마이크 끄기";
        micBtn.dataset.state = "on";
        setPressed(micBtn, true);
        track.addEventListener("ended", function () {
          if (micSlot.getStream() !== stream) return;
          micSlot.stop();
          micEnabled = false;
          micBtn.textContent = "마이크 켜기";
          micBtn.dataset.state = "off";
          setPressed(micBtn, false);
          setStageMsg("마이크 연결이 끊겼습니다.", "risk");
        });
        setStageMsg("");
      })
      .catch(function (err) {
        if (!micSlot.isCurrent(token)) return;
        setStageMsg(C.mediaErrorInfo(err && err.name, "마이크").message, "risk");
      })
      .then(function () { if (micSlot.isCurrent(token)) setBusy(micBtn, false); });
  });

  /* ---------- 본인 화면 공유 ---------- */
  var screenBtn = document.getElementById("screenBtn");
  var screenVideo = document.querySelector('[data-role="screen-preview"]');
  var screenPh = document.querySelector('[data-role="screen-placeholder"]');
  setPressed(screenBtn, false);

  function stopScreenShare() {
    screenSlot.stop();
    screenVideo.srcObject = null;
    screenVideo.hidden = true;
    screenPh.hidden = false;
    screenBtn.textContent = "화면 공유 시작";
    screenBtn.dataset.state = "off";
    setPressed(screenBtn, false);
  }

  screenBtn.addEventListener("click", function () {
    if (screenSlot.getStream()) { stopScreenShare(); return; }
    var support = C.supportState(window.isSecureContext, navigator.mediaDevices);
    if (!support.ok) { setStageMsg(support.message, "risk"); return; }
    if (!navigator.mediaDevices.getDisplayMedia) {
      setStageMsg("이 브라우저는 화면 공유 API를 지원하지 않습니다.", "risk");
      return;
    }
    var token = screenSlot.begin();
    setBusy(screenBtn, true);
    navigator.mediaDevices.getDisplayMedia({ video: true, audio: false })
      .then(function (stream) {
        var track = C.adoptTrack(screenSlot, token, stream, "video");
        if (!track) {
          if (screenSlot.isCurrent(token)) setStageMsg("화면 공유 트랙을 가져오지 못했습니다. 다시 시도해 주세요.", "risk");
          return;
        }
        C.attachStream(screenVideo, stream);
        screenPh.hidden = true;
        screenBtn.textContent = "화면 공유 중지";
        screenBtn.dataset.state = "on";
        setPressed(screenBtn, true);
        track.addEventListener("ended", function () {
          if (screenSlot.getStream() !== stream) return;
          stopScreenShare();
          setStageMsg("화면 공유가 중단되었습니다.", "risk");
        });
        setStageMsg("");
      })
      .catch(function (err) {
        if (!screenSlot.isCurrent(token)) return;
        setStageMsg(C.mediaErrorInfo(err && err.name, "화면 공유").message, "risk");
      })
      .then(function () { if (screenSlot.isCurrent(token)) setBusy(screenBtn, false); });
  });

  /* ---------- 나가기 (네이티브 <dialog> — 포커스 트랩·Escape 닫기를 브라우저가 처리한다) ---------- */
  var leaveDialog = document.getElementById("leaveDialog");

  document.getElementById("leaveBtn").addEventListener("click", function () { leaveDialog.showModal(); });
  document.getElementById("leaveCancel").addEventListener("click", function () { leaveDialog.close(); });
  leaveDialog.addEventListener("click", function (e) { if (e.target === leaveDialog) leaveDialog.close(); });

  /* dispose() 가 지금 켜져 있는 스트림은 물론, 이 시점 이후 늦게 도착하는 getUserMedia
     결과도 slot.resolve() 를 거치는 순간 자동으로 정지시킨다. */
  function stopAll() {
    cameraSlot.dispose();
    micSlot.dispose();
    screenSlot.dispose();
  }

  document.getElementById("leaveConfirm").addEventListener("click", function () {
    stopAll();
    window.location.href = "/v2/lxp/trainee/live-class-precheck.html";
  });

  window.addEventListener("pagehide", stopAll);

  /* ---------- 좁은 화면 탭 전환 (1023px 이하) — WAI-ARIA APG 탭 패턴 ---------- */
  var tabs = document.getElementById("classroomTabs");
  var tabButtons = Array.prototype.slice.call(tabs.querySelectorAll('[role="tab"]'));
  var mq = window.matchMedia("(max-width: 1023px)");

  function syncTabMode() {
    var narrow = mq.matches;
    tabs.hidden = !narrow;
    var active = tabs.querySelector('[aria-selected="true"]') || tabButtons[0];
    var activeId = active.getAttribute("aria-controls");
    document.querySelectorAll(".classroom-pane").forEach(function (p) {
      p.hidden = narrow && p.id !== activeId;
    });
  }

  function activateTab(btn) {
    tabButtons.forEach(function (b) {
      var on = b === btn;
      b.setAttribute("aria-selected", String(on));
      b.tabIndex = on ? 0 : -1;
      b.classList.toggle("is-active", on);
      b.classList.toggle("btn-secondary", on);
      b.classList.toggle("btn-gray", !on);
    });
    syncTabMode();
  }

  tabs.addEventListener("click", function (e) {
    var b = e.target.closest('[role="tab"]');
    if (!b) return;
    activateTab(b);
  });

  tabs.addEventListener("keydown", function (e) {
    var idx = tabButtons.indexOf(document.activeElement);
    if (idx === -1) return;
    var next = null;
    if (e.key === "ArrowRight") next = tabButtons[(idx + 1) % tabButtons.length];
    else if (e.key === "ArrowLeft") next = tabButtons[(idx - 1 + tabButtons.length) % tabButtons.length];
    else if (e.key === "Home") next = tabButtons[0];
    else if (e.key === "End") next = tabButtons[tabButtons.length - 1];
    if (!next) return;
    e.preventDefault();
    activateTab(next);
    next.focus();
  });

  mq.addEventListener("change", syncTabMode);
  syncTabMode();
})();
