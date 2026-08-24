/* ============================================================
   proctor-review.js — 사후 신분·녹화·이벤트 검토 (LXP-016)

   타임라인은 실제 영상이 아니라 초 단위 카운터다. 마커를 선택하면
   재생 위치와 상세 패널이 함께 이동한다.
   판정과 메모는 sessionStorage 에만 남는다.
   ============================================================ */

(function () {
  "use strict";

  var D = window.LXP_EXAM_DATA;
  var E = window.ExamDemo;

  var TOTAL = 2700;                 /* 45분 녹화(데모) */
  var current = null;               /* 선택된 응시자 */
  var selectedEventId = null;
  var playing = false;
  var timer = null;

  var sel        = document.getElementById("candSelect");
  var loadNote   = document.getElementById("loadNote");
  var seek       = document.getElementById("seek");
  var curTime    = document.getElementById("curTime");
  var totTime    = document.getElementById("totTime");
  var playBtn    = document.getElementById("playBtn");
  var track      = document.getElementById("markerTrack");
  var detailEl   = document.getElementById("eventDetail");
  var listEl     = document.getElementById("eventList");
  var memoEl     = document.getElementById("verdictMemo");
  var stateEl    = document.getElementById("verdictState");

  seek.max = String(TOTAL);
  totTime.textContent = E.fmtDuration(TOTAL);

  function eventsOf(id) {
    return D.events.filter(function (e) { return e.candidateId === id; })
                   .sort(function (a, b) { return a.sec - b.sec; });
  }

  /* ---------- 응시자 선택 ---------- */
  sel.innerHTML = D.candidates.map(function (c) {
    return '<option value="' + c.id + '">' + E.esc(c.name) + " (" + E.esc(c.seat) + ")</option>";
  }).join("");

  function pickInitial() {
    var want = E.qs("candidate");
    var found = D.candidates.filter(function (c) { return c.id === want; })[0];
    if (want && !found) {
      loadNote.textContent = "요청한 응시자(" + want + ")를 찾을 수 없어 첫 응시자를 표시합니다.";
      E.toast("응시자를 찾을 수 없어 첫 응시자를 열었습니다", "warn");
    } else if (found) {
      loadNote.textContent = "실시간 관제에서 전달된 응시자를 열었습니다.";
    } else {
      /* 쿼리가 없으면 실시간 관제에서 마지막으로 보던 응시자를 잇는다.
         첫 응시자로 되돌리면 감독관이 방금 보던 사람을 다시 찾아야 한다. */
      found = D.candidates.filter(function (c) { return c.id === E.load().proctor.selectedId; })[0];
      loadNote.textContent = "";
    }
    return found || D.candidates[0];
  }

  function renderCandidate() {
    var c = current;
    document.getElementById("rvAvatar").textContent = c.name.charAt(0);
    document.getElementById("rvName").textContent = c.name;
    document.getElementById("rvSeat").textContent = c.course + " · " + c.seat;
    document.getElementById("rvContact").textContent = "연락처 " + c.phone;
    document.getElementById("rvProgress").textContent = c.progress + "%";
    document.getElementById("rvBirth").textContent = c.birth;
    document.getElementById("rvIdStatus").textContent = c.idStatus;
    var recLabel = document.getElementById("recLabel");
    if (recLabel) recLabel.textContent = c.name + " · " + c.seat;

    var slot = document.getElementById("rvIdSlot");
    slot.textContent = c.idStatus === "미제출"
      ? "제출된 신분증이 없습니다"
      : "신분증 이미지 자리 (데모에서는 표시하지 않음)";

    var evs = eventsOf(c.id);
    document.getElementById("rvEventCount").textContent = evs.length + "건";
    renderMarkers(evs);
    renderList(evs);
    renderVerdict();
    selectedEventId = null;
    detailEl.innerHTML = '<p class="hint-text">마커를 선택하면 상세가 표시됩니다.</p>';
  }

  function renderMarkers(evs) {
    if (!evs.length) {
      track.innerHTML = '<p class="hint-text track-empty">기록된 이벤트가 없습니다.</p>';
      return;
    }
    track.innerHTML = evs.map(function (ev, i) {
      var left = Math.min(98, (ev.sec / TOTAL) * 100);
      return '<button class="marker" type="button" data-ev="' + ev.id + '" data-sev="' + ev.severity + '"' +
             ' style="left:' + left + '%" aria-pressed="false"' +
             ' aria-label="' + E.esc(ev.at + " " + ev.type) + '">' + (i + 1) + "</button>";
    }).join("");
  }

  function renderList(evs) {
    if (!evs.length) {
      listEl.innerHTML = '<tr><td colspan="4" class="hint-text">기록된 이벤트가 없습니다.</td></tr>';
      return;
    }
    listEl.innerHTML = evs.map(function (ev) {
      var tone = ev.severity === "risk" ? "sev-critical" : "sev-warn";
      var label = ev.severity === "risk" ? "심각" : "주의";
      return '<tr><td class="mono nowrap">' + E.esc(ev.at) + "</td><td>" + E.esc(ev.type) +
             '</td><td class="' + tone + '">' + label + "</td><td>" + E.esc(ev.desc) + "</td></tr>";
    }).join("");
  }

  function selectEvent(id) {
    var ev = D.events.filter(function (e) { return e.id === id; })[0];
    if (!ev) return;
    selectedEventId = id;

    track.querySelectorAll(".marker").forEach(function (m) {
      m.setAttribute("aria-pressed", String(m.dataset.ev === id));
    });

    setTime(ev.sec);
    detailEl.innerHTML =
      '<div class="info-label">' + E.esc(ev.at) + "</div>" +
      '<div class="info-value ev-type">' + E.esc(ev.type) + "</div>" +
      '<div class="info-value sub">' + E.esc(ev.desc) + "</div>" +
      '<p class="hint-text ev-note">재생 위치를 ' + E.fmtDuration(ev.sec) + " 로 이동했습니다.</p>";
  }

  track.addEventListener("click", function (e) {
    var m = e.target.closest("[data-ev]");
    if (m) selectEvent(m.dataset.ev);
  });

  /* ---------- 재생 ---------- */
  var progressEl = document.getElementById("timelineProgress");

  function setTime(sec) {
    sec = Math.max(0, Math.min(TOTAL, sec));
    seek.value = String(sec);
    curTime.textContent = E.fmtDuration(sec);
    if (progressEl) progressEl.style.width = ((sec / TOTAL) * 100) + "%";
  }

  seek.addEventListener("input", function () { setTime(Number(seek.value)); });

  document.getElementById("backBtn").addEventListener("click", function () { setTime(Number(seek.value) - 10); });
  document.getElementById("fwdBtn").addEventListener("click", function () { setTime(Number(seek.value) + 10); });

  /* 40px 원형 버튼 안이라 글자를 넣으면 두 줄로 접힌다.
     운영 recordings.html 처럼 기호만 바꾸고 이름은 aria-label / title 로 준다. */
  function setPlayLabel(on) {
    playBtn.textContent = on ? "⏸" : "▶";
    playBtn.setAttribute("aria-label", on ? "일시정지" : "재생");
    playBtn.setAttribute("title", on ? "일시정지" : "재생");
    playBtn.setAttribute("aria-pressed", String(on));
  }

  playBtn.addEventListener("click", function () {
    playing = !playing;
    setPlayLabel(playing);
    if (playing) {
      timer = window.setInterval(function () {
        var next = Number(seek.value) + 5;      /* 데모: 1초당 5초 진행 */
        if (next >= TOTAL) {
          setTime(TOTAL);
          playing = false;
          setPlayLabel(false);
          window.clearInterval(timer);
          return;
        }
        setTime(next);
      }, 1000);
    } else {
      window.clearInterval(timer);
    }
  });

  /* ---------- 판정 ---------- */
  function renderVerdict() {
    var saved = (E.load().review || {})[current.id];
    document.querySelectorAll('input[name="verdict"]').forEach(function (r) {
      r.checked = !!(saved && saved.status === r.value);
    });
    memoEl.value = saved ? (saved.memo || "") : "";
    stateEl.textContent = saved ? saved.status + " · 기록됨" : "미검토";
  }

  document.getElementById("saveVerdict").addEventListener("click", function () {
    var picked = document.querySelector('input[name="verdict"]:checked');
    if (!picked) { E.toast("판정을 선택하세요", "warn"); return; }
    var value = picked.value, memo = memoEl.value.trim();
    E.patch(function (s) { s.review[current.id] = { status: value, memo: memo }; });
    stateEl.textContent = value + " · 기록됨";
    E.toast("판정을 기록했습니다 (데모)", "ok");
  });

  sel.addEventListener("change", function () {
    /* 재생을 멈추지 않으면 인터벌이 그대로 살아 새 응시자 화면에서 위치가 저절로 흐른다.
       버튼도 "일시정지"로 남아 재생하려면 두 번 눌러야 했다. */
    if (playing) { playing = false; window.clearInterval(timer); setPlayLabel(false); }
    current = D.candidates.filter(function (c) { return c.id === sel.value; })[0];
    loadNote.textContent = "";
    setTime(0);
    renderCandidate();
  });

  /* ---------- 초기화 ---------- */
  current = pickInitial();
  sel.value = current.id;
  setPlayLabel(false);
  setTime(0);
  renderCandidate();
})();
