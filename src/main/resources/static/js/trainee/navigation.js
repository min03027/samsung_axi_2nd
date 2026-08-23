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

    document.querySelectorAll('[data-trainee-header]').forEach(initialize);
}());
