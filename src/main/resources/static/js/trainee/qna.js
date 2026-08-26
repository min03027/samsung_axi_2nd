(function () {
  const $ = (id) => document.getElementById(id);

  function openModal(el) {
    if (!el) return;
    el.classList.add("open");
    el.setAttribute("aria-hidden", "false");
    document.body.style.overflow = "hidden";
  }

  function closeModal(el) {
    if (!el) return;
    el.classList.remove("open");
    el.setAttribute("aria-hidden", "true");
    document.body.style.overflow = "";
  }

  document.addEventListener("DOMContentLoaded", () => {

    // ===== 1️⃣ 질문하기 모달 =====
    const btnOpenAsk = $("btnOpenAsk");
    const askModal = $("askModal");
    const btnSubmitAsk = $("btnSubmitAsk");

    const aTitle = $("aTitle");
    const aBody = $("aBody");
    const aVisibility = $("aVisibility");
    const aCategory = $("aCategory");
    const askTitle = $("askTitle");
    const askError = $("askError");

    btnOpenAsk?.addEventListener("click", () => {
      if (askError) {
        askError.hidden = true;
        askError.textContent = "";
      }
      openModal(askModal);
    });

    // 취업 준비 홈의 진로·취업 상담 버튼에서 들어오면 일반 튜터링이 아니라
    // 담당자 답변이 남는 비공개 Q&A 작성창을 바로 연다.
    const query = new URLSearchParams(window.location.search);
    if (query.get("ask") === "career") {
      if (askTitle) askTitle.textContent = "진로·취업 상담 요청";
      if (aVisibility) aVisibility.value = "private";
      if (aCategory) aCategory.value = "ETC";
      if (aTitle && !aTitle.value.trim()) aTitle.value = "진로·취업 상담 요청";
      if (aBody) {
        aBody.placeholder = "희망 직무, 준비 중인 사항, 상담받고 싶은 내용을 적어주세요.";
      }
      openModal(askModal);
      window.setTimeout(() => aBody?.focus(), 0);
    }

    // 모달 바깥 클릭 닫기
    askModal?.addEventListener("click", (e) => {
      if (e.target === askModal) closeModal(askModal);
    });

    // 닫기 버튼
    document.querySelectorAll('[data-close="askModal"]').forEach((btn) => {
      btn.addEventListener("click", () => closeModal(askModal));
    });

    // ESC 닫기
    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && askModal?.classList.contains("open")) {
        closeModal(askModal);
      }
    });

    // ===== 2️⃣ 제목/내용 유효성 검사 (서버 @Valid 전 클라이언트 1차 검사) =====
    // 모달 자체가 <form id="askForm" method="post" action="/trainee/qna"> 라
    // 검사를 통과하면 그대로 submit 된다 (서버 연동 완료).
    const askForm = $("askForm");

    askForm?.addEventListener("submit", (e) => {
      const title = aTitle?.value.trim() || "";
      const body = aBody?.value.trim() || "";

      if (!title) {
        e.preventDefault();
        showError("제목을 입력해주세요.");
        return;
      }

      if (!body) {
        e.preventDefault();
        showError("내용을 입력해주세요.");
      }
    });

    // 검증 실패로 서버가 폼을 다시 그린 경우 모달을 열어둔다.
    if (askError && !askError.hidden && askError.textContent.trim()) {
      openModal(askModal);
    }

    function showError(message) {
      if (!askError) return;
      askError.hidden = false;
      askError.textContent = message;
    }

    // ===== 3️⃣ tr 클릭 → 상세 페이지 이동 (서버 id 기반) =====
    const rows = document.querySelectorAll("#qnaTbody tr[data-item-id]");

    rows.forEach((row) => {
      row.style.cursor = "pointer";

      row.addEventListener("click", (e) => {
        // 제목 링크는 자체 href 로 이동한다 (중복 이동 방지)
        if (e.target.closest("a")) return;
        const id = row.getAttribute("data-item-id");
        if (id) window.location.href = "/trainee/qna/" + id;
      });
    });

  });
})();
