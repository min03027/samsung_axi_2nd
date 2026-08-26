(function () {
    "use strict";

    var page = document.querySelector(".certificate-editor-page");
    var paper = document.getElementById("certificatePaper");
    if (!page || !paper) return;

    var courseId = page.dataset.courseId || "default";
    var storageKey = "axi-certificate-demo:" + courseId;
    var presetButtons = Array.from(document.querySelectorAll("[data-certificate-preset]"));
    var titleInput = document.getElementById("certificateTitleInput");
    var issuerInput = document.getElementById("certificateIssuerInput");
    var statementInput = document.getElementById("certificateStatementInput");
    var accentInput = document.getElementById("certificateAccentInput");
    var accentOutput = document.getElementById("certificateAccentOutput");
    var titlePreview = document.getElementById("certificatePreviewTitle");
    var issuerPreview = document.getElementById("certificatePreviewIssuer");
    var statementPreview = document.getElementById("certificatePreviewStatement");
    var saveStatus = document.getElementById("certificateSaveStatus");

    var presets = {
        formal: {
            preset: "formal", accent: "#1c3d6e", title: "이 수 증",
            issuer: "Samsung Academy LXP",
            statement: "위 사람은 해당 교육과정을 성실히 이수하였기에 이 증서를 수여합니다.",
            fields: { birth: true, period: true, metrics: true, seal: true }
        },
        tech: {
            preset: "tech", accent: "#2459d9", title: "CERTIFICATE",
            issuer: "Samsung AXI Tech Academy",
            statement: "위 교육생은 실무 프로젝트와 학습 기준을 충족하여 본 과정을 성공적으로 이수하였습니다.",
            fields: { birth: false, period: true, metrics: true, seal: true }
        },
        creative: {
            preset: "creative", accent: "#7c3aed", title: "수료증",
            issuer: "Samsung Academy LXP",
            statement: "배움과 창작의 과정을 완주하고 새로운 가능성을 증명하였기에 이 증서를 수여합니다.",
            fields: { birth: false, period: true, metrics: false, seal: true }
        }
    };

    var state = clone(presets.formal);

    function clone(value) {
        return JSON.parse(JSON.stringify(value));
    }

    function readForm() {
        state.title = titleInput.value.trim() || presets[state.preset].title;
        state.issuer = issuerInput.value.trim() || presets[state.preset].issuer;
        state.statement = statementInput.value.trim() || presets[state.preset].statement;
        state.accent = accentInput.value;
        state.fields = state.fields || {};
        document.querySelectorAll("[data-certificate-toggle]").forEach(function (input) {
            state.fields[input.dataset.certificateToggle] = input.checked;
        });
    }

    function writeForm() {
        titleInput.value = state.title;
        issuerInput.value = state.issuer;
        statementInput.value = state.statement;
        accentInput.value = state.accent;
        document.querySelectorAll("[data-certificate-toggle]").forEach(function (input) {
            input.checked = state.fields[input.dataset.certificateToggle] !== false;
        });
    }

    function render() {
        readForm();
        paper.classList.remove("preset-formal", "preset-tech", "preset-creative");
        paper.classList.add("preset-" + state.preset);
        paper.style.setProperty("--certificate-accent", state.accent);
        titlePreview.textContent = state.title;
        issuerPreview.textContent = state.issuer;
        statementPreview.textContent = state.statement;
        accentOutput.value = state.accent.toUpperCase();
        accentOutput.textContent = state.accent.toUpperCase();

        presetButtons.forEach(function (button) {
            var active = button.dataset.certificatePreset === state.preset;
            button.classList.toggle("is-active", active);
            button.setAttribute("aria-pressed", String(active));
        });
        document.querySelectorAll("[data-certificate-field]").forEach(function (field) {
            field.hidden = state.fields[field.dataset.certificateField] === false;
        });
    }

    function selectPreset(name) {
        state = clone(presets[name]);
        writeForm();
        render();
        saveStatus.textContent = "";
    }

    presetButtons.forEach(function (button) {
        button.addEventListener("click", function () { selectPreset(button.dataset.certificatePreset); });
    });

    [titleInput, issuerInput, statementInput, accentInput].forEach(function (input) {
        input.addEventListener("input", render);
    });
    document.querySelectorAll("[data-certificate-toggle]").forEach(function (input) {
        input.addEventListener("change", render);
    });

    document.getElementById("certificateSaveButton").addEventListener("click", function () {
        readForm();
        try {
            localStorage.setItem(storageKey, JSON.stringify(state));
            saveStatus.textContent = "이 과정의 시연 디자인을 현재 브라우저에 저장했습니다.";
        } catch (error) {
            saveStatus.textContent = "브라우저 저장을 사용할 수 없습니다. 미리보기 변경은 그대로 유지됩니다.";
        }
    });

    document.getElementById("certificateResetButton").addEventListener("click", function () {
        try {
            localStorage.removeItem(storageKey);
        } catch (ignore) {
            // 저장소 접근이 제한된 브라우저에서도 화면 복원은 계속 진행한다.
        }
        selectPreset("formal");
        saveStatus.textContent = "기본 공식형 디자인으로 복원했습니다.";
    });

    document.getElementById("certificatePrintButton").addEventListener("click", function () {
        window.print();
    });

    try {
        var saved = JSON.parse(localStorage.getItem(storageKey));
        if (saved && presets[saved.preset]) {
            state = Object.assign(clone(presets[saved.preset]), saved);
            state.fields = Object.assign({}, presets[saved.preset].fields, saved.fields || {});
        }
    } catch (ignore) {
        try {
            localStorage.removeItem(storageKey);
        } catch (storageError) {
            // 저장소가 막힌 환경에서는 기본 템플릿으로 계속 렌더링한다.
        }
    }
    writeForm();
    render();
}());
