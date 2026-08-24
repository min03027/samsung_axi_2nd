(() => {
  const buttons = [...document.querySelectorAll('[data-axi-filter]')];
  const cards = [...document.querySelectorAll('[data-axi-category]')];
  if (!buttons.length) return;
  const courseKeys = ['data','factory','aiot','robot','cloud','video','uiux','japan','usa','china','cooking','game','design','mobility','system','adsp','sqld','bigdata-cert','engineer'];
  cards.forEach((card, index) => {
    const key = courseKeys[index];
    card.querySelectorAll('a[href="/v2/site/class/course.html"]').forEach(link => { link.href = '/v2/site/class/course.html?course=' + key; });
    card.querySelectorAll('a[href="/v2/site/class/apply.html"]').forEach(link => {
      link.href = card.dataset.axiCategory === 'highschool' ? '/v2/site/campus/counsel.html?course=' + key : '/v2/site/class/apply.html?course=' + key;
    });
  });
  const trackKeys = ['data','factory','aiot','robot','cloud'];
  document.querySelectorAll('.track-card').forEach((card, index) => {
    const link = card.querySelector('a[href^="/v2/site/class/course.html"]');
    if (link) link.href = '/v2/site/class/course.html?course=' + trackKeys[index];
  });
  buttons.forEach(button => button.addEventListener('click', () => {
    const category = button.dataset.axiFilter;
    buttons.forEach(item => item.setAttribute('aria-pressed', String(item === button)));
    document.querySelectorAll('[data-axi-category]').forEach(card => {
      card.hidden = category !== 'all' && card.dataset.axiCategory !== category;
    });
  }));
})();
