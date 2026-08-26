(function () {
    'use strict';

    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

    const state = {
        mode: '혼합 채점',
        preset: '알고리즘·코딩',
        language: 'Python 3.12',
        cases: 3,
        score: 100
    };

    function toast(message) {
        const node = $('#graderToast');
        node.textContent = message;
        node.classList.add('is-visible');
        window.clearTimeout(toast.timer);
        toast.timer = window.setTimeout(() => node.classList.remove('is-visible'), 2400);
    }

    function updateSummary() {
        const checked = $('input[name="gradingMode"]:checked');
        state.mode = checked ? checked.dataset.label : state.mode;
        state.preset = $('#graderPreset').selectedOptions[0].textContent;
        state.language = $('#runtime').selectedOptions[0].textContent;
        state.cases = $$('#caseBody tr').length;
        state.score = $$('.case-points input').reduce((sum, input) => sum + (Number(input.value) || 0), 0);

        $('#summaryExam').textContent = $('#targetExam').selectedOptions[0].textContent;
        $('#summaryMode').textContent = state.mode;
        $('#summaryPreset').textContent = state.preset;
        $('#summaryRuntime').textContent = state.language;
        $('#summaryCases').textContent = `${state.cases}개 · ${state.score}점`;
    }

    function bindSteps() {
        $$('.grader-progress button').forEach(button => {
            button.addEventListener('click', () => {
                $$('.grader-progress button').forEach(item => item.classList.remove('is-active'));
                button.classList.add('is-active');
                const target = document.getElementById(button.dataset.target);
                if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
        });
    }

    function addCase(values = {}) {
        const index = $$('#caseBody tr').length + 1;
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${index}</td>
            <td><input aria-label="테스트 입력 ${index}" value="${values.input || ''}" placeholder="예: [1, 2, 3]"></td>
            <td><input aria-label="기대 출력 ${index}" value="${values.output || ''}" placeholder="예: 6"></td>
            <td class="case-points"><input aria-label="배점 ${index}" type="number" min="0" max="100" value="${values.points || 10}"></td>
            <td><button type="button" class="remove-case" aria-label="테스트 케이스 ${index} 삭제">삭제</button></td>`;
        $('#caseBody').appendChild(row);
        row.querySelectorAll('input').forEach(input => input.addEventListener('input', updateSummary));
        updateSummary();
    }

    document.addEventListener('DOMContentLoaded', () => {
        bindSteps();
        $$('input[name="gradingMode"], #targetExam, #graderPreset, #runtime').forEach(input => {
            input.addEventListener('change', updateSummary);
        });
        $$('.score-rule input').forEach(input => input.addEventListener('input', updateSummary));

        $('#addCase').addEventListener('click', () => addCase());
        $('#caseBody').addEventListener('click', event => {
            const removeButton = event.target.closest('.remove-case');
            if (!removeButton) return;
            removeButton.closest('tr').remove();
            $$('#caseBody tr').forEach((item, i) => { item.children[0].textContent = i + 1; });
            updateSummary();
        });
        $('#validateGrader').addEventListener('click', () => {
            const status = $('#graderStatus');
            status.classList.add('is-ready');
            status.textContent = `검증 준비 완료 · 테스트 케이스 ${state.cases}개 · 총 ${state.score}점`;
            toast('화면 검증을 완료했습니다. 실제 코드 실행은 2차 연동 범위입니다.');
        });
        $('#applyGrader').addEventListener('click', () => {
            updateSummary();
            window.localStorage.setItem('lxp-grader-demo', JSON.stringify(state));
            toast('선택한 시험에 그레이더 설정을 적용한 시연 상태로 표시했습니다.');
        });
        $('#saveDraft').addEventListener('click', () => {
            updateSummary();
            window.localStorage.setItem('lxp-grader-demo', JSON.stringify(state));
            toast('이 브라우저에 임시 설정을 저장했습니다.');
        });

        updateSummary();
    });
})();
