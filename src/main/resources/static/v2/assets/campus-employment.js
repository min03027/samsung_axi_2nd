(() => {
  document.querySelectorAll('[data-employment-marquee]').forEach(viewport => {
    const track = viewport.querySelector('.employment-partners__track');
    const group = viewport.querySelector('[data-employment-marquee-group]');
    if (!track || !group || track.children.length > 1) return;

    const duplicate = group.cloneNode(true);
    duplicate.removeAttribute('data-employment-marquee-group');
    duplicate.setAttribute('aria-hidden', 'true');
    track.appendChild(duplicate);
    viewport.dataset.ready = 'true';
  });

  const root = document.querySelector('[data-employment-courses]');
  const catalog = window.COURSE_CATALOG || {};
  if (!root || !Object.keys(catalog).length) return;

  const statusEngine = window.AXI_RECRUITMENT_STATUS;
  const profiles = {
    cloud: { adminStatus:'recruiting', deadline:'2026-08-28' },
    cooking: { adminStatus:'pre-recruiting', deadline:'' },
    adsp: { adminStatus:'rolling', deadline:'' }
  };
  const featured = ['cloud', 'cooking', 'adsp'];

  const escapeHtml = value => String(value || '').replace(/[&<>"']/g, character => ({
    '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'
  })[character]);

  const statusFor = key => {
    const profile = profiles[key];
    const statusKey = statusEngine.derive(profile);
    return { key:statusKey, label:statusEngine.labels[statusKey] };
  };

  const actionFor = (key, status) => {
    if (status.key === 'pre-recruiting') {
      return { label:'사전 상담', href:`/v2/site/campus/counsel.html?course=${key}` };
    }
    if (status.key === 'closed') {
      return { label:'다음 일정 문의', href:`/v2/site/campus/counsel.html?course=${key}` };
    }
    return { label:'신청하기', href:`/v2/site/class/apply.html?course=${key}` };
  };

  root.innerHTML = featured.map((key, index) => {
    const course = catalog[key];
    if (!course) return '';
    const status = statusFor(key);
    const action = actionFor(key, status);
    return `
      <article class="employment-course-card" data-status="${status.key}">
        <div class="employment-course-card__top">
          <span class="employment-course-card__index">0${index + 1}</span>
          <span class="employment-course-card__status">${escapeHtml(status.label)}</span>
        </div>
        <div class="employment-course-card__visual" aria-hidden="true">
          <img src="/v2/assets/outcome-icons/${key}.png" alt="" loading="lazy">
        </div>
        <div class="employment-course-card__body">
          <p>${escapeHtml(course.cat)}</p>
          <h3>${escapeHtml(course.title)}</h3>
          <dl>
            <div><dt>교육 기간</dt><dd>${escapeHtml(course.period)}</dd></div>
            <div><dt>교육 시간</dt><dd>${escapeHtml(course.time)}</dd></div>
            <div><dt>핵심 기술</dt><dd>${escapeHtml(course.tech)}</dd></div>
          </dl>
        </div>
        <footer>
          <a class="btn btn--outline" href="/v2/site/class/course.html?course=${key}">상세보기</a>
          <a class="btn btn--primary" href="${action.href}">${action.label}</a>
        </footer>
      </article>`;
  }).join('');
})();
