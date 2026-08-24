/* ============================================================
   exam-id-upload.js — 모바일 신분증 제출 (LXP-015)

   파일은 Object URL 로 브라우저 안에서만 미리보기하고, 교체·삭제·
   페이지 이탈 시 반드시 revoke 한다. 네트워크 전송은 하지 않는다.
   ============================================================ */

(function () {
  "use strict";

  var E = window.ExamDemo;

  var MAX_BYTES = 10 * 1024 * 1024;
  var ALLOWED = ["image/jpeg", "image/png", "image/webp"];

  var fileInput = document.getElementById("idFile");
  var preview   = document.getElementById("idPreview");
  var emptyMsg  = document.getElementById("idPreviewEmpty");
  var errorEl   = document.getElementById("idError");
  var clearBtn  = document.getElementById("idClear");
  var consent   = document.getElementById("idConsent");
  var submitBtn = document.getElementById("idSubmit");
  var resultEl  = document.getElementById("idResult");
  var badge     = document.getElementById("idStatusBadge");

  var objectUrl = null;
  var picked = null;

  function revoke() {
    if (objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null; }
  }

  function showError(message) {
    errorEl.textContent = message;
    errorEl.hidden = !message;
  }

  function setBadge(status) {
    badge.textContent = status;
    badge.className = "state-badge" +
      (status === "승인" ? " ok" :
       status === "검토 중" ? " warn" :
       status === "재제출" ? " risk" : "");
  }

  function clearFile() {
    revoke();
    picked = null;
    fileInput.value = "";
    preview.innerHTML = '<span id="idPreviewEmpty">아직 선택된 이미지가 없습니다</span>';
    clearBtn.disabled = true;
    showError("");
    syncSubmit();
  }

  function syncSubmit() {
    submitBtn.disabled = !(picked && consent.checked);
  }

  fileInput.addEventListener("change", function () {
    var f = fileInput.files && fileInput.files[0];
    if (!f) { clearFile(); return; }

    if (ALLOWED.indexOf(f.type) === -1) {
      clearFile();
      showError("JPG, PNG, WEBP 형식만 제출할 수 있습니다. 선택한 형식: " + (f.type || "알 수 없음"));
      return;
    }
    if (f.size > MAX_BYTES) {
      clearFile();
      showError("파일이 10MB를 넘습니다(" + (f.size / 1048576).toFixed(1) + "MB). 더 낮은 화질로 다시 촬영해 주세요.");
      return;
    }

    revoke();                       /* 이전 미리보기 URL 먼저 해제 */
    objectUrl = URL.createObjectURL(f);
    picked = f;

    var img = document.createElement("img");
    img.alt = "제출할 신분증 미리보기";
    img.src = objectUrl;
    preview.innerHTML = "";
    preview.appendChild(img);

    clearBtn.disabled = false;
    showError("");
    syncSubmit();
  });

  clearBtn.addEventListener("click", clearFile);
  consent.addEventListener("change", syncSubmit);

  submitBtn.addEventListener("click", function () {
    if (!picked || !consent.checked) return;

    submitBtn.disabled = true;
    setBadge("검토 중");
    resultEl.textContent = "제출된 이미지를 검토하는 중입니다… (데모 시뮬레이션)";
    E.patch(function (s) {
      s.identity.status = "검토 중";
      s.identity.fileName = picked.name;
    });

    /* 800ms 후 데모 승인 — 실제 심사·전송이 아니다 */
    window.setTimeout(function () {
      setBadge("승인");
      resultEl.textContent = "신분 확인이 완료되었습니다(데모 자동 승인). 사전점검 화면에서 상태를 새로고침하세요.";
      E.patch(function (s) {
        s.identity.status = "승인";
        s.identity.submittedAt = new Date().toISOString();
        s.checks.identity = "pass";
      });
      E.toast("신분 확인 완료 (데모)", "ok");
      submitBtn.disabled = false;
      submitBtn.textContent = "다시 제출하기";
    }, 800);
  });

  window.addEventListener("pagehide", revoke);

  /* 초기 상태 복원 */
  (function init() {
    var st = E.load();
    setBadge(st.identity.status || "미제출");
    if (st.identity.status === "승인") {
      resultEl.textContent = "이미 신분 확인이 완료된 상태입니다(데모). 필요하면 다시 제출할 수 있습니다.";
    }
    syncSubmit();
  })();
})();
