(function () {
  "use strict";

  var tabs = Array.prototype.slice.call(document.querySelectorAll("[data-lxp-tab]"));
  var panels = Array.prototype.slice.call(document.querySelectorAll("[data-lxp-panel]"));
  if (!tabs.length || !panels.length) return;

  function activate(key, moveFocus) {
    tabs.forEach(function (tab) {
      var selected = tab.getAttribute("data-lxp-tab") === key;
      tab.setAttribute("aria-selected", selected ? "true" : "false");
      tab.setAttribute("tabindex", selected ? "0" : "-1");
      if (selected && moveFocus) tab.focus();
    });
    panels.forEach(function (panel) {
      var selected = panel.getAttribute("data-lxp-panel") === key;
      panel.hidden = !selected;
      panel.classList.toggle("is-active", selected);
    });
  }

  tabs.forEach(function (tab, index) {
    tab.addEventListener("click", function () {
      activate(tab.getAttribute("data-lxp-tab"), false);
    });
    tab.addEventListener("keydown", function (event) {
      var nextIndex = index;
      if (event.key === "ArrowDown" || event.key === "ArrowRight") nextIndex = (index + 1) % tabs.length;
      else if (event.key === "ArrowUp" || event.key === "ArrowLeft") nextIndex = (index - 1 + tabs.length) % tabs.length;
      else if (event.key === "Home") nextIndex = 0;
      else if (event.key === "End") nextIndex = tabs.length - 1;
      else return;
      event.preventDefault();
      activate(tabs[nextIndex].getAttribute("data-lxp-tab"), true);
    });
  });

  activate("home", false);
})();
