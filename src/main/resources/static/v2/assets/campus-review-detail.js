(() => {
  const allowed = new Set(['20136','20135','20126','20117','20116','20106','20040','20039','20038','20037','20036','20035','20034','20031','20030','20029','20028','20027','20026','20018','20017','20016','20015','20014','20013','20012','20011']);
  const id = new URLSearchParams(location.search).get('id') || '';
  const loading = document.querySelector('[data-review-loading]');
  const detail = document.querySelector('[data-review-detail]');
  const error = document.querySelector('[data-review-error]');
  const courseRecommendations = {
    '20136':[['data','데이터 분석']],
    '20135':[['aiot','AIoT·데이터 분석']],
    '20126':[['data','데이터 분석'],['robot','로봇 AI']],
    '20117':[['robot','로봇 AI']],
    '20116':[['data','데이터·AI 개발']],
    '20106':[['aiot','AIoT·빅데이터']],
    '20040':[['cloud','웹·앱 개발']],
    '20039':[['cloud','앱 개발']],
    '20038':[['cloud','백엔드 개발']],
    '20037':[['cloud','웹·앱 개발']],
    '20036':[['cloud','IT 개발']],
    '20035':[['cloud','IT 개발']],
    '20034':[['cloud','IT 개발']],
    '20031':[['cloud','IT 개발']],
    '20030':[['cloud','IT 개발']],
    '20029':[['cloud','웹 개발']],
    '20028':[['factory','RPA·자동화']],
    '20027':[['factory','산업 자동화']],
    '20026':[['factory','RPA·자동화']],
    '20018':[['cloud','정보시스템 개발']],
    '20017':[['cloud','IT 연구개발']],
    '20016':[['cloud','정보시스템']],
    '20015':[['data','데이터 분석']],
    '20014':[['cloud','IT 시스템']],
    '20013':[['cloud','IT·정보시스템']],
    '20012':[['cloud','정보시스템·보안']],
    '20011':[['aiot','기업 솔루션·IoT']]
  };
  const addCourseRecommendations = body => {
    const recommendations = courseRecommendations[id] || [];
    if (!recommendations.length) return;
    const section = document.createElement('section');
    section.className = 'review-course-recommendations';
    section.setAttribute('aria-label', '수료 과정 관련 교육과정');
    section.innerHTML = `<p>RELATED COURSES</p><h2>배운 분야와 비슷한 과정</h2><div>${recommendations.map(([key,label]) => `<a href="/v2/site/class/course.html?course=${key}"><span>${label}</span><strong>관련 교육과정 보기 →</strong></a>`).join('')}</div>`;
    const summaryTable = body.querySelector('table');
    if (summaryTable) summaryTable.insertAdjacentElement('afterend', section);
    else body.prepend(section);
  };
  const fail = () => { loading.hidden = true; detail.hidden = true; error.hidden = false; };
  if (!allowed.has(id)) { fail(); return; }
  fetch(`/v2/assets/reviews/content/${id}.html?v=20260824-01`)
    .then(response => { if (!response.ok) throw new Error('not found'); return response.text(); })
    .then(html => {
      document.querySelector('[data-review-cover]').src = `/v2/assets/reviews/${id}.png`;
      const body = document.querySelector('[data-review-body]');
      body.innerHTML = html;
      addCourseRecommendations(body);
      const heading = document.querySelector('[data-review-body] h1');
      if (heading) document.title = `${heading.textContent.trim()} — ${window.BRAND?.name || '삼성AXI'}`;
      loading.hidden = true;
      detail.hidden = false;
    })
    .catch(fail);
})();
