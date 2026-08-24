(() => {
  const source = 'https://samsungaxi.com/p/?j=87&ej_code=woosoo&st=100&sv=&pno=9&sort=0';
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
  const reviews = raw.map(([uid,title,name,image,field]) => ({uid,title,name,field,year:'2026',image:`https://samsungaxi.com/data/board/woosoo/${image}`,source:`${source}&page=${uid >= 20106 ? 1 : uid >= 20027 ? 2 : 3}&act=view&bbs_uid=${uid}`}));
  const featured = {...reviews[2], fullName:'김유신', company:'브레인크루(주)', role:'AI·AIgent 개발', course:'데이터 분석 · 로봇 AI', summary:'무역회사 오퍼 대신 IT를 선택하고 두 과정을 연달아 수강했습니다. 데이터 처리 감각을 로봇·영상 AI로 연결해 현재 AI·AIgent 개발자로 일합니다.', skills:['Hadoop','Tableau','TensorFlow','라이다','뎁스카메라','자율주행·협동로봇']};
  const escapeHtml = value => String(value).replace(/[&<>'"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
  const card = item => `<article class="review-card"><button type="button" data-review-id="${item.uid}" aria-label="${escapeHtml(item.title)} 상세 보기"><img src="${item.image}" alt="${escapeHtml(item.name)} 수료생 인터뷰 대표 이미지" loading="lazy"><span><small>2026 · CAREER TRANSITION</small><strong>${escapeHtml(item.title)}</strong><em>${escapeHtml(item.name)} 수료생</em></span></button></article>`;
  document.querySelector('[data-featured-review]').innerHTML = `<article class="featured-review"><img src="${featured.image}" alt="김유신 수료생 인터뷰 대표 이미지"><div><span>FEATURED · DATA + ROBOT AI</span><h2>${featured.title}</h2><p>${featured.summary}</p><dl><div><dt>연계 과정</dt><dd>${featured.course}</dd></div><div><dt>현재</dt><dd>${featured.company} · ${featured.role}</dd></div></dl><div class="featured-review__skills">${featured.skills.map(skill => `<b>${skill}</b>`).join('')}</div><button class="btn btn--primary" type="button" data-review-id="${featured.uid}">인터뷰 상세 보기</button></div></article>`;
  const grid = document.querySelector('[data-review-grid]');
  const form = document.querySelector('[data-review-filter]');
  const count = document.querySelector('[data-review-count]');
  const empty = document.querySelector('[data-review-empty]');
  function render() { const data = new FormData(form); const query = String(data.get('query') || '').trim().toLowerCase(); const year = data.get('year'); const field = data.get('field'); const matches = reviews.filter(item => (!query || `${item.title} ${item.name}`.toLowerCase().includes(query)) && (year === 'all' || item.year === year) && (field === 'all' || item.field.split(' ').includes(field))); grid.innerHTML = matches.map(card).join(''); count.textContent = matches.length; empty.hidden = matches.length !== 0; }
  form.addEventListener('input', render); form.addEventListener('reset', () => setTimeout(render)); render();
  const dialog = document.querySelector('[data-review-dialog]');
  const detail = document.querySelector('[data-review-detail]');
  function openDetail(uid) { const item = reviews.find(review => review.uid === Number(uid)); if (!item) return; const isFeatured = item.uid === featured.uid; detail.innerHTML = `<img src="${item.image}" alt="${escapeHtml(item.name)} 수료생 인터뷰"><div class="review-dialog__copy"><span>2026 · CAREER TRANSITION INTERVIEW</span><h2>${escapeHtml(item.title)}</h2><p><b>${escapeHtml(isFeatured ? featured.fullName : item.name)}</b> 수료생의 공개 취업 인터뷰입니다.</p>${isFeatured ? `<dl><div><dt>과정</dt><dd>${featured.course}</dd></div><div><dt>현재 직장</dt><dd>${featured.company}</dd></div><div><dt>현재 직무</dt><dd>${featured.role}</dd></div></dl><p>${featured.summary}</p>` : '<p>과정 경험과 취업 전환의 전체 내용은 삼성AXI 원문 인터뷰에서 확인할 수 있습니다.</p>'}<div><a class="btn btn--primary" href="${item.source}" target="_blank" rel="noopener">원문 인터뷰 전체 보기 ↗</a><a class="btn btn--outline" href="/v2/site/class/index.html">관련 과정 보기</a></div></div>`; dialog.showModal(); }
  document.addEventListener('click', event => { const trigger = event.target.closest('[data-review-id]'); if (trigger) openDetail(trigger.dataset.reviewId); });
  document.querySelector('[data-review-close]').addEventListener('click', () => dialog.close()); dialog.addEventListener('click', event => { if (event.target === dialog) dialog.close(); });
})();
