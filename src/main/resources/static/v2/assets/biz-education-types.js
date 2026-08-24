(function () {
  "use strict";

  var tabs = Array.prototype.slice.call(document.querySelectorAll("[data-biz-type-tab]"));
  var panels = Array.prototype.slice.call(document.querySelectorAll("[data-biz-type-panel]"));
  if (!tabs.length || !panels.length) return;

  function selectType(key, moveFocus) {
    tabs.forEach(function (tab) {
      var active = tab.getAttribute("data-biz-type-tab") === key;
      tab.setAttribute("aria-selected", active ? "true" : "false");
      tab.tabIndex = active ? 0 : -1;
      if (active && moveFocus) tab.focus();
    });
    panels.forEach(function (panel) {
      panel.hidden = panel.getAttribute("data-biz-type-panel") !== key;
    });
  }

  tabs.forEach(function (tab, index) {
    tab.addEventListener("click", function () {
      selectType(tab.getAttribute("data-biz-type-tab"), false);
    });
    tab.addEventListener("keydown", function (event) {
      var next = index;
      if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
      else if (event.key === "ArrowLeft") next = (index - 1 + tabs.length) % tabs.length;
      else if (event.key === "Home") next = 0;
      else if (event.key === "End") next = tabs.length - 1;
      else return;
      event.preventDefault();
      selectType(tabs[next].getAttribute("data-biz-type-tab"), true);
    });
  });

  document.querySelectorAll("[data-education-type]").forEach(function (link) {
    link.addEventListener("click", function () {
      var issue = document.getElementById("issue");
      if (issue) issue.value = link.getAttribute("data-education-type") + "의 대상·일정·운영 방식을 문의합니다.";
    });
  });
})();
