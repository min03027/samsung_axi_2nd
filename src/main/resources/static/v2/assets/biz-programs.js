(function () {
  "use strict";
  var cards = Array.prototype.slice.call(document.querySelectorAll(".biz-program-card"));
  document.querySelectorAll("[data-filter]").forEach(function (button) {
    button.addEventListener("click", function () {
      var value = button.dataset.filter;
      document.querySelectorAll("[data-filter]").forEach(function (item) { item.setAttribute("aria-pressed", String(item === button)); });
      cards.forEach(function (card) { card.hidden = value !== "all" && card.dataset.job !== value; });
    });
  });

  var dialog = document.getElementById("program-dialog");
  var dialogContent = document.getElementById("program-dialog-content");
  function details(card) {
    return "<span class=\"eyebrow\">" + card.dataset.program + "</span><h2>" + card.dataset.title + "</h2><dl><dt>권장 대상</dt><dd>" + card.dataset.target + "</dd><dt>권장 시간</dt><dd>" + card.dataset.time + "</dd><dt>진행 방식</dt><dd>" + card.dataset.mode + "</dd><dt>커리큘럼</dt><dd>" + card.dataset.curriculum + "</dd><dt>예상 산출물</dt><dd>" + card.dataset.output + "</dd><dt>지원 안내</dt><dd>정부지원·내일배움카드 적용은 사업장·참여자·과정 승인 조건 확인 후 확정됩니다.</dd></dl><div class=\"row row--wrap\"><a class=\"btn btn--primary\" href=\"/v2/site/biz/index.html?program=" + encodeURIComponent(card.dataset.job) + "#biz-contact\">이 과정 문의</a><a class=\"btn btn--outline\" href=\"/v2/site/biz/cases.html\">관련 사례 보기</a></div>";
  }
  document.querySelectorAll("[data-program-detail]").forEach(function (button) {
    button.addEventListener("click", function () {
      dialogContent.innerHTML = details(button.closest(".biz-program-card"));
      dialog.showModal();
    });
  });
  if (dialog) {
    dialog.querySelector(".biz-program-dialog__close").addEventListener("click", function () { dialog.close(); });
    dialog.addEventListener("click", function (event) { if (event.target === dialog) dialog.close(); });
  }

  var programSelect = document.getElementById("report-program");
  cards.forEach(function (card, index) {
    var option = document.createElement("option");
    option.value = String(index);
    option.textContent = card.dataset.program + " · " + card.dataset.title;
    programSelect.appendChild(option);
  });
  document.getElementById("biz-report-form").addEventListener("submit", function (event) {
    event.preventDefault();
    var card = cards[Number(programSelect.value) || 0];
    var company = document.getElementById("report-company").value || "우리 조직";
    var count = document.getElementById("report-count").value || card.dataset.target;
    var time = document.getElementById("report-time").value;
    var goal = document.getElementById("report-goal").value || card.querySelector("p").textContent;
    document.getElementById("biz-report-preview").innerHTML = "<span>교육 도입 검토안</span><h3>" + company + "<br>" + card.dataset.title + "</h3><dl><dt>교육 대상</dt><dd>" + count + "</dd><dt>교육 시간</dt><dd>" + time + "</dd><dt>교육 목표</dt><dd>" + goal + "</dd><dt>주요 구성</dt><dd>" + card.dataset.curriculum + "</dd><dt>예상 산출물</dt><dd>" + card.dataset.output + "</dd><dt>기대 효과</dt><dd>반복 업무 시간을 줄이고 팀이 함께 사용할 수 있는 기준과 결과물을 확보합니다.</dd><dt>유사 사례</dt><dd>익명 공개 기업교육 사례에서 관계 유형과 과제별 사례를 확인할 수 있습니다.</dd></dl><p class=\"biz-report-note\">지원 여부와 최종 구성은 상담 및 운영 조건 확인 후 확정됩니다. 발송·관리자 저장은 LXP 관리자에서 진행합니다.</p><div class=\"row row--wrap\"><button class=\"btn btn--primary\" type=\"button\" onclick=\"window.print()\">인쇄·PDF 저장</button><a class=\"btn btn--outline\" href=\"/v2/site/biz/cases.html\">유사 사례 보기</a></div>";
  });
})();
