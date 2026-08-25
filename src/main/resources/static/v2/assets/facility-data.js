(() => {
  const imageBase = 'https://samsungaxi.com/data/facility/';
  const images = {
    classroom1:imageBase + 'fac_x_1776317699.jpg',
    classroom2:imageBase + 'fac_x_1776318970.jpg',
    classroom3:imageBase + 'fac_0_1773360614.jpg',
    classroom4:imageBase + 'fac_0_1773361898.jpg',
    classroom5:imageBase + 'fac_0_1773361923.jpg',
    lab:imageBase + 'fac_x_1700739994.jpg',
    main:imageBase + 'fac_x_1745611709.jpg'
  };

  const dormAvailable = '국비 과정 수강 신청 후 별도 기숙사 신청이 가능합니다. 타지역 교육생을 우선 배치하며 입실 가능 여부는 상담에서 확인합니다.';
  const dormConsult = '과정과 기수에 따라 기숙사 운영 범위가 달라질 수 있습니다. 신청 전에 입실 가능 여부와 식사 제공 조건을 확인해 주세요.';
  const dormSchedule = '수시 운영 과정은 일정과 수업 방식에 따라 기숙사 제공 여부가 달라집니다. 상담에서 수업 일정과 함께 확인해 주세요.';

  const profiles = {
    data:{name:'데이터 예측 자동화 과정',room:'전산 실습실',roomDesc:'1인 1PC 환경에서 데이터 수집·분석·시각화·모델 구현을 같은 자리에서 이어갑니다.',equipment:['1인 1PC','Hadoop','Tableau','TensorFlow'],dorm:'숙식 상담 가능',dormDesc:dormAvailable,image:images.classroom1},
    factory:{name:'스마트 환경공정 제어 과정',room:'스마트팩토리 실습 환경',roomDesc:'설비 데이터와 생산정보를 연결하는 팀 실습을 중심으로 운영하며 배정 실습실은 개강 안내에서 확정합니다.',equipment:['1인 1PC','PLC','MES','ERP'],dorm:'숙식 상담 가능',dormDesc:dormAvailable,image:images.lab},
    aiot:{name:'AIoT 산업솔루션 과정',room:'AIoT 전산 실습실',roomDesc:'센서 데이터 수집부터 분석 모델과 운영 화면까지 한 공간에서 연결해 실습합니다.',equipment:['1인 1PC','IoT 센서','Python','분석 환경'],dorm:'숙식 상담 가능',dormDesc:dormAvailable,image:images.classroom2},
    robot:{name:'자율주행·협동로봇 과정',room:'로봇 AI 실습 환경',roomDesc:'영상·거리 센서와 제어 소프트웨어를 연결해 자율주행과 협동로봇 시연을 준비합니다.',equipment:['1인 1PC','LiDAR','Depth Camera','Robot AI'],dorm:'숙식 상담 가능',dormDesc:dormAvailable,image:images.lab},
    cloud:{name:'클라우드 웹&앱 개발 과정',room:'클라우드 개발 실습실',roomDesc:'개발·테스트·배포를 반복할 수 있는 전산 환경에서 팀 프로젝트를 진행합니다.',equipment:['1인 1PC','Java','Docker','보안 실습 환경'],dorm:'숙식 상담 가능',dormDesc:dormAvailable,image:images.classroom3},
    video:{name:'AI 영상편집 과정',room:'디지털 콘텐츠 실습실',roomDesc:'영상 편집과 AI 콘텐츠 제작을 위한 전산 실습 환경에서 개인 포트폴리오를 완성합니다.',equipment:['1인 1PC','영상 편집 도구','AI 제작 도구','포트폴리오 환경'],dorm:'기숙사 확인 필요',dormDesc:dormConsult,image:images.classroom4},
    uiux:{name:'AI UI/UX 과정',room:'디자인 전산 실습실',roomDesc:'사용자 리서치부터 프로토타입 제작까지 디자인 실습을 한 공간에서 이어갑니다.',equipment:['1인 1PC','Figma','생성형 AI','프로토타이핑 도구'],dorm:'기숙사 확인 필요',dormDesc:dormConsult,image:images.classroom4},
    japan:{name:'일본 Java 취업 과정',room:'엔터프라이즈 개발 실습실',roomDesc:'Java 기업형 프로젝트와 해외취업 준비를 함께 진행하는 전산 실습 환경입니다.',equipment:['1인 1PC','Java','기업형 개발 환경','면접 준비 공간'],dorm:'숙식 상담 가능',dormDesc:dormAvailable,image:images.classroom3},
    usa:{name:'미국 국제마케터 과정',room:'데이터·마케팅 실습실',roomDesc:'시장 데이터 분석과 글로벌 캠페인 기획을 위한 개인·팀 작업 환경을 제공합니다.',equipment:['1인 1PC','데이터 분석 도구','대시보드 도구','발표 환경'],dorm:'숙식 상담 가능',dormDesc:dormAvailable,image:images.classroom1},
    china:{name:'중국 글로벌 PM 과정',room:'AI 제품기획 실습실',roomDesc:'제품 요구사항과 로드맵을 팀 단위로 설계하고 발표할 수 있는 전산 실습 환경입니다.',equipment:['1인 1PC','AI 기획 도구','협업 도구','발표 환경'],dorm:'숙식 상담 가능',dormDesc:dormAvailable,image:images.classroom5},
    cooking:{name:'한식·양식 조리 과정',room:'과정 전용 조리 실습공간',roomDesc:'조리 실습실의 배정 위치와 사용 장비는 일반고 위탁 사전상담 및 개강 안내에서 확정합니다.',equipment:['한식 실기 장비','양식 실기 장비','위생 관리 도구','조리 작업대'],dorm:'기숙사 확인 필요',dormDesc:dormConsult,image:images.main},
    game:{name:'게임콘텐츠 제작 과정',room:'게임 제작 전산 실습실',roomDesc:'기획·그래픽·프로그래밍을 연결해 플레이 가능한 결과물을 만드는 전산 실습 환경입니다.',equipment:['1인 1PC','게임 제작 도구','그래픽 도구','프로그램 실습 환경'],dorm:'기숙사 확인 필요',dormDesc:dormConsult,image:images.classroom5},
    design:{name:'디지털디자인 과정',room:'디지털디자인 실습실',roomDesc:'그래픽과 영상광고 콘텐츠 제작 및 자격 실기를 준비하는 전산 실습 환경입니다.',equipment:['1인 1PC','그래픽 도구','영상 제작 도구','자격 실기 환경'],dorm:'기숙사 확인 필요',dormDesc:dormConsult,image:images.classroom4},
    mobility:{name:'스마트모빌리티 SW 과정',room:'AI 로봇·SW 실습 환경',roomDesc:'자율주행 스마트모빌리티 응용 소프트웨어와 센서 제어를 함께 실습합니다.',equipment:['1인 1PC','AI 로봇 도구','자율주행 센서','응용SW 환경'],dorm:'기숙사 확인 필요',dormDesc:dormConsult,image:images.lab},
    system:{name:'정보시스템 구축 과정',room:'정보시스템 전산 실습실',roomDesc:'시스템 구축·운영과 프로그램기능사 실기를 함께 준비하는 개인 실습 환경입니다.',equipment:['1인 1PC','서버 실습 환경','운영 도구','프로그램 실기 환경'],dorm:'기숙사 확인 필요',dormDesc:dormConsult,image:images.classroom3},
    adsp:{name:'ADsP·ADP 과정',room:'자격 대비 학습공간',roomDesc:'이론·문제풀이·분석 실습을 진행하며 세부 교육 장소는 개강 일정과 함께 안내합니다.',equipment:['학습용 PC','데이터 분석 도구','기출문제 자료','모의평가 환경'],dorm:'일정별 확인',dormDesc:dormSchedule,image:images.classroom1},
    sqld:{name:'SQLD·SQLP 과정',room:'SQL 자격 대비 학습공간',roomDesc:'데이터 모델링과 SQL 문제풀이·실습을 진행하며 장소는 개강 일정과 함께 안내합니다.',equipment:['학습용 PC','SQL 실습 환경','기출문제 자료','모의평가 환경'],dorm:'일정별 확인',dormDesc:dormSchedule,image:images.classroom2},
    'bigdata-cert':{name:'빅데이터 분석기사 과정',room:'빅데이터 자격 실습공간',roomDesc:'필기 이론과 분석 실기를 연결해 준비하며 장소는 개강 일정과 함께 안내합니다.',equipment:['학습용 PC','분석 실기 환경','기출문제 자료','모의평가 환경'],dorm:'일정별 확인',dormDesc:dormSchedule,image:images.classroom1},
    engineer:{name:'정보처리 자격 과정',room:'정보처리 자격 학습공간',roomDesc:'필기·실기 문제풀이와 프로그래밍 실습을 진행하며 장소는 개강 일정과 함께 안내합니다.',equipment:['학습용 PC','프로그래밍 환경','기출문제 자료','모의평가 환경'],dorm:'일정별 확인',dormDesc:dormSchedule,image:images.classroom3}
  };

  window.AxiFacilityProfiles = profiles;

  const params = new URLSearchParams(location.search);
  const requestedKey = params.get('course');
  const fallbackKey = 'data';
  const initialKey = profiles[requestedKey] ? requestedKey : fallbackKey;

  const setText = (selector,value) => document.querySelectorAll(selector).forEach(node => { node.textContent = value; });
  const setImages = profile => document.querySelectorAll('[data-facility-profile-image]').forEach(image => {
    image.src = profile.image;
    image.alt = profile.room + ' 실제 시설 사진';
  });
  const setChips = profile => document.querySelectorAll('[data-facility-equipment]').forEach(root => {
    root.replaceChildren(...profile.equipment.map(label => {
      const chip = document.createElement('span');
      chip.textContent = label;
      return chip;
    }));
  });

  const render = key => {
    const profile = profiles[key] || profiles[fallbackKey];
    setText('[data-facility-course-name]',profile.name);
    setText('[data-facility-room]',profile.room);
    setText('[data-facility-room-desc]',profile.roomDesc);
    setText('[data-facility-dorm]',profile.dorm);
    setText('[data-facility-dorm-desc]',profile.dormDesc);
    setImages(profile);
    setChips(profile);
    document.querySelectorAll('[data-facility-link]').forEach(link => {
      link.href = '/v2/site/campus/facility.html?course=' + encodeURIComponent(key) + '#course-facility-guide';
    });
    document.querySelectorAll('[data-facility-counsel]').forEach(link => {
      link.href = '/v2/site/campus/counsel.html?course=' + encodeURIComponent(key);
    });
    const select = document.querySelector('[data-facility-course-select]');
    if (select) select.value = key;
  };

  const select = document.querySelector('[data-facility-course-select]');
  if (select) {
    select.replaceChildren(...Object.entries(profiles).map(([key,profile]) => {
      const option = document.createElement('option');
      option.value = key;
      option.textContent = profile.name;
      return option;
    }));
    select.addEventListener('change',event => {
      const key = event.target.value;
      const next = new URL(location.href);
      next.searchParams.set('course',key);
      history.replaceState({},'',next);
      render(key);
    });
  }

  render(initialKey);
})();
