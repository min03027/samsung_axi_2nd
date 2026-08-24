(function () {
  "use strict";
  var form = document.getElementById("biz-case-filters");
  var cards = Array.prototype.slice.call(document.querySelectorAll(".biz-case"));
  var count = document.getElementById("biz-case-count");
  var empty = document.getElementById("biz-case-empty");
  function applyFilters() {
    var filters = new FormData(form);
    var visible = 0;
    cards.forEach(function (card) {
      var matched = true;
      ["job", "industry", "size", "time", "tech", "task"].forEach(function (key) {
        var value = filters.get(key);
        if (value && value !== "all" && card.dataset[key].split(" ").indexOf(value) === -1) matched = false;
      });
      card.hidden = !matched;
      if (matched) visible += 1;
    });
    count.textContent = "공개 사례 " + visible + "건";
    empty.hidden = visible !== 0;
  }
  form.addEventListener("submit", function (event) {
    event.preventDefault();
    applyFilters();
  });
  form.addEventListener("reset", function () { window.setTimeout(applyFilters, 0); });
  var initial = new URLSearchParams(location.search).get("industry");
  if (initial && form.elements.industry) form.elements.industry.value = initial;
  applyFilters();
})();
