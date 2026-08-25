/* ============================================================
   exam-common.js — 시험 데모 공통 동작

   페이지 사이 상태는 sessionStorage 의 lxpExamDemo 키 하나만 쓴다.
   저장값은 전부 가상 데이터이며 서버로 전송되지 않는다.

   화면 문법은 운영 UI(online-test.css 의 .modal-backdrop, btn-style.css 의 .btn)를 따른다.

   제공: ExamDemo.load/save/patch/reset
         ExamDemo.toast / openModal / closeModal
         ExamDemo.fmtClock / fmtDuration / esc / qs
   ============================================================ */

(function () {
  "use strict";

  var KEY = "lxpExamDemo";

  /* 손상된 JSON 이 들어와도 화면이 죽지 않도록 항상 기본값으로 복구한다. */
  function defaults() {
    return {
      version: 1,
      examId: "frontend-2026",
      checks: { camera: "idle", display: "idle", fullscreen: "idle", multiMonitor: "idle", identity: "idle" },
      multiMonitorSource: "unknown",   // api | demo | unknown
      identity: { status: "미제출", fileName: "", submittedAt: "" },
      entered: false,
      workspace: { lang: "python", questionId: "q1", ratios: [26, 42], code: {}, solved: {}, submitted: false },
      proctor: { selectedId: "c01", scenario: "normal", extraEvents: [] },
      review: {},                      // candidateId -> {status, memo}
      infra: { scenario: "normal", scaleState: "idle", history: [] }
    };
  }

  function load() {
    var raw = null;
    try { raw = window.sessionStorage.getItem(KEY); } catch (e) { return defaults(); }
    if (!raw) return defaults();
    var parsed;
    try { parsed = JSON.parse(raw); } catch (e) { return defaults(); }
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return defaults();
    /* 누락 키는 기본값으로 메운다 — 이전 버전 상태가 남아 있어도 안전하다. */
    var base = defaults();
    Object.keys(base).forEach(function (k) {
      if (parsed[k] == null) parsed[k] = base[k];
      else if (typeof base[k] === "object" && !Array.isArray(base[k])) {
        Object.keys(base[k]).forEach(function (k2) {
          if (parsed[k][k2] == null) parsed[k][k2] = base[k][k2];
        });
      }
    });
    return parsed;
  }

  function save(state) {
    try { window.sessionStorage.setItem(KEY, JSON.stringify(state)); } catch (e) { /* 저장 실패해도 화면은 계속 동작한다 */ }
    return state;
  }

  function patch(fn) {
    var s = load();
    fn(s);
    return save(s);
  }

  function reset() {
    try { window.sessionStorage.removeItem(KEY); } catch (e) { /* noop */ }
    return defaults();
  }

  /* ---------- 토스트 ----------
     오류는 role="alert", 그 외는 role="status" 로 읽힌다. */
  var toastHost = null;
  function toast(message, tone) {
    if (!toastHost) {
      toastHost = document.createElement("div");
      toastHost.className = "toast-host";
      document.body.appendChild(toastHost);
    }
    var t = document.createElement("div");
    t.className = "toast" + (tone ? " " + tone : "");
    t.setAttribute("role", tone === "risk" ? "alert" : "status");
    t.textContent = message;
    toastHost.appendChild(t);
    window.setTimeout(function () {
      t.classList.add("is-out");
      window.setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 240);
    }, 3200);
  }

  /* ---------- 모달 ----------
     Escape 닫기 · 바깥 클릭 닫기 · 최초 포커스 이동 · 닫은 뒤 트리거 포커스 복귀 */
  var lastTrigger = null;

  function focusables(root) {
    return Array.prototype.slice.call(root.querySelectorAll(
      'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'
    )).filter(function (el) { return el.offsetParent !== null || el === document.activeElement; });
  }

  function openModal(el, trigger) {
    if (!el) return;
    lastTrigger = trigger || document.activeElement;
    el.hidden = false;
    el.classList.add("open");
    document.body.classList.add("has-modal");

    var f = focusables(el);
    var first = el.querySelector("[data-autofocus]") || f[0];
    if (first) first.focus();

    function onKey(e) {
      if (e.key === "Escape") { e.preventDefault(); closeModal(el); return; }
      if (e.key !== "Tab") return;
      var list = focusables(el);
      if (!list.length) return;
      var a = list[0], z = list[list.length - 1];
      if (e.shiftKey && document.activeElement === a) { e.preventDefault(); z.focus(); }
      else if (!e.shiftKey && document.activeElement === z) { e.preventDefault(); a.focus(); }
    }
    function onClick(e) { if (e.target === el) closeModal(el); }

    el.__onKey = onKey; el.__onClick = onClick;
    document.addEventListener("keydown", onKey);
    el.addEventListener("click", onClick);
  }

  function closeModal(el) {
    if (!el || el.hidden) return;
    el.hidden = true;
    el.classList.remove("open");
    document.body.classList.remove("has-modal");
    if (el.__onKey) document.removeEventListener("keydown", el.__onKey);
    if (el.__onClick) el.removeEventListener("click", el.__onClick);
    el.__onKey = null; el.__onClick = null;
    if (lastTrigger && document.contains(lastTrigger)) lastTrigger.focus();
    lastTrigger = null;
  }

  /* 모든 [data-close-modal] 버튼은 자동으로 닫기 동작을 얻는다. */
  document.addEventListener("click", function (e) {
    var btn = e.target.closest && e.target.closest("[data-close-modal]");
    if (!btn) return;
    var box = btn.closest(".modal-backdrop");
    if (box) { e.preventDefault(); closeModal(box); }
  });

  /* ---------- 포맷 ---------- */
  function pad(n) { return (n < 10 ? "0" : "") + n; }

  function fmtClock(ms) {
    if (ms < 0) ms = 0;
    var s = Math.floor(ms / 1000);
    var h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    return (h > 0 ? pad(h) + ":" : "") + pad(m) + ":" + pad(sec);
  }

  function fmtDuration(sec) {
    var m = Math.floor(sec / 60);
    return pad(m) + ":" + pad(Math.floor(sec % 60));
  }

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function qs(name) {
    try { return new URLSearchParams(window.location.search).get(name); }
    catch (e) { return null; }
  }

  /* 상태 → 배지 클래스 (색만으로 구분하지 않도록 라벨을 함께 쓴다) */
  /* 상태는 색만으로 구분하지 않는다 — 배지에 항상 라벨 텍스트가 함께 들어간다. */
  var TONE = { ok: "ok", warn: "warn", risk: "risk", offline: "", idle: "" };
  function badge(tone, label) {
    return '<span class="state-badge ' + (TONE[tone] || "") + '">' + esc(label) + "</span>";
  }

  window.ExamDemo = {
    load: load, save: save, patch: patch, reset: reset, defaults: defaults,
    toast: toast, openModal: openModal, closeModal: closeModal,
    fmtClock: fmtClock, fmtDuration: fmtDuration, esc: esc, qs: qs, badge: badge
  };
})();
