(async () => {
  const form = document.querySelector('[data-application-form]');
  if (!form) return;
  const catalog = {...(window.COURSE_CATALOG || {})};
  const kind = form.dataset.applicationForm;
  const params = new URLSearchParams(location.search);
  const requestedCourseId = params.get('courseId');
  const requestedKey = params.get('course');
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
  const initialKey = requestedCourseId && catalog['cms-' + requestedCourseId]
    ? 'cms-' + requestedCourseId
    : (requestedKey && catalog[requestedKey] ? requestedKey : '');
  const draftKey = `tomorrow-ai-${kind}-draft`;
  const submittedKey = `tomorrow-ai-${kind}-submitted`;
  const picker = form.querySelector('[data-course-picker]');
  const pickerWrap = form.querySelector('[data-course-picker-wrap]');
  const recommendNote = form.querySelector('[data-recommend-note]');
  const routeInputs = [...form.querySelectorAll('input[name="applicationRoute"]')];
  const courseInput = form.querySelector('[data-course-value]');
  const courseIdInput = form.querySelector('[data-course-id]');
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
  const attachment = form.elements.attachment;
  const attachmentNote = form.querySelector('[data-attachment-note]');
  const draftState = form.querySelector('[data-draft-state]');
  const done = document.querySelector('[data-form-complete]');
  const shell = document.querySelector('[data-form-shell]');
  const steps = [...form.querySelectorAll('[data-form-step]')];
  const indicators = [...document.querySelectorAll('[data-step-indicator]')];
  let current = 0;
  let mode = 'new';
  let attachmentMeta = null;
  let submittedRecord = readStorage(submittedKey);
  let saveTimer = 0;

  function readStorage(key) {
    try { return JSON.parse(localStorage.getItem(key) || 'null'); } catch (_) { return null; }
  }

  function categoryFor(course) {
    const cat = course.cat || '';
    if (cat.startsWith('KDT')) return 'KDT 신기술';
    if (cat.startsWith('AI ·')) return 'AI·디자인 실무';
    if (cat.startsWith('해외취업')) return '해외취업';
    if (cat.startsWith('일반고')) return '일반고 위탁';
    return '자격증';
  }

  ['KDT 신기술', 'AI·디자인 실무', '해외취업', '일반고 위탁', '자격증'].forEach(groupName => {
    const entries = Object.entries(catalog).filter(([, course]) => categoryFor(course) === groupName);
    if (!entries.length) return;
    const group = document.createElement('optgroup');
    group.label = groupName;
    entries.forEach(([key, course]) => {
      const option = document.createElement('option');
      option.value = key;
      option.textContent = course.title;
      group.append(option);
    });
    picker.append(group);
  });

  const selectedRoute = () => form.elements.applicationRoute?.value || '';
  const selectedCourse = () => catalog[picker.value] || null;

  function displayCourse() {
    const recommendation = selectedRoute() === 'recommend';
    const course = selectedCourse();
    const title = recommendation ? '과정 추천이 필요해요' : (course?.title || '신청할 과정부터 선택해 주세요');
    const cat = recommendation ? '맞춤 과정 추천 상담' : (course?.cat || '아직 선택하지 않았어요');
    const meta = recommendation
      ? '경험 · 목표 · 가능 일정을 확인해 추천'
      : (course ? `${course.period || '일정 상담'} · 정원 ${course.capacity || '상담 시 안내'}` : '과정별 일정과 정원을 확인해 안내합니다.');
    courseInput.value = recommendation ? '과정 추천 요청' : (course?.title || '');
    if (courseIdInput) courseIdInput.value = recommendation ? '' : (course?.id || '');
    document.querySelectorAll('[data-course-title]').forEach(el => { el.textContent = title; });
    document.querySelectorAll('[data-course-cat]').forEach(el => { el.textContent = cat; });
    document.querySelectorAll('[data-course-meta]').forEach(el => { el.textContent = meta; });
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
    if (summaryDescription) summaryDescription.textContent = recommendation
      ? '제출 후 담당자가 현재 상황과 목표를 확인하고 가장 적합한 과정을 추천해 드립니다.'
      : '제출 후 담당자가 신청 내용을 확인하고 선발 절차와 준비사항을 개별 안내합니다.';
    if (submitLabel) submitLabel.textContent = mode === 'supplement'
      ? '보완 내용 재제출'
      : (recommendation ? '추천 상담 요청' : '신청서 제출');
    if (requiredDocuments) requiredDocuments.textContent = course?.requiredDocuments || '담당자 확인 후 안내';
  }

  function setRoute(route) {
    routeInputs.forEach(input => { input.checked = input.value === route; });
    pickerWrap.hidden = route !== 'course';
    recommendNote.hidden = route !== 'recommend';
    picker.required = route === 'course';
    const firstNext = steps?.[0]?.querySelector('[data-step-next]') || form.querySelector('[data-step-next]');
    if (firstNext) firstNext.textContent = route === 'recommend' ? '사전상담으로 이동 →' : '기본 정보 입력하기 →';
    displayCourse();
  }

  function show(index, options = {}) {
    current = Math.max(0, Math.min(index, steps.length - 1));
    steps.forEach((step, i) => { step.hidden = i !== current; });
    indicators.forEach((item, i) => {
      item.classList.toggle('is-active', i <= current);
      if (i === current) item.setAttribute('aria-current', 'step');
      else item.removeAttribute('aria-current');
    });
    if (current === 3) renderReview();
    if (options.scroll !== false) window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function collectData() {
    const data = {};
    new FormData(form).forEach((value, key) => {
      if (typeof File !== 'undefined' && value instanceof File) return;
      data[key] = value;
    });
    data.courseKey = picker.value;
    data.attachmentMeta = attachmentMeta;
    return data;
  }

  function restoreFields(data = {}) {
    Object.entries(data).forEach(([name, value]) => {
      if (['course', 'courseKey', 'applicationRoute', 'attachmentMeta'].includes(name)) return;
      const input = form.elements[name];
      if (!input) return;
      if (typeof RadioNodeList !== 'undefined' && input instanceof RadioNodeList) {
        [...input].forEach(item => { item.checked = item.value === value; });
      } else if (input.type === 'checkbox') {
        input.checked = value === true || value === input.value || value === 'on';
      } else if (input.type !== 'file') {
        input.value = value ?? '';
      }
    });
    attachmentMeta = data.attachmentMeta || null;
    const savedCourseKey = data.courseKey || Object.keys(catalog).find(key => catalog[key].title === data.course);
    if (data.applicationRoute === 'recommend') {
      picker.value = '';
      setRoute('recommend');
    } else if (savedCourseKey && catalog[savedCourseKey]) {
      picker.value = savedCourseKey;
      setRoute('course');
    } else {
      picker.value = '';
      displayCourse();
    }
    updateAttachmentNote(Boolean(attachmentMeta));
  }

  function updateAttachmentNote(restored = false) {
    if (!attachmentNote) return;
    if (!attachmentMeta) {
      attachmentNote.textContent = 'PDF·ZIP·DOC·DOCX 파일을 첨부할 수 있습니다.';
      return;
    }
    const size = attachmentMeta.size ? ` · ${Math.max(1, Math.round(attachmentMeta.size / 1024))}KB` : '';
    attachmentNote.textContent = restored
      ? `저장된 파일: ${attachmentMeta.name}${size} · 보안을 위해 제출 전 파일을 다시 선택해 주세요.`
      : `첨부됨: ${attachmentMeta.name}${size}`;
  }

  function formatTime(value) {
    if (!value) return '';
    return new Intl.DateTimeFormat('ko-KR', {
      year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit'
    }).format(new Date(value));
  }

  function setDraftState(message) {
    if (draftState) draftState.textContent = message;
    const messageNode = form.querySelector('[data-save-message]');
    if (messageNode) messageNode.textContent = message;
  }

  function saveDraft(manual = false) {
    const now = new Date().toISOString();
    const record = {
      version: 2,
      data: collectData(),
      currentStep: current,
      mode,
      updatedAt: now
    };
    localStorage.setItem(draftKey, JSON.stringify(record));
    const time = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' }).format(new Date(now));
    setDraftState(manual ? `지금 저장했습니다 · ${time}` : `자동 저장됨 · ${time}`);
  }

  function queueDraft() {
    window.clearTimeout(saveTimer);
    setDraftState('변경 내용을 저장하는 중입니다…');
    saveTimer = window.setTimeout(() => saveDraft(false), 450);
  }

  function appendReviewBlock(container, title, rows, editStep = null) {
    const article = document.createElement('article');
    const header = document.createElement('header');
    const heading = document.createElement('h3');
    heading.textContent = title;
    header.append(heading);
    if (editStep !== null) {
      const edit = document.createElement('button');
      edit.type = 'button';
      edit.dataset.editStep = String(editStep);
      edit.textContent = '수정';
      header.append(edit);
    }
    article.append(header);
    const list = document.createElement('dl');
    rows.forEach(([label, value]) => {
      const dt = document.createElement('dt');
      const dd = document.createElement('dd');
      dt.textContent = label;
      dd.textContent = value || '입력 안 함';
      list.append(dt, dd);
    });
    article.append(list);
    container.append(article);
  }

  function renderDataSummary(container, data, editable = false) {
    container.replaceChildren();
    appendReviewBlock(container, '신청 과정', [
      ['신청 유형', data.applicationRoute === 'recommend' ? '과정 추천 요청' : '과정 직접 신청'],
      ['과정', data.course]
    ], editable ? 0 : null);
    appendReviewBlock(container, '기본 정보', [
      ['이름', data.name], ['생년월일', data.birth], ['연락처', data.phone],
      ['이메일', data.email], ['현재 상태', data.employment], ['희망 직무', data.job]
    ], editable ? 1 : null);
    appendReviewBlock(container, '경험과 목표', [
      ['신청 동기', data.motivation], ['관련 경험·경력', data.career], ['보유 기술', data.skills]
    ], editable ? 2 : null);
    appendReviewBlock(container, '지원 조건', [
      ['국민내일배움카드', data.card], ['기숙사 상담', data.dorm],
      ['첨부파일', data.attachmentMeta?.name || '첨부 안 함']
    ], editable ? 2 : null);
  }

  function renderReview() {
    const review = form.querySelector('[data-review-grid]');
    if (review) renderDataSummary(review, collectData(), true);
  }

  function statusMeta(status) {
    const table = {
      submitted: {
        label: '제출 완료', kicker: '신청서 제출이 완료되었습니다.',
        title: '신청이 안전하게 접수됐습니다.\n담당자가 확인 후 연락드리겠습니다.', current: 0
      },
      reviewing: {
        label: '검토 중', kicker: '담당자가 신청 내용을 살펴보고 있습니다.',
        title: '과정과 지원 내용을\n꼼꼼히 검토 중입니다.', current: 1
      },
      supplement_requested: {
        label: '보완 요청', kicker: '추가 확인이 필요한 내용이 있습니다.',
        title: '요청 내용을 확인하고\n신청서를 보완해 주세요.', current: 1
      },
      resubmitted: {
        label: '보완 제출 완료', kicker: '보완한 신청서가 다시 접수되었습니다.',
        title: '수정한 내용을\n다시 확인하고 있습니다.', current: 1
      },
      accepted: {
        label: '최종 안내', kicker: '신청서 검토가 완료되었습니다.',
        title: '다음 일정을\n확인해 주세요.', current: 2
      }
    };
    return table[status] || table.submitted;
  }

  function motivationMeta(data = {}) {
    const messages = {
      data: ['데이터 AI 과정과 함께', '데이터를 예측과 서비스 결과로 바꾸는 분석가로 한 발짝 내딛어 봅시다.'],
      factory: ['스마트팩토리 과정과 함께', '산업 데이터를 실제 공정의 변화로 연결하는 자동화 실무자로 성장해 봅시다.'],
      aiot: ['AIoT 과정과 함께', '현장의 데이터를 산업 문제를 푸는 솔루션으로 완성해 봅시다.'],
      robot: ['로봇 AI 과정과 함께', '화면 속 모델을 현실에서 움직이는 기술로 만들어 봅시다.'],
      cloud: ['클라우드 풀스택 과정과 함께', '아이디어를 사용자가 만나는 웹·앱 서비스로 완성해 봅시다.'],
      video: ['AI 영상편집 과정과 함께', '아이디어를 사람의 시선을 붙잡는 콘텐츠로 만들어 봅시다.'],
      uiux: ['AI UI/UX 과정과 함께', '사용자의 문제를 더 나은 제품 경험으로 설계해 봅시다.'],
      japan: ['일본취업 Java 과정과 함께', '기술과 언어를 갖춘 글로벌 개발자로 한 발짝 내딛어 봅시다.'],
      usa: ['미국취업 국제마케팅 과정과 함께', '데이터로 글로벌 시장을 설득하는 마케터로 성장해 봅시다.'],
      china: ['중국취업 AI PM 과정과 함께', '아이디어를 글로벌 제품의 성장으로 연결해 봅시다.'],
      cooking: ['조리실무 과정과 함께', '자격과 현장 기본기를 갖춘 조리사로 첫발을 내디뎌 봅시다.'],
      game: ['게임콘텐츠 과정과 함께', '상상을 직접 플레이할 수 있는 결과물로 만들어 봅시다.'],
      design: ['디지털디자인 과정과 함께', '아이디어를 사람에게 닿는 이미지와 영상으로 완성해 봅시다.'],
      mobility: ['AI 스마트모빌리티 과정과 함께', '스스로 판단하고 움직이는 기술을 직접 구현해 봅시다.'],
      system: ['정보시스템 과정과 함께', '서비스가 안정적으로 움직이게 하는 실무자로 성장해 봅시다.'],
      adsp: ['ADsP·ADP 과정과 함께', '데이터 분석 역량을 합격이라는 결과로 증명해 봅시다.'],
      sqld: ['SQLD·SQLP 과정과 함께', 'SQL과 데이터 모델링 실력을 합격이라는 결과로 완성해 봅시다.'],
      'bigdata-cert': ['빅데이터 분석기사 과정과 함께', '분석 실무 역량을 국가자격으로 증명해 봅시다.'],
      engineer: ['정보처리 기사 과정과 함께', '개발의 기본기를 자격과 실무 역량으로 탄탄하게 완성해 봅시다.']
    };
    const resolvedKey = data.courseKey || Object.keys(catalog).find(key => catalog[key].title === data.course);
    if (data.applicationRoute === 'recommend' || !resolvedKey || !messages[resolvedKey]) {
      return ['모두의 AI와 함께', '나에게 맞는 배움과 새로운 커리어를 향해 한 발짝 내딛어 봅시다.'];
    }
    return messages[resolvedKey];
  }

  function showStatus(record) {
    if (!record?.data) return;
    submittedRecord = record;
    shell.hidden = true;
    done.hidden = false;
    const meta = statusMeta(record.status);
    done.querySelector('[data-application-status-label]').textContent = meta.label;
    done.querySelector('[data-status-kicker]').textContent = meta.kicker;
    done.querySelector('[data-status-title]').textContent = meta.title;
    done.querySelector('[data-status-summary]').textContent = record.status === 'submitted'
      ? `${record.data.course || '과정 추천 상담'} 신청 내용을 확인한 뒤 담당자가 순차적으로 연락드리겠습니다.`
      : `${record.data.course || '과정 추천 상담'} 신청 상태를 이 화면에서 계속 확인할 수 있습니다.`;
    const motivation = motivationMeta(record.data);
    done.querySelector('[data-status-motivation-title]').textContent = motivation[0];
    done.querySelector('[data-status-motivation-copy]').textContent = motivation[1];
    done.querySelector('[data-receipt]').textContent = record.receipt;
    done.querySelector('[data-submitted-at]').textContent = `${formatTime(record.submittedAt)} 제출${record.revision ? ` · ${record.revision}회 보완` : ''}`;
    done.querySelectorAll('[data-status-step]').forEach((item, index) => {
      item.classList.toggle('is-done', index < meta.current);
      item.classList.toggle('is-current', index === meta.current);
    });
    const supplementPanel = done.querySelector('[data-supplement-panel]');
    supplementPanel.hidden = record.status !== 'supplement_requested';
    done.querySelector('[data-supplement-message]').textContent = record.supplement?.message || '담당자가 요청한 내용을 확인한 뒤 관련 항목을 수정해 다시 제출해 주세요.';
    const editButton = done.querySelector('[data-edit-submission]');
    editButton.hidden = !['submitted', 'reviewing'].includes(record.status);
    renderDataSummary(done.querySelector('[data-submitted-summary]'), record.data, false);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  routeInputs.forEach(input => input.addEventListener('change', () => {
    setRoute(input.value);
    queueDraft();
  }));
  picker.addEventListener('change', () => {
    displayCourse();
    queueDraft();
  });
  attachment?.addEventListener('change', () => {
    const file = attachment.files?.[0];
    attachmentMeta = file ? { name: file.name, size: file.size, type: file.type } : null;
    updateAttachmentNote(false);
    queueDraft();
  });

  form.addEventListener('input', event => {
    if (event.target.type !== 'file') queueDraft();
  });
  form.addEventListener('change', event => {
    if (event.target.type !== 'file' && event.target.name !== 'applicationRoute') queueDraft();
  });
  window.addEventListener('pagehide', () => {
    if (!shell.hidden) saveDraft(false);
  });

  form.addEventListener('click', event => {
    const next = event.target.closest('[data-step-next]');
    const prev = event.target.closest('[data-step-prev]');
    const edit = event.target.closest('[data-edit-step]');
    if (next) {
      const required = [...steps[current].querySelectorAll('[required]')];
      if (!required.every(input => input.reportValidity())) return;
      if (current === 0 && selectedRoute() === 'recommend') {
        location.href = '/v2/site/campus/counsel.html';
        return;
      }
      show(current + 1);
      saveDraft(false);
    }
    if (prev) {
      show(current - 1);
      saveDraft(false);
    }
    if (edit) {
      show(Number(edit.dataset.editStep));
      saveDraft(false);
    }
  });

  form.querySelectorAll('[data-save-draft]').forEach(button => {
    button.addEventListener('click', () => {
      saveDraft(true);
      if (button.closest('.application-draft-bar')) return;
      const original = button.textContent;
      button.textContent = '저장됨 ✓';
      button.classList.add('is-saved');
      window.setTimeout(() => {
        button.textContent = original;
        button.classList.remove('is-saved');
      }, 1400);
    });
  });

  done.querySelector('[data-edit-submission]').addEventListener('click', () => {
    if (!submittedRecord?.data) return;
    mode = 'edit';
    restoreFields(submittedRecord.data);
    done.hidden = true;
    shell.hidden = false;
    setDraftState('제출한 내용을 수정 중입니다. 다시 제출해야 변경이 반영됩니다.');
    show(3);
  });

  done.querySelector('[data-start-supplement]').addEventListener('click', () => {
    if (!submittedRecord?.data) return;
    mode = 'supplement';
    restoreFields(submittedRecord.data);
    done.hidden = true;
    shell.hidden = false;
    setDraftState('보완 요청 내용을 반영한 뒤 마지막 단계에서 다시 제출해 주세요.');
    displayCourse();
    show(2);
  });

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
      const now = new Date().toISOString();
      const previous = submittedRecord?.data ? submittedRecord : null;
      const statusHistory = [...(previous?.history || [])];
      const status = mode === 'supplement' ? 'resubmitted' : 'submitted';
      statusHistory.push({ status, at: now });
      const record = {
        version: 2,
        data: collectData(),
        receipt: result.receiptNumber,
        status,
        submittedAt: previous?.submittedAt || now,
        updatedAt: now,
        revision: mode === 'supplement' ? (previous?.revision || 0) + 1 : (previous?.revision || 0),
        supplement: mode === 'supplement' ? { ...(previous?.supplement || {}), resolvedAt: now } : (previous?.supplement || null),
        history: statusHistory
      };
      localStorage.setItem(submittedKey, JSON.stringify(record));
      localStorage.removeItem(draftKey);
      mode = 'new';
      window.history.replaceState(null, '', `${location.pathname}?status=1`);
      showStatus(record);
    } catch (error) {
      if (message) message.textContent = error.message;
      if (submit) submit.disabled = false;
    }
  });

  const draftRecord = readStorage(draftKey);
  const draftData = draftRecord?.data || (draftRecord && !draftRecord.version ? draftRecord : null);

  if (params.get('demo') === 'supplement' && submittedRecord?.data) {
    submittedRecord.status = 'supplement_requested';
    submittedRecord.supplement = {
      requestedAt: new Date().toISOString(),
      message: '관련 경험 항목에 담당 역할과 사용한 도구를 조금 더 구체적으로 적어 주세요.'
    };
    localStorage.setItem(submittedKey, JSON.stringify(submittedRecord));
  }

  if (params.get('status') === '1' && submittedRecord?.data) {
    showStatus(submittedRecord);
  } else if (submittedRecord?.data && initialKey && submittedRecord.data.courseKey === initialKey && !draftData) {
    // 제출 직후 화면 전환 전에 탭을 닫거나 이전 버전 오류가 발생했어도,
    // 같은 과정으로 다시 들어오면 저장된 접수 상태를 바로 복구한다.
    window.history.replaceState(null, '', `${location.pathname}?status=1`);
    showStatus(submittedRecord);
  } else if (initialKey && draftData && draftData.courseKey === initialKey) {
    restoreFields(draftData);
    mode = draftRecord.mode || 'new';
    setDraftState(`임시저장 내용을 불러왔습니다${draftRecord.updatedAt ? ` · ${formatTime(draftRecord.updatedAt)}` : ''}`);
    show(Number.isInteger(draftRecord.currentStep) ? draftRecord.currentStep : 0, { scroll: false });
  } else if (initialKey) {
    picker.value = initialKey;
    setRoute('course');
    show(0, { scroll: false });
  } else if (submittedRecord?.data) {
    showStatus(submittedRecord);
  } else if (draftData) {
    restoreFields(draftData);
    mode = draftRecord.mode || 'new';
    setDraftState(`임시저장 내용을 불러왔습니다${draftRecord.updatedAt ? ` · ${formatTime(draftRecord.updatedAt)}` : ''}`);
    show(Number.isInteger(draftRecord.currentStep) ? draftRecord.currentStep : 0, { scroll: false });
  } else {
    picker.value = '';
    displayCourse();
    show(0, { scroll: false });
  }
})();
