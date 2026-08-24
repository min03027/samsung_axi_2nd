(() => {
  const categoryButtons = [...document.querySelectorAll('[data-axi-filter]')];
  const form = document.querySelector('.axi-course-search');
  const keywordInput = document.querySelector('[data-axi-keyword]');
  const jobSelect = document.querySelector('[data-axi-job-filter]');
  const skillSelect = document.querySelector('[data-axi-skill-filter]');
  const regionSelect = document.querySelector('[data-axi-region-filter]');
  const modeSelect = document.querySelector('[data-axi-mode-filter]');
  const lodgingSelect = document.querySelector('[data-axi-lodging-filter]');
  const durationSelect = document.querySelector('[data-axi-duration-filter]');
  const statusSelect = document.querySelector('[data-axi-status-select]');
  const fundingSelect = document.querySelector('[data-axi-funding-filter]');
  const feeSelect = document.querySelector('[data-axi-fee-filter]');
  const sortSelect = document.querySelector('[data-axi-sort]');
  const resultCount = document.querySelector('[data-axi-result-count]');
  const filterSummary = document.querySelector('[data-axi-filter-summary]');
  const cards = [...document.querySelectorAll('[data-axi-category]')];
  const grid = document.querySelector('.axi-course-grid');
  if (!categoryButtons.length || !cards.length || !grid) return;

  const courseKeys = ['data','factory','aiot','robot','cloud','video','uiux','japan','usa','china','cooking','game','design','mobility','system','adsp','sqld','bigdata-cert','engineer'];
  const profiles = {
    data:          { job:'data',        skill:'data',    lodging:'available', duration:'short',   funding:'yes',     start:'2026-08-18', end:'2026-10-22', adminStatus:'recruiting',     deadline:'2026-08-18' },
    factory:       { job:'ai',          skill:'smart',   lodging:'available', duration:'long',    funding:'yes',     start:'2026-08-24', end:'2027-03-22', adminStatus:'recruiting',     deadline:'2026-08-24' },
    aiot:          { job:'ai',          skill:'smart',   lodging:'available', duration:'long',    funding:'yes',     start:'2026-08-24', end:'2027-03-18', adminStatus:'recruiting',     deadline:'2026-08-24' },
    robot:         { job:'robot',       skill:'robot',   lodging:'available', duration:'long',    funding:'yes',     start:'2026-08-24', end:'2027-03-05', adminStatus:'recruiting',     deadline:'2026-08-24' },
    cloud:         { job:'developer',   skill:'cloud',   lodging:'available', duration:'long',    funding:'yes',     start:'2026-08-28', end:'2027-03-24', adminStatus:'recruiting',     deadline:'2026-08-28' },
    video:         { job:'design',      skill:'design',  lodging:'consult',   duration:'short',   funding:'yes',     start:'2026-08-24', end:'2026-10-15', adminStatus:'recruiting',     deadline:'2026-08-24' },
    uiux:          { job:'design',      skill:'design',  lodging:'consult',   duration:'medium',  funding:'yes',     start:'2026-08-24', end:'2026-12-21', adminStatus:'recruiting',     deadline:'2026-08-24' },
    japan:         { job:'developer',   skill:'cloud',   lodging:'available', duration:'medium',  funding:'consult', start:'2026-08-28', end:'2027-02-26', adminStatus:'recruiting',     deadline:'2026-08-28' },
    usa:           { job:'global',      skill:'data',    lodging:'available', duration:'medium',  funding:'consult', start:'2026-08-28', end:'2027-03-17', adminStatus:'recruiting',     deadline:'2026-08-28' },
    china:         { job:'global',      skill:'global',  lodging:'available', duration:'medium',  funding:'consult', start:'2026-08-28', end:'2027-02-26', adminStatus:'recruiting',     deadline:'2026-08-28' },
    cooking:       { job:'culinary',    skill:'culinary',lodging:'consult',   duration:'long',    funding:'yes',     start:'2027-03-01', end:'2028-01-07', adminStatus:'pre-recruiting', deadline:'' },
    game:          { job:'game',        skill:'game',    lodging:'consult',   duration:'long',    funding:'yes',     start:'2027-03-01', end:'2028-01-07', adminStatus:'pre-recruiting', deadline:'' },
    design:        { job:'design',      skill:'design',  lodging:'consult',   duration:'long',    funding:'yes',     start:'2027-03-01', end:'2028-01-07', adminStatus:'pre-recruiting', deadline:'' },
    mobility:      { job:'robot',       skill:'robot',   lodging:'consult',   duration:'long',    funding:'yes',     start:'2027-03-01', end:'2028-01-07', adminStatus:'pre-recruiting', deadline:'' },
    system:        { job:'it',          skill:'it',      lodging:'consult',   duration:'long',    funding:'yes',     start:'2027-03-01', end:'2028-01-07', adminStatus:'pre-recruiting', deadline:'' },
    adsp:          { job:'certificate', skill:'data',    lodging:'none',      duration:'consult', funding:'consult', start:'',           end:'',           adminStatus:'rolling',         deadline:'' },
    sqld:          { job:'certificate', skill:'data',    lodging:'none',      duration:'consult', funding:'consult', start:'',           end:'',           adminStatus:'rolling',         deadline:'' },
    'bigdata-cert':{ job:'certificate', skill:'data',    lodging:'none',      duration:'consult', funding:'consult', start:'',           end:'',           adminStatus:'rolling',         deadline:'' },
    engineer:      { job:'certificate', skill:'it',      lodging:'none',      duration:'consult', funding:'consult', start:'',           end:'',           adminStatus:'rolling',         deadline:'' }
  };
  const selfFees = {
    data:300000, factory:400000, aiot:400000, robot:400000, cloud:400000,
    video:556080, uiux:300000, japan:null, usa:null, china:null,
    cooking:0, game:0, design:0, mobility:0, system:0,
    adsp:null, sqld:null, 'bigdata-cert':null, engineer:null
  };
  const labels = {
    job: { data:'데이터 분석', ai:'AI 자동화', robot:'로봇 모빌리티', developer:'SW 클라우드 개발', design:'디자인 콘텐츠', global:'글로벌 기획', culinary:'조리', game:'게임 개발', it:'IT 운영', certificate:'자격증' },
    skill: { data:'데이터 SQL', ai:'AI 머신러닝', smart:'AIoT 스마트팩토리', robot:'로봇 자율주행', cloud:'Java Cloud', design:'디자인 영상', global:'글로벌 PM', culinary:'조리', game:'게임', it:'IT 정보처리' },
    region: { seongnam:'경기 성남(대표)', consult:'지역 상담' },
    mode: { offline:'오프라인', online:'온라인', hybrid:'혼합', consult:'방식 상담' },
    lodging: { available:'숙식 상담 가능', none:'숙식 미제공', consult:'숙식 확인 필요' },
    duration: { short:'3개월 이하', medium:'4~6개월', long:'7개월 이상', consult:'일정 상담' },
    funding: { yes:'국비지원', consult:'지원여부 상담' }
  };
  const feeBand = fee => fee === null ? 'consult' : fee === 0 ? 'free' : fee <= 300000 ? 'low' : fee <= 500000 ? 'mid' : 'high';
  const feeLabel = fee => fee === null ? '교육비 상담' : fee === 0 ? '본인부담 0원' : '본인부담 ' + new Intl.NumberFormat('ko-KR').format(fee) + '원';

  cards.forEach((card, index) => {
    const key = courseKeys[index];
    const profile = profiles[key];
    const isCertificate = card.dataset.axiCategory === 'certificate';
    const status = card.querySelector('.axi-status');
    const statusKey = window.AXI_RECRUITMENT_STATUS.derive({
      adminStatus: card.dataset.axiAdminStatus || profile.adminStatus,
      deadline: card.dataset.axiRecruitmentDeadline || profile.deadline
    });
    const fee = selfFees[key];
    const region = isCertificate ? 'consult' : 'seongnam';
    const mode = isCertificate ? 'consult' : 'offline';

    Object.assign(card.dataset, {
      courseKey: key,
      axiStatus: statusKey,
      axiFee: feeBand(fee),
      axiJob: profile.job,
      axiSkill: profile.skill,
      axiRegion: region,
      axiMode: mode,
      axiLodging: profile.lodging,
      axiDuration: profile.duration,
      axiFunding: profile.funding,
      axiStart: profile.start,
      axiEnd: profile.end,
      axiAdminStatus: profile.adminStatus,
      axiRecruitmentDeadline: profile.deadline,
      axiOriginalIndex: String(index)
    });
    if (status) {
      status.dataset.axiStatus = statusKey;
      status.textContent = window.AXI_RECRUITMENT_STATUS.labels[statusKey];
    }

    const price = document.createElement('span');
    price.className = 'axi-course-fee';
    price.textContent = feeLabel(fee);
    card.querySelector('p').insertAdjacentElement('afterend', price);

    const facts = document.createElement('div');
    facts.className = 'axi-course-facts';
    const periodLabel = profile.start && profile.end ? profile.start.replaceAll('-', '.') + '–' + profile.end.replaceAll('-', '.') : '수시모집';
    facts.innerHTML = '<span>' + periodLabel + ' · ' + labels.duration[profile.duration] + '</span><span>' + labels.region[region] + ' · ' + labels.mode[mode] + '</span><span>' + labels.funding[profile.funding] + ' · ' + labels.lodging[profile.lodging] + '</span>';
    price.insertAdjacentElement('afterend', facts);

    const visual = document.createElement('div');
    visual.className = 'axi-course-visual';
    visual.setAttribute('aria-hidden', 'true');
    visual.innerHTML = '<img src="/v2/assets/outcome-icons/' + key + '.png" alt="" loading="lazy">';
    card.insertBefore(visual, card.querySelector('footer'));

    card.dataset.axiSearch = [card.textContent, labels.job[profile.job], labels.skill[profile.skill], labels.region[region], labels.mode[mode], labels.lodging[profile.lodging], labels.duration[profile.duration], labels.funding[profile.funding]].join(' ').toLocaleLowerCase('ko-KR');

    card.querySelectorAll('a[href="/v2/site/class/course.html"]').forEach(link => {
      link.href = '/v2/site/class/course.html?course=' + key;
    });
    card.querySelectorAll('a[href="/v2/site/class/apply.html"]').forEach(link => {
      link.href = card.dataset.axiCategory === 'highschool'
        ? '/v2/site/campus/counsel.html?course=' + key
        : '/v2/site/class/apply.html?course=' + key;
    });
  });

  const trackKeys = ['data','factory','aiot','robot','cloud'];
  document.querySelectorAll('.track-card').forEach((card, index) => {
    const link = card.querySelector('a[href^="/v2/site/class/course.html"]');
    if (link) link.href = '/v2/site/class/course.html?course=' + trackKeys[index];
  });

  const empty = document.createElement('div');
  empty.className = 'axi-course-empty';
  empty.hidden = true;
  empty.innerHTML = '<strong>조건에 맞는 과정이 없습니다.</strong><span>검색어 또는 상세 조건을 바꾸거나 초기화해보세요.</span>';
  grid.insertAdjacentElement('afterend', empty);

  const filterSelects = [jobSelect, skillSelect, regionSelect, modeSelect, lodgingSelect, durationSelect, statusSelect, fundingSelect, feeSelect].filter(Boolean);
  const visibleCards = () => [...grid.querySelectorAll('.axi-course-card:not([hidden])')];
  const selectLabel = select => select && select.value !== 'all' ? select.options[select.selectedIndex].textContent : '';
  const updateSummary = category => {
    if (!filterSummary) return;
    const categoryLabel = category === 'all' ? '' : categoryButtons.find(button => button.dataset.axiFilter === category)?.querySelector('strong')?.textContent || '';
    const keyword = keywordInput?.value.trim();
    const active = [categoryLabel, keyword ? '“' + keyword + '”' : '', ...filterSelects.map(selectLabel)].filter(Boolean);
    filterSummary.textContent = active.length ? active.join(' · ') : '전체 조건';
  };
  const sortCards = () => {
    const sort = sortSelect?.value || 'recommended';
    const sorted = [...cards].sort((a, b) => {
      if (sort === 'latest') return (b.dataset.axiStart || '').localeCompare(a.dataset.axiStart || '') || Number(a.dataset.axiOriginalIndex) - Number(b.dataset.axiOriginalIndex);
      if (sort === 'deadline') {
        if (a.dataset.axiStatus === 'closing' && b.dataset.axiStatus !== 'closing') return -1;
        if (a.dataset.axiStatus !== 'closing' && b.dataset.axiStatus === 'closing') return 1;
        if (!a.dataset.axiStart && b.dataset.axiStart) return 1;
        if (a.dataset.axiStart && !b.dataset.axiStart) return -1;
        return (a.dataset.axiStart || '').localeCompare(b.dataset.axiStart || '') || Number(a.dataset.axiOriginalIndex) - Number(b.dataset.axiOriginalIndex);
      }
      return Number(a.dataset.axiOriginalIndex) - Number(b.dataset.axiOriginalIndex);
    });
    sorted.forEach(card => grid.appendChild(card));
  };
  const applyFilters = () => {
    const category = categoryButtons.find(button => button.getAttribute('aria-pressed') === 'true')?.dataset.axiFilter || 'all';
    const keyword = keywordInput?.value.trim().toLocaleLowerCase('ko-KR') || '';
    const values = {
      job: jobSelect?.value || 'all', skill: skillSelect?.value || 'all', region: regionSelect?.value || 'all', mode: modeSelect?.value || 'all',
      lodging: lodgingSelect?.value || 'all', duration: durationSelect?.value || 'all', status: statusSelect?.value || 'all',
      funding: fundingSelect?.value || 'all', fee: feeSelect?.value || 'all'
    };
    sortCards();
    cards.forEach(card => {
      const matches = [
        category === 'all' || card.dataset.axiCategory === category,
        !keyword || card.dataset.axiSearch.includes(keyword),
        values.job === 'all' || card.dataset.axiJob === values.job,
        values.skill === 'all' || card.dataset.axiSkill === values.skill,
        values.region === 'all' || card.dataset.axiRegion === values.region,
        values.mode === 'all' || card.dataset.axiMode === values.mode,
        values.lodging === 'all' || card.dataset.axiLodging === values.lodging,
        values.duration === 'all' || card.dataset.axiDuration === values.duration,
        values.status === 'all' || card.dataset.axiStatus === values.status || (values.status === 'open' && card.dataset.axiStatus === 'closing'),
        values.funding === 'all' || card.dataset.axiFunding === values.funding,
        values.fee === 'all' || card.dataset.axiFee === values.fee
      ];
      card.hidden = !matches.every(Boolean);
    });
    const count = visibleCards().length;
    if (resultCount) resultCount.textContent = count + '개 과정';
    empty.hidden = count > 0;
    updateSummary(category);
  };

  categoryButtons.forEach(button => button.addEventListener('click', () => {
    categoryButtons.forEach(item => item.setAttribute('aria-pressed', String(item === button)));
    applyFilters();
  }));
  filterSelects.forEach(select => select.addEventListener('change', applyFilters));
  sortSelect?.addEventListener('change', applyFilters);
  keywordInput?.addEventListener('input', applyFilters);
  form?.addEventListener('submit', event => event.preventDefault());
  form?.addEventListener('reset', () => {
    categoryButtons.forEach((button, index) => button.setAttribute('aria-pressed', String(index === 0)));
    requestAnimationFrame(applyFilters);
  });
  applyFilters();
})();
