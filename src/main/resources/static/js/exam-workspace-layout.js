/* =============================================================
   LXP-009 맞춤형 응시 레이아웃

   기존 do-test.html 의 답안 저장·타이머·제출·문항 이동 로직을 <b>건드리지 않는다.</b>
   이 파일은 CSS 변수(--pane-nav, --code-editor)만 조절한다. DOM 을 다시 그리지 않으므로
   조절 중에 textarea 가 재생성되거나 입력이 날아가는 일이 없다.

   window.ExamLayout.register() 로 코딩 작업공간이 자기 separator 를 추가할 수 있다.
   ============================================================= */
(function () {
  "use strict";

  /* localStorage 키에 버전을 넣는다. 나중에 패널 구성이 바뀌면 v2 로 올리면
     예전 값이 조용히 무시되고 기본값으로 시작한다. */
  var STORE_KEY = "lxp.exam.layout.v1";

  var STEP = 2;        /* 방향키 */
  var BIG_STEP = 10;   /* Shift + 방향키 */

  /* 좁은 화면에서는 비율 조절을 끈다. CSS 의 1023px 분기와 반드시 같은 값이어야 한다. */
  var NARROW = 1023;

  var panes = [];      /* 등록된 separator 정의 */
  var saved = load();

  /* ---------- 저장소 ---------- */

  function load() {
    try {
      var raw = window.localStorage.getItem(STORE_KEY);
      if (!raw) return {};
      var parsed = JSON.parse(raw);
      /* 객체가 아니면(배열·문자열·null) 버린다. 손상된 값으로 화면이 깨지지 않게. */
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return {};
      return parsed;
    } catch (e) {
      /* JSON 이 깨졌거나 스토리지가 막혔다(사파리 프라이빗 등). 기본값으로 간다. */
      return {};
    }
  }

  function persist() {
    try {
      window.localStorage.setItem(STORE_KEY, JSON.stringify(saved));
    } catch (e) {
      /* 저장 실패는 기능을 막지 않는다 — 이번 세션 동안만 유지된다. */
    }
  }

  /** 저장된 값을 읽되, 숫자가 아니거나 범위를 벗어나면 기본값으로 복구한다. */
  function readPct(def) {
    return function (key, min, max) {
      var v = saved[key];
      if (typeof v !== "number" || !isFinite(v)) return def;
      if (v < min || v > max) return def;
      return v;
    };
  }

  function clamp(v, min, max) {
    return v < min ? min : (v > max ? max : v);
  }

  /* ---------- separator 등록 ---------- */

  /**
   * @param {Object} opt
   *   key       저장 키 (문자열)
   *   root      비율을 적용할 컨테이너 (CSS 변수를 여기에 쓴다)
   *   sep       separator 요소
   *   cssVar    조절할 CSS 변수명 (예: "--pane-nav")
   *   axis      "vertical"(좌우 폭) | "horizontal"(위아래 높이)
   *   min,max   허용 퍼센트 범위 — 패널 최소 크기를 보장한다
   *   def       기본 퍼센트
   *   label     접근성 이름
   */
  function register(opt) {
    if (!opt || !opt.root || !opt.sep) return null;

    var min = typeof opt.min === "number" ? opt.min : 15;
    var max = typeof opt.max === "number" ? opt.max : 70;
    var def = clamp(typeof opt.def === "number" ? opt.def : 25, min, max);

    var pane = {
      key: opt.key,
      root: opt.root,
      sep: opt.sep,
      cssVar: opt.cssVar,
      axis: opt.axis === "horizontal" ? "horizontal" : "vertical",
      min: min,
      max: max,
      def: def,
      value: readPct(def)(opt.key, min, max),
      released: false
    };

    /* ARIA — 방향키로 조절하는 컨트롤이므로 역할과 현재값을 반드시 노출한다. */
    var sep = pane.sep;
    sep.setAttribute("role", "separator");
    sep.setAttribute("aria-orientation", pane.axis);
    sep.setAttribute("aria-valuemin", String(min));
    sep.setAttribute("aria-valuemax", String(max));
    sep.setAttribute("tabindex", "0");
    if (opt.label) sep.setAttribute("aria-label", opt.label);

    apply(pane);
    bind(pane);
    panes.push(pane);
    return pane;
  }

  function apply(pane) {
    /* 0px·음수·가로 넘침이 생기지 않도록 항상 clamp 한 값만 쓴다. */
    var v = clamp(pane.value, pane.min, pane.max);
    pane.value = v;
    pane.root.style.setProperty(pane.cssVar, v + "%");
    pane.sep.setAttribute("aria-valuenow", String(Math.round(v)));
    pane.sep.setAttribute("aria-valuetext", Math.round(v) + "%");
  }

  function set(pane, next, store) {
    pane.value = clamp(next, pane.min, pane.max);
    apply(pane);
    if (store !== false) {
      saved[pane.key] = Math.round(pane.value * 10) / 10;
      persist();
    }
  }

  /* ---------- 포인터 드래그 ---------- */

  function bind(pane) {
    var sep = pane.sep;
    var dragging = false;

    sep.addEventListener("pointerdown", function (e) {
      if (pane.released) return;                 /* 해제된 pane 은 조작하지 않는다 */
      if (window.innerWidth <= NARROW) return;   /* 좁은 화면에서는 조절 안 함 */
      if (e.button !== 0 && e.pointerType === "mouse") return;

      dragging = true;
      sep.dataset.dragging = "true";
      document.body.dataset.paneDragging = pane.axis === "horizontal" ? "row" : "true";

      /* Pointer Capture — 포인터가 창 밖으로 나가도 이 요소가 계속 이벤트를 받는다.
         이게 없으면 빠르게 끌었을 때 드래그가 중간에 끊긴다. */
      try {
        sep.setPointerCapture(e.pointerId);
      } catch (err) {
        /* 미지원 브라우저는 아래 pointermove 로 폴백된다. */
      }
      e.preventDefault();
    });

    sep.addEventListener("pointermove", function (e) {
      if (!dragging) return;
      var rect = pane.root.getBoundingClientRect();
      var pct;
      if (pane.axis === "horizontal") {
        if (rect.height <= 0) return;
        pct = ((e.clientY - rect.top) / rect.height) * 100;
      } else {
        if (rect.width <= 0) return;
        pct = ((e.clientX - rect.left) / rect.width) * 100;
      }
      set(pane, pct, false);   /* 드래그 중에는 매 프레임 저장하지 않는다 */
      e.preventDefault();
    });

    function stop(e) {
      if (!dragging) return;
      dragging = false;
      delete sep.dataset.dragging;
      delete document.body.dataset.paneDragging;
      try {
        if (e && e.pointerId != null) sep.releasePointerCapture(e.pointerId);
      } catch (err) { /* 이미 해제됨 */ }
      set(pane, pane.value, true);   /* 끝났을 때 한 번 저장 */
    }

    sep.addEventListener("pointerup", stop);
    sep.addEventListener("pointercancel", stop);
    /* 캡처를 잃는 경우(다른 요소가 가져감)도 정리한다. */
    sep.addEventListener("lostpointercapture", stop);

    /* ---------- 키보드 ---------- */

    sep.addEventListener("keydown", function (e) {
      if (pane.released) return;
      if (window.innerWidth <= NARROW) return;

      var step = e.shiftKey ? BIG_STEP : STEP;
      var dir = 0;

      if (pane.axis === "horizontal") {
        if (e.key === "ArrowUp") dir = -1;
        else if (e.key === "ArrowDown") dir = 1;
      } else {
        if (e.key === "ArrowLeft") dir = -1;
        else if (e.key === "ArrowRight") dir = 1;
      }

      if (dir !== 0) {
        set(pane, pane.value + dir * step);
        e.preventDefault();
        return;
      }
      if (e.key === "Home") { set(pane, pane.min); e.preventDefault(); return; }
      if (e.key === "End")  { set(pane, pane.max); e.preventDefault(); return; }
      /* Enter 는 기본 비율로 되돌린다 — 마우스 없이도 복구할 수 있어야 한다. */
      if (e.key === "Enter") { set(pane, pane.def); e.preventDefault(); }
    });
  }

  /**
   * 등록을 <b>해제</b>한다 (P1-2).
   *
   * <p>코딩 문항을 다시 열 때마다 새 separator 가 등록되는데, 해제하지 않으면
   * 분리된 DOM 을 가리키는 항목이 전역 배열에 계속 쌓인다. 그러면 reset()·resize 가
   * 이미 화면에서 사라진 pane 까지 순회하며 죽은 노드에 스타일을 쓴다.</p>
   *
   * <p>이미 해제된 pane 을 다시 넘겨도 오류가 나지 않는다(멱등).</p>
   *
   * @returns {boolean} 실제로 제거했으면 true
   */
  function unregister(pane) {
    if (!pane) return false;
    var i = panes.indexOf(pane);
    if (i < 0) {
      /* 이미 빠졌거나 이 모듈이 등록한 것이 아니다 — 조용히 넘어간다. */
      pane.released = true;
      return false;
    }
    panes.splice(i, 1);
    pane.released = true;
    /* 리스너는 separator 요소와 함께 사라진다(DOM 이 교체되므로).
       다만 남아 있는 경우에도 조작되지 않도록 표시를 지운다. */
    if (pane.sep) {
      delete pane.sep.dataset.dragging;
      pane.sep.removeAttribute("tabindex");
    }
    return true;
  }

  /**
   * 현재 <b>살아 있는</b> 패널만 기본 비율로 되돌리고 저장값을 지운다.
   * 해제된 pane 은 panes 에 없으므로 순회 대상이 아니다.
   */
  function reset() {
    panes.forEach(function (p) {
      p.value = p.def;
      apply(p);
      delete saved[p.key];
    });
    persist();
  }

  /* ---------- 좁은 화면 탭 전환 ---------- */

  /**
   * 1023px 이하에서 두 패널을 탭으로 전환한다.
   * separator 가 숨겨지는 폭에서는 비율 조절 대신 이 UI 를 쓴다.
   */
  function setupTabs(root, tabsHost, items) {
    if (!root || !tabsHost || !items || !items.length) return;

    var buttons = items.map(function (it, i) {
      var b = document.createElement("button");
      b.type = "button";
      b.textContent = it.label;
      b.setAttribute("role", "tab");
      b.setAttribute("aria-selected", i === 0 ? "true" : "false");
      b.dataset.view = it.view;
      tabsHost.appendChild(b);
      return b;
    });

    tabsHost.setAttribute("role", "tablist");
    tabsHost.setAttribute("aria-label", "응시 화면 영역 전환");

    function select(view) {
      root.dataset.paneView = view;
      buttons.forEach(function (b) {
        b.setAttribute("aria-selected", b.dataset.view === view ? "true" : "false");
      });
    }

    tabsHost.addEventListener("click", function (e) {
      var b = e.target.closest("button[data-view]");
      if (b) select(b.dataset.view);
    });

    /* 좌우 방향키로도 탭을 옮길 수 있어야 한다. */
    tabsHost.addEventListener("keydown", function (e) {
      if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
      var cur = buttons.findIndex(function (b) { return b.getAttribute("aria-selected") === "true"; });
      if (cur < 0) return;
      var next = (cur + (e.key === "ArrowRight" ? 1 : buttons.length - 1)) % buttons.length;
      select(buttons[next].dataset.view);
      buttons[next].focus();
      e.preventDefault();
    });

    select(items[0].view);
  }

  /* 폭이 바뀌면 aria 값을 다시 맞춘다(넓은 화면으로 돌아왔을 때 값이 남아 있어야 한다).
     해제된 pane 은 panes 에서 빠졌으므로 순회 대상이 아니다. */
  if (typeof window !== "undefined") {
    window.addEventListener("resize", function () {
      panes.forEach(apply);
    });
  }

  var api = {
    register: register,
    unregister: unregister,
    reset: reset,
    setupTabs: setupTabs,
    NARROW: NARROW,
    /** 테스트·디버그용 — 현재 비율을 읽는다. */
    values: function () {
      var out = {};
      panes.forEach(function (p) { out[p.key] = p.value; });
      return out;
    },
    /** 살아 있는 pane 개수. 누적 여부를 밖에서 확인할 수 있어야 한다. */
    paneCount: function () { return panes.length; }
  };

  if (typeof window !== "undefined") {
    window.ExamLayout = api;
  }
  /* node 로 불러와 등록·해제 계약을 실제로 실행해 검증한다. */
  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
})();
