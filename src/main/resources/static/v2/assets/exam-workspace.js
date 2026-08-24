/* ============================================================
   exam-workspace.js — 코딩 시험장 (LXP-009 / LXP-010)

   LXP-009: 세 패널을 포인터와 키보드로 조절한다. separator 는 실제
            button[role=separator] 이라 키보드만으로도 조절된다.
   LXP-010: 언어 선택, 줄번호 편집기, 테스트 케이스, 콘솔을 제공한다.
            실행·채점은 시뮬레이션이며 서버에서 컴파일하지 않는다.
   ============================================================ */

(function () {
  "use strict";

  var D = window.LXP_EXAM_DATA;
  var E = window.ExamDemo;

  var MIN = { a: 16, b: 24 };   /* 패널 최소 비율(%) — 내용이 뭉개지지 않는 하한 */
  var MAX = { a: 46, b: 60 };

  var splitEl   = document.getElementById("split");
  var codeArea  = document.getElementById("codeArea");
  var gutter    = document.getElementById("codeGutter");
  var listEl    = document.getElementById("questionList");
  var bodyEl    = document.getElementById("questionBody");
  var casesEl   = document.getElementById("testcases");
  var consoleEl = document.getElementById("consoleBox");
  var clockEl   = document.getElementById("remainTime");
  var clockAlert= document.getElementById("clockAlert");
  var langSel   = document.getElementById("langSelect");
  var scenSel   = document.getElementById("scenarioSelect");
  var solvedEl  = document.getElementById("solvedCount");

  var state = E.load();
  var current = state.workspace.questionId || "q1";
  var lang = state.workspace.lang || "python";
  var deadline = Date.now() + 90 * 60 * 1000;   /* 데모: 진입 시점부터 90분 */

  function q(id) { return D.questions.filter(function (x) { return x.id === id; })[0] || D.questions[0]; }
  function codeKey(qid, l) { return qid + ":" + l; }

  function starter(qid, l) {
    var pack = D.starterCode[l] || D.starterCode.python;
    return pack[qid] || "";
  }

  function currentCode() {
    var saved = state.workspace.code[codeKey(current, lang)];
    return saved != null ? saved : starter(current, lang);
  }

  /* ---------- 패널 비율 ---------- */
  function applyRatios() {
    var r = state.workspace.ratios || [24, 36];
    splitEl.style.setProperty("--col-a", r[0] + "%");
    splitEl.style.setProperty("--col-b", r[1] + "%");
    document.getElementById("sepA").setAttribute("aria-valuenow", String(Math.round(r[0])));
    document.getElementById("sepB").setAttribute("aria-valuenow", String(Math.round(r[1])));
  }

  function setRatio(which, value) {
    var r = state.workspace.ratios.slice();
    var i = which === "a" ? 0 : 1;
    var lo = MIN[which], hi = MAX[which];
    r[i] = Math.max(lo, Math.min(hi, value));
    /* 두 패널 합이 너무 커지면 오른쪽 코드 영역이 사라진다 */
    if (r[0] + r[1] > 78) r[i] = which === "a" ? 78 - r[1] : 78 - r[0];
    state.workspace.ratios = r;
    E.patch(function (s) { s.workspace.ratios = r; });
    applyRatios();
  }

  function bindSeparator(id, which) {
    var sep = document.getElementById(id);
    var dragging = false;

    function pct(clientX) {
      var box = splitEl.getBoundingClientRect();
      var raw = ((clientX - box.left) / box.width) * 100;
      return which === "a" ? raw : raw - state.workspace.ratios[0];
    }

    sep.addEventListener("pointerdown", function (e) {
      dragging = true;
      sep.setPointerCapture(e.pointerId);
      e.preventDefault();
    });
    sep.addEventListener("pointermove", function (e) {
      if (!dragging) return;
      setRatio(which, pct(e.clientX));
    });
    sep.addEventListener("pointerup", function (e) {
      dragging = false;
      if (sep.hasPointerCapture(e.pointerId)) sep.releasePointerCapture(e.pointerId);
    });

    /* 키보드 조절 — 화살표 2%, Shift+화살표 10% */
    sep.addEventListener("keydown", function (e) {
      var step = e.shiftKey ? 10 : 2;
      var cur = state.workspace.ratios[which === "a" ? 0 : 1];
      if (e.key === "ArrowLeft")      { e.preventDefault(); setRatio(which, cur - step); }
      else if (e.key === "ArrowRight"){ e.preventDefault(); setRatio(which, cur + step); }
      else if (e.key === "Home")      { e.preventDefault(); setRatio(which, MIN[which]); }
      else if (e.key === "End")       { e.preventDefault(); setRatio(which, MAX[which]); }
    });
  }

  /* ---------- 반응형: 1024px 미만은 탭 ---------- */
  var tabs = document.getElementById("paneTabs");
  var mq = window.matchMedia("(max-width: 1023px)");

  function syncMode() {
    if (mq.matches) {
      splitEl.classList.add("is-tabs");
      tabs.hidden = false;
      splitEl.style.removeProperty("--col-a");
      splitEl.style.removeProperty("--col-b");
      showPane(tabs.querySelector(".is-active").dataset.pane);
    } else {
      splitEl.classList.remove("is-tabs");
      tabs.hidden = true;
      ["paneList", "paneDesc", "paneCode"].forEach(function (id) {
        document.getElementById(id).classList.add("is-active");
      });
      applyRatios();
    }
  }

  function showPane(id) {
    ["paneList", "paneDesc", "paneCode"].forEach(function (p) {
      document.getElementById(p).classList.toggle("is-active", p === id);
    });
    Array.prototype.forEach.call(tabs.children, function (b) {
      var on = b.dataset.pane === id;
      b.classList.toggle("is-active", on);
      b.setAttribute("aria-current", on ? "true" : "false");
    });
  }

  tabs.addEventListener("click", function (e) {
    var b = e.target.closest("[data-pane]");
    if (b) showPane(b.dataset.pane);
  });
  mq.addEventListener("change", syncMode);

  /* ---------- 문제 렌더 ---------- */
  function renderList() {
    listEl.innerHTML = D.questions.map(function (x) {
      var solved = state.workspace.solved[x.id];
      return '<button class="q-btn" type="button" role="listitem" data-q="' + x.id + '"' +
             ' aria-pressed="' + (x.id === current) + '">' +
               '<span class="q-row">' +
                 '<span class="q-name">' + x.no + ". " + E.esc(x.title) + "</span>" +
                 (solved ? '<span class="state-badge ok">통과</span>' : '<span class="state-badge">미제출</span>') +
               "</span>" +
               '<span class="q-meta">' + x.points + "점 · 테스트 " + x.tests.length + "개</span>" +
             "</button>";
    }).join("");

    var done = Object.keys(state.workspace.solved).length;
    solvedEl.textContent = String(done);
    var totalEl = document.getElementById("totalCount");
    if (totalEl) totalEl.textContent = String(D.questions.length);
  }

  function renderBody() {
    var x = q(current);
    bodyEl.innerHTML = '' +
      '<p class="q-meta">문제 ' + x.no + " · " + x.points + "점</p>" +
      "<h3>" + E.esc(x.title) + "</h3>" +
      x.body.map(function (p) { return "<p>" + E.esc(p) + "</p>"; }).join("") +
      "<h3>제약</h3><ul>" +
        x.constraints.map(function (c) { return "<li>" + E.esc(c) + "</li>"; }).join("") +
      "</ul><h3>예시</h3>" +
      x.samples.map(function (s) {
        return '<pre class="console-box">입력  ' + E.esc(s.input) + "\n출력  " + E.esc(s.output) + "</pre>";
      }).join("");
  }

  function renderCases(results) {
    var x = q(current);
    casesEl.innerHTML = x.tests.map(function (t, i) {
      var r = results ? results[i] : null;
      var label = r ? (r.ok ? "통과" : "실패") : "미실행";
      var badgeCls = r ? (r.ok ? "ok" : "risk") : "";
      return '<div class="testcase" data-result="' + (r ? (r.ok ? "pass" : "fail") : "none") + '">' +
        '<div class="tc-head"><span>' + E.esc(t.name) + '</span>' +
          '<span class="state-badge ' + badgeCls + '">' + label + (r ? " · " + r.ms + "ms" : "") + "</span></div>" +
        '<div class="tc-body">' +
          "<div><b>입력</b> " + E.esc(t.input) + "</div>" +
          "<div><b>기대</b> " + E.esc(t.expected) + "</div>" +
          (r && !r.ok ? "<div><b>실제</b> " + E.esc(r.actual) + "</div>" : "") +
        "</div></div>";
    }).join("");
  }

  /* ---------- 편집기 ---------- */
  function syncGutter() {
    var lines = codeArea.value.split("\n").length;
    var out = [];
    for (var i = 1; i <= lines; i++) out.push(i);
    gutter.textContent = out.join("\n");
    gutter.scrollTop = codeArea.scrollTop;
  }

  function loadCode() {
    codeArea.value = currentCode();
    syncGutter();
  }

  /* 편집기 내용을 현재 (문제 × 언어) 칸에 저장한다.
     화면 안의 state 와 sessionStorage 두 곳을 함께 갱신해야
     문제·언어를 오갔다 돌아왔을 때 내용이 남는다.

     ⚠ value 를 코드로 직접 바꾸는 곳(Tab 들여쓰기 등)은 input 이벤트가 발생하지
        않는다. 그런 자리에서는 이 함수를 반드시 직접 불러야 한다. */
  function saveCurrentCode() {
    var key = codeKey(current, lang);
    var value = codeArea.value;
    state.workspace.code[key] = value;
    E.patch(function (s) { s.workspace.code[key] = value; });
  }

  codeArea.addEventListener("input", function () {
    syncGutter();
    saveCurrentCode();
  });
  codeArea.addEventListener("scroll", function () { gutter.scrollTop = codeArea.scrollTop; });

  /* Tab 키가 포커스를 옮기지 않고 들여쓰기가 되게 한다.
     Escape 를 누르면 다시 Tab 으로 빠져나갈 수 있어 키보드 트랩이 되지 않는다. */
  var trapTab = true;
  codeArea.addEventListener("keydown", function (e) {
    if (e.key === "Escape") { trapTab = false; E.toast("Tab 으로 편집기를 빠져나갈 수 있습니다"); return; }
    if (e.key !== "Tab" || !trapTab) return;
    e.preventDefault();
    var s = codeArea.selectionStart, t = codeArea.selectionEnd;
    codeArea.value = codeArea.value.slice(0, s) + "    " + codeArea.value.slice(t);
    codeArea.selectionStart = codeArea.selectionEnd = s + 4;
    syncGutter();
    saveCurrentCode();   /* 스크립트로 바꾼 값이라 input 이벤트가 안 온다 */
  });
  codeArea.addEventListener("focus", function () { trapTab = true; });

  /* ---------- 언어 전환 ---------- */
  langSel.addEventListener("change", function () {
    var next = langSel.value;
    var key = codeKey(current, lang);
    var edited = state.workspace.code[key] != null && state.workspace.code[key] !== starter(current, lang);
    if (edited && !window.confirm("작성한 코드는 언어별로 따로 보관됩니다. " + (D.starterCode[next].label) + " 로 전환할까요?")) {
      langSel.value = lang;
      return;
    }
    lang = next;
    E.patch(function (s) { s.workspace.lang = lang; });
    state.workspace.lang = lang;
    loadCode();
    renderCases(null);
    consoleEl.textContent = D.starterCode[lang].label + " 로 전환했습니다. 실행 버튼을 누르면 결과가 표시됩니다.";
  });

  document.getElementById("resetCode").addEventListener("click", function () {
    if (!window.confirm("현재 문제의 코드를 초기 상태로 되돌릴까요?")) return;
    delete state.workspace.code[codeKey(current, lang)];
    E.patch(function (s) { delete s.workspace.code[codeKey(current, lang)]; });
    loadCode();
    E.toast("초기 코드로 되돌렸습니다");
  });

  /* ---------- 실행 시뮬레이션 ---------- */
  var running = false;
  document.getElementById("runBtn").addEventListener("click", function () {
    if (running) return;
    running = true;
    var btn = this;
    btn.disabled = true;
    btn.textContent = "실행 중…";
    consoleEl.textContent = "[시뮬레이션] 컨테이너 프로비저닝…\n[시뮬레이션] " + D.starterCode[lang].label + " 실행";
    renderCases(null);

    window.setTimeout(function () {
      var mode = scenSel.value;
      var x = q(current);

      if (mode === "compile") {
        consoleEl.textContent =
          "[시뮬레이션] 컴파일 오류\n" +
          (lang === "java"
            ? "Solution.java:5: error: cannot find symbol\n        return reslt;\n               ^\n  symbol: variable reslt"
            : lang === "python"
              ? "  File \"solution.py\", line 4\n    return valuess\n           ^\nNameError: name 'valuess' is not defined"
              : "ReferenceError: valuess is not defined\n    at solve (solution.js:4:10)");
        renderCases(null);
        E.toast("컴파일 오류 (데모)", "risk");
      } else {
        var results = x.tests.map(function (t, i) {
          var ok = mode === "pass" ? true : i < x.tests.length - 1;
          return { ok: ok, ms: 40 + i * 17, actual: ok ? t.expected : "[]" };
        });
        renderCases(results);
        var passed = results.filter(function (r) { return r.ok; }).length;
        consoleEl.textContent =
          "[시뮬레이션] 실행 완료\n테스트 " + passed + "/" + results.length + " 통과\n" +
          "※ 실제 서버 실행 결과가 아닙니다.";

        if (passed === results.length) {
          state.workspace.solved[current] = true;
          E.patch(function (s) { s.workspace.solved[current] = true; });
          E.toast("전체 테스트 통과 (데모)", "ok");
        } else {
          delete state.workspace.solved[current];
          E.patch(function (s) { delete s.workspace.solved[current]; });
          E.toast("일부 테스트 실패 (데모)", "warn");
        }
        renderList();
      }

      btn.disabled = false;
      btn.textContent = "실행";
      running = false;
    }, 900);
  });

  /* ---------- 남은 시간 ----------
     setInterval 누적 오차와 탭 비활성화 영향을 피하려고 매 틱마다 deadline 기준으로 재계산한다. */
  var warned5 = false, warned1 = false, ended = false;

  function tick() {
    var left = deadline - Date.now();
    clockEl.textContent = E.fmtClock(left);

    if (left <= 60000) clockEl.dataset.warn = "urgent";
    else if (left <= 300000) clockEl.dataset.warn = "soon";
    else clockEl.dataset.warn = "none";

    if (left <= 300000 && !warned5) {
      warned5 = true;
      clockAlert.textContent = "남은 시간 5분";
      E.toast("남은 시간 5분", "warn");
    }
    if (left <= 60000 && !warned1) {
      warned1 = true;
      clockAlert.textContent = "남은 시간 1분";
      E.toast("남은 시간 1분", "risk");
    }
    if (left <= 0 && !ended) {
      ended = true;
      finish(true);
    }
  }

  /* ---------- 제출 ---------- */
  var submitModal = document.getElementById("submitModal");
  var timeupModal = document.getElementById("timeupModal");

  document.getElementById("submitBtn").addEventListener("click", function () {
    var done = Object.keys(state.workspace.solved).length;
    var left = D.questions.length - done;
    document.getElementById("submitSummary").textContent =
      left > 0 ? "아직 통과하지 못한 문제가 " + left + "개 있습니다." : "모든 문제가 통과되었습니다.";
    E.openModal(submitModal, this);
  });

  document.getElementById("submitConfirm").addEventListener("click", function () {
    E.closeModal(submitModal);
    finish(false);
  });

  function finish(auto) {
    E.patch(function (s) { s.workspace.submitted = true; });
    if (auto) {
      E.openModal(timeupModal);
    } else {
      E.toast("답안을 제출했습니다 (데모)", "ok");
      window.setTimeout(function () { window.location.href = "/v2/lxp/trainee/exams.html"; }, 700);
    }
  }

  /* ---------- 초기화 ---------- */
  listEl.addEventListener("click", function (e) {
    var b = e.target.closest("[data-q]");
    if (!b) return;
    current = b.dataset.q;
    E.patch(function (s) { s.workspace.questionId = current; });
    renderList(); renderBody(); loadCode(); renderCases(null);
    consoleEl.textContent = "실행 버튼을 누르면 결과가 표시됩니다.";
    if (mq.matches) showPane("paneDesc");
  });

  langSel.value = lang;
  bindSeparator("sepA", "a");
  bindSeparator("sepB", "b");
  renderList(); renderBody(); loadCode(); renderCases(null);
  syncMode();
  tick();
  window.setInterval(tick, 1000);
})();
