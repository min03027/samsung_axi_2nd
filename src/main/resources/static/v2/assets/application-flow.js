(async () => {
  const form = document.querySelector('[data-application-form]');
  if (!form) return;
  const catalog = {};
  const kind = form.dataset.applicationForm;
  const requestedCourseId = new URLSearchParams(location.search).get('courseId');
  const toCatalogCourse = course => ({
    id: course.id,
    cat: course.categoryLabel + ' · ' + (course.category || '과정'),
    title: course.courseName,
    period: `${course.educationStartDate} — ${course.educationEndDate}`,
    time: course.educationTime || '상담 시 안내',
    capacity: course.capacity + '명',
    requiredDocuments: course.requiredDocuments || '담당자 확인 후 안내'
  });
  try {
    const response = await fetch('/v2/api/courses', {headers: {Accept: 'application/json'}});
    if (response.ok) {
      const courses = await response.json();
      courses.forEach(course => { catalog['cms-' + course.id] = toCatalogCourse(course); });
    }
  } catch (error) {
    console.error('신청 가능한 과정 정보를 불러오지 못했습니다.', error);
  }
  if (kind === 'counsel' && requestedCourseId && !catalog['cms-' + requestedCourseId]) {
    try {
      const response = await fetch('/v2/api/public/consultations/courses/' + encodeURIComponent(requestedCourseId), {
        headers: {Accept: 'application/json'}
      });
      if (response.ok) {
        const course = await response.json();
        catalog['cms-' + course.id] = toCatalogCourse(course);
      }
    } catch (error) {
      console.error('상담 과정 정보를 불러오지 못했습니다.', error);
    }
  }
  const initialKey = requestedCourseId && catalog['cms-' + requestedCourseId]
    ? 'cms-' + requestedCourseId : '';
  const draftKey = 'tomorrow-ai-' + kind + '-draft';
  const picker = form.querySelector('[data-course-picker]');
  const pickerWrap = form.querySelector('[data-course-picker-wrap]');
  const recommendNote = form.querySelector('[data-recommend-note]');
  const routeInputs = [...form.querySelectorAll('input[name="applicationRoute"]')];
  const courseInput = form.querySelector('[data-course-value]');
  const courseIdInput = form.querySelector('[data-course-id]');

  const showComplete = receipt => {
    document.querySelector('[data-form-shell]').hidden = true;
    const done = document.querySelector('[data-form-complete]');
    done.hidden = false;
    done.querySelector('[data-receipt]').textContent = receipt;
    window.scrollTo({top: 0, behavior: 'smooth'});
  };

  const postJson = async (url, payload) => {
    const response = await fetch(url, {
      method: 'POST',
      headers: {'Content-Type': 'application/json', Accept: 'application/json'},
      body: JSON.stringify(payload)
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || '접수 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.');
    return body;
  };

  if (kind === 'counsel') {
    const requestedCourse = initialKey ? catalog[initialKey] : null;
    courseInput.value = requestedCourse?.title || '과정 선택 상담';
    if (courseIdInput) courseIdInput.value = requestedCourse?.id || '';
    document.querySelectorAll('[data-course-title]').forEach(el => {
      el.textContent = requestedCourse?.title || '과정 선택 상담';
    });
    form.elements.date.min = new Date().toISOString().slice(0, 10);
    form.addEventListener('submit', async event => {
      event.preventDefault();
      if (!form.reportValidity()) return;
      const submit = form.querySelector('[type="submit"]');
      const message = form.querySelector('[data-save-message]');
      if (submit) submit.disabled = true;
      if (message) message.textContent = '상담 신청을 접수하고 있습니다.';
      try {
        const result = await postJson('/v2/api/public/consultations', {
          courseId: courseIdInput?.value ? Number(courseIdInput.value) : null,
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
        });
        showComplete(result.receiptNumber);
      } catch (error) {
        if (message) message.textContent = error.message;
        if (submit) submit.disabled = false;
      }
    });
    return;
  }

  const categoryFor = course => {
    const cat = course.cat || '';
    if (cat.startsWith('KDT')) return 'KDT 신기술';
    if (cat.startsWith('AI ·')) return 'AI·디자인 실무';
    if (cat.startsWith('해외취업')) return '해외취업';
    if (cat.startsWith('일반고')) return '일반고 위탁';
    return '자격증';
  };
  ['KDT 신기술','AI·디자인 실무','해외취업','일반고 위탁','자격증'].forEach(groupName => {
    const entries = Object.entries(catalog).filter(([,course]) => categoryFor(course) === groupName);
    if (!entries.length) return;
    const group = document.createElement('optgroup');
    group.label = groupName;
    entries.forEach(([key,course]) => {
      const option = document.createElement('option');
      option.value = key;
      option.textContent = course.title;
      group.append(option);
    });
    picker.append(group);
  });

  const selectedRoute = () => form.elements.applicationRoute?.value || '';
  const selectedCourse = () => catalog[picker.value] || null;
  const displayCourse = () => {
    const recommendation = selectedRoute() === 'recommend';
    const course = selectedCourse();
    const title = recommendation ? '과정 추천이 필요해요' : (course?.title || '신청할 과정부터 선택해 주세요');
    const cat = recommendation ? '맞춤 과정 추천 상담' : (course?.cat || '아직 선택하지 않았어요');
    const meta = recommendation ? '경험 · 목표 · 가능 일정을 확인해 추천' : (course ? `${course.period || '일정 상담'} · 정원 ${course.capacity || '상담 시 안내'}` : '과정별 일정과 정원을 확인해 안내합니다.');
    courseInput.value = recommendation ? '과정 추천 요청' : (course?.title || '');
    if (courseIdInput) courseIdInput.value = recommendation ? '' : (course?.id || '');
    document.querySelectorAll('[data-course-title]').forEach(el => el.textContent = title);
    document.querySelectorAll('[data-course-cat]').forEach(el => el.textContent = cat);
    document.querySelectorAll('[data-course-meta]').forEach(el => el.textContent = meta);
    const preview = form.querySelector('[data-course-preview]');
    preview.hidden = !course || recommendation;
    if (course && !recommendation) {
      preview.querySelector('[data-preview-cat]').textContent = course.cat;
      preview.querySelector('[data-preview-title]').textContent = course.title;
      preview.querySelector('[data-preview-meta]').textContent = `${course.period} · ${course.time} · 정원 ${course.capacity}`;
    }
    const summaryLabel = form.querySelector('[data-summary-label]');
    const summaryDescription = form.querySelector('[data-summary-description]');
    const submitLabel = form.querySelector('[data-submit-label]');
    const requiredDocuments = form.querySelector('[data-required-documents]');
    if (summaryLabel) summaryLabel.textContent = recommendation ? '요청 내용' : '신청 과정';
    if (summaryDescription) summaryDescription.textContent = recommendation ? '제출 후 담당자가 현재 상황과 목표를 확인하고 가장 적합한 과정을 추천해 드립니다.' : '제출 후 담당자가 신청 내용을 확인하고 선발 절차와 준비사항을 개별 안내합니다.';
    if (submitLabel) submitLabel.textContent = recommendation ? '추천 상담 요청' : '신청서 제출';
    if (requiredDocuments) requiredDocuments.textContent = course?.requiredDocuments || '담당자 확인 후 안내';
  };
  const setRoute = route => {
    routeInputs.forEach(input => input.checked = input.value === route);
    pickerWrap.hidden = route !== 'course';
    recommendNote.hidden = route !== 'recommend';
    picker.required = route === 'course';
    const firstNext = steps?.[0]?.querySelector('[data-step-next]') || form.querySelector('[data-step-next]');
    if (firstNext) firstNext.textContent = route === 'recommend' ? '사전상담으로 이동 →' : '기본 정보 입력하기 →';
    displayCourse();
  };
  routeInputs.forEach(input => input.addEventListener('change', () => setRoute(input.value)));
  picker.addEventListener('change', displayCourse);

  const steps = [...form.querySelectorAll('[data-form-step]')];
  const indicators = [...document.querySelectorAll('[data-step-indicator]')];
  let current = 0;
  const show = index => {
    current = Math.max(0, Math.min(index, steps.length - 1));
    steps.forEach((step, i) => step.hidden = i !== current);
    indicators.forEach((item, i) => item.classList.toggle('is-active', i <= current));
    window.scrollTo({top:0,behavior:'smooth'});
  };
  form.addEventListener('click', event => {
    const next = event.target.closest('[data-step-next]');
    const prev = event.target.closest('[data-step-prev]');
    if (next) {
      const required = [...steps[current].querySelectorAll('[required]')];
      if (!required.every(input => input.reportValidity())) return;
      if (current === 0 && selectedRoute() === 'recommend') {
        location.href = '/v2/site/campus/counsel.html';
        return;
      }
      show(current + 1);
    }
    if (prev) show(current - 1);
  });
  const serialize = () => Object.fromEntries(new FormData(form).entries());
  form.querySelector('[data-save-draft]')?.addEventListener('click', () => {
    localStorage.setItem(draftKey, JSON.stringify(serialize()));
    const message = document.querySelector('[data-save-message]');
    if (message) message.textContent = '이 브라우저에 임시 저장했습니다.';
  });
  let saved = {};
  try { saved = JSON.parse(localStorage.getItem(draftKey) || '{}'); } catch (_) {}
  Object.entries(saved).forEach(([name,value]) => {
    const input = form.elements[name];
    if (!input || name === 'course' || name === 'applicationRoute') return;
    if (input instanceof RadioNodeList) [...input].forEach(item => item.checked = item.value === value);
    else if (input.type === 'checkbox') input.checked = value === input.value || value === 'on';
    else input.value = value;
  });
  const savedRoute = saved.applicationRoute;
  const savedCourseKey = Object.keys(catalog).find(key => catalog[key].title === saved.course);
  if (initialKey) { picker.value = initialKey; setRoute('course'); }
  else if (savedRoute === 'recommend') setRoute('recommend');
  else if (savedRoute === 'course' && savedCourseKey) { picker.value = savedCourseKey; setRoute('course'); }
  else { picker.value = ''; displayCourse(); }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const message = document.querySelector('[data-save-message]');
    const submit = form.querySelector('[type="submit"]');
    if (!courseIdInput?.value) {
      if (message) message.textContent = '현재 모집 중인 과정을 선택해 주세요. 과정 추천은 사전상담을 이용해 주세요.';
      return;
    }
    if (submit) submit.disabled = true;
    if (message) message.textContent = '신청서를 안전하게 접수하고 있습니다.';
    try {
      const result = await postJson('/v2/api/public/applications', {
        courseId: Number(courseIdInput.value),
        name: form.elements.name.value,
        birth: form.elements.birth.value,
        email: form.elements.email.value,
        phone: form.elements.phone.value,
        employment: form.elements.employment.value,
        job: form.elements.job.value,
        motivation: form.elements.motivation.value,
        career: form.elements.career.value,
        skills: form.elements.skills.value,
        card: form.elements.card.value,
        dorm: form.elements.dorm.value,
        privacy: form.elements.privacy.checked,
        truth: form.elements.truth.checked
      });
      localStorage.removeItem(draftKey);
      showComplete(result.receiptNumber);
    } catch (error) {
      if (message) message.textContent = error.message;
      if (submit) submit.disabled = false;
    }
  });
  show(0);
})();
