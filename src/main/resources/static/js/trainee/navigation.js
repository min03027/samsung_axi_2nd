(function () {
    'use strict';

    var MOBILE_BREAKPOINT = 1024;

    function initialize(header) {
        var toggle = header.querySelector('.trainee-nav-toggle');
        var nav = header.querySelector('#trainee-global-nav');
        if (!toggle || !nav) return;

        function isMobile() {
            return window.innerWidth <= MOBILE_BREAKPOINT;
        }

        function closeMenu() {
            header.classList.remove('is-open');
            document.body.classList.remove('trainee-nav-open');
            toggle.setAttribute('aria-expanded', 'false');
            toggle.setAttribute('aria-label', '전체 메뉴 열기');
        }

        function openMenu() {
            header.classList.add('is-open');
            document.body.classList.add('trainee-nav-open');
            toggle.setAttribute('aria-expanded', 'true');
            toggle.setAttribute('aria-label', '전체 메뉴 닫기');
        }

        toggle.addEventListener('click', function () {
            if (header.classList.contains('is-open')) closeMenu();
            else openMenu();
        });

        header.querySelectorAll('.gnb-item.has-submenu > .gnb-link').forEach(function (link) {
            link.setAttribute('aria-haspopup', 'true');
            link.setAttribute('aria-expanded', 'false');

            link.addEventListener('click', function (event) {
                if (!isMobile()) return;

                var item = link.closest('.gnb-item');
                if (!item.classList.contains('submenu-open')) {
                    event.preventDefault();
                    header.querySelectorAll('.gnb-item.submenu-open').forEach(function (openItem) {
                        if (openItem !== item) {
                            openItem.classList.remove('submenu-open');
                            var openLink = openItem.querySelector(':scope > .gnb-link');
                            if (openLink) openLink.setAttribute('aria-expanded', 'false');
                        }
                    });
                    item.classList.add('submenu-open');
                    link.setAttribute('aria-expanded', 'true');
                }
            });
        });

        nav.addEventListener('click', function (event) {
            var link = event.target.closest('a');
            if (!link || link.parentElement.classList.contains('gnb-item')) return;
            closeMenu();
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeMenu();
                toggle.focus();
            }
        });

        window.addEventListener('resize', function () {
            if (!isMobile()) {
                closeMenu();
                header.querySelectorAll('.gnb-item.submenu-open').forEach(function (item) {
                    item.classList.remove('submenu-open');
                    var link = item.querySelector(':scope > .gnb-link');
                    if (link) link.setAttribute('aria-expanded', 'false');
                });
            }
        });
    }

    var dismissedAlertIds = new Set();

    function bindAlertLater(popup) {
        if (!popup) return;
        var button = popup.querySelector('[data-alert-later]');
        if (!button || button.dataset.bound === 'true') return;
        button.dataset.bound = 'true';
        button.addEventListener('click', function () {
            var notificationId = popup.dataset.notificationId;
            if (notificationId) dismissedAlertIds.add(String(notificationId));
            popup.remove();
        });
    }

    function csrfInput() {
        var source = document.querySelector('.trainee-site-header form[action$="/logout"] input[type="hidden"]');
        if (!source) return null;
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = source.name;
        input.value = source.value;
        return input;
    }

    function createAlertPopup(data) {
        if (!data || !data.notificationId || document.getElementById('traineeAlertPopup')) return;
        if (dismissedAlertIds.has(String(data.notificationId))) return;

        var backdrop = document.createElement('div');
        backdrop.className = 'trainee-alert-backdrop';
        backdrop.id = 'traineeAlertPopup';
        backdrop.dataset.notificationId = String(data.notificationId);

        var dialog = document.createElement('section');
        dialog.className = 'trainee-alert-dialog';
        dialog.setAttribute('role', 'dialog');
        dialog.setAttribute('aria-modal', 'true');
        dialog.setAttribute('aria-labelledby', 'traineeAlertTitle');

        var kicker = document.createElement('span');
        kicker.className = 'trainee-alert-kicker';
        kicker.textContent = 'NEW ALERT';

        var title = document.createElement('h2');
        title.id = 'traineeAlertTitle';
        title.textContent = data.title || '새 알림';

        var content = document.createElement('p');
        content.textContent = data.content || '확인이 필요한 새 알림이 있습니다.';

        var form = document.createElement('form');
        form.method = 'post';
        form.action = '/trainee/alarm/read';
        var csrf = csrfInput();
        if (csrf) form.appendChild(csrf);

        var idInput = document.createElement('input');
        idInput.type = 'hidden';
        idInput.name = 'ids';
        idInput.value = data.notificationId;
        form.appendChild(idInput);

        var returnInput = document.createElement('input');
        returnInput.type = 'hidden';
        returnInput.name = 'returnTo';
        returnInput.value = data.confirmUrl || '/trainee/alarm';
        form.appendChild(returnInput);

        var later = document.createElement('button');
        later.type = 'button';
        later.className = 'trainee-alert-later';
        later.dataset.alertLater = '';
        later.textContent = '나중에';

        var confirm = document.createElement('button');
        confirm.type = 'submit';
        confirm.className = 'trainee-alert-confirm';
        confirm.textContent = '알림 확인';

        form.appendChild(later);
        form.appendChild(confirm);
        dialog.appendChild(kicker);
        dialog.appendChild(title);
        dialog.appendChild(content);
        dialog.appendChild(form);
        backdrop.appendChild(dialog);
        document.body.appendChild(backdrop);
        bindAlertLater(backdrop);
        confirm.focus();
    }

    function pollAlertPopup() {
        if (document.getElementById('traineeAlertPopup')) return;
        fetch('/trainee/alarm/popup', {
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin'
        }).then(function (response) {
            if (response.status === 204) return null;
            if (!response.ok) throw new Error('알림 조회 실패');
            return response.json();
        }).then(function (data) {
            if (data) createAlertPopup(data);
        }).catch(function () {
            // 일시적인 네트워크 오류는 다음 주기에 다시 확인한다.
        });
    }

    document.querySelectorAll('[data-trainee-header]').forEach(initialize);
    bindAlertLater(document.getElementById('traineeAlertPopup'));
    window.setTimeout(pollAlertPopup, 1000);
    window.setInterval(pollAlertPopup, 15000);
}());
