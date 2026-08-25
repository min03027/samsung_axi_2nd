(() => {
  const form = document.getElementById('course-recommender');
  const result = document.getElementById('recommend-result');
  const list = document.getElementById('recommend-list');
  const profile = document.getElementById('recommend-profile');
  if (!form || !result || !list || !profile) return;

  const catalog = window.COURSE_CATALOG || {};
  const labels = {
    career: {
      'data-ai':'데이터·AI', 'smart-factory':'스마트팩토리·AIoT', robot:'로봇·자율주행',
      developer:'웹·클라우드 개발', content:'디자인·콘텐츠', global:'글로벌 취업·기획'
    },
    experience: { none:'관련 경험 없음', learning:'학습·자격 준비 경험', project:'프로젝트 경험', work:'관련 실무 경험' },
    education: { 'highschool-current':'일반고 재학 중', 'highschool-graduate':'고교 졸업·검정고시', college:'대학 재학·졸업', graduate:'대학원·기타' },
    region: { seongnam:'경기 성남', capital:'서울·경기 수도권', any:'지역 무관', consult:'지역 상담 필요' },
    lodging: { needed:'숙식 필요', 'not-needed':'숙식 불필요', undecided:'숙식 미정' },
    funding: { required:'국비지원 필수', preferred:'가능하면 국비 희망', 'not-needed':'국비 무관', undecided:'국비 상담 후 결정' },
    schedule: { immediate:'가능한 빨리', within3:'3개월 이내', later:'3개월 이후', flexible:'일정 상담' },
    field: { ai:'생성형AI·산업AI', data:'데이터 분석', automation:'자동화·제조', software:'SW·클라우드', creative:'디자인·영상', global:'해외취업', certificate:'자격증' }
  };

  const profiles = {
    data: { careers:['data-ai'], fields:['data','ai'], experiences:['none','learning','project','work'], audience:'general', region:'seongnam', funding:'yes', schedules:['immediate','within3'], type:'KDT 신성장', support:'국비지원 · 숙식 상담 가능', lodging:'available' },
    factory: { careers:['smart-factory'], fields:['automation','ai'], experiences:['none','learning','project','work'], audience:'general', region:'seongnam', funding:'yes', schedules:['immediate','within3'], type:'KDT 신성장', support:'국비지원 · 숙식 상담 가능', lodging:'available' },
    aiot: { careers:['smart-factory','data-ai'], fields:['automation','ai','data'], experiences:['none','learning','project','work'], audience:'general', region:'seongnam', funding:'yes', schedules:['immediate','within3'], type:'KDT 신성장', support:'국비지원 · 숙식 상담 가능', lodging:'available' },
    robot: { careers:['robot'], fields:['automation','ai'], experiences:['none','learning','project'], audience:'general', region:'seongnam', funding:'yes', schedules:['immediate','within3'], type:'KDT 신성장', support:'국비지원 · 숙식 상담 가능', lodging:'available' },
    cloud: { careers:['developer'], fields:['software','ai'], experiences:['none','learning','project','work'], audience:'general', region:'seongnam', funding:'yes', schedules:['immediate','within3'], type:'KDT 신성장', support:'국비지원 · 숙식 상담 가능', lodging:'available' },
    video: { careers:['content'], fields:['creative','ai'], experiences:['none','learning','project','work'], audience:'general', region:'seongnam', funding:'yes', schedules:['immediate','within3','flexible'], type:'AI Campus', support:'국비지원 · 숙식 여부 상담', lodging:'consult' },
    uiux: { careers:['content'], fields:['creative','software'], experiences:['none','learning','project','work'], audience:'general', region:'seongnam', funding:'yes', schedules:['within3','flexible'], type:'AI Campus', support:'국비지원 · 숙식 여부 상담', lodging:'consult' },
    japan: { careers:['global','developer'], fields:['global','software'], experiences:['learning','project','work','none'], audience:'general', region:'seongnam', funding:'consult', schedules:['within3','later','flexible'], type:'해외취업', support:'지원 범위 상담 · 숙식 상담 가능', lodging:'available' },
    usa: { careers:['global','data-ai'], fields:['global','data'], experiences:['learning','project','work','none'], audience:'general', region:'seongnam', funding:'consult', schedules:['within3','later','flexible'], type:'해외취업', support:'지원 범위 상담 · 숙식 상담 가능', lodging:'available' },
    china: { careers:['global'], fields:['global'], experiences:['learning','project','work','none'], audience:'general', region:'seongnam', funding:'consult', schedules:['within3','later','flexible'], type:'해외취업', support:'지원 범위 상담 · 숙식 상담 가능', lodging:'available' },
    cooking: { careers:['content'], fields:['creative','certificate'], experiences:['none','learning'], audience:'highschool', region:'seongnam', funding:'full', schedules:['within3','later'], type:'일반고 위탁', support:'전액 지원 · 숙식 여부 상담', lodging:'consult' },
    game: { careers:['developer','content'], fields:['creative','software'], experiences:['none','learning','project'], audience:'highschool', region:'seongnam', funding:'full', schedules:['within3','later'], type:'일반고 위탁', support:'전액 지원 · 숙식 여부 상담', lodging:'consult' },
    design: { careers:['content'], fields:['creative','certificate'], experiences:['none','learning','project'], audience:'highschool', region:'seongnam', funding:'full', schedules:['within3','later'], type:'일반고 위탁', support:'전액 지원 · 숙식 여부 상담', lodging:'consult' },
    mobility: { careers:['robot'], fields:['automation','ai'], experiences:['none','learning','project'], audience:'highschool', region:'seongnam', funding:'full', schedules:['within3','later'], type:'일반고 위탁', support:'전액 지원 · 숙식 여부 상담', lodging:'consult' },
    system: { careers:['developer'], fields:['software','certificate'], experiences:['none','learning','project'], audience:'highschool', region:'seongnam', funding:'full', schedules:['within3','later'], type:'일반고 위탁', support:'전액 지원 · 숙식 여부 상담', lodging:'consult' },
    adsp: { careers:['data-ai'], fields:['data','certificate'], experiences:['learning','project','work','none'], audience:'general', region:'consult', funding:'consult', schedules:['immediate','within3','flexible'], type:'자격증', support:'수시모집 · 지원 여부 상담', lodging:'none' },
    sqld: { careers:['data-ai','developer'], fields:['data','software','certificate'], experiences:['learning','project','work'], audience:'general', region:'consult', funding:'consult', schedules:['immediate','within3','flexible'], type:'자격증', support:'수시모집 · 지원 여부 상담', lodging:'none' },
    'bigdata-cert': { careers:['data-ai'], fields:['data','certificate'], experiences:['learning','project','work'], audience:'general', region:'consult', funding:'consult', schedules:['immediate','within3','flexible'], type:'자격증', support:'수시모집 · 지원 여부 상담', lodging:'none' },
    engineer: { careers:['developer'], fields:['software','certificate'], experiences:['none','learning','project','work'], audience:'general', region:'consult', funding:'consult', schedules:['immediate','within3','flexible'], type:'자격증', support:'수시모집 · 지원 여부 상담', lodging:'none' }
  };

  function scoreCourse(key, answers) {
    const item = profiles[key];
    const course = catalog[key];
    if (!item || !course) return null;
    let score = 0;
    const reasons = [];

    if (item.careers.includes(answers.career)) {
      score += item.careers[0] === answers.career ? 38 : 30;
      reasons.push(`${labels.career[answers.career]} 희망 과정과 연결`);
    }
    if (item.fields.includes(answers.field)) {
      score += 28;
      reasons.push(`${labels.field[answers.field]} 관심 분야와 일치`);
    }

    const currentHighschool = answers.education === 'highschool-current';
    if (item.audience === 'highschool') {
      if (currentHighschool) {
        score += 34;
        reasons.push('일반고 재학생 대상 과정');
      } else {
        score -= 90;
      }
    } else if (currentHighschool) {
      score -= 36;
    } else {
      score += 10;
    }

    if (item.experiences.includes(answers.experience)) {
      score += 12;
      reasons.push(`${labels.experience[answers.experience]}에서 시작 가능`);
    } else {
      score -= 10;
    }

    if (answers.region === 'any') score += 5;
    else if (answers.region === 'consult') score += 3;
    else if (item.region === 'seongnam' && answers.region === 'seongnam') {
      score += 12;
      reasons.push('희망 지역과 교육장 일치');
    } else if (item.region === 'seongnam' && answers.region === 'capital') {
      score += 7;
    } else if (item.region === 'consult') {
      score += 2;
    }

    if (answers.lodging === 'needed') {
      if (item.lodging === 'available') {
        score += 15;
        reasons.push('숙식 상담 가능한 과정');
      } else if (item.lodging === 'consult') {
        score += 6;
        reasons.push('숙식 가능 여부 상담 필요');
      } else {
        score -= 24;
      }
    } else if (answers.lodging === 'not-needed') {
      score += 5;
    }

    if (answers.funding === 'required') {
      if (['yes','full'].includes(item.funding)) {
        score += 18;
        reasons.push(item.funding === 'full' ? '전액 지원 대상 과정' : '국비지원 대상 과정');
      } else {
        score -= 18;
      }
    } else if (answers.funding === 'preferred' && ['yes','full'].includes(item.funding)) {
      score += 11;
    } else if (answers.funding === 'undecided' && item.funding === 'consult') {
      score += 5;
    }

    if (answers.schedule === 'flexible') score += 6;
    else if (item.schedules.includes(answers.schedule)) {
      score += 12;
      reasons.push(`${labels.schedule[answers.schedule]} 일정과 가까움`);
    } else {
      score -= 5;
    }

    return { key, score, reasons:reasons.slice(0, 4), item, course };
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
    const reasons = match.reasons.length ? match.reasons : ['입력 조건과 가까운 대안 과정'];
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
    const matches = Object.keys(profiles).map(key => scoreCourse(key, answers)).filter(Boolean).sort((a, b) => b.score - a.score).slice(0, 3);

    profile.replaceChildren();
    const headings = { career:'희망 과정', experience:'경험', education:'학력', region:'희망 지역', lodging:'숙식', funding:'국비지원', schedule:'희망 일정', field:'관심 분야' };
    Object.keys(headings).forEach(name => {
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
