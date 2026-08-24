(() => {
  const allowed = new Set(['20136','20135','20126','20117','20116','20106','20040','20039','20038','20037','20036','20035','20034','20031','20030','20029','20028','20027','20026','20018','20017','20016','20015','20014','20013','20012','20011']);
  const id = new URLSearchParams(location.search).get('id') || '';
  const loading = document.querySelector('[data-review-loading]');
  const detail = document.querySelector('[data-review-detail]');
  const error = document.querySelector('[data-review-error]');
  const fail = () => { loading.hidden = true; detail.hidden = true; error.hidden = false; };
  if (!allowed.has(id)) { fail(); return; }
  fetch(`/v2/assets/reviews/content/${id}.html?v=20260824-01`)
    .then(response => { if (!response.ok) throw new Error('not found'); return response.text(); })
    .then(html => {
      document.querySelector('[data-review-cover]').src = `/v2/assets/reviews/${id}.png`;
      document.querySelector('[data-review-body]').innerHTML = html;
      const heading = document.querySelector('[data-review-body] h1');
      if (heading) document.title = `${heading.textContent.trim()} — ${window.BRAND?.name || '삼성AXI'}`;
      loading.hidden = true;
      detail.hidden = false;
    })
    .catch(fail);
})();
