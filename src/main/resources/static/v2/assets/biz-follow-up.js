(() => {
  const companies = [
    {id:'manufacturing',name:'제조 A사',program:'품질·현장 업무 자동화',participants:24,owner:'기업교육팀 김매니저',next:'2026.09.04 성과 리뷰',updated:'2026.08.26 기준',survey:'92%',output:'현장 보고 템플릿 3종',stages:[['교육','done'],['설문·성과','done'],['후속과제','active'],['심화교육','next'],['컨설팅·SI','wait']],actions:[['현장 템플릿 적용 결과 확인','2026.09.04','진행 중'],['품질팀 심화교육 범위 협의','2026.09.11','예정'],['PoC 대상 공정 선정','미정','검토']],history:[['2026.08.26','성과 검토','수료 설문과 결과물 검수','성과 요약 1부','완료'],['2026.08.19','교육','품질 기록·보고 자동화 실습','현장 템플릿 3종','완료'],['2026.09.04','후속과제','현장 적용 결과 리뷰','적용 체크리스트','진행 중']]},
    {id:'finance',name:'금융 C사',program:'규정 문서 기반 RAG PoC',participants:12,owner:'AX컨설팅팀 이리드',next:'2026.09.08 보안 검토',updated:'2026.08.25 기준',survey:'88%',output:'검색 PoC·보안 체크리스트',stages:[['교육','done'],['설문·성과','done'],['후속과제','done'],['심화교육','active'],['컨설팅·SI','next']],actions:[['보안 검토 의견 반영','2026.09.08','진행 중'],['운영 데이터 범위 확정','2026.09.15','예정']],history:[['2026.08.25','교육','규정 문서 검색·답변 PoC','검색 PoC','완료'],['2026.08.29','후속과제','근거 표시와 권한 기준 보완','보안 체크리스트','완료'],['2026.09.08','컨설팅 검토','실서비스 적용 범위 협의','검토 의견서','예정']]},
    {id:'public',name:'공공 F기관',program:'행정 보고서·자료 요약',participants:30,owner:'공공교육팀 박매니저',next:'2026.09.02 심화 수요 조사',updated:'2026.08.24 기준',survey:'95%',output:'행정 문서 프롬프트 5종',stages:[['교육','done'],['설문·성과','done'],['후속과제','active'],['심화교육','wait'],['컨설팅·SI','wait']],actions:[['부서별 활용 사례 취합','2026.09.02','진행 중'],['심화교육 대상자 확인','2026.09.09','예정']],history:[['2026.08.24','교육','행정 문서 요약과 보고서 실습','프롬프트 5종','완료'],['2026.08.26','설문·성과','참여자 만족도와 적용 업무 확인','설문 요약','완료'],['2026.09.02','후속과제','부서별 활용 사례 취합','사례 목록','진행 중']]}
  ];
  const select = document.getElementById('followup-company');
  if (!select) return;
  const summary = document.querySelector('[data-followup-summary]');
  const stages = document.querySelector('[data-followup-stages]');
  const actions = document.querySelector('[data-followup-actions]');
  const owner = document.querySelector('[data-followup-owner]');
  const history = document.querySelector('[data-followup-history]');
  const updated = document.querySelector('[data-followup-updated]');
  const statusClass = status => status === '완료' ? 'done' : status === '진행 중' ? 'active' : 'next';
  select.innerHTML = companies.map(company => `<option value="${company.id}">${company.name} · ${company.program}</option>`).join('');

  function render() {
    const company = companies.find(item => item.id === select.value) || companies[0];
    summary.innerHTML = `<div><dt>교육 과정</dt><dd>${company.program}</dd></div><div><dt>참여 인원</dt><dd>${company.participants}명</dd></div><div><dt>설문 응답</dt><dd>${company.survey}</dd></div><div><dt>주요 결과물</dt><dd>${company.output}</dd></div>`;
    updated.textContent = company.updated;
    stages.innerHTML = company.stages.map(([label,state],index) => `<li class="is-${state}"><i>${String(index+1).padStart(2,'0')}</i><strong>${label}</strong><span>${state === 'done' ? '완료' : state === 'active' ? '진행 중' : state === 'next' ? '다음 단계' : '검토 전'}</span></li>`).join('');
    actions.innerHTML = company.actions.map(([title,date,status]) => `<article><div><span class="followup-status is-${statusClass(status)}">${status}</span><h3>${title}</h3></div><time>${date}</time></article>`).join('');
    owner.innerHTML = `<div><dt>운영 담당</dt><dd>${company.owner}</dd></div><div><dt>다음 일정</dt><dd>${company.next}</dd></div><div><dt>관계 구분</dt><dd>교육 수행 · 확장 검토</dd></div>`;
    history.innerHTML = company.history.map(([date,stage,content,output,status]) => `<tr><td>${date}</td><td><strong>${stage}</strong></td><td>${content}</td><td>${output}</td><td><span class="followup-status is-${statusClass(status)}">${status}</span></td></tr>`).join('');
  }
  select.addEventListener('change', render);
  render();
})();
