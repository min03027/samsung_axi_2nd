(function () {
    var header = document.querySelector('.landing-header');
    var toggle = document.querySelector('.menu-toggle');
    var nav = document.querySelector('.landing-nav');

    function updateHeader() {
        if (header) header.classList.toggle('scrolled', window.scrollY > 20);
    }
    updateHeader();
    window.addEventListener('scroll', updateHeader, { passive: true });

    if (toggle && nav) {
        toggle.addEventListener('click', function () {
            var open = nav.classList.toggle('open');
            toggle.setAttribute('aria-expanded', String(open));
        });
        nav.querySelectorAll('a').forEach(function (link) {
            link.addEventListener('click', function () {
                nav.classList.remove('open');
                toggle.setAttribute('aria-expanded', 'false');
            });
        });
    }

    var items = document.querySelectorAll('.reveal');
    if (!('IntersectionObserver' in window)) {
        items.forEach(function (item) { item.classList.add('visible'); });
        return;
    }
    var observer = new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
            if (entry.isIntersecting) {
                entry.target.classList.add('visible');
                entry.target.querySelectorAll('[data-count]').forEach(function (counter) {
                    if (counter.dataset.done) return;
                    counter.dataset.done = 'true';
                    var target = Number(counter.dataset.count);
                    var started = performance.now();
                    function tick(now) {
                        var progress = Math.min((now - started) / 1100, 1);
                        counter.textContent = Math.round(target * (1 - Math.pow(1 - progress, 3)));
                        if (progress < 1) requestAnimationFrame(tick);
                    }
                    requestAnimationFrame(tick);
                });
                observer.unobserve(entry.target);
            }
        });
    }, { threshold: 0.12 });
    items.forEach(function (item, index) {
        item.style.transitionDelay = Math.min(index % 4, 3) * 70 + 'ms';
        observer.observe(item);
    });

    var heroImage = document.querySelector('.hero-image img');
    if (heroImage && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        window.addEventListener('scroll', function () {
            if (window.scrollY < window.innerHeight) {
                heroImage.style.transform = 'scale(1.04) translateY(' + (window.scrollY * 0.035) + 'px)';
            }
        }, { passive: true });
    }
}());
