(() => {
  const common = { status:'현재 모집 중', days:'월–금', method:'오프라인 실습 교육' };
  const courses = {
    data:{cat:'KDT · DATA AI',title:'심층 데이터 분석을 통한 개인 맞춤형 예측 자동화 서비스 솔루션 개발',hero:'데이터로 고객의<br><em>다음 행동을 예측하세요</em>',desc:'Hadoop으로 데이터를 연결하고 Tableau로 설득하며 TensorFlow로 개인 맞춤형 예측 서비스를 완성합니다.',period:'2026.08.18 — 2026.10.22',time:'09:00–17:40 · 하루 8시간',tuition:'5,097,490원',self:'300,000원',support:'4,797,490원',capacity:'20명',tech:'Hadoop · Tableau · TensorFlow'},
    factory:{cat:'KDT · SMART FACTORY',title:'[KDT] ESG AIoT를 활용한 스마트 환경공정제어 자동화시스템 구축 과정(MES, ERP, PLC)',hero:'산업 데이터를 연결해<br><em>공정을 스스로 움직이게</em>',desc:'MES, ERP, PLC를 연결하고 ESG 관점의 스마트 환경공정 제어 시스템을 구축합니다.',period:'2026.08.24 — 2027.03.22',time:'09:10–17:40 · 하루 8시간',tuition:'10,591,620원',self:'400,000원',support:'10,191,620원',instructor:'황영일',capacity:'20명',tech:'MES · ERP · PLC'},
    aiot:{cat:'KDT · AIoT BIG DATA',title:'[KDT] 인공지능 AIoT를 이용한 빅데이터 분석 산업솔루션 개발 취업연계 부트캠프',hero:'AI와 IoT를 연결해<br><em>산업 문제를 해결하세요</em>',desc:'현장 데이터를 수집하고 인공지능으로 분석해 실제 산업에 적용할 솔루션을 개발합니다.',period:'2026.08.24 — 2027.03.18',time:'09:00–17:40 · 하루 8시간',tuition:'10,329,930원',self:'400,000원',support:'9,929,930원',capacity:'20명',tech:'AIoT · Big Data · 산업솔루션'},
    robot:{cat:'KDT · ROBOT AI',title:'[KDT] 로봇 AI 영상기법을 활용한 실용적인 산업화 자율주행, 서비스 협동로봇 개발자 과정(라이다, 뎁스카메라)',hero:'화면 속 AI를 넘어<br><em>현실에서 움직이는 로봇으로</em>',desc:'라이다와 뎁스카메라, 로봇 AI 영상기법을 활용해 자율주행 및 협동로봇을 구현합니다.',period:'2026.08.24 — 2027.03.05',time:'09:10–17:40 · 하루 8시간',tuition:'9,732,840원',self:'400,000원',support:'9,332,840원',capacity:'25명',tech:'LiDAR · Depth Camera · Robot AI'},
    cloud:{cat:'KDT · CLOUD FULL STACK',title:'[KDT] 풀스택 클라우드 기반의 웹&앱 개발자 취업연계 부트캠프(자바, 도커, 보안)',hero:'아이디어를 코드로,<br><em>서비스를 클라우드로</em>',desc:'Java 기반 웹·앱 개발부터 Docker 배포와 보안까지 서비스 개발 전 과정을 경험합니다.',period:'2026.08.28 — 2027.03.24',time:'하루 8시간 · 주 5일',tuition:'10,329,930원',self:'400,000원',support:'9,929,930원',capacity:'20명',tech:'Java · Docker · Security'},
    video:{cat:'AI · VIDEO',title:'초급부터 고급 실무까지 AI 기반 영상편집 실무',hero:'AI와 영상 편집으로<br><em>콘텐츠를 완성하세요</em>',desc:'영상 편집의 기초부터 AI 활용 고급 실무까지 결과물 중심으로 학습합니다.',period:'2026.08.24 — 2026.10.15',time:'09:00–15:40 · 주 5일',tuition:'1,588,800원',self:'556,080원',support:'1,032,720원',capacity:'25명',tech:'AI 편집 · 영상 콘텐츠 · 포트폴리오'},
    uiux:{cat:'AI · UI/UX',title:'AI 활용 UI/UX 실무 디자이너 트레이닝',hero:'AI를 디자인 도구로<br><em>사용자 경험을 설계하세요</em>',desc:'생성형 AI를 활용해 리서치부터 UI 설계와 실무 포트폴리오까지 완성합니다.',period:'2026.08.24 — 2026.12.21',time:'09:00–17:40 · 주 5일',tuition:'5,507,840원',self:'300,000원',support:'5,207,840원',capacity:'상담 시 안내',tech:'UI/UX · 생성형 AI · 디자인 실무'},
    japan:{cat:'해외취업 · 일본',title:'자바 엔터프라이즈 개발자 양성과정',hero:'자바 개발 역량으로<br><em>일본 IT 취업에 도전하세요</em>',desc:'엔터프라이즈 자바 개발 역량과 해외취업 준비를 함께 진행합니다.',period:'2026.08.28 — 2027.02.26',time:'09:10–17:40 · 하루 8시간',tuition:'상담 시 안내',self:'상담 시 안내',support:'상담 시 안내',capacity:'30명',tech:'Java · Enterprise · 일본취업'},
    usa:{cat:'해외취업 · USA',title:'데이터분석기반 국제마케터 양성과정',hero:'데이터를 읽는 마케터로<br><em>미국 취업에 도전하세요</em>',desc:'데이터 분석과 글로벌 마케팅 실무를 연결해 국제 마케터 역량을 준비합니다.',period:'2026.08.28 — 2027.03.17',time:'09:10–17:40 · 하루 8시간',tuition:'상담 시 안내',self:'상담 시 안내',support:'상담 시 안내',capacity:'30명',tech:'Data Analytics · Global Marketing'},
    china:{cat:'해외취업 · CHINA',title:'AI 기반 글로벌 프로덕트 매니저(PM) 양성과정',hero:'AI 제품을 기획하는<br><em>글로벌 PM으로 시작하세요</em>',desc:'AI 제품 기획과 프로젝트 관리 역량을 기반으로 중국 취업을 준비합니다.',period:'2026.08.28 — 2027.02.26',time:'09:10–17:40 · 하루 8시간',tuition:'상담 시 안내',self:'상담 시 안내',support:'상담 시 안내',capacity:'30명',tech:'AI Product · PM · 중국취업'},
    cooking:{cat:'일반고 위탁 · 외식분야',title:'[일반고] 한식양식조리기능사(조리실무)',hero:'기능사 자격과 함께<br><em>주방의 기본기를 만드세요</em>',desc:'한식과 양식 조리기능사 취득 및 현장 조리 실무를 함께 준비합니다.',period:'2027.03.01 — 2028.01.07',time:'09:10–16:40 · 주 5일',tuition:'전액무료',self:'0원',support:'전액 지원',capacity:'24명',tech:'한식 · 양식 · 조리기능사'},
    game:{cat:'일반고 위탁 · 게임콘텐츠제작',title:'[일반고] 게임콘텐츠제작_프로그램기능사 취득',hero:'아이디어를 플레이 가능한<br><em>게임 콘텐츠로 만드세요</em>',desc:'게임 콘텐츠 제작 실무와 프로그램기능사 취득을 함께 준비합니다.',period:'2027.03.01 — 2028.01.07',time:'09:00–16:40 · 주 5일',tuition:'전액무료',self:'0원',support:'전액 지원',capacity:'25명',tech:'게임 콘텐츠 · 프로그래밍 · 프로그램기능사'},
    design:{cat:'일반고 위탁 · 디지털디자인',title:'[일반고] 디지털디자인&영상광고콘텐츠제작_컴퓨터그래픽기능사 취득',hero:'디자인과 영상으로<br><em>브랜드를 움직이세요</em>',desc:'디지털디자인과 영상광고 콘텐츠 제작, 컴퓨터그래픽기능사를 함께 준비합니다.',period:'2027.03.01 — 2028.01.07',time:'09:00–16:40 · 하루 7시간',tuition:'전액무료',self:'0원',support:'전액 지원',capacity:'25명',tech:'디지털디자인 · 영상광고 · 그래픽기능사'},
    mobility:{cat:'일반고 위탁 · IT/디자인',title:'[일반고] AI 로봇 자율주행 스마트모빌리티 응용SW개발자 양성과정',hero:'AI 로봇을 움직이는<br><em>소프트웨어를 만드세요</em>',desc:'자율주행 스마트모빌리티에 필요한 AI 로봇 응용 소프트웨어를 개발합니다.',period:'2027.03.01 — 2028.01.07',time:'09:00–16:40 · 주 5일',tuition:'전액무료',self:'0원',support:'전액 지원',capacity:'25명',tech:'AI 로봇 · 자율주행 · 응용SW'},
    system:{cat:'일반고 위탁 · 정보시스템구축',title:'[일반고] 정보시스템 구축·운영 실무자 과정 & 프로그램기능사',hero:'서비스를 안정적으로<br><em>구축하고 운영하세요</em>',desc:'정보시스템 구축과 운영 실무, 프로그램기능사 취득을 함께 준비합니다.',period:'2027.03.01 — 2028.01.07',time:'09:00–16:40 · 주 5일',tuition:'전액무료',self:'0원',support:'전액 지원',capacity:'25명',tech:'정보시스템 · 운영 · 프로그램기능사'},
    adsp:{cat:'자격증 · DATA',title:'ADsP · ADP 자격증 완벽 대비',hero:'데이터 분석 역량을<br><em>자격으로 증명하세요</em>',desc:'ADsP와 ADP 시험에 필요한 핵심 이론과 문제풀이를 준비합니다.',period:'수시모집',time:'일정 상담',tuition:'상담 시 안내',self:'상담 시 안내',support:'적용 여부 상담',capacity:'상담 시 안내',tech:'ADsP · ADP · 데이터 분석'},
    sqld:{cat:'자격증 · SQL',title:'SQLD · SQLP 자격증 완벽 대비',hero:'SQL 실력을<br><em>자격으로 완성하세요</em>',desc:'SQLD와 SQLP 시험에 필요한 데이터 모델링 및 SQL 활용 역량을 준비합니다.',period:'수시모집',time:'일정 상담',tuition:'상담 시 안내',self:'상담 시 안내',support:'적용 여부 상담',capacity:'상담 시 안내',tech:'SQLD · SQLP · 데이터 모델링'},
    'bigdata-cert':{cat:'자격증 · BIG DATA',title:'빅데이터 분석기사 자격증 완벽 대비',hero:'빅데이터 분석 실무를<br><em>국가자격으로 증명하세요</em>',desc:'빅데이터 분석기사 필기와 실기 시험을 체계적으로 준비합니다.',period:'수시모집',time:'일정 상담',tuition:'상담 시 안내',self:'상담 시 안내',support:'적용 여부 상담',capacity:'상담 시 안내',tech:'빅데이터 분석 · 필기 · 실기'},
    engineer:{cat:'자격증 · IT',title:'정보처리 산업기사 · 기사 자격증 대비반',hero:'개발의 기본기를<br><em>정보처리 자격으로 증명하세요</em>',desc:'정보처리 산업기사와 기사 필기·실기 시험을 함께 준비합니다.',period:'수시모집',time:'일정 상담',tuition:'상담 시 안내',self:'상담 시 안내',support:'적용 여부 상담',capacity:'상담 시 안내',tech:'정보처리 · 필기 · 실기'}
  };
  const storyProfiles = {
    data:{why:'차트를 설명하는 사람에서<br><em>다음 행동을 예측하는 사람으로</em>',whyDesc:'분석 결과를 보여주는 데서 멈추지 않고, 고객의 다음 선택을 예측해 실제 서비스의 행동으로 연결합니다.',duration:'10 WEEKS LATER',outcomeTitle:'수료할 때<br>예측 서비스를 증명할 결과물 3개',source:'고객 행동·구매·접속 기록',deliverable:'개인 맞춤형 예측 서비스',outcomes:[['분석 가능한 데이터 파이프라인','흩어진 고객 데이터를 수집·정제해 하나의 분석 흐름으로 연결합니다.'],['의사결정 대시보드','핵심 지표와 고객 패턴을 누구나 이해할 수 있는 화면으로 설계합니다.'],['개인화 예측 프로토타입','학습한 모델을 서비스 흐름에 연결해 다음 행동을 예측합니다.']]},
    factory:{why:'공정을 지켜보는 데서<br><em>스스로 제어하는 시스템을 만드는 것까지</em>',whyDesc:'설비와 환경 데이터를 실시간으로 연결하고, 이상 징후에 먼저 반응하는 ESG 스마트공정을 구현합니다.',duration:'28 WEEKS LATER',outcomeTitle:'수료할 때<br>현장에 적용할 자동화 결과물 3개',source:'환경 센서·설비·생산 데이터',deliverable:'스마트 환경공정 제어 시스템',outcomes:[['공정 데이터 연결 맵','MES·ERP·PLC 사이의 데이터 흐름과 제어 지점을 설계합니다.'],['실시간 공정 대시보드','환경과 설비 상태를 한눈에 읽고 이상 징후를 빠르게 찾습니다.'],['ESG 공정제어 시스템','기준을 벗어난 상황에 대응하는 자동 제어 시나리오를 구현합니다.']]},
    aiot:{why:'센서 값을 모으는 데서<br><em>산업의 문제를 해결하는 AIoT까지</em>',whyDesc:'현장에서 발생하는 데이터를 수집하고 AI로 분석해, 실제 운영자가 사용할 수 있는 산업 솔루션으로 완성합니다.',duration:'27 WEEKS LATER',outcomeTitle:'수료할 때<br>산업 데이터를 움직일 결과물 3개',source:'IoT 센서·장비·운영 데이터',deliverable:'AIoT 산업 분석 솔루션',outcomes:[['AIoT 수집 파이프라인','센서와 장비 데이터를 안정적으로 모으는 실시간 흐름을 구축합니다.'],['산업 이상탐지 모델','현장 데이터의 패턴을 학습해 위험과 이상 신호를 찾아냅니다.'],['운영 의사결정 화면','분석 결과를 현장 담당자가 바로 판단할 수 있는 화면으로 제공합니다.']]},
    robot:{why:'화면 속 모델을 만드는 데서<br><em>현실에서 움직이는 로봇을 구현하는 것까지</em>',whyDesc:'카메라와 라이다로 환경을 인식하고 판단과 제어를 연결해 자율주행·협동로봇을 직접 움직입니다.',duration:'27 WEEKS LATER',outcomeTitle:'수료할 때<br>직접 시연할 로봇 결과물 3개',source:'LiDAR·Depth Camera·주행 데이터',deliverable:'자율주행 협동로봇 데모',outcomes:[['로봇 비전 인식 모델','영상과 거리 데이터에서 사람·물체·경로를 구분합니다.'],['자율주행 제어 로직','인식 결과를 이동과 회피 동작으로 연결하는 제어 흐름을 구현합니다.'],['협동로봇 시연 시스템','산업 시나리오 안에서 실제 작동하는 로봇 데모를 완성합니다.']]},
    cloud:{why:'코드를 작성하는 데서<br><em>사용자가 접속하는 서비스로 배포하는 것까지</em>',whyDesc:'프론트엔드와 서버, 데이터베이스를 연결하고 클라우드 배포와 보안까지 서비스 전체 수명주기를 경험합니다.',duration:'28 WEEKS LATER',outcomeTitle:'수료할 때<br>개발 역량을 보여줄 결과물 3개',source:'사용자 요구·서비스 데이터·코드',deliverable:'클라우드 기반 웹&앱 서비스',outcomes:[['풀스택 웹&앱 서비스','화면부터 서버와 데이터베이스까지 연결된 제품을 개발합니다.'],['클라우드 배포 파이프라인','Docker 기반으로 반복 가능한 배포와 운영 환경을 구성합니다.'],['개발자 기술 포트폴리오','설계와 문제 해결 과정을 채용 담당자가 읽을 수 있게 정리합니다.']]},
    video:{why:'편집 기능을 익히는 데서<br><em>시선을 붙잡는 콘텐츠를 완성하는 것까지</em>',whyDesc:'AI 도구를 편집 과정에 활용해 기획, 제작, 후반 작업의 속도와 표현력을 함께 높입니다.',duration:'8 WEEKS LATER',outcomeTitle:'수료할 때<br>바로 공개할 영상 결과물 3개',source:'기획안·촬영 소스·AI 생성 에셋',deliverable:'AI 기반 영상 포트폴리오',outcomes:[['숏폼 콘텐츠','첫 3초에 메시지가 전달되는 세로형 콘텐츠를 제작합니다.'],['브랜드 프로모션 영상','브랜드의 톤과 목적에 맞춘 완성형 홍보 영상을 만듭니다.'],['영상 제작 포트폴리오','기획 의도와 AI 활용 과정까지 함께 보여주는 작업집을 완성합니다.']]},
    uiux:{why:'예쁜 화면을 그리는 데서<br><em>사용자의 선택을 설계하는 것까지</em>',whyDesc:'리서치와 문제 정의를 바탕으로 AI를 활용해 더 빠르게 검증하고, 설득력 있는 제품 경험을 완성합니다.',duration:'17 WEEKS LATER',outcomeTitle:'수료할 때<br>디자인 판단을 보여줄 결과물 3개',source:'사용자 인터뷰·행동 데이터·서비스 요구',deliverable:'AI 활용 UX 제품 포트폴리오',outcomes:[['사용자 리서치 리포트','사용자의 실제 문제와 행동을 근거로 핵심 기회를 정의합니다.'],['인터랙티브 프로토타입','핵심 사용자 흐름을 직접 눌러 검증할 수 있는 화면으로 만듭니다.'],['UX 케이스 스터디','문제부터 검증까지 디자인 판단의 근거를 포트폴리오로 정리합니다.']]},
    japan:{why:'자바를 배우는 데서<br><em>일본 현업 개발자로 연결되는 것까지</em>',whyDesc:'엔터프라이즈 개발 역량과 일본식 채용 준비를 함께 진행해 기술과 취업 준비의 간격을 줄입니다.',duration:'26 WEEKS LATER',outcomeTitle:'수료할 때<br>일본 취업을 위한 준비물 3개',source:'기업 요구사항·자바 코드·채용 정보',deliverable:'일본 IT 취업 포트폴리오',outcomes:[['자바 기업형 프로젝트','실제 기업 구조를 반영한 서버 애플리케이션을 개발합니다.'],['일문 기술 포트폴리오','개발 경험과 문제 해결 과정을 일본 채용 문법에 맞게 정리합니다.'],['면접 커뮤니케이션','기술 선택과 협업 경험을 일본어로 설명하는 힘을 준비합니다.']]},
    usa:{why:'감으로 캠페인을 만드는 데서<br><em>데이터로 글로벌 시장을 설득하는 것까지</em>',whyDesc:'시장과 고객 데이터를 읽고 실행 가능한 마케팅 전략으로 바꿔 미국 취업에 필요한 분석형 마케터 역량을 만듭니다.',duration:'29 WEEKS LATER',outcomeTitle:'수료할 때<br>글로벌 마케팅 결과물 3개',source:'시장·고객·캠페인 데이터',deliverable:'데이터 기반 글로벌 캠페인',outcomes:[['시장 분석 리포트','데이터로 목표 시장의 기회와 경쟁 구도를 설명합니다.'],['캠페인 성과 대시보드','채널별 성과와 고객 반응을 비교해 다음 액션을 찾습니다.'],['영문 마케팅 포트폴리오','분석과 전략, 실행 결과를 해외 채용 기준에 맞게 정리합니다.']]},
    china:{why:'아이디어를 제안하는 데서<br><em>AI 제품의 성장을 책임지는 PM까지</em>',whyDesc:'사용자 문제를 찾고 AI 기능의 우선순위를 정해 중국 시장에 맞는 제품 전략과 실행 계획을 설계합니다.',duration:'26 WEEKS LATER',outcomeTitle:'수료할 때<br>글로벌 PM 결과물 3개',source:'사용자 문제·시장 데이터·AI 기술',deliverable:'AI 제품 전략 포트폴리오',outcomes:[['제품 요구사항 문서','사용자 문제와 비즈니스 목표를 개발 가능한 요구사항으로 정리합니다.'],['AI 기능 로드맵','효과와 난이도를 기준으로 기능 우선순위와 출시 계획을 설계합니다.'],['중국 시장 진출 전략','현지 사용자와 경쟁 환경을 반영한 제품 전략을 제안합니다.']]},
    cooking:{why:'레시피를 따라 하는 데서<br><em>현장에서 통하는 조리 기본기를 갖추는 것까지</em>',whyDesc:'한식과 양식의 기본 조리법을 반복 실습하고 자격 취득과 현장 수행 능력을 함께 준비합니다.',duration:'ONE YEAR LATER',outcomeTitle:'수료할 때<br>주방에서 증명할 역량 3개',source:'식재료·조리 공정·위생 기준',deliverable:'조리기능사와 현장 실무 역량',outcomes:[['한식 실기 메뉴','시험 기준에 맞는 한식 메뉴를 시간 안에 완성합니다.'],['양식 실기 메뉴','기본 소스와 조리법을 적용해 양식 과제를 수행합니다.'],['주방 운영 기본기','위생·동선·원가를 고려해 현장 실무를 준비합니다.']]},
    game:{why:'게임을 즐기는 데서<br><em>플레이 가능한 콘텐츠를 만드는 것까지</em>',whyDesc:'기획한 규칙을 코드와 그래픽으로 구현하며 게임 제작 직무와 자격 취득을 함께 준비합니다.',duration:'ONE YEAR LATER',outcomeTitle:'수료할 때<br>플레이할 수 있는 결과물 3개',source:'게임 기획·그래픽 에셋·프로그램 코드',deliverable:'플레이 가능한 게임 포트폴리오',outcomes:[['게임 시스템 기획서','핵심 재미와 규칙, 사용자 흐름을 문서로 설계합니다.'],['플레이어블 프로토타입','직접 실행하고 테스트할 수 있는 게임을 구현합니다.'],['게임 제작 포트폴리오','담당 역할과 개발 과정을 진학·취업 자료로 정리합니다.']]},
    design:{why:'툴을 다루는 데서<br><em>브랜드 메시지를 보이게 만드는 것까지</em>',whyDesc:'그래픽과 영상의 기초를 익히고 실제 광고 콘텐츠를 제작해 자격과 포트폴리오를 함께 준비합니다.',duration:'ONE YEAR LATER',outcomeTitle:'수료할 때<br>보여줄 디자인 결과물 3개',source:'브랜드 메시지·이미지·영상 소스',deliverable:'디지털 광고 디자인 포트폴리오',outcomes:[['브랜드 그래픽 세트','일관된 콘셉트로 포스터와 소셜 이미지를 디자인합니다.'],['영상 광고 콘텐츠','기획부터 편집까지 메시지가 분명한 영상을 제작합니다.'],['그래픽 자격 포트폴리오','자격 실기 역량과 창작 결과물을 한 번에 보여줍니다.']]},
    mobility:{why:'로봇을 조종하는 데서<br><em>스스로 판단하는 모빌리티를 만드는 것까지</em>',whyDesc:'센서와 AI, 응용 소프트웨어를 연결해 실제 환경을 인식하고 이동하는 스마트모빌리티를 구현합니다.',duration:'ONE YEAR LATER',outcomeTitle:'수료할 때<br>움직이는 모빌리티 결과물 3개',source:'주행 센서·카메라·제어 신호',deliverable:'AI 스마트모빌리티 데모',outcomes:[['주행 환경 인식','센서 데이터로 차선과 장애물, 이동 공간을 구분합니다.'],['자율주행 알고리즘','인지 결과에 따라 경로를 정하고 이동을 제어합니다.'],['스마트모빌리티 시연','하드웨어와 소프트웨어를 연결해 실제 주행을 시연합니다.']]},
    system:{why:'컴퓨터를 사용하는 데서<br><em>서비스가 멈추지 않게 운영하는 것까지</em>',whyDesc:'정보시스템의 구축과 운영 원리를 익히고 장애 대응과 프로그램 개발 역량을 함께 준비합니다.',duration:'ONE YEAR LATER',outcomeTitle:'수료할 때<br>IT 운영 역량을 증명할 결과물 3개',source:'사용자 요청·서버 로그·프로그램 코드',deliverable:'정보시스템 구축·운영 포트폴리오',outcomes:[['업무 프로그램','요구사항을 분석해 실제 사용할 수 있는 프로그램을 개발합니다.'],['시스템 운영 문서','설치·권한·백업·장애 대응 절차를 운영 기준으로 정리합니다.'],['문제 해결 기록','발생한 장애를 분석하고 복구한 과정을 포트폴리오로 남깁니다.']]},
    adsp:{why:'용어를 암기하는 데서<br><em>데이터 분석 사고를 시험에서 증명하는 것까지</em>',whyDesc:'핵심 개념을 문제 유형과 연결하고 반복 풀이로 ADsP·ADP 합격에 필요한 판단 속도를 높입니다.',duration:'AFTER THE COURSE',outcomeTitle:'과정을 마치면<br>시험장에 가져갈 준비 3가지',source:'핵심 이론·기출 유형·오답 데이터',deliverable:'ADsP·ADP 합격 준비',outcomes:[['핵심 개념 지도','데이터 이해부터 분석 방법론까지 출제 구조로 정리합니다.'],['유형별 문제 풀이','자주 출제되는 패턴과 함정을 빠르게 구분합니다.'],['개인 오답 노트','약한 영역을 진단하고 시험 전 복습 순서를 완성합니다.']]},
    sqld:{why:'SQL 문법을 외우는 데서<br><em>데이터 구조를 판단하고 푸는 것까지</em>',whyDesc:'데이터 모델링과 SQL 활용 원리를 문제에 적용해 SQLD·SQLP 시험의 복합 유형에 대응합니다.',duration:'AFTER THE COURSE',outcomeTitle:'과정을 마치면<br>SQL 시험을 풀 준비 3가지',source:'데이터 모델·SQL 문장·기출 문제',deliverable:'SQLD·SQLP 합격 준비',outcomes:[['모델링 핵심 노트','정규화와 관계 설계를 시험 기준에 맞게 구조화합니다.'],['SQL 문제 해결법','실행 결과와 성능을 추론하며 복합 쿼리 문제를 풉니다.'],['실전 모의고사 기록','시간 배분과 오답 원인을 점검해 합격 전략을 완성합니다.']]},
    'bigdata-cert':{why:'분석 과정을 아는 데서<br><em>필기와 실기로 완주하는 것까지</em>',whyDesc:'빅데이터 분석 전 과정을 이론과 실습으로 연결해 분석기사 필기와 실기를 함께 대비합니다.',duration:'AFTER THE COURSE',outcomeTitle:'과정을 마치면<br>분석기사 준비물 3가지',source:'분석 이론·실습 데이터·기출 문제',deliverable:'빅데이터 분석기사 합격 준비',outcomes:[['필기 개념 체계','분석 기획부터 모델 평가까지 출제 영역을 연결해 정리합니다.'],['실기 분석 코드','전처리와 모델링, 평가 과정을 제한 시간 안에 구현합니다.'],['모의시험 리포트','점수와 풀이 시간을 분석해 마지막 보완 영역을 찾습니다.']]},
    engineer:{why:'개발 지식을 흩어 배우는 데서<br><em>정보처리 자격으로 체계화하는 것까지</em>',whyDesc:'소프트웨어 설계부터 데이터베이스와 운영까지 핵심 지식을 연결해 필기와 실기 합격을 준비합니다.',duration:'AFTER THE COURSE',outcomeTitle:'과정을 마치면<br>정보처리 시험 준비물 3가지',source:'개발 이론·알고리즘·기출 문제',deliverable:'정보처리 기사 합격 준비',outcomes:[['과목별 핵심 요약','방대한 범위를 출제 빈도와 연관 개념 중심으로 압축합니다.'],['실기 문제 풀이 코드','알고리즘과 SQL 문제를 직접 작성하며 실전 감각을 만듭니다.'],['합격 전략표','취약 과목과 남은 기간에 맞춰 개인별 복습 계획을 완성합니다.']]}
  };
  Object.keys(storyProfiles).forEach(profileKey => Object.assign(courses[profileKey], storyProfiles[profileKey]));
  const key = new URLSearchParams(location.search).get('course') || 'data';
  const highschoolKeys = ['cooking','game','design','mobility','system'];
  const isHighschool = highschoolKeys.includes(key);
  const data = Object.assign({}, common, courses[key] || courses.data);
  window.COURSE_CATALOG = courses;
  window.COURSE_KEY = key;
  window.COURSE_CURRENT = data;
  const category = data.cat.startsWith('KDT') ? 'kdt'
    : data.cat.startsWith('AI ·') ? 'creative'
    : data.cat.startsWith('해외취업') ? 'global'
    : data.cat.startsWith('일반고') ? 'highschool'
    : 'license';
  const courseGuides = {
    data:['데이터 분석·AI 서비스 취업을 준비하는 분','데이터 분석가 · ML 엔지니어 · AI 서비스 기획',['1–2주','3–4주','5–7주','8–10주']],
    factory:['제조 데이터와 공정 자동화에 관심 있는 분','스마트팩토리 엔지니어 · MES/ERP 개발자 · 자동화 엔지니어',['1–4주','5–11주','12–20주','21–28주']],
    aiot:['IoT와 AI를 연결해 산업 문제를 풀고 싶은 분','AIoT 개발자 · 산업 데이터 분석가 · 솔루션 엔지니어',['1–4주','5–10주','11–19주','20–27주']],
    robot:['센서·영상·로봇 소프트웨어를 직접 다루고 싶은 분','로봇 SW 개발자 · 자율주행 엔지니어 · 컴퓨터비전 엔지니어',['1–5주','6–12주','13–20주','21–27주']],
    cloud:['웹 개발부터 배포까지 서비스 전체를 만들고 싶은 분','풀스택 개발자 · 백엔드 개발자 · 클라우드 엔지니어',['1–5주','6–12주','13–20주','21–28주']],
    video:['AI를 활용해 영상 결과물과 포트폴리오를 만들고 싶은 분','영상 편집자 · 콘텐츠 크리에이터 · 브랜드 콘텐츠 제작자',['1–2주','3–4주','5–6주','7–8주']],
    uiux:['리서치부터 프로토타입까지 UX 실무를 경험하고 싶은 분','UI/UX 디자이너 · 프로덕트 디자이너 · UX 리서처',['1–3주','4–7주','8–12주','13–17주']],
    japan:['Java 개발 역량과 일본 취업 준비를 함께 하고 싶은 분','Java 개발자 · 엔터프라이즈 개발자 · 일본 IT 엔지니어',['1–6주','7–14주','15–21주','22–26주']],
    usa:['데이터 기반 마케팅으로 미국 취업을 준비하는 분','글로벌 마케터 · 퍼포먼스 마케터 · 마케팅 데이터 분석가',['1–6주','7–14주','15–23주','24–29주']],
    china:['AI 제품 기획과 중국 시장 진출에 관심 있는 분','AI 프로덕트 매니저 · 서비스 기획자 · 글로벌 PM',['1–5주','6–12주','13–20주','21–26주']],
    cooking:['조리 자격증과 현장 실무를 함께 준비하는 일반고 3학년','한식·양식 조리사 · 호텔 셰프 · 외식 서비스 직무',['1학기 기초','1학기 실무','2학기 심화','수료 프로젝트']],
    game:['게임 기획·그래픽·프로그래밍 진로를 준비하는 일반고 3학년','게임 기획자 · 게임 개발자 · 게임 그래픽 디자이너',['1학기 기초','1학기 실무','2학기 심화','수료 프로젝트']],
    design:['디자인·영상·광고 분야 진로를 준비하는 일반고 3학년','그래픽 디자이너 · 영상 편집자 · 디지털 마케터',['1학기 기초','1학기 실무','2학기 심화','수료 프로젝트']],
    mobility:['AI 로봇과 자율주행 SW 진로를 준비하는 일반고 3학년','로봇 SW 개발자 · 모빌리티 엔지니어 · 임베디드 개발자',['1학기 기초','1학기 실무','2학기 심화','수료 프로젝트']],
    system:['개발과 IT 시스템 운영 진로를 준비하는 일반고 3학년','정보시스템 운영자 · IT 지원 엔지니어 · 주니어 개발자',['1학기 기초','1학기 실무','2학기 심화','수료 프로젝트']],
    adsp:['ADsP·ADP 취득으로 데이터 역량을 증명하려는 분','데이터 분석 실무자 · 데이터 기획자',['핵심 개념','유형 학습','실전 문제','최종 점검']],
    sqld:['SQLD·SQLP 취득과 데이터베이스 역량이 필요한 분','SQL 개발자 · 데이터베이스 실무자',['핵심 개념','유형 학습','실전 문제','최종 점검']],
    'bigdata-cert':['빅데이터 분석기사 필기와 실기를 함께 준비하는 분','빅데이터 분석가 · 데이터 처리 실무자',['필기 이론','분석 실습','기출 문제','모의 시험']],
    engineer:['정보처리 산업기사·기사 취득을 준비하는 분','소프트웨어 개발자 · 정보시스템 실무자',['필기 이론','실기 핵심','기출 문제','모의 시험']]
  };
  const guide = courseGuides[key] || courseGuides.data;
  document.body.dataset.courseCategory = category;
  document.body.dataset.courseKey = key;
  const hero = document.querySelector('.sales-hero');
  if (!hero) return;
  document.title = data.title + ' — ' + (window.BRAND?.name || '내일의AI');
  hero.querySelector('.sales-chip').textContent = data.cat + ' · 모집중';
  hero.querySelector('h1').innerHTML = data.hero;
  hero.querySelector('.sales-hero__copy>p:nth-of-type(2)').textContent = data.desc;
  const intro = document.querySelector('.sales-info__intro');
  intro.querySelector('.sales-info__eyebrow').textContent = data.cat;
  intro.classList.toggle('is-long', data.title.length > 32);
  intro.querySelector('h2').innerHTML = data.title;
  intro.querySelector('p').innerHTML = data.tech + '<br>' + data.desc;
  const emblem = intro.querySelector('[data-course-emblem]');
  emblem.querySelector('img').src = '/v2/assets/outcome-icons/' + key + '.png';
  const rows = [...document.querySelectorAll('.sales-info__table>div')];
  rows[0].querySelector('dd').innerHTML = '<strong>' + data.status + '</strong><small>정원 충원 시 조기 마감</small>';
  rows[1].querySelector('dd').innerHTML = data.period;
  rows[2].querySelector('dd').innerHTML = data.time + '<small>' + data.days + '</small>';
  rows[3].querySelector('dd').innerHTML = data.method + '<small>실습 · 프로젝트 · 피드백 중심</small>';
  rows[4].querySelector('dd').textContent = data.tuition;
  rows[5].querySelector('dd').innerHTML = '<strong>' + data.self + '</strong>';
  rows[6].querySelector('dd').textContent = data.support;
  rows[7].hidden = !data.instructor;
  rows[7].querySelector('dd').textContent = data.instructor || '';
  rows[8].querySelector('dd').innerHTML = data.capacity + '<small>지원 절차를 개별 안내합니다.</small>';
  const question = document.querySelector('.sales-question');
  question.querySelector('h2').innerHTML = data.why;
  question.querySelector('.container>p').textContent = data.whyDesc;
  question.querySelector('.course-question__visual img').src = '/v2/assets/outcome-icons/' + key + '.png';
  const beforeLabels = {
    kdt:'도구 이름만 나열하는 지원자',
    creative:'툴을 다뤄봤다고 말하는 지원자',
    global:'해외 취업을 희망한다고 말하는 지원자',
    highschool:'자격과 진로를 따로 준비하는 학생',
    license:'이론을 외웠다고 말하는 응시자'
  };
  question.querySelector('[data-course-before]').textContent = beforeLabels[category];
  question.querySelector('[data-course-after]').textContent = data.deliverable + '로 실력을 증명하는 사람';
  question.querySelectorAll('.course-conviction__after li').forEach((item,index) => {
    item.textContent = data.outcomes[index][0];
  });
  const outcome = document.querySelector('.sales-outcome');
  outcome.querySelector('.sales-section-head>span').textContent = data.duration;
  outcome.querySelector('.sales-section-head h2').innerHTML = data.outcomeTitle;
  outcome.querySelectorAll('.outcome-stage article').forEach((card,index) => {
    const item = data.outcomes[index];
    card.querySelector('small').textContent = '0' + (index + 1) + ' · RESULT';
    card.querySelector('h3').textContent = item[0];
    card.querySelector('p').textContent = item[1];
    const icon = card.querySelector('.outcome-icon');
    const image = icon.querySelector('img');
    icon.style.setProperty('--outcome-icon-index', index);
    image.src = '/v2/assets/outcome-icons/' + key + '.png';
  });
  const stack = document.querySelector('.sales-stack');
  stack.querySelector('.sales-section-head h2').innerHTML = '배운 기술이<br><em>' + data.deliverable + '로 이어지는 흐름</em>';
  stack.querySelector('.sales-stack__lead').textContent = data.source + '에서 시작해 ' + data.deliverable + '로 완성되는 전 과정을 직접 연결합니다.';
  stack.querySelector('.data-flow__source p').textContent = data.source;
  const tools = data.tech.split(' · ');
  stack.querySelectorAll('.data-flow li').forEach((item,index) => {
    item.querySelector('strong').textContent = tools[index] || tools[tools.length - 1];
    item.querySelector('p').textContent = data.outcomes[index][1];
  });
  stack.querySelector('.data-flow__result b').textContent = '완성';
  stack.querySelector('.data-flow__result>span').textContent = data.deliverable;
  stack.querySelector('.data-flow__result p').textContent = '시연·검증 가능한 최종 결과물';
  const curriculum = document.querySelector('.sales-curriculum');
  curriculum.querySelector('.sales-section-head h2').innerHTML = '배우는 데서 멈추지 않고<br><em>' + data.deliverable + '까지 완성합니다</em>';
  curriculum.querySelector('[data-learning-summary]').textContent = data.source + '에서 시작해 ' + data.outcomes.map(item => item[0]).join(', ') + '를 직접 만들고 연결합니다. 수료할 때는 설명이 아니라 시연 가능한 결과물로 역량을 보여줍니다.';
  curriculum.querySelector('[data-course-audience]').textContent = guide[0];
  curriculum.querySelector('[data-course-career]').textContent = guide[1];
  curriculum.querySelector('[data-course-result]').textContent = data.deliverable;
  const phaseTitles = [tools[0] + ' 기초와 문제 정의',(tools[1] || tools[0]) + ' 실무 적용',(tools[2] || tools[tools.length - 1]) + ' 결과물 구현',data.deliverable + ' 완성과 발표'];
  curriculum.querySelectorAll('.curriculum-list article').forEach((item,index) => {
    item.querySelector('h3').textContent = phaseTitles[index];
    item.querySelector('p').textContent = index < 3 ? data.outcomes[index][1] : '결과물을 시연하고 선택의 근거와 개선 과정을 포트폴리오로 정리합니다.';
    item.querySelector('b').textContent = guide[2][index];
  });
  document.querySelector('.sales-info').insertAdjacentElement('afterend', curriculum);
  document.querySelectorAll('a[href^="/v2/site/class/apply.html"]').forEach(a => {
    a.href = isHighschool ? '/v2/site/campus/counsel.html?course=' + key : '/v2/site/class/apply.html?course=' + key;
    if (isHighschool) a.textContent = '사전상담 신청';
  });
  document.querySelectorAll('a[href^="/v2/site/campus/counsel.html"]').forEach(a => a.href = '/v2/site/campus/counsel.html?course=' + key);
  const sticky = document.querySelector('.sales-sticky');
  sticky.querySelector('b').textContent = data.title;
  sticky.querySelector('span').textContent = data.period + ' · 정원 ' + data.capacity;
  const apply = document.querySelector('.sales-apply');
  apply.querySelector('p').textContent = data.period + ' · 정원 ' + data.capacity;
  apply.querySelector('h2').innerHTML = data.title + '<br><em>' + (isHighschool ? '진로 상담부터 시작해 보세요.' : '지원할 준비가 됐나요?') + '</em>';
  if (isHighschool) document.querySelector('.sales-info__note').textContent = '※ 일반계 고등학교 3학년 위탁교육 지원 대상과 절차는 사전상담에서 확인합니다.';

  const journeySections = [
    ['overview','.sales-info','과정 정보'],
    ['curriculum','.sales-curriculum','커리큘럼'],
    ['why-course','.sales-question','과정 목표'],
    ['outcome','.sales-outcome','완성 결과물'],
    ['project-flow','.sales-stack','기술 흐름'],
    ['learning-experience','.sales-proof','학습 경험']
  ].map(item => {
    const section = document.querySelector(item[1]);
    if (section) { section.id = item[0]; section.classList.add('course-scene'); }
    return {id:item[0],label:item[2],section};
  }).filter(item => item.section);
  const journey = document.createElement('nav');
  journey.className = 'course-journey';
  journey.setAttribute('aria-label','과정 상세 빠른 이동');
  journey.innerHTML = journeySections.map((item,index) => '<a href="#' + item.id + '"' + (index === 0 ? ' aria-current="step"' : '') + '><i></i><span>' + item.label + '</span></a>').join('');
  document.body.append(journey);
  const journeyLinks = [...journey.querySelectorAll('a')];
  const activateJourney = id => journeyLinks.forEach(link => link.setAttribute('aria-current', link.hash === '#' + id ? 'step' : 'false'));
  const updateJourney = () => {
    const guideLine = window.innerHeight * .42;
    let current = journeySections[0];
    journeySections.forEach(item => {
      if (item.section.getBoundingClientRect().top <= guideLine) current = item;
    });
    if (current) activateJourney(current.id);
  };
  let journeyFrame = 0;
  const requestJourneyUpdate = () => {
    if (journeyFrame) return;
    journeyFrame = requestAnimationFrame(() => {
      updateJourney();
      journeyFrame = 0;
    });
  };
  journeyLinks.forEach(link => link.addEventListener('click', () => activateJourney(link.hash.slice(1))));
  window.addEventListener('scroll', requestJourneyUpdate, {passive:true});
  window.addEventListener('resize', requestJourneyUpdate);
  updateJourney();
  if ('IntersectionObserver' in window) {
    const sceneObserver = new IntersectionObserver(entries => entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('is-visible');
      }
    }),{threshold:.18,rootMargin:'-18% 0px -52%'});
    journeySections.forEach(item => sceneObserver.observe(item.section));
    const revealObserver = new IntersectionObserver(entries => entries.forEach(entry => {
      if (entry.isIntersecting) { entry.target.classList.add('is-visible'); revealObserver.unobserve(entry.target); }
    }),{threshold:.1});
    journeySections.forEach(item => revealObserver.observe(item.section));
    document.body.classList.add('course-enhanced');
    journeySections[0]?.section.classList.add('is-visible');
  } else journeySections.forEach(item => item.section.classList.add('is-visible'));
})();
