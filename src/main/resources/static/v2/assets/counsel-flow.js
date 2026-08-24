(() => {
  const form = document.querySelector('[data-counsel-form]');
  if (!form) return;
  const params = new URLSearchParams(location.search);
  const catalog = window.COURSE_CATALOG || {};
  let courseKey = params.get('course');
  let course = courseKey && catalog[courseKey] ? catalog[courseKey] : null;
  const fromRecommendation = params.get('from') === 'recommend';
  const shell = document.querySelector('[data-form-shell]');
  const complete = document.querySelector('[data-form-complete]');
  const summary = document.querySelector('[data-recommendation-summary]');
  const courseTitle = document.querySelector('[data-course-title]');
  const courseValue = form.querySelector('[data-course-value]');
  const courseKeyValue = form.querySelector('[data-course-key-value]');
  const courseDisplay = form.querySelector('[data-course-display]');
  let hasRecommendation = false;
  let activeAnswers = {};
  let activeAnswerLabels = {};
  function syncCourseSelection(title, key) {
    const selectedTitle = title || (fromRecommendation ? '추천 결과 상담' : '상담에서 과정을 함께 찾아드려요');
    courseTitle.textContent = selectedTitle;
    courseValue.value = title || '';
    courseKeyValue.value = key || '';
    courseDisplay.value = title || '상담에서 함께 선택합니다';
    courseDisplay.closest('.form-course-lock')?.classList.toggle('is-selected', Boolean(title));
    if (title && !form.elements.type.value) form.elements.type.value = '과정 선택';
  }
  syncCourseSelection(course?.title, courseKey);
  const requestedType = params.get('type');
  if (requestedType && [...form.elements.type.options].some(option => option.value === requestedType)) {
    form.elements.type.value = requestedType;
  }

  let recommendation = null;
  try { recommendation = JSON.parse(sessionStorage.getItem('axi-course-recommendation') || 'null'); } catch (_) { recommendation = null; }
  const queryLabels = {
    interest:{data:'데이터 분석·AI',factory:'스마트팩토리·AIoT',robot:'로봇·자율주행',cloud:'웹·클라우드 개발',creative:'디자인·콘텐츠',global:'글로벌·기획'},
    level:{beginner:'처음 시작',basic:'기초 학습 경험',project:'프로젝트 경험'},
    duration:{short:'3개월 이내',medium:'4~6개월',long:'7개월 이상',any:'기간 상관없음'},
    lodging:{needed:'숙식 필요','not-needed':'숙식 불필요',undecided:'숙식 미정'},
    status:{job:'구직·취업 준비 중',worker:'재직 중',student:'일반고 3학년'}
  };
  function applyRecommendation(nextRecommendation, nextCourseKey) {
    if (!nextRecommendation) return;
    const answers = nextRecommendation.answers || {};
    const answerLabels = nextRecommendation.labels || Object.fromEntries(Object.entries(answers).map(([name,value]) => [name, queryLabels[name]?.[value] || value]));
    if (!Object.keys(answerLabels).length) return;
    hasRecommendation = true;
    activeAnswers = answers;
    activeAnswerLabels = answerLabels;
    courseKey = nextCourseKey || nextRecommendation.recommended?.[0]?.key || courseKey;
    course = courseKey && catalog[courseKey] ? catalog[courseKey] : course;
    summary.hidden = false;
    const primary = nextRecommendation.recommended?.find(item => item.key === courseKey) || nextRecommendation.recommended?.[0];
    syncCourseSelection(course?.title || primary?.title || '추천 결과 상담', courseKey);
    document.querySelector('[data-recommendation-copy]').textContent = primary?.reasons?.length
      ? primary.reasons.join(' · ')
      : '입력한 조건을 바탕으로 추천된 과정입니다. 세부 적합도는 상담에서 확인해 드립니다.';
    const tags = document.querySelector('[data-recommendation-tags]');
    tags.replaceChildren();
    Object.values(answerLabels).forEach(value => {
      const li = document.createElement('li');
      li.textContent = value;
      tags.append(li);
    });
    form.querySelector('[data-recommendation-value]').value = Object.values(answerLabels).join(' · ');
    if (answers.lodging === 'needed') form.elements.dorm.value = '필요';
    if (answers.lodging === 'not-needed') form.elements.dorm.value = '불필요';
    if (answers.lodging === 'undecided') form.elements.dorm.value = '미정';
    form.elements.type.value = '과정 선택';
  }

  const queryAnswers = Object.fromEntries(['interest','level','duration','lodging','status'].map(name => [name, params.get(name)]).filter(([,value]) => value));
  if (fromRecommendation) {
    applyRecommendation(recommendation || {
      answers:queryAnswers,
      labels:Object.fromEntries(Object.entries(queryAnswers).map(([name,value]) => [name, queryLabels[name]?.[value] || value]))
    }, courseKey);
  }
  window.addEventListener('axi:course-recommended', event => applyRecommendation(event.detail, event.detail?.recommended?.[0]?.key));

  function showComplete(record) {
    document.querySelector('[data-receipt]').textContent = record.receipt;
    shell.hidden = true;
    complete.hidden = false;
  }

  let submitted = null;
  try { submitted = JSON.parse(localStorage.getItem('tomorrow-ai-counsel-submitted') || 'null'); } catch (_) { submitted = null; }
  if (params.get('status') === '1' && submitted?.receipt) showComplete(submitted);

  form.addEventListener('submit', event => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const now = new Date();
    const record = {
      data:Object.fromEntries(new FormData(form).entries()),
      courseKey,
      recommendation:hasRecommendation ? { answers:activeAnswers, labels:activeAnswerLabels } : null,
      submittedAt:now.toISOString(),
      receipt:`AXI-C-${now.toISOString().slice(2,10).replaceAll('-','')}-${String(Date.now()).slice(-4)}`
    };
    localStorage.setItem('tomorrow-ai-counsel-submitted', JSON.stringify(record));
    showComplete(record);
    history.replaceState(null, '', `${location.pathname}?status=1`);
    window.scrollTo({ top:0, behavior:'smooth' });
  });
})();
