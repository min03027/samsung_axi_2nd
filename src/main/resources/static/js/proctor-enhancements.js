/* =============================================================
   실시간 감독 화면 확장 (LXP-003 / 005 / 013 / 020)

   <b>서버가 아는 사실과 프론트 데모를 엄격히 분리한다.</b>

   서버가 실제로 주는 값 (LiveAttemptRow):
     traineeName, loginId, attemptNo, status, statusLabel, startedAt,
     remainSeconds, remainLabel, eventCount, warnCount, criticalCount,
     warningSent, ip, live
   → 검색·필터·요약·위험도는 <b>전부 이 값들로만</b> 계산한다.

   서버가 주지 않는 것:
     미디어 스트림 URL, 컨테이너·노드 지표
   → <video> 에 가짜 영상을 넣지 않고 상태 placeholder 만 보여 준다.
     실행환경 지표는 DEMO 상수로 고립시키고 화면에 데모임을 항상 표시한다.
   ============================================================= */
(function () {
  "use strict";

  /* ===================== 공통 ===================== */

  function $(id) { return document.getElementById(id); }

  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  function csrf() {
    /* 이 화면의 th:action 폼에 Thymeleaf 가 넣어 준 히든 필드를 재사용한다. */
    var input = document.querySelector('input[name="_csrf"]');
    if (input) return { name: "_csrf", value: input.value };
    var meta = document.querySelector('meta[name="_csrf"]');
    if (meta) return { name: "_csrf", value: meta.content };
    return null;
  }

  var toastBox = null;

  function toast(msg, tone) {
    if (!toastBox) {
      toastBox = el("div", "proctor-toast");
      toastBox.setAttribute("aria-live", "polite");
      document.body.appendChild(toastBox);
    }
    var t = el("div", null, msg);
    t.dataset.tone = tone || "ok";
    toastBox.appendChild(t);
    setTimeout(function () {
      if (t.parentNode) t.parentNode.removeChild(t);
    }, 4200);
  }

  /* ===================== 경고 응답 판정 (P1-1) =====================

     fetch 는 기본적으로 redirect 를 <b>따라간다</b>. Spring 의 경고 엔드포인트는
     성공 시 감독 화면으로 redirect 하는데, 감독 세션이 만료돼 있으면 그 redirect 가
     /login 으로 가고 로그인 HTML 이 <b>200 으로</b> 돌아온다.
     그러면 res.ok 가 true 라서 "경고를 발송했습니다" 토스트가 뜨지만
     실제로는 아무것도 저장되지 않았다. 상태 코드만 보면 이것을 구분할 수 없다.

     그래서 <b>최종 URL</b>(res.url)까지 함께 본다. 다만 pathname 만 보면
     "https://evil.example/admin/evaluation/monitoring/12" 처럼 외부 도메인이
     같은 경로를 흉내 낸 경우도 성공으로 오인한다. 그래서 최종 URL 의 <b>origin</b> 을
     현재 페이지 origin(window.location.origin)과 정확히 비교한다. 순수 함수로
     분리해 테스트한다.
     ================================================================= */

  /** 경고 발송이 실제로 성공한 감독 화면 경로인가. */
  var SUCCESS_PATHS = [
    "/admin/evaluation/monitoring",   /* 관리자 */
    "/instructor/proctor"             /* 강사 */
  ];

  /** 로그인·인증 화면으로 밀려난 흔적. */
  var LOGIN_HINTS = ["/login", "/oauth2/authorization", "/saml2/authenticate"];

  /**
   * 경고 POST 응답을 판정한다. <b>순수 함수</b> — DOM·네트워크에 접근하지 않는다.
   *
   * @param {Object} res  { ok, status, url, redirected } (fetch Response 의 필요한 부분)
   * @param {string} expectedOrigin  현재 브라우저가 보고 있는 origin (window.location.origin).
   *   같은 pathname 이어도 origin 이 다르면(reverse proxy 우회·외부 리다이렉트 등) 성공으로 보지 않는다.
   * @returns {{ok: boolean, reason: string, message: string}}
   */
  function warningVerdict(res, expectedOrigin) {
    if (!res) {
      return { ok: false, reason: "NO_RESPONSE", message: "응답을 받지 못했습니다. 다시 시도해 주세요." };
    }

    /* ① 상태 코드가 실패면 그대로 실패. */
    if (!res.ok) {
      if (res.status === 401) {
        return { ok: false, reason: "UNAUTHORIZED",
                 message: "로그인 세션이 만료되었습니다. 다시 로그인한 뒤 재시도하세요." };
      }
      if (res.status === 403) {
        return { ok: false, reason: "FORBIDDEN",
                 message: "이 응시자에게 경고를 보낼 권한이 없습니다." };
      }
      return { ok: false, reason: "HTTP_ERROR",
               message: "경고 발송이 실패했습니다. (HTTP " + res.status + ")" };
    }

    /* ② 200 이라도 최종 URL 을 확인한다. URL 을 못 읽으면 성공으로 단정하지 않는다. */
    var loc = parseLocation(res.url);
    if (loc === null) {
      return { ok: false, reason: "UNKNOWN_TARGET",
               message: "응답 위치를 확인할 수 없어 발송 여부를 보장할 수 없습니다. 목록을 새로 고쳐 확인해 주세요." };
    }

    /* ③ origin 이 현재 페이지와 다르면 pathname 이 같아 보여도 신뢰하지 않는다.
       expectedOrigin 이 없거나 파싱할 수 없을 때도 안전하지 않은 것으로 취급한다. */
    var expected = parseOrigin(expectedOrigin);
    if (expected === null || loc.origin !== expected) {
      return { ok: false, reason: "ORIGIN_MISMATCH",
               message: "예상하지 못한 외부 주소로 이동해 경고 발송 성공을 확인할 수 없습니다." };
    }

    /* ④ 로그인·인증 화면으로 밀려났다 → 세션 만료. 저장되지 않았다. */
    var path = loc.pathname;
    for (var i = 0; i < LOGIN_HINTS.length; i++) {
      if (path === LOGIN_HINTS[i] || path.indexOf(LOGIN_HINTS[i] + "/") === 0
          || path.indexOf(LOGIN_HINTS[i] + "?") === 0) {
        return { ok: false, reason: "SESSION_EXPIRED",
                 message: "로그인 세션이 만료되었습니다. 다시 로그인한 뒤 재시도하세요." };
      }
    }

    /* ⑤ 감독 화면 범위여야 성공이다. */
    for (var j = 0; j < SUCCESS_PATHS.length; j++) {
      if (path === SUCCESS_PATHS[j] || path.indexOf(SUCCESS_PATHS[j] + "/") === 0) {
        return { ok: true, reason: "OK", message: "" };
      }
    }

    /* ⑥ 그 밖의 경로(오류 화면 등)는 성공으로 간주하지 않는다. */
    return { ok: false, reason: "UNEXPECTED_REDIRECT",
             message: "예상하지 못한 화면으로 이동했습니다. 경고가 저장되었는지 목록에서 확인해 주세요." };
  }

  /** 절대/상대 URL 에서 { origin, pathname } 을 뽑는다. 파싱 실패는 null. */
  function parseLocation(url) {
    if (!url || typeof url !== "string") return null;
    try {
      /* 상대 경로도 다룰 수 있도록 base 를 준다. base 가 없는 환경(node)에서는 절대 URL 만 온다. */
      var base = (typeof location !== "undefined" && location.href) ? location.href : "http://localhost/";
      var u = new URL(url, base);
      return { origin: u.origin, pathname: u.pathname };
    } catch (e) {
      return null;
    }
  }

  /** origin 문자열을 검증한다. 비어 있거나 파싱할 수 없으면 null (안전하지 않은 것으로 취급). */
  function parseOrigin(origin) {
    if (!origin || typeof origin !== "string") return null;
    try {
      return new URL(origin).origin;
    } catch (e) {
      return null;
    }
  }

  /** 절대/상대 URL 에서 경로만 뽑는다. 파싱 실패는 null (성공으로 넘기지 않기 위해). */
  function pathOf(url) {
    var loc = parseLocation(url);
    return loc === null ? null : loc.pathname;
  }

  /* ===================== 위험도 판정 ===================== */

  /**
   * 서버가 준 값만으로 위험도를 정한다.
   * critical > 0 → 위험 / warn > 0 → 주의 / live 아님 → 연결 끊김 / 그 외 정상
   */
  function riskOf(row) {
    if (!row.live) return "offline";
    if (row.criticalCount > 0) return "critical";
    if (row.warnCount > 0) return "warn";
    return "ok";
  }

  var RISK_LABEL = {
    all: "전체", ok: "정상", warn: "주의", critical: "위험", offline: "연결 끊김"
  };

  /* ===================== 1. 도구 막대 · 필터 (LXP-020) ===================== */

  function setupFilters(rows) {
    var host = $("proctorToolsMount");
    if (!host) return;

    var tools = el("div", "proctor-tools");

    /* 검색 */
    var f1 = el("div", "field");
    var l1 = el("label", null, "이름 · 계정 검색");
    l1.setAttribute("for", "pxSearch");
    var search = document.createElement("input");
    search.type = "search";
    search.id = "pxSearch";
    search.placeholder = "응시자 이름 또는 계정";
    f1.appendChild(l1);
    f1.appendChild(search);

    /* 상태 */
    var f2 = el("div", "field");
    var l2 = el("label", null, "응시 상태");
    l2.setAttribute("for", "pxStatus");
    var statusSel = document.createElement("select");
    statusSel.id = "pxStatus";
    var statuses = ["all"].concat(
      rows.map(function (r) { return r.status; })
          .filter(function (v, i, a) { return v && a.indexOf(v) === i; })
    );
    statuses.forEach(function (s) {
      var o = document.createElement("option");
      o.value = s;
      /* 라벨은 서버가 준 statusLabel 을 그대로 쓴다 — 임의 번역하지 않는다. */
      if (s === "all") {
        o.textContent = "전체";
      } else {
        var m = rows.find(function (r) { return r.status === s; });
        o.textContent = (m && m.statusLabel) ? m.statusLabel : s;
      }
      statusSel.appendChild(o);
    });
    f2.appendChild(l2);
    f2.appendChild(statusSel);

    /* 위험도 */
    var f3 = el("div", "field");
    var l3 = el("label", null, "위험도");
    l3.setAttribute("for", "pxRisk");
    var riskSel = document.createElement("select");
    riskSel.id = "pxRisk";
    ["all", "ok", "warn", "critical", "offline"].forEach(function (k) {
      var o = document.createElement("option");
      o.value = k;
      o.textContent = RISK_LABEL[k];
      riskSel.appendChild(o);
    });
    f3.appendChild(l3);
    f3.appendChild(riskSel);

    tools.appendChild(f1);
    tools.appendChild(f2);
    tools.appendChild(f3);
    tools.appendChild(el("div", "spacer"));
    var count = el("p", "result-count", "");
    count.setAttribute("aria-live", "polite");
    tools.appendChild(count);
    host.appendChild(tools);

    /* ---------- 요약 칩 (클릭하면 위험도 필터) ---------- */

    var summary = el("div", "risk-summary");
    summary.setAttribute("role", "group");
    summary.setAttribute("aria-label", "위험도 요약 및 필터");

    var chips = {};
    ["all", "ok", "warn", "critical", "offline"].forEach(function (k) {
      var b = document.createElement("button");
      b.type = "button";
      b.className = "risk-chip";
      b.dataset.risk = k;
      b.setAttribute("aria-pressed", k === "all" ? "true" : "false");
      b.appendChild(document.createTextNode(RISK_LABEL[k] + " "));
      var n = el("b", null, "0");
      b.appendChild(n);
      b.appendChild(document.createTextNode("명"));
      summary.appendChild(b);
      chips[k] = { btn: b, num: n };
    });
    host.appendChild(summary);

    summary.addEventListener("click", function (e) {
      var b = e.target.closest(".risk-chip");
      if (!b) return;
      riskSel.value = b.dataset.risk;
      applyFilter();
    });

    /* ---------- 필터 적용 ---------- */

    function applyFilter() {
      var q = search.value.trim().toLowerCase();
      var st = statusSel.value;
      var rk = riskSel.value;
      var shown = 0;
      var counts = { all: 0, ok: 0, warn: 0, critical: 0, offline: 0 };

      rows.forEach(function (r) {
        var risk = riskOf(r);
        counts.all++;
        counts[risk]++;

        var hitQ = !q
          || (r.traineeName || "").toLowerCase().indexOf(q) >= 0
          || (r.loginId || "").toLowerCase().indexOf(q) >= 0;
        var hitS = st === "all" || r.status === st;
        var hitR = rk === "all" || risk === rk;
        var on = hitQ && hitS && hitR;
        if (on) shown++;

        /* 타일과 표 행을 같은 기준으로 함께 숨긴다. */
        var tile = document.getElementById("tile-" + r.attemptId);
        if (tile) tile.hidden = !on;
        document.querySelectorAll('tr[data-attempt-id="' + r.attemptId + '"]')
          .forEach(function (tr) { tr.hidden = !on; });
        document.querySelectorAll('.student-btn[data-attempt-id="' + r.attemptId + '"]')
          .forEach(function (bt) { bt.hidden = !on; });
      });

      Object.keys(chips).forEach(function (k) {
        chips[k].num.textContent = String(counts[k]);
        chips[k].btn.setAttribute("aria-pressed", rk === k ? "true" : "false");
      });

      count.textContent = shown + " / " + rows.length + "명 표시";
    }

    search.addEventListener("input", applyFilter);
    statusSel.addEventListener("change", applyFilter);
    riskSel.addEventListener("change", applyFilter);
    applyFilter();

    return { applyFilter: applyFilter };
  }

  /* ===================== 2. 3면 감독 타일 (LXP-005) ===================== */

  var SOURCES = [
    { key: "webcam", name: "웹캠", icon: "◉" },
    { key: "screen", name: "화면", icon: "▭" },
    { key: "mobile", name: "모바일", icon: "▯" }
  ];

  function setupTiles(rows) {
    var grid = $("videoGrid");
    if (!grid) return;

    rows.forEach(function (r) {
      var tile = document.getElementById("tile-" + r.attemptId);
      if (!tile) return;

      /* 기존 placeholder 문구는 3분할 안으로 옮긴다. */
      var oldPh = tile.querySelector(".video-placeholder");
      if (oldPh) oldPh.remove();

      var wrap = el("div", "tri-sources");

      SOURCES.forEach(function (src) {
        var box = el("div", "tri-src");
        /* ★ 실제 타일에는 <b>서버 사실만</b> 넣는다 (P2-1).
           서버가 알려 주는 것은 응시 여부(live)뿐이므로 connecting / offline 두 가지다.
           connected·denied·unsupported 를 실제 타일에 주입하면 거짓 상태가 된다.
           그 다섯 가지 표시 방식은 아래 "프론트 상태 미리보기(데모)" 에서만 보여 준다.
           가짜 <video> 도 넣지 않는다. */
        box.dataset.state = r.live ? "connecting" : "offline";

        box.appendChild(el("p", "tri-src__name", src.name));
        var icon = el("div", "tri-src__icon", src.icon);
        icon.setAttribute("aria-hidden", "true");
        box.appendChild(icon);

        var stateText = r.live ? "연결 대기" : "끊김";
        var st = el("span", "tri-src__state", stateText);
        box.appendChild(st);

        box.appendChild(el("p", "tri-src__note", "실시간 스트리밍 연동 필요"));

        /* 스크린리더용 요약 — 색과 아이콘에만 의존하지 않는다. */
        box.setAttribute("role", "group");
        box.setAttribute("aria-label",
          r.traineeName + " " + src.name + " 소스: " + stateText + ", 실시간 스트리밍 연동 필요");

        wrap.appendChild(box);
      });

      tile.insertBefore(wrap, tile.firstChild);

      /* 확대 / 복귀 / 학생 전환 */
      var acts = el("div", "tile-actions");

      var zoomBtn = el("button", "btn btn-secondary", "확대");
      zoomBtn.type = "button";
      zoomBtn.addEventListener("click", function () {
        var on = tile.dataset.zoom === "true";
        /* 한 번에 하나만 확대한다. */
        grid.querySelectorAll('.video-item[data-zoom="true"]').forEach(function (t) {
          delete t.dataset.zoom;
          var b = t.querySelector(".tile-actions .btn");
          if (b) b.textContent = "확대";
        });
        if (!on) {
          tile.dataset.zoom = "true";
          zoomBtn.textContent = "복귀";
          tile.scrollIntoView({ block: "nearest" });
        }
      });

      var logBtn = el("button", "btn btn-secondary", "이벤트 로그");
      logBtn.type = "button";
      logBtn.addEventListener("click", function () {
        /* 기존 페이지의 showEvents 를 그대로 쓴다 — 조회 경로를 새로 만들지 않는다. */
        if (typeof window.showEvents === "function") {
          window.showEvents(String(r.attemptId), r.traineeName);
        }
      });

      var warnBtn = el("button", "btn btn-primary", "경고 발송");
      warnBtn.type = "button";
      warnBtn.addEventListener("click", function () { openWarnModal(r); });

      acts.appendChild(zoomBtn);
      acts.appendChild(logBtn);
      acts.appendChild(warnBtn);
      tile.appendChild(acts);
    });
  }

  /* ===================== 2-b. 프론트 상태 미리보기 (데모) =====================

     ★ 실제 타일과 <b>완전히 분리</b>한다 (P2-1).
     백엔드가 per-source(웹캠·화면·모바일) 연결·권한 상태를 주지 않으므로,
     실제 타일에는 서버 사실(live)만 넣는다. 다섯 가지 상태의 색·아이콘·텍스트는
     "미리보기(데모)" 영역에서만 확인한다. 이 영역은 실제 응시자 데이터를
     읽지도, 바꾸지도 않는다 — row.live·위험도·경고 수·필터 결과에 영향이 없다.
     ========================================================================= */

  var DEMO_STATES = [
    { key: "connected",   label: "연결",      note: "미디어 스트림이 도착한 상태" },
    { key: "connecting",  label: "연결 중",   note: "응시 중이지만 스트림 대기" },
    { key: "offline",     label: "끊김",      note: "연결이 없거나 종료됨" },
    { key: "denied",      label: "권한 거부", note: "응시자가 카메라·화면 권한을 거부" },
    { key: "unsupported", label: "미지원",    note: "브라우저가 해당 소스를 지원하지 않음" }
  ];

  function setupStateDemo() {
    var host = $("videoGrid");
    if (!host || !host.parentNode) return;

    var box = el("section", "state-demo");
    box.id = "stateDemo";
    box.setAttribute("aria-label", "소스 상태 표시 미리보기 (데모)");

    var banner = el("p", "demo-banner",
      "프론트 상태 미리보기(데모) — 아래는 <표시 방식>만 보여 주는 범례입니다. "
      + "실제 응시자 상태가 아니며, 위 타일·위험도·필터에 영향을 주지 않습니다.");
    box.appendChild(banner);

    var grid = el("div", "state-demo__grid");
    DEMO_STATES.forEach(function (st) {
      /* 실제 타일과 같은 클래스를 써서 표시 방식이 일치함을 보인다. */
      var cell = el("div", "tri-src");
      cell.dataset.state = st.key;
      cell.dataset.demo = "true";        /* 실제 타일과 구분하는 표식 */

      cell.appendChild(el("p", "tri-src__name", st.label));
      var icon = el("div", "tri-src__icon", DEMO_ICON[st.key] || "◦");
      icon.setAttribute("aria-hidden", "true");
      cell.appendChild(icon);
      cell.appendChild(el("span", "tri-src__state", st.label));
      cell.appendChild(el("p", "tri-src__note", st.note));

      /* 색·아이콘만으로 전달하지 않는다 — 접근 가능한 이름을 붙인다. */
      cell.setAttribute("role", "group");
      cell.setAttribute("aria-label", "데모 상태 " + st.label + ": " + st.note);
      grid.appendChild(cell);
    });
    box.appendChild(grid);

    box.appendChild(el("p", "tri-src__note",
      "실제 per-source 판정은 미디어 상태 API 연동이 필요합니다. "
      + "연동 전에는 위 타일이 서버가 아는 사실(응시 중 / 끊김)만 표시합니다."));

    /* 타일 그리드 바로 뒤에 둔다 — 같은 화면에서 바로 대조할 수 있게. */
    host.parentNode.insertBefore(box, host.nextSibling);
  }

  var DEMO_ICON = {
    connected: "●", connecting: "◐", offline: "○", denied: "⃠", unsupported: "—"
  };

  /* ===================== 3. 경고 모달 (LXP-020) ===================== */

  var modal = null, modalRefs = null, lastFocus = null, currentRow = null;

  function buildModal() {
    modal = el("div", "warn-modal");
    modal.id = "warnModal";
    modal.setAttribute("role", "dialog");
    modal.setAttribute("aria-modal", "true");
    modal.setAttribute("aria-labelledby", "warnModalTitle");

    var box = el("div", "warn-modal__box");
    var title = el("h3", null, "경고 발송");
    title.id = "warnModalTitle";
    var target = el("p", "warn-modal__target", "");

    var label = el("label", null, "경고 메시지");
    label.setAttribute("for", "warnMsgInput");
    var ta = document.createElement("textarea");
    ta.id = "warnMsgInput";
    ta.placeholder = "응시자에게 표시할 경고 내용을 입력하세요.";

    var msg = el("p", "warn-modal__msg", "");
    msg.setAttribute("aria-live", "polite");

    var acts = el("div", "warn-modal__actions");
    var cancel = el("button", "btn btn-secondary", "취소");
    cancel.type = "button";
    var send = el("button", "btn btn-primary", "전송");
    send.type = "button";
    acts.appendChild(cancel);
    acts.appendChild(send);

    box.appendChild(title);
    box.appendChild(target);
    box.appendChild(label);
    box.appendChild(ta);
    box.appendChild(msg);
    box.appendChild(acts);
    modal.appendChild(box);
    document.body.appendChild(modal);

    modalRefs = { box: box, target: target, ta: ta, msg: msg, cancel: cancel, send: send };

    cancel.addEventListener("click", closeWarnModal);
    send.addEventListener("click", submitWarn);

    /* 배경 클릭으로 닫기 */
    modal.addEventListener("click", function (e) {
      if (e.target === modal) closeWarnModal();
    });

    /* Esc 로 닫고, Tab 은 모달 안에서만 돈다 (포커스 트랩). */
    modal.addEventListener("keydown", function (e) {
      if (e.key === "Escape") { closeWarnModal(); return; }
      if (e.key !== "Tab") return;
      var f = [ta, cancel, send].filter(function (n) { return !n.disabled; });
      var first = f[0], last = f[f.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        last.focus(); e.preventDefault();
      } else if (!e.shiftKey && document.activeElement === last) {
        first.focus(); e.preventDefault();
      }
    });
  }

  function openWarnModal(row) {
    if (!modal) buildModal();
    currentRow = row;
    lastFocus = document.activeElement;
    modalRefs.target.textContent =
      "대상: " + row.traineeName + " (" + row.loginId + ") · 회차 " + row.attemptNo;
    modalRefs.ta.value = "";
    modalRefs.msg.textContent = "";
    modalRefs.msg.removeAttribute("data-tone");
    modalRefs.send.disabled = false;
    modal.dataset.open = "true";
    modalRefs.ta.focus();
  }

  function closeWarnModal() {
    if (!modal) return;
    delete modal.dataset.open;
    currentRow = null;
    if (lastFocus && typeof lastFocus.focus === "function") lastFocus.focus();
  }

  function submitWarn() {
    if (!currentRow) return;
    var text = modalRefs.ta.value.trim();
    if (!text) {
      modalRefs.msg.dataset.tone = "err";
      modalRefs.msg.textContent = "경고 메시지를 입력해 주세요.";
      modalRefs.ta.focus();
      return;
    }

    var prefix = window._proctorUrls && window._proctorUrls.attemptPrefix;
    if (!prefix) {
      modalRefs.msg.dataset.tone = "err";
      modalRefs.msg.textContent = "경고 발송 주소를 찾을 수 없습니다. 페이지를 새로 고쳐 주세요.";
      return;
    }

    var token = csrf();
    var body = new URLSearchParams();
    body.set("message", text);
    if (token) body.set(token.name, token.value);

    modalRefs.send.disabled = true;
    modalRefs.msg.removeAttribute("data-tone");
    modalRefs.msg.textContent = "전송 중…";

    fetch(prefix + currentRow.attemptId + "/warning", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: body.toString(),
      credentials: "same-origin"
    })
      .then(function (res) {
        /* ★ 상태 코드·최종 URL·origin 을 함께 본다 (P1-1).
           세션이 만료돼 /login 으로 redirect 되면 200 이 오지만 저장되지 않았다.
           origin 까지 확인해야 pathname 만 같은 외부 리다이렉트를 성공으로 오인하지 않는다. */
        var v = warningVerdict(res, window.location.origin);
        if (!v.ok) {
          modalRefs.msg.dataset.tone = "err";
          modalRefs.msg.textContent = v.message;
          modalRefs.send.disabled = false;   /* 다시 시도할 수 있게 되돌린다 */
          toast(v.message, "err");
          /* 실패에서는 성공 토스트도, 새로고침도 하지 않는다. */
          return;
        }
        var name = currentRow.traineeName;
        closeWarnModal();
        toast(name + " 에게 경고를 발송했습니다.", "ok");
        /* 발송 건수는 서버가 세는 값이다. 화면에서 임의로 더하지 않고 새로 읽는다. */
        setTimeout(function () { location.reload(); }, 900);
      })
      .catch(function () {
        modalRefs.msg.dataset.tone = "err";
        modalRefs.msg.textContent = "네트워크 오류로 전송하지 못했습니다. 다시 시도해 주세요.";
        modalRefs.send.disabled = false;
        toast("네트워크 오류로 경고를 보내지 못했습니다.", "err");
      });
  }

  /* ===================== 4. 실행환경 패널 (LXP-013) =====================
     ★ 아래 DEMO 상수는 <b>프론트 데모 데이터</b>다. 서버·인프라와 연결되어 있지 않다.
        실제 API 호출도, WebSocket 도 열지 않는다. 화면에 항상 데모임을 표시한다.
     ===================================================================== */

  var DEMO = {
    normal: {
      kpi: { active: 42, queued: 0, avgStartSec: 8, errorRate: 0.4 },
      nodes: [
        { id: "runner-01", state: "healthy", cpu: 38, mem: 51, queue: 0, updated: "방금" },
        { id: "runner-02", state: "healthy", cpu: 44, mem: 47, queue: 0, updated: "방금" },
        { id: "runner-03", state: "healthy", cpu: 29, mem: 40, queue: 0, updated: "방금" }
      ]
    },
    busy: {
      kpi: { active: 118, queued: 17, avgStartSec: 26, errorRate: 2.1 },
      nodes: [
        { id: "runner-01", state: "busy", cpu: 87, mem: 79, queue: 6, updated: "방금" },
        { id: "runner-02", state: "busy", cpu: 91, mem: 83, queue: 8, updated: "방금" },
        { id: "runner-03", state: "healthy", cpu: 62, mem: 58, queue: 3, updated: "방금" }
      ]
    },
    fault: {
      kpi: { active: 74, queued: 39, avgStartSec: 71, errorRate: 12.6 },
      nodes: [
        { id: "runner-01", state: "down", cpu: 0, mem: 0, queue: 0, updated: "2분 전" },
        { id: "runner-02", state: "busy", cpu: 95, mem: 90, queue: 21, updated: "방금" },
        { id: "runner-03", state: "busy", cpu: 93, mem: 88, queue: 18, updated: "방금" }
      ]
    }
  };

  var PROVISION = [
    { no: "1", name: "증설 요청" },
    { no: "2", name: "프로비저닝" },
    { no: "3", name: "준비 완료" }
  ];

  function setupRuntimePanel() {
    var host = $("runtimePanelMount");
    if (!host) return;

    var panel = el("div", "runtime-panel");
    panel.id = "runtimePanel";

    var tabs = el("div", "runtime-panel__tabs");
    tabs.setAttribute("role", "tablist");
    tabs.setAttribute("aria-label", "감독 부가 정보");

    var bodies = {};
    var TABS = [
      { id: "runtime", label: "실행환경" },
      { id: "help", label: "연동 안내" }
    ];
    var tabBtns = TABS.map(function (t, i) {
      var b = el("button", null, t.label);
      b.type = "button";
      b.id = "rtTab-" + t.id;
      b.setAttribute("role", "tab");
      b.setAttribute("aria-selected", i === 0 ? "true" : "false");
      b.dataset.tab = t.id;
      tabs.appendChild(b);

      var body = el("div", "runtime-panel__body");
      body.setAttribute("role", "tabpanel");
      body.setAttribute("aria-labelledby", b.id);
      if (i !== 0) body.hidden = true;
      bodies[t.id] = body;
      return b;
    });

    panel.appendChild(tabs);
    TABS.forEach(function (t) { panel.appendChild(bodies[t.id]); });
    host.appendChild(panel);

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
      var next = (cur + (e.key === "ArrowRight" ? 1 : tabBtns.length - 1)) % tabBtns.length;
      selectTab(tabBtns[next].dataset.tab);
      tabBtns[next].focus();
      e.preventDefault();
    });

    /* ---------- 실행환경 탭 ---------- */

    var rt = bodies.runtime;

    var banner = el("div", "demo-banner",
      "프론트 데모 — 실제 인프라 미연결. 아래 수치는 화면 확인용 고정 예시이며 " +
      "실제 컨테이너·오토스케일링과 연결되어 있지 않습니다.");
    rt.appendChild(banner);

    var scenarioBar = el("div", "scenario-bar");
    scenarioBar.appendChild(el("span", null, "시나리오:"));
    var scenarioBtns = [
      { key: "normal", label: "정상" },
      { key: "busy", label: "혼잡" },
      { key: "fault", label: "장애" }
    ].map(function (s) {
      var b = el("button", "btn btn-secondary", s.label);
      b.type = "button";
      b.dataset.scenario = s.key;
      b.setAttribute("aria-pressed", s.key === "normal" ? "true" : "false");
      scenarioBar.appendChild(b);
      return b;
    });
    rt.appendChild(scenarioBar);

    var kpiGrid = el("div", "kpi-grid");
    rt.appendChild(kpiGrid);

    /* 증설 3단계 */
    rt.appendChild(el("h4", null, "증설 진행"));
    var steps = el("div", "provision-steps");
    var stepEls = PROVISION.map(function (p) {
      var s = el("div", "provision-step");
      s.dataset.state = "idle";
      s.appendChild(el("p", "provision-step__no", "STEP " + p.no));
      s.appendChild(el("p", "provision-step__name", p.name));
      s.appendChild(el("p", "provision-step__state", "대기"));
      steps.appendChild(s);
      return s;
    });
    rt.appendChild(steps);

    var provActions = el("div", "scenario-bar");
    var provBtn = el("button", "btn btn-primary", "증설 요청");
    provBtn.type = "button";
    var retryBtn = el("button", "btn btn-secondary", "재시도");
    retryBtn.type = "button";
    retryBtn.hidden = true;
    provActions.appendChild(provBtn);
    provActions.appendChild(retryBtn);
    rt.appendChild(provActions);
    var provMsg = el("p", "tri-src__note", "");
    provMsg.setAttribute("aria-live", "polite");
    rt.appendChild(provMsg);

    /* 노드 표 */
    rt.appendChild(el("h4", null, "노드 · 컨테이너"));
    var tableWrap = el("div", null);
    tableWrap.style.overflowX = "auto";
    var table = el("table", "proctor-table runtime-table");
    table.innerHTML =
      "<thead><tr><th>노드</th><th>상태</th><th>CPU</th><th>메모리</th>"
      + "<th>대기열</th><th>마지막 갱신</th></tr></thead><tbody></tbody>";
    tableWrap.appendChild(table);
    rt.appendChild(tableWrap);
    var tbody = table.querySelector("tbody");

    var NODE_LABEL = { healthy: "정상", busy: "혼잡", down: "장애", starting: "준비 중" };

    function renderScenario(key) {
      var d = DEMO[key];
      if (!d) return;

      kpiGrid.replaceChildren();
      [
        { label: "활성 세션", value: d.kpi.active, unit: "개", tone: "" },
        { label: "대기 요청", value: d.kpi.queued, unit: "건",
          tone: d.kpi.queued > 20 ? "risk" : (d.kpi.queued > 0 ? "warn" : "") },
        { label: "평균 시작 시간", value: d.kpi.avgStartSec, unit: "초",
          tone: d.kpi.avgStartSec > 60 ? "risk" : (d.kpi.avgStartSec > 20 ? "warn" : "") },
        { label: "오류율", value: d.kpi.errorRate, unit: "%",
          tone: d.kpi.errorRate > 10 ? "risk" : (d.kpi.errorRate > 1 ? "warn" : "") }
      ].forEach(function (k) {
        var c = el("div", "kpi");
        if (k.tone) c.dataset.tone = k.tone;
        c.appendChild(el("p", "kpi__label", k.label));
        var v = el("p", "kpi__value", String(k.value));
        v.appendChild(el("small", null, " " + k.unit));
        c.appendChild(v);
        kpiGrid.appendChild(c);
      });

      tbody.replaceChildren();
      d.nodes.forEach(function (n) {
        var tr = document.createElement("tr");
        var tdId = el("td", null, n.id);
        var tdState = document.createElement("td");
        var badge = el("span", "node-state", NODE_LABEL[n.state] || n.state);
        badge.dataset.s = n.state;
        tdState.appendChild(badge);
        tr.appendChild(tdId);
        tr.appendChild(tdState);
        tr.appendChild(el("td", null, n.cpu + "%"));
        tr.appendChild(el("td", null, n.mem + "%"));
        tr.appendChild(el("td", null, String(n.queue)));
        tr.appendChild(el("td", null, n.updated));
        tbody.appendChild(tr);
      });

      scenarioBtns.forEach(function (b) {
        b.setAttribute("aria-pressed", b.dataset.scenario === key ? "true" : "false");
      });
    }

    scenarioBar.addEventListener("click", function (e) {
      var b = e.target.closest("button[data-scenario]");
      if (b) renderScenario(b.dataset.scenario);
    });

    /* ---------- 증설 시나리오 (데모) ---------- */

    var provTimers = [];

    function clearProv() {
      provTimers.forEach(clearTimeout);
      provTimers = [];
    }

    function setStep(i, state, text) {
      stepEls[i].dataset.state = state;
      stepEls[i].querySelector(".provision-step__state").textContent = text;
    }

    function runProvision(shouldFail) {
      clearProv();
      retryBtn.hidden = true;
      provBtn.disabled = true;
      stepEls.forEach(function (_, i) { setStep(i, "idle", "대기"); });
      provMsg.textContent = "데모 증설 절차를 시작합니다. 실제 인프라는 호출하지 않습니다.";

      setStep(0, "active", "요청 중");
      provTimers.push(setTimeout(function () {
        setStep(0, "done", "완료");
        setStep(1, "active", "프로비저닝 중");

        provTimers.push(setTimeout(function () {
          if (shouldFail) {
            setStep(1, "failed", "실패");
            setStep(2, "idle", "대기");
            provMsg.textContent = "데모 시나리오: 프로비저닝이 실패했습니다. 재시도할 수 있습니다.";
            retryBtn.hidden = false;
            provBtn.disabled = false;
            return;
          }
          setStep(1, "done", "완료");
          setStep(2, "active", "준비 중");
          provTimers.push(setTimeout(function () {
            setStep(2, "done", "준비 완료");
            provMsg.textContent = "데모 증설이 완료된 상태로 표시했습니다.";
            provBtn.disabled = false;
          }, 900));
        }, 1200));
      }, 700));
    }

    provBtn.addEventListener("click", function () {
      /* 장애 시나리오에서는 실패 흐름을 보여 준다 — 무작위가 아니라 시나리오에 따라 결정한다. */
      var cur = scenarioBtns.find(function (b) {
        return b.getAttribute("aria-pressed") === "true";
      });
      runProvision(cur && cur.dataset.scenario === "fault");
    });

    retryBtn.addEventListener("click", function () { runProvision(false); });

    /* ---------- 연동 안내 탭 ---------- */

    var help = bodies.help;
    help.appendChild(el("h4", null, "지금 서버가 실제로 주는 값"));
    var ul1 = document.createElement("ul");
    [
      "응시자 목록과 상태 (응시 중 / 제출 / 무효)",
      "회차별 이벤트 로그와 심각도 집계",
      "발송한 경고 건수",
      "남은 시간과 접속 IP"
    ].forEach(function (t) { ul1.appendChild(el("li", null, t)); });
    help.appendChild(ul1);

    help.appendChild(el("h4", null, "연동이 필요한 것"));
    var ul2 = document.createElement("ul");
    [
      "웹캠·화면·모바일 실시간 스트리밍 (WebRTC 송출 서버)",
      "녹화 저장과 재생 URL",
      "서버 측 영상 검증·얼굴 대조·위치 이탈 판정",
      "실행 샌드박스 컨테이너와 오토스케일링 지표",
      "외부 경고 발송 채널 (문자·푸시)"
    ].forEach(function (t) { ul2.appendChild(el("li", null, t)); });
    help.appendChild(ul2);

    help.appendChild(el("p", "tri-src__note",
      "위 항목이 없는 동안 이 화면은 서버가 아는 사실만 표시하고, "
      + "실행환경 탭은 데모임을 명시합니다. 가짜 영상이나 조작된 운영 수치를 만들지 않습니다."));

    renderScenario("normal");
  }

  /* ===================== 초기화 ===================== */

  function init() {
    var rows = (window._proctorRows || []).slice();
    setupFilters(rows);
    setupTiles(rows);
    setupStateDemo();      /* 실제 타일과 분리된 상태 범례 (P2-1) */
    setupRuntimePanel();
  }

  /* 브라우저에서만 DOM 을 건드린다. node 로 불러와 순수 함수를 테스트할 수 있어야 한다. */
  if (typeof document !== "undefined") {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", init);
    } else {
      init();
    }
  }

  var api = { toast: toast, riskOf: riskOf, warningVerdict: warningVerdict, pathOf: pathOf };

  if (typeof window !== "undefined") {
    window.ProctorEnhancements = api;
  }
  /* node 에서 require 해 판정 로직을 실제로 실행해 검증한다 (문자열 검사가 아니라). */
  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
})();
