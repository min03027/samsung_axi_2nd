(async () => {
  const form = document.querySelector('[data-counsel-form]');
  if (!form) return;
  const params = new URLSearchParams(location.search);
  const catalog = window.COURSE_CATALOG || {};
  const requestedCourseId = params.get('courseId');
  let courseKey = params.get('course');
  let course = courseKey && catalog[courseKey] ? catalog[courseKey] : null;
  if (requestedCourseId) {
    try {
      const response = await fetch('/v2/api/public/consultations/courses/' + encodeURIComponent(requestedCourseId), {
        headers: {Accept: 'application/json'}
      });
      if (response.ok) {
        const cmsCourse = await response.json();
        courseKey = 'cms-' + cmsCourse.id;
        course = {id: cmsCourse.id, title: cmsCourse.courseName};
      }
    } catch (error) {
      console.error('상담 과정 정보를 불러오지 못했습니다.', error);
    }
  }
  const fromRecommendation = params.get('from') === 'recommend';
  const shell = document.querySelector('[data-form-shell]');
  const complete = document.querySelector('[data-form-complete]');
  const summary = document.querySelector('[data-recommendation-summary]');
  const courseTitle = document.querySelector('[data-course-title]');
  const courseValue = form.querySelector('[data-course-value]');
  const courseKeyValue = form.querySelector('[data-course-key-value]');
  const courseIdValue = form.querySelector('[data-course-id]');
  const courseDisplay = form.querySelector('[data-course-display]');
  let hasRecommendation = false;
  let activeAnswers = {};
  let activeAnswerLabels = {};
  function syncCourseSelection(title, key) {
    const selectedTitle = title || (fromRecommendation ? '추천 결과 상담' : '상담에서 과정을 함께 찾아드려요');
    courseTitle.textContent = selectedTitle;
    courseValue.value = title || '';
    courseKeyValue.value = key || '';
    if (courseIdValue) courseIdValue.value = course?.id || '';
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
    career:{'data-ai':'데이터·AI','smart-factory':'스마트팩토리·AIoT',robot:'로봇·자율주행',developer:'웹·클라우드 개발',content:'디자인·콘텐츠',global:'글로벌 취업·기획'},
    experience:{none:'관련 경험 없음',learning:'학습·자격 준비 경험',project:'프로젝트 경험',work:'관련 실무 경험'},
    education:{'highschool-current':'일반고 재학 중','highschool-graduate':'고교 졸업·검정고시',college:'대학 재학·졸업',graduate:'대학원·기타'},
    region:{seongnam:'경기 성남',capital:'서울·경기 수도권',any:'지역 무관',consult:'지역 상담 필요'},
    lodging:{needed:'숙식 필요','not-needed':'숙식 불필요',undecided:'숙식 미정'},
    funding:{required:'국비지원 필수',preferred:'가능하면 국비 희망','not-needed':'국비 무관',undecided:'국비 상담 후 결정'},
    schedule:{immediate:'가능한 빨리',within3:'3개월 이내',later:'3개월 이후',flexible:'일정 상담'},
    field:{ai:'생성형AI·산업AI',data:'데이터 분석',automation:'자동화·제조',software:'SW·클라우드',creative:'디자인·영상',global:'해외취업',certificate:'자격증'}
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
    form.querySelectorAll('[data-recommendation-field]').forEach(input => {
      input.value = answers[input.dataset.recommendationField] || '';
    });
    if (answers.lodging === 'needed') form.elements.dorm.value = '필요';
    if (answers.lodging === 'not-needed') form.elements.dorm.value = '불필요';
    if (answers.lodging === 'undecided') form.elements.dorm.value = '미정';
    form.elements.type.value = '과정 선택';
  }

  const recommendationFields = ['career','experience','education','region','lodging','funding','schedule','field'];
  const queryAnswers = Object.fromEntries(recommendationFields.map(name => [name, params.get(name)]).filter(([,value]) => value));
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

  form.elements.date.min = new Date().toISOString().slice(0, 10);
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const submit = form.querySelector('[type="submit"]');
    const message = form.querySelector('[data-save-message]');
    submit.disabled = true;
    if (message) message.textContent = '상담 신청을 안전하게 접수하고 있습니다.';
    try {
      const response = await fetch('/v2/api/public/consultations', {
        method: 'POST',
        headers: {'Content-Type': 'application/json', Accept: 'application/json'},
        body: JSON.stringify({
          courseId: courseIdValue?.value ? Number(courseIdValue.value) : null,
          name: form.elements.name.value,
          phone: form.elements.phone.value,
          email: form.elements.email.value,
          type: form.elements.type.value,
          date: form.elements.date.value,
          time: form.elements.time.value,
          contact: form.elements.contact.value,
          dorm: form.elements.dorm.value,
          message: form.elements.message.value,
          privacy: form.elements.privacy.checked
        })
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(result.message || '접수 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.');
      const now = new Date();
      const record = {
        data:Object.fromEntries(new FormData(form).entries()),
        courseKey,
        recommendation:hasRecommendation ? { answers:activeAnswers, labels:activeAnswerLabels } : null,
        submittedAt:now.toISOString(),
        receipt:result.receiptNumber
      };
      localStorage.setItem('tomorrow-ai-counsel-submitted', JSON.stringify(record));
      showComplete(record);
      history.replaceState(null, '', `${location.pathname}?status=1`);
      window.scrollTo({ top:0, behavior:'smooth' });
    } catch (error) {
      if (message) message.textContent = error.message;
      submit.disabled = false;
    }
  });
})();
