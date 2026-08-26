(function () {
    "use strict";

    var page = document.querySelector(".certificate-editor-page");
    var paper = document.getElementById("certificatePaper");
    if (!page || !paper) return;

    var form = document.getElementById("certificateEditorForm");
    var presetInput = document.getElementById("certificatePresetInput");
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

    var state = {
        preset: presetInput && presets[presetInput.value] ? presetInput.value : "formal",
        title: titleInput.value,
        issuer: issuerInput.value,
        statement: statementInput.value,
        accent: accentInput.value,
        fields: {}
    };

    function clone(value) {
        return JSON.parse(JSON.stringify(value));
    }

    function readForm() {
        state.title = titleInput.value.trim() || presets[state.preset].title;
        state.issuer = issuerInput.value.trim() || presets[state.preset].issuer;
        state.statement = statementInput.value.trim() || presets[state.preset].statement;
        state.accent = accentInput.value;
        if (presetInput) presetInput.value = state.preset;
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
        saveStatus.textContent = "과정 디자인을 저장하고 이수증 출력에 적용합니다.";
    });

    document.getElementById("certificateResetButton").addEventListener("click", function () {
        selectPreset("formal");
        saveStatus.textContent = "기본 공식형 디자인을 불러왔습니다. 적용하려면 저장 버튼을 눌러 주세요.";
    });

    document.getElementById("certificatePrintButton").addEventListener("click", function () {
        window.print();
    });

    if (form) {
        form.addEventListener("submit", function () {
            readForm();
            if (presetInput) presetInput.value = state.preset;
        });
    }
    render();
}());
