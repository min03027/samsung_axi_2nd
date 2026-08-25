/* ============================================================
   mobile-identity.js — QR 로 열린 모바일 신분증 제출

   미리보기는 Object URL 로만 만들고 교체·삭제·이탈 시 반드시 revoke 한다.
   제출에 성공해도 자동 승인은 없다.
   그리고 <b>신분증 하나만으로는 검토 대기가 아니다</b> — 얼굴 사진이 아직 없으면 세션은
   PENDING 을 유지한다. 안내 문구는 서버가 세션 상태를 보고 정해 내려보내므로
   화면은 res.message 를 그대로 쓴다 (P1-2).
   ============================================================ */

(function () {
  "use strict";

  function meta(name) {
    var el = document.querySelector('meta[name="' + name + '"]');
    return el ? el.getAttribute("content") : "";
  }
  var cfgEl = document.getElementById("mobileIdConfig");
  var CFG = {
    token: cfgEl ? cfgEl.dataset.token : "",
    csrfHeader: meta("_csrf_header"),
    csrfToken: meta("_csrf")
  };
  var $ = function (id) { return document.getElementById(id); };

  var MAX_BYTES = 10 * 1024 * 1024;
  var ALLOWED = ["image/jpeg", "image/png"];

  var preview = $("preview"), previewEmpty = $("previewEmpty"), errorBox = $("errorBox");
  var clearBtn = $("clearBtn"), consent = $("consent"), submitBtn = $("submitBtn");
  var resultBox = $("resultBox"), statusBadge = $("statusBadge");
  var progress = $("progress"), progressBar = $("progressBar");

  var objectUrl = null, picked = null, submitted = false;

  function revoke() {
    if (objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null; }
  }

  function showError(msg) {
    errorBox.textContent = msg || "";
    errorBox.hidden = !msg;
  }

  function syncSubmit() {
    submitBtn.disabled = !(picked && consent.checked) || submitted;
  }

  function clearFile() {
    revoke();
    picked = null;
    $("idFile").value = "";
    $("idPick").value = "";
    preview.innerHTML = '<p class="preview-empty" id="previewEmpty">아직 선택된 이미지가 없습니다</p>';
    clearBtn.disabled = true;
    showError("");
    syncSubmit();
  }

  function accept(file) {
    if (!file) { clearFile(); return; }
    if (ALLOWED.indexOf(file.type) === -1) {
      clearFile();
      showError("JPG 또는 PNG 형식만 제출할 수 있습니다. 선택한 형식: " + (file.type || "알 수 없음"));
      return;
    }
    if (file.size > MAX_BYTES) {
      clearFile();
      showError("파일이 10MB를 넘습니다 (" + (file.size / 1048576).toFixed(1) + "MB). 더 낮은 화질로 다시 촬영해 주세요.");
      return;
    }

    revoke();                         /* 교체 전에 이전 URL 부터 해제 */
    objectUrl = URL.createObjectURL(file);
    picked = file;

    var img = document.createElement("img");
    img.alt = "제출할 신분증 미리보기";
    img.src = objectUrl;
    preview.innerHTML = "";
    preview.appendChild(img);

    clearBtn.disabled = false;
    showError("");
    syncSubmit();
  }

  $("idFile").addEventListener("change", function () { accept(this.files && this.files[0]); });
  $("idPick").addEventListener("change", function () { accept(this.files && this.files[0]); });
  clearBtn.addEventListener("click", clearFile);
  consent.addEventListener("change", syncSubmit);

  submitBtn.addEventListener("click", function () {
    if (!picked || !consent.checked) return;
    submitBtn.disabled = true;
    progress.hidden = false;
    progressBar.style.width = "0%";
    resultBox.textContent = "제출하는 중입니다…";

    var fd = new FormData();
    fd.append("file", picked);
    fd.append("consent", "true");

    /* 업로드 진행률을 보여주려고 XHR 을 쓴다 (fetch 로는 진행률을 못 읽는다). */
    var xhr = new XMLHttpRequest();
    xhr.open("POST", "/m/id/" + CFG.token + "/upload");
    if (CFG.csrfHeader) xhr.setRequestHeader(CFG.csrfHeader, CFG.csrfToken);

    xhr.upload.onprogress = function (e) {
      if (e.lengthComputable) {
        progressBar.style.width = Math.round((e.loaded / e.total) * 100) + "%";
      }
    };
    xhr.onload = function () {
      progress.hidden = true;
      var res;
      try { res = JSON.parse(xhr.responseText); } catch (e) { res = { ok: false, message: "서버 응답을 읽지 못했습니다." }; }
      if (res.ok) {
        submitted = true;
        resultBox.textContent = res.message;
        resultBox.className = "result-box ok";
        /* 완전 제출이어야 '검토 대기(warn)' 다. 신분증만 냈으면 아직 진행 중이다. */
        setBadge(res.statusLabel, res.submissionComplete ? "warn" : "");
        clearBtn.disabled = true;
        consent.disabled = true;
        document.querySelectorAll(".file-btn").forEach(function (b) { b.classList.add("is-disabled"); });
        pollStatus();
      } else {
        resultBox.textContent = res.message || "제출에 실패했습니다.";
        resultBox.className = "result-box err";
        submitBtn.disabled = false;
      }
    };
    xhr.onerror = function () {
      progress.hidden = true;
      resultBox.textContent = "네트워크 오류로 제출하지 못했습니다. 다시 시도해 주세요.";
      resultBox.className = "result-box err";
      submitBtn.disabled = false;
    };
    xhr.send(fd);
  });

  function setBadge(text, tone) {
    statusBadge.textContent = text;
    statusBadge.className = "state-badge" + (tone ? " " + tone : "");
  }

  /** 제출 후 운영진 판정을 기다린다. 승인/반려가 나오면 이 화면에도 표시된다. */
  function pollStatus() {
    fetch("/m/id/" + CFG.token + "/status")
      .then(function (r) { return r.json(); })
      .then(function (res) {
        if (!res.ok) return;
        var tone = res.status === "APPROVED" ? "ok"
                 : res.status === "REJECTED" ? "risk" : "warn";
        setBadge(res.statusLabel, tone);
        if (res.status === "APPROVED") {
          resultBox.textContent = "신분 확인이 승인되었습니다. PC 화면에서 시험을 시작하세요.";
          resultBox.className = "result-box ok";
        } else if (res.status === "REJECTED") {
          resultBox.textContent = "반려되었습니다 — " + (res.reason || "사유 없음");
          resultBox.className = "result-box err";
        }
      })
      .catch(function () { /* 다음 폴링에서 회복 */ });
  }

  window.addEventListener("pagehide", revoke);

  /* 부분 제출·판정 대기·판정 완료를 모두 따라가야 하므로 항상 폴링한다.
     라벨 문자열로 분기하던 이전 방식은 문구가 바뀌면 조용히 멈춘다 (P1-2). */
  setInterval(pollStatus, 4000);
  syncSubmit();
})();
