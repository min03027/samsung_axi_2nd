/* =============================================================
   LXP-010 개발환경 유사 코딩 작업공간

   기존 답안 저장 계약을 그대로 쓴다 — 코드는 `answerText` 로 저장된다.
   서버 DTO(AttemptQuestionRow)를 바꾸지 않았으므로 코드/언어를 담을 새 필드는 없다.

   <b>서버가 내려주지 않는 것을 만들어내지 않는다.</b>
   AttemptQuestionRow 에는 questionId·type·text·choices·savedChoiceId·savedText 뿐이다.
   테스트케이스·정답·기대 출력이 없으므로 그 탭은 "연동 필요" 를 표시한다.
   실행 샌드박스도 없으므로 가짜 실행 결과를 만들지 않는다.
   ============================================================= */
(function () {
  "use strict";

  var LANG_KEY = "lxp.exam.coding.lang.v1";

  var LANGS = [
    { id: "javascript", label: "JavaScript", comment: "//", indent: 2 },
    { id: "python", label: "Python", comment: "#", indent: 4 },
    { id: "java", label: "Java", comment: "//", indent: 4 }
  ];

  function langById(id) {
    for (var i = 0; i < LANGS.length; i++) {
      if (LANGS[i].id === id) return LANGS[i];
    }
    return LANGS[0];
  }

  function loadLang() {
    try {
      var v = window.localStorage.getItem(LANG_KEY);
      /* 저장된 값이 목록에 없으면(구버전·손상) 기본값으로 복구한다. */
      return v && langById(v).id === v ? v : LANGS[0].id;
    } catch (e) {
      return LANGS[0].id;
    }
  }

  function saveLang(id) {
    try {
      window.localStorage.setItem(LANG_KEY, id);
    } catch (e) { /* 저장 실패는 편집을 막지 않는다 */ }
  }

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  /**
   * 코딩 작업공간을 그린다.
   *
   * @param {HTMLElement} host     답안 영역 (비워진 상태로 들어온다)
   * @param {Object} q             AttemptQuestionRow
   * @param {Object} ctx           { onSave(text), onDirty() }
   */
  function render(host, q, ctx) {
    ctx = ctx || {};
    var lang = langById(loadLang());

    var ws = el("div", "code-ws");
    ws.setAttribute("data-code-ws", "1");

    /* ---------- 상단 바 ---------- */
    var bar = el("div", "code-ws__bar");

    var langLabel = el("label", null, "언어");
    var langSelId = "codeLang-" + q.questionId;
    langLabel.setAttribute("for", langSelId);

    var sel = document.createElement("select");
    sel.id = langSelId;
    LANGS.forEach(function (l) {
      var o = document.createElement("option");
      o.value = l.id;
      o.textContent = l.label;
      if (l.id === lang.id) o.selected = true;
      sel.appendChild(o);
    });

    var runBtn = el("button", "btn btn-gray", "구문 검사 실행");
    runBtn.type = "button";

    /* 항상 보이는 배지. 실제 실행이 아니라는 사실을 숨기지 않는다. */
    var badge = el("span", "sim-badge", "로컬 시뮬레이션");
    badge.title = "브라우저에서만 확인합니다. 서버 컴파일·실행·채점은 하지 않습니다.";

    bar.appendChild(langLabel);
    bar.appendChild(sel);
    bar.appendChild(runBtn);
    bar.appendChild(badge);
    ws.appendChild(bar);

    /* ---------- 편집기 ---------- */
    var editor = el("div", "code-editor");
    var gutter = el("pre", "code-gutter");
    gutter.setAttribute("aria-hidden", "true");   /* 줄번호는 스크린리더에 읽히지 않게 */

    var input = document.createElement("textarea");
    input.className = "code-input";
    input.id = "answerText";      /* 기존 코드가 이 id 로 답안을 찾는다 — 계약 유지 */
    input.setAttribute("spellcheck", "false");
    input.setAttribute("autocapitalize", "off");
    input.setAttribute("autocomplete", "off");
    input.setAttribute("wrap", "off");
    input.setAttribute("aria-label", "코드 편집기");
    /* Tab 동작이 바뀌므로 안내한다 — 키보드 사용자가 갇히지 않아야 한다. */
    input.setAttribute("aria-describedby", "codeKeyHelp");
    input.value = q.savedText || "";   /* 서버 savedText 우선 복원 */

    editor.appendChild(gutter);
    editor.appendChild(input);
    ws.appendChild(editor);

    /* ---------- 구분선 (편집기 / 아래 패널) ---------- */
    var sep = document.createElement("button");
    sep.type = "button";
    sep.className = "pane-sep";
    ws.appendChild(sep);

    /* ---------- 아래 패널 ---------- */
    var panel = el("div", "code-panel");
    var tabs = el("div", "code-panel__tabs");
    tabs.setAttribute("role", "tablist");
    tabs.setAttribute("aria-label", "코드 실행 정보");

    var bodies = {};
    var TABS = [
      { id: "tc", label: "테스트케이스" },
      { id: "run", label: "실행 결과" },
      { id: "console", label: "콘솔" }
    ];

    var tabBtns = TABS.map(function (t, i) {
      var b = el("button", null, t.label);
      b.type = "button";
      b.setAttribute("role", "tab");
      b.id = "codeTab-" + t.id + "-" + q.questionId;
      b.setAttribute("aria-selected", i === 0 ? "true" : "false");
      b.dataset.tab = t.id;
      tabs.appendChild(b);

      var body = el("div", "code-panel__body");
      body.setAttribute("role", "tabpanel");
      body.setAttribute("aria-labelledby", b.id);
      if (i !== 0) body.hidden = true;
      bodies[t.id] = body;
      return b;
    });

    panel.appendChild(tabs);
    TABS.forEach(function (t) { panel.appendChild(bodies[t.id]); });
    ws.appendChild(panel);

    /* ---------- 탭 내용 ---------- */

    /* 테스트케이스 — 서버가 주지 않으므로 만들어내지 않는다. */
    var tcNote = el("div", "code-note");
    tcNote.appendChild(el("strong", null, "테스트케이스가 연동되지 않았습니다. "));
    tcNote.appendChild(document.createTextNode(
      "현재 서버 응답(AttemptQuestionRow)에는 문항 지문과 저장된 답안만 있고 " +
      "테스트케이스·기대 출력 필드가 없습니다. 예시를 임의로 만들면 실제 채점 기준과 " +
      "달라지므로 표시하지 않습니다. 채점 기준은 문항 지문을 확인해 주세요."
    ));
    bodies.tc.appendChild(tcNote);

    /* 실행 결과 */
    var runNote = el("div", "code-note");
    runNote.appendChild(el("strong", null, "실행 샌드박스가 없습니다. "));
    runNote.appendChild(document.createTextNode(
      "이 화면의 «구문 검사 실행» 은 브라우저에서 JavaScript 구문만 확인합니다. " +
      "코드를 실제로 컴파일·실행하거나 채점하지 않으며, Python·Java 는 브라우저에서 검사할 수 없습니다. " +
      "실제 실행은 서버 실행 샌드박스 연동이 필요합니다."
    ));
    bodies.run.appendChild(runNote);
    var runOut = el("pre", "code-console", "아직 실행하지 않았습니다.");
    bodies.run.appendChild(runOut);

    /* 콘솔 */
    var consoleOut = el("pre", "code-console", "");
    bodies.console.appendChild(consoleOut);

    /* 키보드 안내 (aria-describedby 대상) */
    var keyHelp = el("p", "integrity-limits");
    keyHelp.id = "codeKeyHelp";
    keyHelp.textContent =
      "Tab 키는 들여쓰기를 넣습니다. 편집기에서 빠져나가려면 Esc 를 누른 뒤 Tab 을 누르세요.";
    ws.appendChild(keyHelp);

    host.appendChild(ws);

    /* ---------- 줄번호 ---------- */

    function renderGutter() {
      /* 줄 수만 세어 다시 그린다. 코드 내용을 gutter 에 넣지 않는다. */
      var lines = input.value.split("\n").length;
      var buf = [];
      for (var i = 1; i <= lines; i++) buf.push(i);
      gutter.textContent = buf.join("\n");
    }

    /* 편집기 스크롤과 gutter 를 같이 움직인다. */
    input.addEventListener("scroll", function () {
      gutter.scrollTop = input.scrollTop;
    });

    /* ---------- 저장 (기존 answerText 계약) ---------- */

    var timer = null;

    function scheduleSave() {
      if (typeof ctx.onDirty === "function") ctx.onDirty();
      clearTimeout(timer);
      timer = setTimeout(function () {
        if (typeof ctx.onSave === "function") ctx.onSave(input.value);
      }, 800);
    }

    input.addEventListener("input", function () {
      renderGutter();
      scheduleSave();
    });

    input.addEventListener("blur", function () {
      clearTimeout(timer);
      if (typeof ctx.onSave === "function") ctx.onSave(input.value);
    });

    /* ---------- Tab 들여쓰기 ---------- */

    var escaped = false;

    input.addEventListener("keydown", function (e) {
      if (e.key === "Escape") {
        /* 한 번 Esc 를 누르면 다음 Tab 은 포커스 이동으로 넘긴다 — 키보드 트랩 방지. */
        escaped = true;
        return;
      }
      if (e.key !== "Tab") {
        escaped = false;
        return;
      }
      if (escaped) {
        escaped = false;
        return;    /* 기본 동작: 다음 요소로 포커스 이동 */
      }

      e.preventDefault();
      var pad = new Array(langById(sel.value).indent + 1).join(" ");
      var start = input.selectionStart;
      var end = input.selectionEnd;

      if (e.shiftKey) {
        /* Shift+Tab — 커서 앞의 들여쓰기를 한 단계 줄인다. */
        var before = input.value.slice(0, start);
        if (before.endsWith(pad)) {
          input.value = before.slice(0, -pad.length) + input.value.slice(end);
          input.selectionStart = input.selectionEnd = start - pad.length;
        }
      } else {
        input.value = input.value.slice(0, start) + pad + input.value.slice(end);
        input.selectionStart = input.selectionEnd = start + pad.length;
      }
      renderGutter();
      scheduleSave();
    });

    /* ---------- 언어 선택 ---------- */

    sel.addEventListener("change", function () {
      saveLang(sel.value);
      log("언어를 " + langById(sel.value).label + " 로 바꿨습니다. "
          + "언어 선택은 이 브라우저에만 저장되며 답안 코드에는 영향을 주지 않습니다.");
    });

    /* ---------- 탭 전환 ---------- */

    function selectTab(id) {
      tabBtns.forEach(function (b) {
        var on = b.dataset.tab === id;
        b.setAttribute("aria-selected", on ? "true" : "false");
        bodies[b.dataset.tab].hidden = !on;
      });
    }

    tabs.addEventListener("click", function (e) {
      var b = e.target.closest("button[data-tab]");
      if (b) selectTab(b.dataset.tab);
    });

    tabs.addEventListener("keydown", function (e) {
      if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
      var cur = tabBtns.findIndex(function (b) { return b.getAttribute("aria-selected") === "true"; });
      if (cur < 0) return;
      var next = (cur + (e.key === "ArrowRight" ? 1 : tabBtns.length - 1)) % tabBtns.length;
      selectTab(tabBtns[next].dataset.tab);
      tabBtns[next].focus();
      e.preventDefault();
    });

    /* ---------- 콘솔 ---------- */

    function log(msg) {
      var line = "[" + new Date().toLocaleTimeString() + "] " + msg;
      consoleOut.textContent = consoleOut.textContent
        ? consoleOut.textContent + "\n" + line
        : line;
      consoleOut.scrollTop = consoleOut.scrollHeight;
    }

    /* ---------- 구문 검사 ---------- */

    runBtn.addEventListener("click", function () {
      selectTab("run");
      var chosen = langById(sel.value);

      if (chosen.id !== "javascript") {
        runOut.textContent =
          chosen.label + " 는 브라우저에서 구문을 검사할 수 없습니다.\n" +
          "실제 컴파일·실행·채점은 서버 실행 샌드박스 연동이 필요합니다.";
        log(chosen.label + " 구문 검사는 브라우저에서 지원되지 않습니다.");
        return;
      }

      /* JavaScript 만: new Function 은 <b>파싱만</b> 한다 — 호출하지 않으므로 코드가 실행되지 않는다.
         구문 오류를 실제로 잡아 주므로 가짜 결과가 아니다. */
      var code = input.value;
      if (!code.trim()) {
        runOut.textContent = "코드가 비어 있습니다.";
        log("빈 코드입니다.");
        return;
      }
      try {
        /* eslint-disable-next-line no-new-func */
        new Function(code);
        runOut.textContent =
          "JavaScript 구문 오류가 발견되지 않았습니다.\n\n" +
          "확인한 것: 구문(파싱)뿐입니다.\n" +
          "확인하지 않은 것: 실행 결과, 정답 여부, 성능, 테스트케이스 통과.\n" +
          "실제 실행·채점은 서버 실행 샌드박스 연동이 필요합니다.";
        log("구문 검사 통과 (실행하지 않았습니다).");
      } catch (err) {
        runOut.textContent =
          "JavaScript 구문 오류\n\n" + String(err && err.message ? err.message : err);
        log("구문 오류: " + String(err && err.message ? err.message : err));
      }
    });

    /* ---------- 초기화 ---------- */

    renderGutter();
    log("코드 작업공간을 열었습니다. 코드는 기존 답안 저장 방식으로 자동 저장됩니다.");

    /* 편집기 / 아래 패널 비율 조절 등록. 레이아웃 모듈이 없으면 조용히 넘어간다. */
    var layoutPane = null;
    if (window.ExamLayout && typeof window.ExamLayout.register === "function") {
      layoutPane = window.ExamLayout.register({
        key: "codeEditor",
        root: ws,
        sep: sep,
        cssVar: "--code-editor",
        axis: "horizontal",
        min: 25,
        max: 75,
        def: 46,
        label: "코드 편집기와 실행 정보 영역 높이 조절"
      });
    }

    var disposed = false;

    return {
      /**
       * 문항을 떠날 때 정리한다 (P1-2).
       *
       * <p>저장 타이머만 지우면 <b>레이아웃 등록이 남는다.</b> 코딩 문항을 왕복할 때마다
       * 새 separator 가 등록되므로, 해제하지 않으면 분리된 DOM 을 가리키는 항목이
       * 전역 배열에 쌓이고 reset()·resize 가 죽은 노드를 순회한다.</p>
       *
       * <p>여러 번 호출해도 안전하다(멱등).</p>
       */
      dispose: function () {
        if (disposed) return;
        disposed = true;
        clearTimeout(timer);
        if (layoutPane && window.ExamLayout
            && typeof window.ExamLayout.unregister === "function") {
          window.ExamLayout.unregister(layoutPane);
        }
        layoutPane = null;
      },
      /** 현재 편집 중인 코드. dispose 전에 마지막 값을 읽어 저장할 수 있게 한다. */
      value: function () { return input.value; }
    };
  }

  window.ExamCoding = {
    render: render,
    /** do-test.html 이 타입을 검사할 때 쓰는 계약. CODING 문항에만 활성화한다. */
    handles: function (q) { return !!q && q.type === "CODING"; }
  };
})();
