(function () {
  "use strict";
  var defaults = {
    home:{title:"삼성AXI — 6개월 뒤, 설명할 수 있는 실력을 만드세요",description:"직접 만든 결과물과 설명할 수 있는 실력을 남기는 AI 실무 교육.",url:"/v2/index.html"},
    class:{title:"몰입클라쓰 전체 과정 — 삼성AXI",description:"데이터·로봇·AIoT·스마트팩토리·클라우드 실무 과정을 비교하고 나에게 맞는 과정을 찾으세요.",url:"/v2/site/class/index.html"},
    campus:{title:"취업캠퍼스 — 삼성AXI",description:"공부하는 오늘이 취업하는 내일이 되도록 과정·취업지원·시설·상담을 한곳에서 확인하세요.",url:"/v2/site/campus/index.html"},
    reviews:{title:"수료생 인터뷰 — 삼성AXI",description:"삼성AXI 수료생의 교육 경험과 프로젝트, 취업 전환 이후의 현재 직무를 확인하세요.",url:"/v2/site/class/reviews.html"},
    biz:{title:"비즈워크래프트 — 삼성AXI",description:"AI를 배우는 조직에서 AI로 일하는 조직으로, 진단부터 교육과 AX 정착까지 설계합니다.",url:"/v2/site/biz/index.html"},
    cases:{title:"기업교육 사례 — 삼성AXI",description:"직무·산업·교육시간·기술별 비즈워크래프트 기업교육 사례를 확인하세요.",url:"/v2/site/biz/cases.html"}
  };
  var image = "/v2/assets/axi-share-card.svg";
  var selected = document.querySelector("[data-seo-page]");
  if (!selected) return;
  var title = document.querySelector("[data-seo-title]");
  var description = document.querySelector("[data-seo-description]");
  var imageInput = document.querySelector("[data-seo-image]");
  var previewLink = document.querySelector("[data-preview-page]");
  var storageKey = "lxp.demo.seo.pages.v1";
  var saved = {};
  try { saved = JSON.parse(localStorage.getItem(storageKey) || "{}"); } catch (ignore) { saved = {}; }
  function data() { return Object.assign({}, defaults[selected.value], saved[selected.value] || {}); }
  function showToast(message) { var toast=document.querySelector("[data-seo-toast]"); toast.textContent=message; toast.classList.add("is-visible"); clearTimeout(showToast.timer); showToast.timer=setTimeout(function(){toast.classList.remove("is-visible");},2200); }
  function syncPreview() {
    document.querySelector("[data-title-length]").textContent = title.value.length + " / 60";
    document.querySelector("[data-description-length]").textContent = description.value.length + " / 160";
    document.querySelector("[data-preview-url]").textContent = "samsung-axi-2nd.min0302748.workers.dev" + data().url;
    document.querySelector("[data-preview-title]").textContent = title.value;
    document.querySelector("[data-preview-description]").textContent = description.value;
    document.querySelector("[data-preview-image]").src = imageInput.value || image;
    document.querySelector("[data-share-title]").textContent = title.value;
    document.querySelector("[data-share-description]").textContent = description.value;
  }
  function load() { var page=data(); title.value=page.title; description.value=page.description; imageInput.value=page.image || image; previewLink.href=page.url; syncPreview(); }
  selected.addEventListener("change", load);
  [title,description,imageInput].forEach(function(field){field.addEventListener("input",syncPreview);});
  document.querySelector("[data-save-seo]").addEventListener("click",function(){saved[selected.value]={title:title.value.trim(),description:description.value.trim(),image:imageInput.value.trim()};localStorage.setItem(storageKey,JSON.stringify(saved));showToast("SEO 설정을 시연용으로 저장했습니다.");});
  document.querySelectorAll("[data-seo-tab]").forEach(function(tab){tab.addEventListener("click",function(){document.querySelectorAll("[data-seo-tab]").forEach(function(item){item.setAttribute("aria-selected",String(item===tab));});document.querySelectorAll("[data-seo-panel]").forEach(function(panel){panel.hidden=panel.dataset.seoPanel!==tab.dataset.seoTab;});});});
  document.querySelector("[data-refresh-index]").addEventListener("click",function(){showToast("공개 콘텐츠 14건의 색인을 확인했습니다.");});
  load();
})();
