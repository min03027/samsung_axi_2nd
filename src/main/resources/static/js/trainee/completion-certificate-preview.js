(function () {
  document.addEventListener("DOMContentLoaded", function () {
    const preview = document.getElementById("certificatePreview");
    const frame = document.getElementById("certificatePreviewFrame");
    const title = document.getElementById("certificatePreviewTitle");
    const closeButton = preview?.querySelector("[data-certificate-close]");
    let opener = null;

    function openCertificate(link) {
      if (!preview || !frame) return;
      opener = link;
      frame.src = link.href;
      if (title) {
        const course = link.dataset.courseLabel;
        title.textContent = course ? course + " 이수증" : "이수증";
      }
      preview.hidden = false;
      document.body.style.overflow = "hidden";
      closeButton?.focus();
    }

    function closeCertificate() {
      if (!preview || preview.hidden) return;
      preview.hidden = true;
      if (frame) frame.src = "about:blank";
      document.body.style.overflow = "";
      opener?.focus();
    }

    document.querySelectorAll("[data-certificate-preview]").forEach(function (link) {
      link.addEventListener("click", function (event) {
        event.preventDefault();
        openCertificate(link);
      });
    });

    closeButton?.addEventListener("click", closeCertificate);
    preview?.addEventListener("click", function (event) {
      if (event.target === preview) closeCertificate();
    });
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape") closeCertificate();
    });
  });
})();
