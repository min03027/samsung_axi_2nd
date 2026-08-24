(() => {
  const content = {
    stats: [
      { value:'1982', label:'세종교육에서 시작한 직업교육' },
      { value:'3년 인증', label:'직업훈련 우수훈련기관 선정' },
      { value:'KDT', label:'K-디지털 트레이닝 훈련기관' },
      { value:'2022 대상', label:'직업능력개발훈련 부문' }
    ],
    history: [
      { year:'1982', tag:'BEGINNING', title:'(주)세종교육 설립', description:'성남에서 직업교육의 첫걸음을 시작했습니다.' },
      { year:'1992', tag:'LOCAL TRUST', title:'성남시 최우수 IT교육기관상', description:'지역의 정보화 교육을 이끄는 교육기관으로 인정받았습니다.' },
      { year:'2009', tag:'NEW TRAINING', title:'국내 최초 전산교사양성 과정 개발', description:'고용노동부 지정 실업자 직업훈련으로 새로운 직무교육을 열었습니다.' },
      { year:'2013', tag:'FOUNDATION', title:'교육법인 등록과 국가고시 시험장 선정', description:'(주)세종교육 교육법인 등록, 세종경영관광학원 설립과 ITQ 국가고시 시험장 선정을 이어갔습니다.' },
      { year:'2017', tag:'QUALITY', title:'직업훈련 우수훈련기관 선정', description:'3년 인증을 획득하고 일반고 특화·정보시스템 과정의 운영 기반을 넓혔습니다.' },
      { year:'2022', tag:'RECOGNITION', title:'직업능력개발훈련 부문 대상', description:'대한민국 No.1 교육대상에서 직업능력개발훈련 부문 대상을 수상했습니다.' },
      { year:'2023', tag:'K-DIGITAL', title:'K-디지털 트레이닝 훈련기관 선정', description:'AI·데이터 실무와 기업 프로젝트 중심의 KDT 과정 운영을 시작했습니다.' },
      { year:'2025', tag:'PARTNERSHIP', title:'삼성SDS Brity 협력', description:'산업 자동화와 AI 실무교육의 접점을 넓히며 새로운 협력 기반을 만들었습니다.' },
      { year:'2026', tag:'NOW', title:`${window.BRAND?.name || '내일의AI'}로 새로운 출발`, description:'세종교육의 직업교육 경험을 이어받아 AI 전환 시대의 교육과 취업 연결을 확장합니다.', current:true }
    ]
  };

  const statsRoot = document.querySelector('[data-campus-stats]');
  if (statsRoot) {
    statsRoot.replaceChildren(...content.stats.map(item => {
      const stat = document.createElement('div');
      stat.className = 'hero__stat';
      const value = document.createElement('b');
      value.className = 'num';
      value.textContent = item.value;
      const label = document.createElement('span');
      label.textContent = item.label;
      stat.append(value, label);
      return stat;
    }));
  }

  const historyRoot = document.querySelector('[data-campus-history]');
  if (historyRoot) {
    const line = historyRoot.querySelector('.employment-history__line');
    const entries = content.history.map((item, index) => {
      const article = document.createElement('article');
      article.className = `employment-history__item ${index % 2 ? 'is-right' : 'is-left'}`;
      if (item.current) article.classList.add('employment-history__item--now');
      const time = document.createElement('time');
      time.dateTime = item.year;
      time.textContent = item.year;
      const marker = document.createElement('i');
      marker.setAttribute('aria-hidden', 'true');
      const copy = document.createElement('div');
      const tag = document.createElement('small');
      tag.textContent = item.tag;
      const title = document.createElement('h3');
      title.textContent = item.title;
      const description = document.createElement('p');
      description.textContent = item.description;
      copy.append(tag, title, description);
      article.append(time, marker, copy);
      return article;
    });
    historyRoot.replaceChildren(line, ...entries);
  }

  window.CAMPUS_CONTENT = content;
})();
