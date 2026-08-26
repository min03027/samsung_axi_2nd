(function () {
  "use strict";

  var evidence = [
    { id: "cert-01", category: "인증", title: "정보보호 관리체계 인증서", issuer: "공인 인증기관", period: "2026.01.15 ~ 2029.01.14", state: "valid", expiring: false, description: "플랫폼 정보보호 관리체계의 인증 범위와 유효기간을 확인하는 운영 증빙입니다." },
    { id: "ops-01", category: "운영실적", title: "2025 공공 AI 교육 운영실적", issuer: "교육운영본부", period: "2025.01.01 ~ 2025.12.31", state: "valid", expiring: false, description: "과정 수, 수료 인원, 운영시간과 장애 대응 기록을 정리한 연간 실적 자료입니다." },
    { id: "sla-01", category: "SLA", title: "LXP 서비스 수준 협약서", issuer: "인프라운영팀", period: "2026.01.01 ~ 2026.12.31", state: "review", expiring: true, description: "가용성, 장애 응답시간, 복구 목표와 정기 보고 기준을 명시한 협약 자료입니다." },
    { id: "patent-01", category: "특허", title: "학습 이탈 예측 방법 및 시스템", issuer: "특허청", period: "등록 2024.09.20", state: "valid", expiring: false, description: "학습 활동 지표를 활용한 이탈 위험 분석 관련 기술 권리 자료입니다." },
    { id: "cert-02", category: "인증", title: "클라우드 서비스 보안 점검표", issuer: "보안책임자", period: "검토 예정 2026.09.30", state: "draft", expiring: true, description: "클라우드 운영 환경의 접근통제와 데이터 보호 항목을 확인하는 내부 점검 자료입니다." },
    { id: "ops-02", category: "운영실적", title: "장애 대응 모의훈련 결과", issuer: "서비스운영팀", period: "실시 2026.07.18", state: "review", expiring: false, description: "장애 탐지부터 공지, 복구, 사후 분석까지의 모의훈련 결과입니다." }
  ];
  var stateLabel = { valid: "검증 완료", review: "검토 필요", draft: "초안" };

  function esc(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (ch) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
    });
  }

  function toast(message) {
    var old = document.querySelector(".ops-toast");
    if (old) old.remove();
    var node = document.createElement("div");
    node.className = "ops-toast";
    node.setAttribute("role", "status");
    node.textContent = message;
    document.body.appendChild(node);
    window.setTimeout(function () { node.remove(); }, 2600);
  }

  function initList() {
    var body = document.getElementById("evidenceBody");
    if (!body) return;
    var search = document.getElementById("evidenceSearch");
    var category = document.getElementById("evidenceCategory");
    var state = document.getElementById("evidenceState");
    var count = document.getElementById("evidenceCount");

    document.getElementById("evidenceTotal").textContent = evidence.length;
    document.getElementById("evidenceValid").textContent = evidence.filter(function (item) { return item.state === "valid"; }).length;
    document.getElementById("evidenceReview").textContent = evidence.filter(function (item) { return item.state === "review"; }).length;
    document.getElementById("evidenceExpiring").textContent = evidence.filter(function (item) { return item.expiring; }).length;

    function render() {
      var keyword = search.value.trim().toLowerCase();
      var filtered = evidence.filter(function (item) {
        return (!keyword || (item.title + " " + item.issuer).toLowerCase().indexOf(keyword) >= 0) &&
          (category.value === "all" || item.category === category.value) &&
          (state.value === "all" || item.state === state.value);
      });
      count.textContent = "표시 " + filtered.length + "건";
      body.innerHTML = filtered.length ? filtered.map(function (item) {
        return "<tr><td>" + esc(item.category) + "</td><td><strong>" + esc(item.title) + "</strong></td><td>" + esc(item.issuer) +
          "</td><td>" + esc(item.period) + "</td><td><span class=\"ops-status " + item.state + "\">" + stateLabel[item.state] +
          "</span></td><td><a class=\"ops-button\" href=\"/v2/admin/evidence-detail.html?id=" + encodeURIComponent(item.id) + "\">상세 보기</a></td></tr>";
      }).join("") : "<tr><td class=\"ops-empty\" colspan=\"6\">조건에 맞는 증빙이 없습니다.</td></tr>";
    }
    [search, category, state].forEach(function (el) { el.addEventListener(el === search ? "input" : "change", render); });
    document.getElementById("evidenceReset").addEventListener("click", function () { search.value = ""; category.value = "all"; state.value = "all"; render(); search.focus(); });
    document.getElementById("evidenceRegisterBtn").addEventListener("click", function () { toast("1차 시연에서는 등록 화면만 표현합니다. 파일 저장은 2차 연동 범위입니다."); });
    render();
  }

  function initDetail() {
    var title = document.getElementById("detailTitle");
    if (!title) return;
    var id = new URLSearchParams(window.location.search).get("id") || evidence[0].id;
    var item = evidence.find(function (entry) { return entry.id === id; }) || evidence[0];
    var reviewed = window.sessionStorage.getItem("evidence-reviewed-" + item.id) === "1";
    if (reviewed) item = Object.assign({}, item, { state: "valid" });
    document.getElementById("detailCategory").textContent = item.category.toUpperCase() + " · EVIDENCE";
    title.textContent = item.title;
    document.getElementById("detailDescription").textContent = item.description;
    var badge = document.getElementById("detailStatus");
    badge.className = "ops-status " + item.state;
    badge.textContent = stateLabel[item.state];
    var meta = [
      ["자료 번호", item.id.toUpperCase()], ["분류", item.category], ["발급·확인 기관", item.issuer], ["유효기간", item.period],
      ["보관 등급", item.category === "특허" ? "제한 열람" : "내부 공개"], ["최종 확인자", reviewed ? "현재 관리자(데모)" : "운영관리자"]
    ];
    document.getElementById("detailMeta").innerHTML = meta.map(function (row) { return "<div><dt>" + esc(row[0]) + "</dt><dd>" + esc(row[1]) + "</dd></div>"; }).join("");
    document.getElementById("detailTimeline").innerHTML = [
      ["자료 메타데이터 등록", "2026.08.21 14:20"], ["운영 담당자 1차 확인", "2026.08.22 10:05"],
      [item.state === "valid" ? "검증 완료" : "추가 확인 요청", reviewed ? "방금 전(데모)" : "2026.08.25 16:40"]
    ].map(function (row) { return "<li>" + esc(row[0]) + "<time>" + esc(row[1]) + "</time></li>"; }).join("");
    document.getElementById("markReviewedBtn").addEventListener("click", function () {
      window.sessionStorage.setItem("evidence-reviewed-" + item.id, "1");
      badge.className = "ops-status valid"; badge.textContent = stateLabel.valid;
      toast("검토 완료 상태를 이 브라우저에 표시했습니다. 서버에는 저장되지 않습니다.");
    });
  }

  document.addEventListener("DOMContentLoaded", function () { initList(); initDetail(); });
})();
