(() => {
  const raw = [
    [20136,'불안함을 없애는 커리큘럼 ㅡ데이터를 쓰는 법은 여기서 익혔어요','임O은','20260707181846_1783415926_0_1.png','data'],
    [20135,'웹 개발을 넘어, 스스로 판단하는 AI로','허O원','20260707181709_1783415829_0_1.png','ai web'],
    [20126,'무역회사 대신 선택한 IT, 데이터와 로봇','김O신','20260617174609_1781685969_0_1.png','data ai'],
    [20117,'가위를 내려놓고, 코드를 잡았습니다','조O래','20260616205959_1781611199_0_1.png','career'],
    [20116,'엑셀을 쓰던 손으로, 이제 코드를 씁니다','김O은','20260616210155_1781611315_0_1.png','data career'],
    [20106,'교실 밖에서, 스스로 찾은 AI의 길','임O리','20260616210233_1781611353_0_1.png','ai career'],
    [20040,'흥미도 꿈도 없던 제가, IT를 업으로 삼기까지','류O지','20260616210429_1781611469_0_1.png','career'],
    [20039,'내가 쓰던 앱을, 이제 직접 만듭니다','김O은','20260616210546_1781611546_0_1.png','web'],
    [20038,'책 한 권으로 시작된 개발자의 길','박O정','20260616210716_1781611636_0_1.png','web career'],
    [20037,'제자리를 벗어나, 최신 기술로 도약합니다','차O현','20260616210750_1781611670_0_1.png','career'],
    [20036,'긴 공백을 지나, 다시 찾은 자신감','황O연','20260616210853_1781611733_0_1.png','career'],
    [20035,'막막하던 길 끝에서, 새로운 가능성을','조O현','20260616210944_1781611784_0_1.png','career'],
    [20034,'꿈꾸던 대기업, 현실이 되기까지','박O슬','20260616211012_1781611812_0_1.png','career'],
    [20031,'막연했던 진로 끝에, 또렷해진 길','황O국','20260616211252_1781611972_0_1.png','career'],
    [20030,'전공을 바꿔 도전한, 비전공자의 첫 합격','송O영','20260616211316_1781611996_0_1.png','career'],
    [20029,'넓게 배운 IT를 넘어, 웹 개발 하나에 집중','배O선','20260616211346_1781612026_0_1.png','web'],
    [20028,'취미였던 코딩이, 커리어가 되기까지','최O정','20260616211430_1781612070_0_1.png','web career'],
    [20027,'행정학에서 IT로, 다시 시작한 커리어','김O솔','20260616212525_1781612725_0_1.png','career'],
    [20026,'이론뿐이던 전공에, 실무를 채우다','우O길','20260616212558_1781612758_0_1.png','career'],
    [20018,'막연했던 취준에 분명한 방향을','김O영','20260616213035_1781613035_0_1.png','career'],
    [20017,'먼저 합격한 선배에게서, 취업의 길을 배웠습니다','정O철','20260616213057_1781613057_0_1.png','career'],
    [20016,'막막했던 자소서가, 합격의 무기로','신O연','20260616213134_1781613094_0_1.png','career'],
    [20015,'전공이 달라도 원하던 자리로','문O식','20260616213405_1781613245_0_1.png','career'],
    [20014,'차근차근 따라가다, 노력이 습관이 되기까지','박O희','20260616213431_1781613271_0_1.png','career'],
    [20013,'기초부터 탄탄하게, 한 걸음씩 쌓은 실력','신O민','20260617090845_1781654925_0_1.png','career'],
    [20012,'수많은 프로젝트가, 나의 실전 능력으로','안O진','20260617090906_1781654946_0_2.png','career'],
    [20011,'하고 싶은 게 없던 제가, 가고 싶은 곳을 찾기까지','양O민','20260617090929_1781654969_0_1.png','career']
  ];
  const profiles = {
    20136:['테이블에이아이','데이터','데이터 분석'],20135:['유티정보','AI 개발','AIoT·데이터 분석'],20126:['브레인크루','AI 개발','데이터 분석·로봇 AI'],20117:['취업기업 비공개','AI 개발','로봇 AI'],20116:['LG전자·롯데관광','개발','데이터·AI'],20106:['더존비즈온','AI 연구','AIoT·빅데이터'],20040:['SK케미칼','IT 개발','웹·앱 개발'],20039:['한화생명','앱 개발','웹·앱 개발'],20038:['하나은행','백엔드 개발','웹·앱 개발'],20037:['넥슨','개발','웹·앱 개발'],20036:['삼성화재','IT 개발','웹·앱 개발'],20035:['NC소프트','개발','웹·앱 개발'],20034:['LG디스플레이','IT 직무','웹·앱 개발'],20031:['KT','IT 직무','웹·앱 개발'],20030:['SK하이닉스','IT 직무','웹·앱 개발'],20029:['새마을금고','웹 개발','웹·앱 개발'],20028:['CJ','자동화','RPA·자동화'],20027:['국민은행','자동화','RPA·자동화'],20026:['SK C&C','자동화','RPA·자동화'],20018:['한화시스템','시스템 개발','정보시스템'],20017:['KT','연구개발','정보시스템'],20016:['LG전자','IT 직무','정보시스템'],20015:['현대백화점','온라인사업 기획','데이터 분석'],20014:['SK하이닉스','IT 직무','정보시스템'],20013:['삼성SDI','R&D','정보시스템'],20012:['우리은행','정보보안','정보시스템'],20011:['야놀자','기업 솔루션','AIoT·빅데이터']
  };
  const reviews = raw.map(([uid,title,name,image,field]) => { const [company,job,course] = profiles[uid]; return {uid,title,name,field,company,job,course,year:'2026',image:`/v2/assets/reviews/${uid}.png`,detail:`/v2/site/class/review.html?id=${uid}`}; });
  const featured = {...reviews[2], fullName:'김유신', company:'브레인크루(주)', role:'AI·AIgent 개발', course:'데이터 분석 · 로봇 AI', summary:'무역회사 오퍼 대신 IT를 선택하고 두 과정을 연달아 수강했습니다. 데이터 처리 감각을 로봇·영상 AI로 연결해 현재 AI·AIgent 개발자로 일합니다.', skills:['Hadoop','Tableau','TensorFlow','라이다','뎁스카메라','자율주행·협동로봇']};
  const escapeHtml = value => String(value).replace(/[&<>'"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
  const card = item => `<article class="review-card"><a href="${item.detail}" aria-label="${escapeHtml(item.title)} 상세 보기"><img src="${item.image}" alt="${escapeHtml(item.name)} 수료생 인터뷰 대표 이미지" loading="lazy"><span><small>${escapeHtml(item.company)} · ${escapeHtml(item.job)}</small><strong>${escapeHtml(item.title)}</strong><em>${escapeHtml(item.name)} 수료생 · ${escapeHtml(item.course)}</em></span></a></article>`;
  document.querySelector('[data-featured-review]').innerHTML = `<article class="featured-review"><img src="${featured.image}" alt="김유신 수료생 인터뷰 대표 이미지"><div><span>FEATURED · DATA + ROBOT AI</span><h2>${featured.title}</h2><p>${featured.summary}</p><dl><div><dt>연계 과정</dt><dd>${featured.course}</dd></div><div><dt>현재</dt><dd>${featured.company} · ${featured.role}</dd></div></dl><div class="featured-review__skills">${featured.skills.map(skill => `<b>${skill}</b>`).join('')}</div><a class="btn btn--primary" href="${featured.detail}">인터뷰 상세 보기</a></div></article>`;
  const grid = document.querySelector('[data-review-grid]');
  const form = document.querySelector('[data-review-filter]');
  const count = document.querySelector('[data-review-count]');
  const empty = document.querySelector('[data-review-empty]');
  const populate = (selector,key) => { const select = document.querySelector(selector); [...new Set(reviews.map(item => item[key]))].sort((a,b) => a.localeCompare(b,'ko')).forEach(value => select.add(new Option(value,value))); };
  populate('[data-review-company]','company'); populate('[data-review-job]','job'); populate('[data-review-course]','course');
  function render() { const data = new FormData(form); const query = String(data.get('query') || '').trim().toLowerCase(); const company = data.get('company'); const job = data.get('job'); const course = data.get('course'); const year = data.get('year'); const matches = reviews.filter(item => (!query || `${item.title} ${item.name} ${item.company} ${item.job} ${item.course}`.toLowerCase().includes(query)) && (company === 'all' || item.company === company) && (job === 'all' || item.job === job) && (course === 'all' || item.course === course) && (year === 'all' || item.year === year)); grid.innerHTML = matches.map(card).join(''); count.textContent = matches.length; empty.hidden = matches.length !== 0; }
  form.addEventListener('input', render); form.addEventListener('reset', () => setTimeout(render)); render();
})();
