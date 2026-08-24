(() => {
  const content = {
    stats: [
      { value:'1982', label:'세종교육에서 시작한 직업교육' },
      { value:'3년 인증', label:'직업훈련 우수훈련기관 선정' },
      { value:'KDT', label:'K-디지털 트레이닝 훈련기관' },
      { value:'2022 대상', label:'직업능력개발훈련 부문' }
    ],
    awards: [
      { year:'1996', label:'성남시 감사패 수령', image:'https://www.samsungaxi.com/images/aw3.png' },
      { year:'2018', label:'생산성본부 감사패 수령', image:'https://www.samsungaxi.com/images/aw4.png' },
      { year:'2018', label:'성남시 감사패 수령', image:'https://www.samsungaxi.com/images/aw5.png' },
      { year:'2019–2021', label:'이수자평가 A등급 획득', image:'https://www.samsungaxi.com/images/aw7.png' },
      { year:'2022', label:'대한민국 교육대상 수상', image:'https://www.samsungaxi.com/images/aw2.png' },
      { year:'2022', label:'산업인력공단 감사패 수령', image:'https://www.samsungaxi.com/images/aw6.png' },
      { year:'2023', label:'K-Digital 훈련기관 선정', image:'https://www.samsungaxi.com/images/aw8.png' },
      { year:'2025', label:'Samsung SDS Brity AI RPA', image:'https://www.samsungaxi.com/images/aw9.png' }
    ],
    credentials: [
      {
        meta:'1996 · 2018 / 지자체 감사',
        title:'성남시 감사패 수령',
        description:'지역 직업교육과 인재 양성에 기여한 공로로 성남시 감사패를 수령한 공개 기록입니다.'
      },
      {
        meta:'과정별 공개 성과',
        title:'취업률 100% 달성 과정 다수',
        description:'기존 홈페이지에 공개된 개별 과정·기수의 성과입니다. 전체 과정 평균이나 향후 취업 보장을 의미하지 않습니다.'
      },
      {
        meta:'2019–2021 / 훈련 품질 평가',
        title:'이수자평가 A등급 획득',
        description:'해당 연도 이수자평가에서 A등급을 획득한 기관 공개 기록입니다.'
      },
      {
        meta:'2025 / 교육 도구 사용',
        title:'Samsung SDS Brity Automation 교육용 라이선스 사용',
        description:'Samsung SDS Brity Automation 라이선스를 실습 교육에 사용하는 관계이며, 공동운영·채용보장 또는 공식 파트너십을 뜻하지 않습니다.'
      }
    ],
    history: [
      { year:'1982', tag:'BEGINNING', title:'(주)세종교육 설립', description:'성남에서 직업교육의 첫걸음을 시작했습니다.' },
      { year:'1992', tag:'LOCAL TRUST', title:'성남시 최우수 IT교육기관상', description:'지역의 정보화 교육을 이끄는 교육기관으로 인정받았습니다.' },
      { year:'2009', tag:'NEW TRAINING', title:'국내 최초 전산교사양성 과정 개발', description:'고용노동부 지정 실업자 직업훈련으로 새로운 직무교육을 열었습니다.' },
      { year:'2013', tag:'FOUNDATION', title:'교육법인 등록과 국가고시 시험장 선정', description:'(주)세종교육 교육법인 등록, 세종경영관광학원 설립과 ITQ 국가고시 시험장 선정을 이어갔습니다.' },
      { year:'2017', tag:'QUALITY', title:'직업훈련 우수훈련기관 선정', description:'3년 인증을 획득하고 일반고 특화·정보시스템 과정의 운영 기반을 넓혔습니다.' },
      { year:'2022', tag:'RECOGNITION', title:'직업능력개발훈련 부문 대상', description:'대한민국 No.1 교육대상에서 직업능력개발훈련 부문 대상을 수상했습니다.' },
      { year:'2023', tag:'K-DIGITAL', title:'K-디지털 트레이닝 훈련기관 선정', description:'AI·데이터 실무와 기업 프로젝트 중심의 KDT 과정 운영을 시작했습니다.' },
      { year:'2025', tag:'LICENSE USE', title:'Brity Automation 교육용 라이선스 사용', description:'Samsung SDS의 자동화 솔루션을 실습 교육 도구로 사용하며, 공동운영이나 채용보장 관계를 의미하지 않습니다.' },
      { year:'2026', tag:'NOW', title:`${window.BRAND?.name || '내일의AI'}로 새로운 출발`, description:'세종교육의 직업교육 경험을 이어받아 AI 전환 시대의 교육과 취업 연결을 확장합니다.', current:true }
    ],
    network: [
      { number:'01', title:'취업캠퍼스', role:'국비·취업연계 교육과 진단, 포트폴리오, 기업 매칭을 연결합니다.', action:'모집 과정 보기', href:'#campus-courses' },
      { number:'02', title:'배민캠퍼스', role:'프로젝트 발표와 기업 초청, 팀 교류를 위한 판교 학습 공간을 안내합니다.', action:'공간 확인하기', href:'/v2/site/campus/facility.html#commons' },
      { number:'03', title:'몰입클라쓰', role:'전체 교육과정을 조건별로 비교하고 상세 확인과 신청까지 이어갑니다.', action:'과정 찾기', href:'/v2/site/class/index.html' },
      { number:'04', title:'비즈워크래프트', role:'기업·공공조직의 AX 과제에 맞춘 진단과 직무교육을 제공합니다.', action:'기업교육 보기', href:'/v2/site/biz/index.html' },
      { number:'05', title:'동문회', role:'수료생 멘토링과 취업 후 성장, 후배와의 경험 연결을 담당합니다.', action:'사후관리 보기', href:'/v2/site/campus/support.html#support-followup' },
      { number:'06', title:'숙식센터', role:'원거리 교육생의 기숙사, 식사, 통학 조건과 입실 상담을 안내합니다.', action:'숙식시설 보기', href:'/v2/site/campus/facility.html#dorm' }
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

  const awardsRoot = document.querySelector('[data-campus-awards]');
  if (awardsRoot) {
    awardsRoot.replaceChildren(...content.awards.map(item => {
      const figure = document.createElement('figure');
      const image = document.createElement('img');
      image.src = item.image;
      image.alt = `${item.year} ${item.label}`;
      image.loading = 'lazy';
      image.decoding = 'async';
      image.referrerPolicy = 'no-referrer';
      const caption = document.createElement('figcaption');
      const year = document.createElement('strong');
      year.textContent = item.year;
      const label = document.createElement('span');
      label.textContent = item.label;
      caption.append(year, label);
      figure.append(image, caption);
      return figure;
    }));
  }

  const credentialsRoot = document.querySelector('[data-campus-credentials]');
  if (credentialsRoot) {
    credentialsRoot.replaceChildren(...content.credentials.map(item => {
      const article = document.createElement('article');
      const meta = document.createElement('small');
      meta.textContent = item.meta;
      const title = document.createElement('h3');
      title.textContent = item.title;
      const description = document.createElement('p');
      description.textContent = item.description;
      article.append(meta, title, description);
      return article;
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

  const networkRoot = document.querySelector('[data-campus-network]');
  if (networkRoot) {
    networkRoot.replaceChildren(...content.network.map(item => {
      const article = document.createElement('article');
      const number = document.createElement('span');
      number.textContent = item.number;
      const copy = document.createElement('div');
      const title = document.createElement('h3');
      title.textContent = item.title;
      const role = document.createElement('p');
      role.textContent = item.role;
      copy.append(title, role);
      const link = document.createElement('a');
      link.href = item.href;
      link.textContent = `${item.action} →`;
      link.setAttribute('aria-label', `${item.title} ${item.action}`);
      article.append(number, copy, link);
      return article;
    }));
  }

  window.CAMPUS_CONTENT = content;
})();
