/* ============================================================
   track-slider.js — KDT 트랙 카드 무한 캐러셀

   맨 끝에서 맨 처음으로 되감기지 않고 계속 같은 방향으로 돈다.
   방법: 카드 한 벌을 복제해 뒤에 붙여서 "마지막 다음에 첫 카드"가
   실제로 존재하게 만들고, 복제 구간에 들어가면 스크롤이 멈춘 뒤
   같은 그림의 원본 위치로 조용히 되돌린다(사용자는 알아채지 못한다).
   ============================================================ */
(() => {
  const deck = document.querySelector('.track-deck');
  if (!deck) return;

  const originals = [...deck.querySelectorAll('.track-card')];
  const N = originals.length;
  if (N < 2) return;

  const dots = [...document.querySelectorAll('.track-deck__nav i')];
  const prev = document.querySelector('[data-track-prev]');
  const next = document.querySelector('[data-track-next]');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;

  // 복제본은 보조기기·탭 이동에서 빠지도록 감춘다.
  originals.forEach(card => {
    const clone = card.cloneNode(true);
    clone.dataset.clone = 'true';
    clone.setAttribute('aria-hidden', 'true');
    clone.removeAttribute('role');
    clone.querySelectorAll('a,button').forEach(el => el.tabIndex = -1);
    deck.appendChild(clone);
  });

  const cards = [...deck.querySelectorAll('.track-card')];
  let index = 0;      // cards 기준 위치 (0 ~ 2N-1)
  let silent = false; // 조용한 위치 보정 중 — scroll 리스너가 끼어들지 않게
  let timer;

  const posOf = i => cards[i].offsetLeft - (deck.clientWidth - cards[i].clientWidth) / 2;

  const paint = () => {
    const live = index % N;
    cards.forEach((c, i) => c.classList.toggle('is-active', i % N === live));
    dots.forEach((d, i) => d.classList.toggle('is-active', i === live));
  };

  const scrollTo = (i, behavior) => {
    deck.scrollTo({ left: posOf(i), behavior: reduced ? 'auto' : behavior });
  };

  // 스크롤이 멈춘 뒤 실행 (scrollend 미지원 브라우저는 타임아웃으로)
  const afterScroll = fn => {
    let done = false;
    const run = () => { if (done) return; done = true; deck.removeEventListener('scrollend', run); fn(); };
    deck.addEventListener('scrollend', run, { once: true });
    setTimeout(run, 700);
  };

  const settle = () => {
    // 복제 구간(N 이상)에 있으면 같은 그림의 원본 위치로 되돌린다
    if (index < N) return;
    afterScroll(() => {
      silent = true;
      index -= N;
      scrollTo(index, 'auto');
      requestAnimationFrame(() => { silent = false; });
    });
  };

  const advance = step => {
    if (step < 0 && index === 0) {
      // 뒤로 갈 때는 먼저 복제 구간의 같은 그림으로 순간이동한 뒤 왼쪽으로 부드럽게
      silent = true;
      index = N;
      scrollTo(index, 'auto');
      requestAnimationFrame(() => {
        silent = false;
        index -= 1;
        scrollTo(index, 'smooth');
        paint();
      });
      return;
    }
    index += step;
    scrollTo(index, 'smooth');
    paint();
    settle();
  };

  const goTo = live => {
    index = live;
    scrollTo(index, 'smooth');
    paint();
  };

  const stop = () => clearInterval(timer);
  const start = () => { stop(); if (!reduced) timer = setInterval(() => advance(1), 4500); };

  prev?.addEventListener('click', () => { advance(-1); start(); });
  next?.addEventListener('click', () => { advance(1); start(); });
  dots.forEach((dot, i) => dot.addEventListener('click', () => { goTo(i); start(); }));

  let wheelLocked = false;
  deck.addEventListener('wheel', event => {
    if (wheelLocked || Math.abs(event.deltaY) < 8) return;
    event.preventDefault();
    wheelLocked = true;
    advance(event.deltaY > 0 ? 1 : -1);
    start();
    setTimeout(() => { wheelLocked = false; }, 620);
  }, { passive: false });

  // 손으로 끌었을 때 현재 위치를 따라잡는다
  let frame;
  deck.addEventListener('scroll', () => {
    if (silent) return;
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => {
      if (silent) return;
      const center = deck.scrollLeft + deck.clientWidth / 2;
      let closest = 0;
      cards.forEach((c, i) => {
        if (Math.abs(c.offsetLeft + c.clientWidth / 2 - center) <
            Math.abs(cards[closest].offsetLeft + cards[closest].clientWidth / 2 - center)) closest = i;
      });
      index = closest;
      paint();
    });
  });

  deck.addEventListener('mouseenter', stop);
  deck.addEventListener('mouseleave', start);
  deck.addEventListener('focusin', stop);
  deck.addEventListener('focusout', start);
  deck.addEventListener('pointerdown', stop);
  deck.addEventListener('pointerup', start);

  paint();
  start();
})();
