(() => {
  const root = document.querySelector('[data-career-carousel]');
  if (!root) return;
  const track = root.querySelector('[data-career-track]');
  const slides = [...track.children];
  const dots = root.querySelector('[data-career-dots]');
  const current = root.querySelector('[data-career-current]');
  let index = 0;
  let timer;
  slides.forEach((_, slideIndex) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.setAttribute('aria-label', `${slideIndex + 1}번째 인터뷰 보기`);
    button.addEventListener('click', () => show(slideIndex, true));
    dots.appendChild(button);
  });
  const dotButtons = [...dots.children];
  function show(nextIndex, manual = false) {
    index = (nextIndex + slides.length) % slides.length;
    track.style.transform = `translate3d(${-index * 100}%,0,0)`;
    current.textContent = String(index + 1).padStart(2, '0');
    dotButtons.forEach((dot, dotIndex) => dot.setAttribute('aria-current', dotIndex === index ? 'true' : 'false'));
    if (manual) restart();
  }
  function restart() {
    clearInterval(timer);
    if (!matchMedia('(prefers-reduced-motion: reduce)').matches) timer = setInterval(() => show(index + 1), 5600);
  }
  root.querySelector('[data-career-prev]').addEventListener('click', () => show(index - 1, true));
  root.querySelector('[data-career-next]').addEventListener('click', () => show(index + 1, true));
  root.addEventListener('focusin', () => clearInterval(timer));
  root.addEventListener('focusout', restart);
  document.addEventListener('visibilitychange', () => document.hidden ? clearInterval(timer) : restart());
  show(0);
  restart();
})();
