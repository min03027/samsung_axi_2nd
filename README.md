# AXI Project 수강생 플랫폼 개선 내역

이 문서는 GitHub 원본 [`woongscoding/axi_project`](https://github.com/woongscoding/axi_project)의 `main` 브랜치, 커밋 [`01b91b9`](https://github.com/woongscoding/axi_project/commit/01b91b977b9320e6fea030ce612cd0188cdf51ff)을 기준으로 수강생 플랫폼에 추가·수정한 내용을 정리한 문서입니다.

> 초기 작업 중 중단한 사용자 랜딩 페이지는 아래 변경 내역과 업로드 범위에서 제외합니다.

## 작업 목표

기존 로그인·권한·관리자 기능과 수강생 학습 기능을 유지하면서, 수강생이 로그인 후 다음 내용을 빠르게 확인할 수 있도록 화면과 정보 구조를 개선했습니다.

- 내가 현재 과정을 잘 따라가고 있는지
- 오늘 해야 할 학습과 일정이 무엇인지
- 문제가 있을 때 어디에서 도움을 받을 수 있는지
- 출결·과제·시험·피드백이 어떤 상태인지

## 주요 변경 사항

### 1. 수강생 홈 UX 개편

기존 행정형 대시보드를 친근한 **안심 대시보드** 형태로 재구성했습니다.

- 현재 진도, 권장 진도, 출석, 오늘 일정 안내
- 중요한 행동을 `학습 이어하기`, `오늘 일정`, `도움 요청` 중심으로 정리
- 오늘 일정 타임라인 제공
- 실제 튜터링 대화 기반 최근 코치 피드백 표시
- 학습 상태와 출석 수치에 대한 사용자 친화적 해석 제공
- 반 진도 비교를 경쟁 순위표 대신 미니 레이스 형태로 표현
- 성장 요약, 진행 중인 과정, 중요 공지 영역 재배치
- 중요 공지 로그인 팝업 및 `오늘 하루 보지 않기` 제공
- 기존 AI 학습 챗봇 유지

주요 파일:

- `src/main/resources/templates/trainee/index.html`
- `src/main/resources/static/css/trainee/index.css`
- `src/main/resources/static/js/trainee/index.js`

### 2. 실제 데이터 기반 홈 정보 보강

수강생 홈에 표시되는 상태 정보는 기존 DB와 서비스 데이터를 재사용합니다.

- 실제 과정 기간을 기준으로 권장 진도 계산
- 실제 학습 완료율과 출결률 연결
- 실제 과제·시험·설문 마감 정보 연결
- 실제 튜터링 답변을 최근 코치 피드백으로 표시
- 반 평균과 내 진도를 비교해 안정감을 주는 안내 문구 생성

관련 파일:

- `src/main/java/com/ssa/lms/dashboard/dto/CoursePaceView.java`
- `src/main/java/com/ssa/lms/dashboard/dto/TraineeDashboardView.java`
- `src/main/java/com/ssa/lms/dashboard/service/TraineeDashboardService.java`

### 3. 오늘 일정 데이터 정확성 개선

오늘 일정에는 실제 날짜가 확인되는 데이터만 포함합니다.

- 차시: `lessonDate`가 오늘인 경우만 표시
- 과제·시험·설문: 실제 마감일이 오늘인 경우만 표시
- 미래·과거 일정 제외
- 날짜가 없는 항목 제외
- 정확한 시간이 있으면 `HH:mm`으로 표시
- 날짜만 있는 마감 항목은 `오늘 마감`으로 표시
- 임의 날짜·시간 및 mock 일정 생성 금지

### 4. 온라인 시험 화면 가독성 개선

- 제목과 안내 문구의 글자 크기 및 행간 확대
- 안내 영역과 시험 목록 사이 간격 확대
- 필터 입력 요소와 카드 내부 여백 확대
- 시험 카드의 제목·메타 정보 가독성 개선
- 태블릿·모바일 카드 레이아웃 유지

관련 파일:

- `src/main/resources/static/css/trainee/online-test.css`

### 5. 수강생 플랫폼 IA 및 공통 내비게이션 개편

수강생 플랫폼의 상위 메뉴를 다음 8개로 정리했습니다.

| 상위 메뉴 | 주요 기능 |
|---|---|
| 홈 | 오늘 행동과 현재 상태 확인 |
| 내 학습 | 과정, 이어서 학습, 학습 콘텐츠 |
| 과제·평가 | 과제, 시험, 설문, 평가 안내 |
| 출결·이수 | 출결 현황, 수료 진행 상태 |
| AI 학습지원 | AI 튜터, 강사 질문, 튜터링, 맞춤 추천 |
| 성장·피드백 | 학습 기록과 성장 흐름 |
| 취업·포트폴리오 | 취업 준비, 직무 로드맵, 포트폴리오 |
| 공지·알림 | 공지사항과 개인 알림함 |

공통 내비게이션 개선 사항:

- 홈 메뉴 명시
- 알림 아이콘을 실제 `/trainee/alarm`에 연결
- 데스크톱 드롭다운 메뉴 제공
- 모바일 햄버거 메뉴 제공
- 현재 메뉴 활성 상태 표시
- 기존 수강생 URL과 템플릿 active 키 호환 유지
- 모바일 가로 스크롤 및 메뉴 잘림 방지

관련 파일:

- `src/main/resources/templates/fragments/trainee.html`
- `src/main/resources/static/css/basic-form-trainee.css`
- `src/main/resources/static/js/trainee/navigation.js`
- `docs/trainee-ia-v1.md`

### 6. 신규 수강생 허브 화면

| 경로 | 용도 | 현재 상태 |
|---|---|---|
| `/trainee/evaluations` | 과제·시험·설문 통합 진입 | 기존 실제 기능 연결 |
| `/trainee/growth` | 성장·피드백 허브 | 화면 구조 및 기존 기능 연결 |
| `/trainee/career` | 취업·포트폴리오 허브 | 화면 구조 및 직무 로드맵 연결 |

신규 허브에는 임의 진도율, 점수, 취업률 또는 가짜 활동 내역을 넣지 않았습니다. 백엔드 기능이 없는 영역은 향후 실제 데이터 연동을 위한 구조만 제공합니다.

관련 파일:

- `src/main/java/com/ssa/lms/web/trainee/TraineePlatformController.java`
- `src/main/resources/templates/trainee/evaluations.html`
- `src/main/resources/templates/trainee/growth.html`
- `src/main/resources/templates/trainee/career.html`
- `src/main/resources/static/css/trainee/platform-hubs.css`

### 7. 중요 공지 관리 개선

관리자 공지 등록 화면의 `상단 고정` 항목을 `중요 공지 · 로그인 팝업 노출`로 명확하게 변경했습니다. 해당 공지는 수강생 홈 로그인 시 팝업으로 표시됩니다.

관련 파일:

- `src/main/resources/templates/admin/admin-07-notice/notice-add.html`

## 구현 상태 구분

### 실제 기능 및 데이터 연결

- 과정, 콘텐츠, 학습 진도
- 과제 제출
- 시험 응시
- 설문 참여
- 출결 및 이수 현황
- AI 질의응답과 일반 질의응답
- 튜터링
- 맞춤 커리큘럼과 직무 로드맵
- 공지, 알림, 내 정보

### UI 구조만 마련된 영역

- 강의평가·강사평가 저장 및 집계
- 통합 주간 성장 리포트
- 포트폴리오 저장·공개
- 지원 기업·면접·취업 상태 관리
- 수료 후 사후관리 기록

## 기존 기능 영향

- 로그인 및 인증 방식 변경 없음
- 학생·강사·관리자 role 분기 변경 없음
- DB 구조 및 Repository 변경 없음
- 관리자 기능 삭제 없음
- 기존 수강생 URL 삭제 또는 변경 없음
- 과제 제출, 시험 응시, 설문 응답, 진도 저장 등 기존 API 유지

## 테스트 및 검수

- 기존 17개와 신규 3개를 포함한 수강생 화면 20개 경로 렌더링 확인
- 20개 경로 모두 HTTP 200 및 완전한 HTML 응답 확인
- 신규 IA 및 허브 화면 렌더링 테스트 통과
- 수강생 홈 실제 데이터 구성 테스트 추가
- PC 1440px와 모바일 390px 기준 내비게이션 검수
- PC·모바일 가로 스크롤 없음 확인
- `git diff --check` 통과

관련 테스트:

- `src/test/java/com/ssa/lms/dashboard/DashboardServiceTest.java`
- `src/test/java/com/ssa/lms/web/trainee/TraineePlatformIaRenderTest.java`
- `src/test/java/com/ssa/lms/web/ai/AiScreenRenderTest.java`

## GitHub 업로드 시 주의사항

초기에 제작하다 중단한 랜딩 페이지 파일은 이 작업 범위에 포함하지 않습니다. 업로드 또는 커밋할 때 다음 항목을 제외해야 합니다.

- `src/main/java/com/ssa/lms/web/landing/`
- `src/main/resources/templates/landing/`
- `src/main/resources/static/css/landing.css`
- `src/main/resources/static/js/landing.js`
- `src/main/resources/static/img/landing-*.png`
- `src/test/java/com/ssa/lms/web/LandingPageRenderTest.java`
- 랜딩 페이지 연결을 위해 변경한 `src/main/java/com/ssa/lms/web/HomeController.java`

## 다음 단계 권장 순서

1. 기존 진도·출결·과제·시험 데이터를 `/trainee/growth`에 집계
2. 강의평가·강사평가 저장 및 관리자 집계 기능 구현
3. 포트폴리오 저장과 공개 범위 관리
4. 지원 기업·면접·취업 상태 관리
5. 수료생 사후관리 기능 연결

