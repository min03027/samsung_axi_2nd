(() => {
  const apiBase = '/v2/api/courses';
  const formatDate = value => value ? value.replaceAll('-', '.') : '상담 시 안내';
  const formatMoney = value => value == null ? '상담 시 안내' : Number(value).toLocaleString('ko-KR') + '원';
  const node = (tag, className, text) => {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text != null) element.textContent = text;
    return element;
  };
  const link = (className, text, href) => {
    const element = node('a', className, text);
    element.href = href;
    return element;
  };

  const compactCard = course => {
    const card = node('article', 'course-card');
    const thumb = node('div', 'course-card__thumb');
    const status = course.dDay == null ? course.recruitmentStatusLabel
      : `${course.recruitmentStatusLabel} · D-${String(course.dDay).padStart(2, '0')}`;
    thumb.append(node('span', 'badge badge--accent', status));
    const body = node('div', 'course-card__body');
    body.append(node('p', 'course-card__cat', `${course.categoryLabel} · ${course.category || '과정'}`));
    body.append(node('h3', 'course-card__title', course.courseName));
    body.append(node('p', 'small muted', course.oneLineIntroduction || '과정 상세에서 교육 내용을 확인하세요.'));
    const meta = node('div', 'course-card__meta');
    meta.append(node('span', '', `${formatDate(course.educationStartDate)} 개강`));
    meta.append(node('span', '', `${course.capacity}명`));
    if (course.educationMethod) meta.append(node('span', '', course.educationMethod));
    if (Number(course.governmentSupport) > 0) meta.append(node('span', 'badge badge--ok', '정부지원'));
    body.append(meta);
    const foot = node('div', 'course-card__foot');
    foot.append(link('btn btn--outline btn--sm', '상세보기', `/v2/site/class/course.html?courseId=${course.id}`));
    foot.append(link('btn btn--primary btn--sm', '신청하기', course.applicationUrl));
    card.append(thumb, body, foot);
    return card;
  };

  const fullCard = course => {
    const card = node('article', 'axi-course-card');
    card.dataset.axiCategory = course.categoryKey;
    const head = node('div');
    head.append(node('span', 'axi-cat', `${course.categoryLabel} · ${course.category || '과정'}`));
    head.append(node('span', 'axi-status', course.recruitmentStatusLabel));
    card.append(head);
    card.append(node('h3', '', course.courseName));
    card.append(node('p', '', course.oneLineIntroduction || '상세 교육 내용과 일정을 확인하세요.'));
    const foot = node('footer');
    foot.append(link('btn btn--outline btn--sm', '상세보기', `/v2/site/class/course.html?courseId=${course.id}`));
    foot.append(link('btn btn--primary btn--sm', '신청하기', course.applicationUrl));
    card.append(foot);
    return card;
  };

  const replaceWithMessage = (grid, message) => {
    if (!grid) return;
    grid.replaceChildren(node('p', 'public-course-empty', message));
    grid.setAttribute('aria-busy', 'false');
  };

  const loadCourseList = async () => {
    const compactGrid = document.querySelector('[data-public-course-grid]');
    const fullGrid = document.querySelector('[data-public-course-grid-all]');
    if (!compactGrid && !fullGrid) return;
    try {
      const response = await fetch(apiBase, { headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error(`course api ${response.status}`);
      const courses = await response.json();
      if (!courses.length) {
        replaceWithMessage(compactGrid, '현재 홈페이지에 공개된 모집 과정이 없습니다. 상담을 통해 다음 일정을 안내받을 수 있어요.');
        replaceWithMessage(fullGrid, '현재 공개 준비 중인 과정입니다. 상담을 신청하면 다음 모집 일정을 안내해 드립니다.');
      } else {
        if (compactGrid) {
          compactGrid.replaceChildren(...courses.slice(0, 6).map(compactCard));
          compactGrid.setAttribute('aria-busy', 'false');
        }
        if (fullGrid) {
          fullGrid.replaceChildren(...courses.map(fullCard));
          fullGrid.setAttribute('aria-busy', 'false');
        }
      }
      document.querySelectorAll('[data-public-course-count]').forEach(el => { el.textContent = String(courses.length); });
      const counts = courses.reduce((result, course) => {
        result[course.categoryKey] = (result[course.categoryKey] || 0) + 1;
        return result;
      }, {});
      document.querySelectorAll('[data-axi-filter]').forEach(button => {
        const small = button.querySelector('small');
        if (!small) return;
        const count = button.dataset.axiFilter === 'all' ? courses.length : (counts[button.dataset.axiFilter] || 0);
        small.textContent = button.dataset.axiFilter === 'all' ? `ALL · ${count}` : `${String(count).padStart(2, '0')} COURSES`;
      });
      document.dispatchEvent(new CustomEvent('public-courses:loaded'));
    } catch (error) {
      console.error('공개 과정 정보를 불러오지 못했습니다.', error);
      replaceWithMessage(compactGrid, '과정 정보를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.');
      replaceWithMessage(fullGrid, '과정 정보를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.');
    }
  };

  const fact = (label, value) => {
    document.querySelectorAll('.sales-info__table > div').forEach(row => {
      if (row.querySelector('dt')?.textContent.trim() !== label) return;
      const cell = row.querySelector('dd');
      if (cell) cell.textContent = value || '상담 시 안내';
    });
  };

  const loadCourseDetail = async () => {
    const params = new URLSearchParams(location.search);
    const courseId = params.get('courseId');
    if (!courseId || !document.body.classList.contains('sales-course')) return;
    try {
      const response = await fetch(`${apiBase}/${encodeURIComponent(courseId)}`, { headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error(`course detail api ${response.status}`);
      const course = await response.json();
      document.title = `${course.courseName} — Samsung AXI`;
      const chip = document.querySelector('.sales-chip');
      if (chip) chip.textContent = `${course.categoryLabel} · ${course.recruitmentStatusLabel}`;
      const heroTitle = document.querySelector('.sales-hero__copy h1');
      if (heroTitle) heroTitle.textContent = course.courseName;
      const heroDescription = document.querySelector('.sales-hero__copy > p:not(.sales-chip)');
      if (heroDescription) heroDescription.textContent = course.oneLineIntroduction || '';
      const orbitNumber = document.querySelector('.predict-orbit b');
      if (orbitNumber) {
        orbitNumber.textContent = String(course.capacity);
        orbitNumber.append(node('small', '', '명'));
      }
      const orbitLabel = document.querySelector('.predict-orbit span');
      if (orbitLabel) orbitLabel.textContent = 'RECRUITMENT CAPACITY';
      const predictNote = document.querySelector('.predict-note');
      if (predictNote) predictNote.textContent = course.categoryLabel;
      const eyebrow = document.querySelector('.sales-info__eyebrow');
      if (eyebrow) eyebrow.textContent = `${course.cohort || '모집 회차'} · ${course.categoryLabel}`;
      const infoTitle = document.querySelector('.sales-info__intro h2');
      if (infoTitle) infoTitle.textContent = course.courseName;
      const infoDescription = document.querySelector('.sales-info__intro > p');
      if (infoDescription) infoDescription.textContent = course.oneLineIntroduction || '';

      fact('모집 상태', course.dDay == null ? course.recruitmentStatusLabel : `${course.recruitmentStatusLabel} · D-${course.dDay}`);
      fact('교육 기간', `${formatDate(course.educationStartDate)} — ${formatDate(course.educationEndDate)}`);
      fact('교육 시간', course.educationTime);
      fact('교육 방법', course.educationMethod);
      fact('수강료', formatMoney(course.tuitionFee));
      fact('본인부담금', formatMoney(course.selfPayment));
      fact('정부지원금', formatMoney(course.governmentSupport));
      fact('모집 정원', `${course.capacity}명`);
      const instructorRow = document.querySelector('[data-instructor-row]');
      if (instructorRow) {
        instructorRow.hidden = false;
        instructorRow.querySelector('dd').textContent = course.instructors.length ? course.instructors.join(', ') : '상담 시 안내';
      }

      const questionTitle = document.querySelector('.sales-question h2');
      if (questionTitle) questionTitle.textContent = '이 과정은 이런 분께 적합합니다';
      const questionText = document.querySelector('.sales-question > .container > p');
      if (questionText) questionText.textContent = course.audience || '과정 상담을 통해 적합도를 확인할 수 있습니다.';
      document.querySelector('.course-conviction')?.setAttribute('hidden', '');
      document.querySelector('.sales-stack')?.setAttribute('hidden', '');

      const outcome = document.querySelector('.sales-outcome');
      const outcomeItems = [
        course.projectPartners ? ['PROJECT PARTNERS', '기업 프로젝트', course.projectPartners] : null,
        course.demoUrl ? ['DEMO', '결과물·데모', course.demoUrl] : null,
        course.mentors ? ['MENTORS', '강사·멘토', course.mentors] : null
      ].filter(Boolean);
      if (outcome) {
        if (!outcomeItems.length) outcome.setAttribute('hidden', '');
        else {
          outcome.querySelector('.sales-section-head span').textContent = 'PROJECT & OUTCOME';
          outcome.querySelector('.sales-section-head h2').textContent = '프로젝트와 공개 결과물';
          const stage = outcome.querySelector('.outcome-stage');
          stage.replaceChildren(...outcomeItems.map((item, index) => {
            const article = node('article');
            article.append(node('small', '', `${String(index + 1).padStart(2, '0')} · ${item[0]}`));
            article.append(node('h3', '', item[1]));
            if (item[0] === 'DEMO') article.append(link('btn btn--outline btn--sm', '데모 열기', item[2]));
            else article.append(node('p', '', item[2]));
            return article;
          }));
        }
      }

      const audience = document.querySelector('[data-course-audience]');
      if (audience) audience.textContent = course.audience || '상담 시 안내';
      const career = document.querySelector('[data-course-career]');
      if (career) career.textContent = course.instructors.length ? `담당 교사 ${course.instructors.join(', ')}` : '담당 교사 배정 후 안내';
      const result = document.querySelector('[data-course-result]');
      if (result) result.textContent = course.projectPartners || course.demoUrl || '과정 프로젝트 결과물';
      const learningSummary = document.querySelector('[data-learning-summary]');
      if (learningSummary) learningSummary.textContent = course.prerequisites
        ? `선수지식: ${course.prerequisites}` : '과목과 차시를 기준으로 실제 교육 내용을 확인하세요.';
      const curriculum = document.querySelector('.curriculum-list');
      if (curriculum) {
        if (course.curriculum.length) {
          curriculum.replaceChildren(...course.curriculum.map((subject, index) => {
            const article = node('article');
            article.append(node('span', '', `PHASE ${String(index + 1).padStart(2, '0')}`));
            const copy = node('div');
            copy.append(node('h3', '', subject.name));
            const lessons = subject.sessions.map(item => item.name).join(' · ');
            copy.append(node('p', '', lessons || subject.description || '상세 차시는 개강 전 안내합니다.'));
            article.append(copy, node('b', '', `${subject.sessions.length} LESSONS`));
            return article;
          }));
        } else {
          curriculum.replaceChildren(node('p', 'public-course-empty', '상세 커리큘럼은 개강 전 공개됩니다.'));
        }
      }

      document.querySelectorAll('a[href^="/v2/site/class/apply.html"]').forEach(a => { a.href = course.applicationUrl; });
      document.querySelectorAll('a[href^="/v2/site/campus/counsel.html"]').forEach(a => { a.href = course.consultationUrl; });
      const sticky = document.querySelector('.sales-sticky');
      if (sticky) {
        sticky.querySelector('b').textContent = course.courseName;
        sticky.querySelector('span').textContent = `${formatDate(course.educationStartDate)} 개강 · 정원 ${course.capacity}명`;
      }
      const apply = document.querySelector('.sales-apply');
      if (apply) {
        apply.querySelector('p').textContent = `${formatDate(course.educationStartDate)} 개강 · 정원 ${course.capacity}명`;
        apply.querySelector('h2').textContent = `${course.courseName}에 참여할 준비가 됐나요?`;
      }
    } catch (error) {
      console.error('공개 과정 상세를 불러오지 못했습니다.', error);
      const chip = document.querySelector('.sales-chip');
      if (chip) chip.textContent = '현재 공개되지 않은 과정';
      document.querySelectorAll('a[href^="/v2/site/class/apply.html"]').forEach(a => {
        a.href = '/v2/site/class/index.html#course-list-all';
        a.textContent = '모집 과정 확인';
      });
    }
  };

  loadCourseList();
  loadCourseDetail();
})();
