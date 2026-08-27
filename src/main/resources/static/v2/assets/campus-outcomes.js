(() => {
  const records = [
    {course:'data',courseLabel:'데이터·AI',year:'2026',role:'ai',roleLabel:'AI·데이터',completed:24,employed:17,certified:19,projects:31,project:'수요 예측·RAG 서비스',certificate:'SQLD·ADsP·수료',caseId:'20126'},
    {course:'data',courseLabel:'데이터·AI',year:'2025',role:'developer',roleLabel:'개발',completed:21,employed:14,certified:15,projects:27,project:'개인화 분석 대시보드',certificate:'ADsP·수료',caseId:'20136'},
    {course:'robot',courseLabel:'로봇·AIoT',year:'2026',role:'automation',roleLabel:'자동화·로봇',completed:18,employed:12,certified:9,projects:24,project:'라이다 자율주행 로봇',certificate:'산업기사·수료',caseId:'20117'},
    {course:'robot',courseLabel:'로봇·AIoT',year:'2024',role:'developer',roleLabel:'개발',completed:16,employed:10,certified:8,projects:19,project:'IoT 관제 서비스',certificate:'정보처리·수료',caseId:'20011'},
    {course:'cloud',courseLabel:'웹·클라우드',year:'2026',role:'developer',roleLabel:'개발',completed:26,employed:19,certified:14,projects:35,project:'클라우드 기반 협업 서비스',certificate:'정보처리·수료',caseId:'20039'},
    {course:'cloud',courseLabel:'웹·클라우드',year:'2025',role:'developer',roleLabel:'개발',completed:22,employed:16,certified:12,projects:29,project:'기업용 웹 애플리케이션',certificate:'정보처리·수료',caseId:'20034'}
  ];
  const form = document.getElementById('outcomes-filter');
  if (!form) return;
  const summary = document.querySelector('[data-outcome-summary]');
  const bars = document.querySelector('[data-outcome-bars]');
  const roles = document.querySelector('[data-outcome-roles]');
  const table = document.querySelector('[data-outcome-records]');
  const caption = document.querySelector('[data-outcome-caption]');
  const sum = (rows, key) => rows.reduce((total, row) => total + row[key], 0);
  const escape = value => String(value).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));

  function render() {
    const data = new FormData(form);
    const course = data.get('course');
    const year = data.get('year');
    const role = data.get('role');
    const filtered = records.filter(row => (course === 'all' || row.course === course) && (year === 'all' || row.year === year) && (role === 'all' || row.role === role));
    const completed = sum(filtered, 'completed');
    const employed = sum(filtered, 'employed');
    const certified = sum(filtered, 'certified');
    const projects = sum(filtered, 'projects');
    const rate = completed ? Math.round(employed / completed * 100) : 0;
    summary.innerHTML = [
      ['수료 기록', completed + '명'],['취업 확인', employed + '명'],['취업 확인율', rate + '%'],['자격 기록', certified + '건'],['프로젝트', projects + '건']
    ].map(([label,value]) => `<div><span>${label}</span><strong>${value}</strong></div>`).join('');
    caption.textContent = `${filtered.length}개 과정·연도 집계 기준`;

    const courseGroups = ['data','robot','cloud'].map(key => {
      const rows = filtered.filter(row => row.course === key);
      return {label:records.find(row => row.course === key).courseLabel,completed:sum(rows,'completed'),employed:sum(rows,'employed')};
    }).filter(item => item.completed);
    bars.innerHTML = courseGroups.length ? courseGroups.map(item => {
      const percent = Math.round(item.employed / item.completed * 100);
      return `<div><header><b>${escape(item.label)}</b><span>${item.employed} / ${item.completed}명</span></header><div><i style="width:${percent}%"></i></div><small>취업 확인율 ${percent}%</small></div>`;
    }).join('') : '<p class="outcomes-empty">선택한 조건에 맞는 샘플 기록이 없습니다.</p>';

    const roleGroups = ['ai','developer','automation'].map(key => {
      const rows = filtered.filter(row => row.role === key);
      return {label:records.find(row => row.role === key).roleLabel,value:sum(rows,'employed')};
    }).filter(item => item.value);
    const roleTotal = roleGroups.reduce((total,item) => total + item.value, 0);
    roles.innerHTML = roleGroups.length ? roleGroups.map(item => `<div><strong>${escape(item.label)}</strong><span>${item.value}명</span><i style="width:${Math.round(item.value / roleTotal * 100)}%"></i></div>`).join('') : '<p class="outcomes-empty">표시할 진출 직무가 없습니다.</p>';

    table.innerHTML = filtered.length ? filtered.map(row => `<tr><td><strong>${escape(row.courseLabel)}</strong></td><td>${row.year}</td><td>${escape(row.project)}</td><td>${escape(row.certificate)}</td><td>${escape(row.roleLabel)}</td><td><a href="/v2/site/class/review.html?id=${row.caseId}">인터뷰 보기 →</a></td></tr>`).join('') : '<tr><td colspan="6" class="outcomes-empty">선택한 조건에 맞는 샘플 기록이 없습니다.</td></tr>';
  }
  form.addEventListener('submit', event => { event.preventDefault(); render(); });
  form.addEventListener('change', render);
  render();
})();
