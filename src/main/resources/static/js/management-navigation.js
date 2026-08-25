(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var toggle = document.querySelector('.management-mobile-toggle');
        var sidebar = document.getElementById('managementSidebar');
        var backdrop = document.querySelector('.management-nav-backdrop');
        if (!toggle || !sidebar || !backdrop) return;

        function closeMenu() {
            document.body.classList.remove('management-nav-open');
            toggle.setAttribute('aria-expanded', 'false');
            toggle.setAttribute('aria-label', '관리 메뉴 열기');
        }

        toggle.addEventListener('click', function () {
            var willOpen = !document.body.classList.contains('management-nav-open');
            document.body.classList.toggle('management-nav-open', willOpen);
            toggle.setAttribute('aria-expanded', String(willOpen));
            toggle.setAttribute('aria-label', willOpen ? '관리 메뉴 닫기' : '관리 메뉴 열기');
        });

        backdrop.addEventListener('click', closeMenu);
        sidebar.addEventListener('click', function (event) {
            if (window.innerWidth <= 900 && event.target.closest('a')) closeMenu();
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') closeMenu();
        });
        window.addEventListener('resize', function () {
            if (window.innerWidth > 900) closeMenu();
        });
    });
}());
