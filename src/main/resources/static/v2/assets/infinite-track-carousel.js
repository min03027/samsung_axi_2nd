/* ============================================================
   infinite-track-carousel.js — KDT 트랙 카드 무한 캐러셀

   맨 끝에서 맨 처음으로 되감기지 않고 계속 같은 방향으로 돈다.
   방법: 카드 두 벌씩을 앞뒤에 붙이고 중앙에서 시작한다.
   버튼·휠·드래그가 멈날 때 같은 카드의 중앙 위치로 조용히 보정한다.
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

  const cloneCard = card => {
    const clone = card.cloneNode(true);
    clone.dataset.clone = 'true';
    clone.setAttribute('aria-hidden', 'true');
    clone.removeAttribute('role');
    clone.querySelectorAll('a,button').forEach(el => el.tabIndex = -1);
    return clone;
  };
  for (let set = 0; set < 2; set += 1) {
    originals.forEach(card => deck.appendChild(cloneCard(card)));
    originals.slice().reverse().forEach(card => deck.insertBefore(cloneCard(card), deck.firstChild));
  }

  const cards = [...deck.querySelectorAll('.track-card')];
  const CENTRAL_START = N * 2;
  const CENTRAL_END = N * 3;
  let index = CENTRAL_START;
  let silent = false; // 조용한 위치 보정 중 — scroll 리스너가 끼어들지 않게
  let moving = false;
  let timer;

  const posOf = i => cards[i].offsetLeft - (deck.clientWidth - cards[i].clientWidth) / 2;

  const paint = () => {
    const live = (index % N + N) % N;
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

  const normalize = () => {
    // 양쪽 복제 구간에 도착하면 같은 카드인 중앙 원본으로 조용히 옮긴다.
    if (index < CENTRAL_START) {
      while (index < CENTRAL_START) index += N;
    } else if (index >= CENTRAL_END) {
      while (index >= CENTRAL_END) index -= N;
    }
    else return;
    silent = true;
    scrollTo(index, 'auto');
    requestAnimationFrame(() => { silent = false; });
  };

  const settle = () => {
    afterScroll(() => {
      silent = true;
      normalize();
      moving = false;
      requestAnimationFrame(() => { silent = false; });
    });
  };

  const advance = step => {
    if (moving) return;
    moving = true;
    index += step;
    scrollTo(index, 'smooth');
    paint();
    settle();
  };

  const goTo = live => {
    if (moving) return;
    moving = true;
    index = CENTRAL_START + live;
    scrollTo(index, 'smooth');
    paint();
    settle();
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

  // 손가락이나 마우스로 직접 끝까지 밀어도 물리적인 끝이 보이기 전에
  // 같은 카드의 중앙 묶음으로 이동한다. scrollend가 없는 브라우저도 대응한다.
  let manualSettleTimer;
  const settleManualScroll = () => {
    if (moving || silent) return;
    normalize();
  };
  deck.addEventListener('scrollend', settleManualScroll);
  deck.addEventListener('scroll', () => {
    clearTimeout(manualSettleTimer);
    manualSettleTimer = setTimeout(settleManualScroll, 160);
  }, { passive: true });

  deck.addEventListener('mouseenter', stop);
  deck.addEventListener('mouseleave', start);
  deck.addEventListener('focusin', stop);
  deck.addEventListener('focusout', start);
  deck.addEventListener('pointerdown', stop);
  deck.addEventListener('pointerup', start);

  scrollTo(index, 'auto');
  paint();
  start();
})();
