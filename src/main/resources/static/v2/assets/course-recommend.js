(() => {
  const form = document.getElementById('course-recommender');
  const result = document.getElementById('recommend-result');
  const list = document.getElementById('recommend-list');
  const profile = document.getElementById('recommend-profile');
  if (!form || !result || !list || !profile) return;

  const catalog = window.COURSE_CATALOG || {};
  const labels = {
    interest: { data:'데이터 분석·AI', factory:'스마트팩토리·AIoT', robot:'로봇·자율주행', cloud:'웹·클라우드 개발', creative:'디자인·콘텐츠', global:'글로벌·기획' },
    level: { beginner:'처음 시작', basic:'기초 학습 경험', project:'프로젝트 경험' },
    duration: { short:'3개월 이내', medium:'4~6개월', long:'7개월 이상', any:'기간 상관없음' },
    lodging: { needed:'숙식 필요', 'not-needed':'숙식 불필요', undecided:'숙식 미정' },
    status: { job:'구직·취업 준비 중', worker:'재직 중', student:'일반고 3학년' }
  };

  const courses = {
    data: { interests:['data'], duration:'short', lodging:'available', statuses:['job','worker'], levels:['beginner','basic','project'], type:'KDT 신성장', support:'국비지원 · 숙식 상담 가능' },
    factory: { interests:['factory'], duration:'long', lodging:'available', statuses:['job'], levels:['beginner','basic','project'], type:'KDT 신성장', support:'국비지원 · 숙식 상담 가능' },
    aiot: { interests:['factory','data'], duration:'long', lodging:'available', statuses:['job'], levels:['beginner','basic','project'], type:'KDT 신성장', support:'국비지원 · 숙식 상담 가능' },
    robot: { interests:['robot'], duration:'long', lodging:'available', statuses:['job'], levels:['basic','project','beginner'], type:'KDT 신성장', support:'국비지원 · 숙식 상담 가능' },
    cloud: { interests:['cloud'], duration:'long', lodging:'available', statuses:['job'], levels:['beginner','basic','project'], type:'KDT 신성장', support:'국비지원 · 숙식 상담 가능' },
    video: { interests:['creative'], duration:'short', lodging:'consult', statuses:['job','worker'], levels:['beginner','basic','project'], type:'AI Campus', support:'국비지원 · 숙식 여부 상담' },
    uiux: { interests:['creative'], duration:'medium', lodging:'consult', statuses:['job','worker'], levels:['beginner','basic','project'], type:'AI Campus', support:'국비지원 · 숙식 여부 상담' },
    japan: { interests:['cloud','global'], duration:'medium', lodging:'available', statuses:['job'], levels:['basic','project','beginner'], type:'해외취업', support:'지원 범위 상담 · 숙식 상담 가능' },
    usa: { interests:['data','global'], duration:'medium', lodging:'available', statuses:['job'], levels:['basic','project','beginner'], type:'해외취업', support:'지원 범위 상담 · 숙식 상담 가능' },
    china: { interests:['global'], duration:'medium', lodging:'available', statuses:['job'], levels:['basic','project','beginner'], type:'해외취업', support:'지원 범위 상담 · 숙식 상담 가능' },
    cooking: { interests:['creative'], duration:'long', lodging:'consult', statuses:['student'], levels:['beginner','basic'], type:'일반고 위탁', support:'전액 지원 · 숙식 여부 상담' },
    game: { interests:['creative','cloud'], duration:'long', lodging:'consult', statuses:['student'], levels:['beginner','basic','project'], type:'일반고 위탁', support:'전액 지원 · 숙식 여부 상담' },
    design: { interests:['creative'], duration:'long', lodging:'consult', statuses:['student'], levels:['beginner','basic','project'], type:'일반고 위탁', support:'전액 지원 · 숙식 여부 상담' },
    mobility: { interests:['robot'], duration:'long', lodging:'consult', statuses:['student'], levels:['beginner','basic','project'], type:'일반고 위탁', support:'전액 지원 · 숙식 여부 상담' },
    system: { interests:['cloud','data'], duration:'long', lodging:'consult', statuses:['student'], levels:['beginner','basic','project'], type:'일반고 위탁', support:'전액 지원 · 숙식 여부 상담' },
    adsp: { interests:['data'], duration:'short', lodging:'none', statuses:['worker','job'], levels:['basic','project','beginner'], type:'자격증', support:'수시모집 · 지원 여부 상담' },
    sqld: { interests:['data','cloud'], duration:'short', lodging:'none', statuses:['worker','job'], levels:['basic','project'], type:'자격증', support:'수시모집 · 지원 여부 상담' },
    'bigdata-cert': { interests:['data'], duration:'short', lodging:'none', statuses:['worker','job'], levels:['basic','project'], type:'자격증', support:'수시모집 · 지원 여부 상담' },
    engineer: { interests:['cloud'], duration:'short', lodging:'none', statuses:['worker','job'], levels:['basic','project','beginner'], type:'자격증', support:'수시모집 · 지원 여부 상담' }
  };

  function scoreCourse(key, answers) {
    const item = courses[key];
    const course = catalog[key];
    if (!item || !course) return null;
    let score = 0;
    const reasons = [];

    if (item.interests.includes(answers.interest)) {
      score += item.interests[0] === answers.interest ? 46 : 38;
      reasons.push(`${labels.interest[answers.interest]} 직무와 직접 연결`);
    }
    if (item.statuses.includes(answers.status)) {
      score += 28;
      reasons.push(`${labels.status[answers.status]} 대상에 적합`);
    } else if ((answers.status === 'student') !== item.statuses.includes('student')) {
      score -= 80;
    } else if (answers.status === 'worker' && item.duration === 'long') {
      score -= 18;
    }
    if (answers.duration === 'any') {
      score += 9;
    } else if (item.duration === answers.duration) {
      score += 20;
      reasons.push(`${labels.duration[answers.duration]} 일정과 일치`);
    } else {
      score -= 8;
    }
    if (item.levels.includes(answers.level)) {
      score += 12;
      reasons.push(`${labels.level[answers.level]} 수준에서 시작 가능`);
    } else {
      score -= 12;
    }
    if (answers.lodging === 'needed') {
      if (item.lodging === 'available') {
        score += 16;
        reasons.push('숙식 상담 가능한 과정');
      } else if (item.lodging === 'consult') {
        score += 5;
        reasons.push('숙식 가능 여부 상담 필요');
      } else {
        score -= 22;
      }
    } else if (answers.lodging === 'not-needed') {
      score += 5;
    }
    if (answers.status === 'worker' && ['short','medium'].includes(item.duration)) score += 10;
    return { key, score, reasons:reasons.slice(0, 3), item, course };
  }

  function recommendationLink(key, answers) {
    const query = new URLSearchParams({ course:key, from:'recommend' });
    Object.entries(answers).forEach(([name, value]) => query.set(name, value));
    return `/v2/site/campus/counsel.html?${query}`;
  }

  function renderCard(match, index, answers) {
    const article = document.createElement('article');
    article.className = 'recommend-card';
    if (index === 0) article.classList.add('is-primary');
    const reasons = match.reasons.length ? match.reasons : ['선택 조건과 가까운 대안 과정'];
    article.innerHTML = `
      <header><span>${index === 0 ? '가장 잘 맞는 과정' : `함께 비교할 과정 ${index}`}</span><small>${match.item.type}</small></header>
      <h3></h3>
      <p class="recommend-card__desc"></p>
      <ul class="recommend-card__reasons"></ul>
      <dl><div><dt>교육 일정</dt><dd></dd></div><div><dt>지원·숙식</dt><dd></dd></div></dl>
      <footer><a class="btn btn--outline" data-detail>상세 보기</a><a class="btn btn--primary" data-counsel>이 과정 상담하기</a></footer>`;
    article.querySelector('h3').textContent = match.course.title;
    article.querySelector('.recommend-card__desc').textContent = match.course.desc;
    reasons.forEach(reason => {
      const li = document.createElement('li');
      li.textContent = reason;
      article.querySelector('ul').append(li);
    });
    const values = article.querySelectorAll('dd');
    values[0].textContent = `${match.course.period} · ${match.course.time}`;
    values[1].textContent = match.item.support;
    article.querySelector('[data-detail]').href = `/v2/site/class/course.html?course=${match.key}`;
    article.querySelector('[data-counsel]').href = recommendationLink(match.key, answers);
    return article;
  }

  form.addEventListener('submit', event => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const answers = Object.fromEntries(new FormData(form).entries());
    const matches = Object.keys(courses).map(key => scoreCourse(key, answers)).filter(Boolean).sort((a, b) => b.score - a.score).slice(0, 3);

    profile.replaceChildren();
    const headings = { interest:'관심 직무', level:'현재 수준', duration:'가능 기간', lodging:'숙식', status:'현재 상태' };
    ['interest','level','duration','lodging','status'].forEach(name => {
      const div = document.createElement('div');
      div.innerHTML = `<dt>${headings[name]}</dt><dd></dd>`;
      div.querySelector('dd').textContent = labels[name][answers[name]];
      profile.append(div);
    });
    list.replaceChildren(...matches.map((match, index) => renderCard(match, index, answers)));

    const stored = {
      answers,
      labels: Object.fromEntries(Object.entries(answers).map(([name, value]) => [name, labels[name][value]])),
      recommended: matches.map(({key, reasons, course}) => ({ key, title:course.title, reasons })),
      savedAt: new Date().toISOString()
    };
    sessionStorage.setItem('axi-course-recommendation', JSON.stringify(stored));
    window.dispatchEvent(new CustomEvent('axi:course-recommended', { detail:stored }));
    document.getElementById('recommend-general-counsel').href = recommendationLink(matches[0].key, answers);
    result.hidden = false;
    result.scrollIntoView({ behavior:'smooth', block:'start' });
  });
})();
