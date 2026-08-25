(function () {
  // -----------------------
  // Mock Data
  // -----------------------
  // 서버(Thymeleaf)가 window._serverExamRows 를 내려주면 그것을 쓰고,
  // 없으면(정적 미리보기) 아래 더미를 그대로 쓴다. — contents.js 와 같은 방식
  const mockExams = window._serverExamRows || [
    {
      id: 'e101',
      name: 'JavaScript 중간평가 (1)',
      courseId: 'c01',
      courseName: '풀스택 웹 개발 (React & Node.js)',
      sessionName: '3차시',
      windowStart: '2026-01-26 09:00',
      windowEnd: '2026-01-27 23:59',
      durationMin: 60,
      questionCount: 20,
      typeText: '객관식 + 서술형',
      retakePolicy: '불가',
      attemptsUsed: 0,
      attemptsTotal: 1,
      status: 'available',
      note: '응시 시작 후 60분 제한시간이 적용됩니다.\n탭 전환/새로고침 시 제한될 수 있어요.'
    },
    {
      id: 'e102',
      name: 'Node.js 환경설정 퀴즈',
      courseId: 'c01',
      courseName: '풀스택 웹 개발 (React & Node.js)',
      sessionName: '1차시',
      windowStart: '2026-01-25 10:00',
      windowEnd: '2026-01-28 23:59',
      durationMin: 20,
      questionCount: 10,
      typeText: '객관식',
      retakePolicy: '가능(최대 2회)',
      attemptsUsed: 1,
      attemptsTotal: 2,
      status: 'in_progress',
      note: '이어하기가 가능한 시험입니다.\n응시 중 종료 후 기간 내 다시 접속하면 이어서 진행할 수 있어요.'
    },
    {
      id: 'e103',
      name: 'Pandas 전처리 미니 테스트',
      courseId: 'c02',
      courseName: '데이터 분석 입문 (Python)',
      sessionName: '2차시',
      windowStart: '2026-01-20 09:00',
      windowEnd: '2026-01-22 23:59',
      durationMin: 30,
      questionCount: 12,
      typeText: '객관식 + 단답형',
      retakePolicy: '불가',
      attemptsUsed: 1,
      attemptsTotal: 1,
      status: 'completed',
      note: '제출 완료된 시험입니다.\n결과보기는 채점 완료 후 제공될 수 있어요.'
    },
    {
      id: 'e104',
      name: 'React 기초 퀴즈 (예정)',
      courseId: 'c01',
      courseName: '풀스택 웹 개발 (React & Node.js)',
      sessionName: '4차시',
      windowStart: '2026-01-28 09:00',
      windowEnd: '2026-01-29 23:59',
      durationMin: 25,
      questionCount: 15,
      typeText: '객관식',
      retakePolicy: '가능(최대 3회)',
      attemptsUsed: 0,
      attemptsTotal: 3,
      status: 'scheduled',
      note: '응시 기간이 시작되면 “응시하기” 버튼이 활성화됩니다.'
    },
    {
      id: 'e105',
      name: 'AI 기초 - Prompt 이해도 테스트',
      courseId: 'c03',
      courseName: 'AI 기초 (Prompt & RAG)',
      sessionName: '1차시',
      windowStart: '2026-01-10 09:00',
      windowEnd: '2026-01-12 18:00',
      durationMin: 40,
      questionCount: 18,
      typeText: '객관식',
      retakePolicy: '불가',
      attemptsUsed: 0,
      attemptsTotal: 1,
      status: 'ended',
      note: '응시 기간이 종료된 시험입니다.'
    }
  ];

  // 과정 필터 목록. 서버 데이터가 있으면 행에서 뽑아 쓴다(별도 API 를 부르지 않는다).
  const mockCourses = window._serverExamRows
    ? (function () {
        const seen = new Map();
        mockExams.forEach(ex => {
          if (ex.courseId && !seen.has(String(ex.courseId))) {
            seen.set(String(ex.courseId), { id: String(ex.courseId), name: ex.courseName });
          }
        });
        return [...seen.values()];
      })()
    : [
        { id: 'c01', name: '풀스택 웹 개발 (React & Node.js)' },
        { id: 'c02', name: '데이터 분석 입문 (Python)' },
        { id: 'c03', name: 'AI 기초 (Prompt & RAG)' }
      ];

  // -----------------------
  // Elements
  // -----------------------
  const fCourse = document.getElementById('fCourse');
  const fStatus = document.getElementById('fStatus');
  const fSort = document.getElementById('fSort');
  const fKeyword = document.getElementById('fKeyword');
  const btnSearch = document.getElementById('btnSearch');
  const btnReset = document.getElementById('btnReset');
  const listHint = document.getElementById('listHint');
  const cardList = document.getElementById('cardList');
  const pagination = document.getElementById('pagination');

  // Modal elements
  const examDetailModal = document.getElementById('examDetailModal');
  const dStatus = document.getElementById('dStatus');
  const dName = document.getElementById('dName');
  const dCourse = document.getElementById('dCourse');
  const dSession = document.getElementById('dSession');
  const dWindow = document.getElementById('dWindow');
  const dDuration = document.getElementById('dDuration');
  const dQuestions = document.getElementById('dQuestions');
  const dType = document.getElementById('dType');
  const dRetake = document.getElementById('dRetake');
  const dAttempts = document.getElementById('dAttempts');
  // 시험 설정 3종 (서버 ExamTakeRow: setCount / resultReleaseText / practiceMode).
  // 정적 미리보기 더미에는 없는 필드라 전부 null 안전하게 다룬다.
  const dSetCount = document.getElementById('dSetCount');
  const dResultRelease = document.getElementById('dResultRelease');
  const dPractice = document.getElementById('dPractice');
  const dNote = document.getElementById('dNote');
  const btnPrimaryAction = document.getElementById('btnPrimaryAction');

  // 서버 연동 전용 엘리먼트 (정적 미리보기에는 없다 → 전부 null 안전하게 다룬다)
  const identityModal = document.getElementById('identityModal');
  const identityExamName = document.getElementById('identityExamName');
  const identityCredential = document.getElementById('identityCredential');
  const btnIdentityConfirm = document.getElementById('btnIdentityConfirm');
  const startForm = document.getElementById('startForm');
  const startVerifyMethod = document.getElementById('startVerifyMethod');
  const startCredential = document.getElementById('startCredential');
  const examUrls = window._examUrls || null;
  let identityExamId = null;

  // -----------------------
  // State
  // -----------------------
  const PAGE_SIZE = 6;
  let currentPage = 1;
  let filtered = [];
  let selectedExamId = null;

  // -----------------------
  // Utils
  // -----------------------
  function escapeText(s) {
    return String(s)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function statusLabel(status) {
    switch (status) {
      case 'available': return '응시가능';
      case 'in_progress': return '응시중';
      case 'completed': return '응시완료';
      case 'scheduled': return '예정';
      case 'ended': return '종료';
      default: return '-';
    }
  }

  function statusClass(status) {
    return status || '';
  }

  /**
   * 시험 한 건의 주 액션. 서버 행에는 startable/blockReason 이 있어 그것을 우선한다
   * (문항 미확정 시험처럼 상태만 봐서는 알 수 없는 차단 사유가 있다).
   */
  function actionForExam(ex) {
    const action = primaryActionFor(ex && ex.status);
    if (ex && ex.startable === false && (action.type === 'start' || action.type === 'resume')) {
      action.enabled = false;
    }
    if (action.type === 'result' && ex && !ex.lastAttemptId && window._examUrls) {
      action.enabled = false;
    }
    return action;
  }

  function primaryActionFor(status) {
    // label + enabled + actionType
    switch (status) {
      case 'available': return { label: '응시하기', enabled: true, type: 'start' };
      case 'in_progress': return { label: '이어하기', enabled: true, type: 'resume' };
      case 'completed': return { label: '결과보기', enabled: true, type: 'result' };
      case 'scheduled': return { label: '응시하기', enabled: false, type: 'start' };
      case 'ended': return { label: '응시 종료', enabled: false, type: 'none' };
      default: return { label: '-', enabled: false, type: 'none' };
    }
  }

  function openModal(el) {
    el.classList.add('open');
    el.setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';
  }
  function closeModal(el) {
    el.classList.remove('open');
    el.setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
  }

  function renderCourseOptions() {
    const opts = mockCourses.map(c => `<option value="${c.id}">${escapeText(c.name)}</option>`).join('');
    fCourse.innerHTML = `<option value="">전체</option>${opts}`;
  }

  // -----------------------
  // Filter + Sort
  // -----------------------
  function applyFilters() {
    const courseId = fCourse.value;
    const status = fStatus.value;
    const sort = fSort.value;
    const keyword = fKeyword.value.trim().toLowerCase();

    filtered = mockExams.filter(ex => {
      if (courseId && ex.courseId !== courseId) return false;
      if (status && ex.status !== status) return false;
      if (keyword) {
        const hay = (ex.name + ' ' + ex.courseName).toLowerCase();
        if (!hay.includes(keyword)) return false;
      }
      return true;
    });

    // sorting
    if (sort === 'available_first') {
      const rank = (s) => {
        // available -> in_progress -> scheduled -> completed -> ended
        if (s === 'available') return 1;
        if (s === 'in_progress') return 2;
        if (s === 'scheduled') return 3;
        if (s === 'completed') return 4;
        if (s === 'ended') return 5;
        return 9;
      };
      filtered.sort((a,b) => {
        const ra = rank(a.status), rb = rank(b.status);
        if (ra !== rb) return ra - rb;
        return a.name.localeCompare(b.name);
      });
    } else if (sort === 'start_asc') {
      filtered.sort((a,b) => a.windowStart.localeCompare(b.windowStart));
    } else if (sort === 'start_desc') {
      filtered.sort((a,b) => b.windowStart.localeCompare(a.windowStart));
    } else if (sort === 'title_asc') {
      filtered.sort((a,b) => a.name.localeCompare(b.name));
    }

    currentPage = 1;
    renderList();
    renderPagination();
    listHint.textContent = `전체 ${filtered.length}건`;
  }

  // -----------------------
  // Render List
  // -----------------------
  function renderList() {
    const start = (currentPage - 1) * PAGE_SIZE;
    const pageItems = filtered.slice(start, start + PAGE_SIZE);

    if (!pageItems.length) {
      cardList.innerHTML = `
        <div style="grid-column:1/-1;background:#fff;border:1px solid #e6e6e6;border-radius:14px;padding:24px;text-align:center;color:#777;">
          조건에 맞는 시험이 없어요.
        </div>
      `;
      return;
    }

    cardList.innerHTML = pageItems.map(ex => {
      const badgeCls = statusClass(ex.status);
      const badgeText = statusLabel(ex.status);
      const action = actionForExam(ex);

      const attemptsLeft = Math.max(0, ex.attemptsTotal - ex.attemptsUsed);
      // 모의 테스트는 응시 횟수 제한이 없다 (서버 ExamAttemptService.start 가 횟수를 세지 않는다).
      const attemptsText = ex.practiceMode ? '무제한' : `${attemptsLeft}/${ex.attemptsTotal}`;

      // 시험 설정 3종 표시 — 정식/모의 구분, 문제 세트 수, 성적 공개 정책
      const practiceTag = ex.practiceMode
        ? '<span class="exam-tag tag-practice">모의 테스트</span>'
        : '';
      const setTag = (Number(ex.setCount) || 1) > 1
        ? `<span class="exam-tag tag-set">문제세트 ${Number(ex.setCount)}종 랜덤</span>`
        : '';
      const releaseTag = ex.resultReleaseText
        ? `<span class="exam-tag tag-release">성적 ${escapeText(ex.resultReleaseText)}</span>`
        : '';
      const tagRow = (practiceTag || setTag || releaseTag)
        ? `<div class="exam-tags">${practiceTag}${setTag}${releaseTag}</div>`
        : '';

      // Determine button class by action label
      let btnClass = 'btn-primary';
      if (action.label === '결과보기') btnClass = 'btn-secondary';
      else if (action.label === '응시 종료') btnClass = 'btn-gray';
      else if (action.label === '응시하기' || action.label === '이어하기') btnClass = 'btn-primary';

      return `
        <div class="exam-card" data-exam-id="${ex.id}">
          <div class="card-top">
            <span class="badge ${badgeCls}">${badgeText}</span>
            <span class="deadline">${escapeText(ex.windowStart)} ~ ${escapeText(ex.windowEnd)}</span>
          </div>

          <div class="card-title">${escapeText(ex.name)}</div>
          ${tagRow}

          <div class="meta">
            <span><span class="dim">과정</span> ${escapeText(ex.courseName)}</span>
            <span><span class="dim">차시</span> ${escapeText(ex.sessionName)}</span>
          </div>

          <div class="meta">
            <span><span class="dim">제한시간</span> ${ex.durationMin}분</span>
            <span><span class="dim">문항</span> ${ex.questionCount}</span>
            <span><span class="dim">유형</span> ${escapeText(ex.typeText)}</span>
          </div>

          <div class="submeta">
            재응시: ${escapeText(ex.retakePolicy)} · 남은시도: ${attemptsText}
          </div>

          <div class="card-actions">
            <button type="button" class="btn btn-gray" data-action="detail">상세보기</button>
            <button type="button" class="btn ${btnClass}" data-action="primary" ${action.enabled ? '' : 'disabled'}>
              ${escapeText(action.label)}
            </button>
          </div>
        </div>
      `;
    }).join('');
  }

  // -----------------------
  // Pagination
  // -----------------------
  function renderPagination() {
    const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
    if (totalPages === 1) {
      pagination.innerHTML = '';
      return;
    }

    const btns = [];
    const maxShow = 7;
    let start = Math.max(1, currentPage - 3);
    let end = Math.min(totalPages, start + (maxShow - 1));
    start = Math.max(1, end - (maxShow - 1));

    btns.push(`<button class="page-btn" data-page="${Math.max(1, currentPage - 1)}">‹</button>`);
    for (let p = start; p <= end; p++) {
      btns.push(`<button class="page-btn ${p === currentPage ? 'active' : ''}" data-page="${p}">${p}</button>`);
    }
    btns.push(`<button class="page-btn" data-page="${Math.min(totalPages, currentPage + 1)}">›</button>`);

    pagination.innerHTML = btns.join('');
  }

  // -----------------------
  // Detail Modal
  // -----------------------
  function openDetail(examId) {
    const ex = mockExams.find(x => x.id === examId);
    if (!ex) return;

    selectedExamId = ex.id;

    // status badge
    dStatus.className = `badge ${statusClass(ex.status)}`;
    dStatus.textContent = statusLabel(ex.status);

    dName.textContent = ex.name;
    dCourse.textContent = ex.courseName;
    dSession.textContent = ex.sessionName || '-';
    dWindow.textContent = `${ex.windowStart} ~ ${ex.windowEnd}`;
    dDuration.textContent = `${ex.durationMin}분`;
    dQuestions.textContent = `${ex.questionCount}문항`;
    dType.textContent = ex.typeText;
    dRetake.textContent = ex.retakePolicy;

    const attemptsLeft = Math.max(0, ex.attemptsTotal - ex.attemptsUsed);
    // 모의 테스트는 횟수를 세지 않으므로 "남은시도" 를 숫자로 보여주면 거짓말이 된다.
    dAttempts.textContent = ex.practiceMode ? '무제한 (모의 테스트)' : `${attemptsLeft}/${ex.attemptsTotal}`;

    if (dSetCount) {
      const sets = Number(ex.setCount) || 1;
      dSetCount.textContent = sets > 1
        ? `${sets}개 — 응시 시작 시 한 세트가 무작위로 배정됩니다`
        : '1개 (전원 동일 문항)';
    }
    if (dResultRelease) dResultRelease.textContent = ex.resultReleaseText || '즉시 공개';
    if (dPractice) {
      dPractice.textContent = ex.practiceMode
        ? '사전 모의 테스트 — 성적에 반영되지 않습니다'
        : '정식 시험 — 성적에 반영됩니다';
    }

    dNote.textContent = ex.note || '-';

    const action = actionForExam(ex);
    btnPrimaryAction.textContent = action.label;
    btnPrimaryAction.disabled = !action.enabled;
    btnPrimaryAction.dataset.actionType = action.type;

    openModal(examDetailModal);
  }

  /** 응시 시작 POST. 서버가 본인인증·기간·재응시 횟수를 다시 검사하고 실패하면 목록으로 되돌린다. */
  function submitStart(examId, credential) {
    startForm.action = examUrls.start.replace('{id}', examId);
    startVerifyMethod.value = 'PASSWORD';
    startCredential.value = credential || '';
    startForm.submit();
  }

  function openIdentity(ex) {
    identityExamId = ex.id;
    identityExamName.textContent = ex.name;
    identityCredential.value = '';
    openModal(identityModal);
    identityCredential.focus();
  }

  function handlePrimaryAction(examId, actionType) {
    const ex = mockExams.find(x => String(x.id) === String(examId));
    if (!ex) return;

    // 서버 연동 모드 — 템플릿이 window._examUrls 를 내려준 경우
    if (examUrls && startForm) {
      if (actionType === 'start' || actionType === 'resume') {
        if (ex.startable === false && actionType === 'start') {
          alert(ex.blockReason || '지금은 응시할 수 없습니다.');
          return;
        }
        /* LXP-015 — 사전점검 대상 시험은 POST start 도, 비밀번호 모달도 열지 않는다.
           QR 신분확인·웹캠 확인을 마쳐야 서버가 입장을 허용하므로 사전점검 화면으로 보낸다.
           이어하기(resume)도 서버가 게이트를 다시 검사하므로 같은 경로로 보낸다. */
        if (ex.precheckRequired && examUrls.precheck) {
          location.href = examUrls.precheck.replace('{id}', ex.id);
          return;
        }
        if (ex.requireIdentityVerification) {
          openIdentity(ex);
        } else {
          submitStart(ex.id, '');
        }
        return;
      }
      if (actionType === 'result') {
        const attemptId = ex.lastAttemptId || ex.inProgressAttemptId;
        if (!attemptId) {
          alert('조회할 응시 회차가 없습니다.');
          return;
        }
        location.href = examUrls.attempt.replace('{id}', attemptId);
      }
      return;
    }

    // 정적 미리보기 모드 (기존 동작 유지)
    if (actionType === 'start' || actionType === 'resume') {
      window.open('do-test.html', '_blank', 'width=1200,height=800,resizable=yes,scrollbars=yes');
      return;
    } else if (actionType === 'result') {
      // 새 창을 화면 가운데에 띄우기
      const w = 1200, h = 800;
      const dualScreenLeft = window.screenLeft !== undefined ? window.screenLeft : window.screenX;
      const dualScreenTop = window.screenTop !== undefined ? window.screenTop : window.screenY;
      const width = window.innerWidth ? window.innerWidth : document.documentElement.clientWidth ? document.documentElement.clientWidth : screen.width;
      const height = window.innerHeight ? window.innerHeight : document.documentElement.clientHeight ? document.documentElement.clientHeight : screen.height;
      const left = dualScreenLeft + (width - w) / 2;
      const top = dualScreenTop + (height - h) / 2;
      window.open('/templates/trainee/grading-modal-result.html', '_blank', `width=${w},height=${h},left=${left},top=${top},resizable=yes,scrollbars=yes`);
    }
  

  }

  // -----------------------
  // Events
  // -----------------------
  function bindEvents() {
    btnSearch.addEventListener('click', applyFilters);
    btnReset.addEventListener('click', () => {
      fCourse.value = '';
      fStatus.value = '';
      fSort.value = 'available_first';
      fKeyword.value = '';
      applyFilters();
    });

    fCourse.addEventListener('change', applyFilters);
    fStatus.addEventListener('change', applyFilters);
    fSort.addEventListener('change', applyFilters);

    fKeyword.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') applyFilters();
    });

    // Card actions
    cardList.addEventListener('click', (e) => {
      const card = e.target.closest('.exam-card');
      if (!card) return;
      const examId = card.dataset.examId;

      const actionBtn = e.target.closest('[data-action]');
      if (!actionBtn) return;

      const action = actionBtn.dataset.action;
      if (action === 'detail') {
        openDetail(examId);
      } else if (action === 'primary') {
        const ex = mockExams.find(x => x.id === examId);
        const pa = actionForExam(ex);
        if (pa.enabled) handlePrimaryAction(examId, pa.type);
      }
    });

    // Pagination
    pagination.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-page]');
      if (!btn) return;
      const p = Number(btn.dataset.page);
      if (!Number.isFinite(p)) return;
      currentPage = p;
      renderList();
      renderPagination();
    });

    // Modal close
    examDetailModal.addEventListener('click', (e) => {
      if (e.target === examDetailModal) closeModal(examDetailModal);
    });
    examDetailModal.querySelectorAll('[data-close="examDetailModal"]').forEach(btn => {
      btn.addEventListener('click', () => closeModal(examDetailModal));
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && examDetailModal.classList.contains('open')) {
        closeModal(examDetailModal);
      }
    });

    // Modal primary action
    btnPrimaryAction.addEventListener('click', () => {
      if (!selectedExamId) return;
      const actionType = btnPrimaryAction.dataset.actionType || 'none';
      if (actionType === 'none') return;
      handlePrimaryAction(selectedExamId, actionType);
    });

    // 본인인증 모달 (서버 연동 모드에서만 존재)
    if (identityModal) {
      identityModal.addEventListener('click', (e) => {
        if (e.target === identityModal) closeModal(identityModal);
      });
      identityModal.querySelectorAll('[data-close="identityModal"]').forEach(btn => {
        btn.addEventListener('click', () => closeModal(identityModal));
      });
      identityCredential.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') btnIdentityConfirm.click();
      });
      btnIdentityConfirm.addEventListener('click', () => {
        const credential = identityCredential.value.trim();
        if (!credential) {
          alert('본인인증을 위해 비밀번호를 입력하세요.');
          identityCredential.focus();
          return;
        }
        if (!identityExamId) return;
        submitStart(identityExamId, credential);
      });
    }
  }

  // -----------------------
  // Init
  // -----------------------
  function init() {
    // course options
    const opts = mockCourses.map(c => `<option value="${c.id}">${escapeText(c.name)}</option>`).join('');
    fCourse.innerHTML = `<option value="">전체</option>${opts}`;

    filtered = [...mockExams];
    applyFilters();
    bindEvents();
  }

  init();
})();