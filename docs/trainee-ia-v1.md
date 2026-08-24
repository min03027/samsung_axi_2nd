# 수강생 플랫폼 IA v1

기준일: 2026-08-21
원칙: 기존 수강생 URL·권한·도메인 로직을 유지하고, 신규 기능은 기존 상위 구조를 바꾸지 않고 하위 페이지로 확장한다.

## 상위 IA

| 상위 영역 | 목적 | 하위 메뉴 및 대표 경로 |
|---|---|---|
| 홈 | 오늘 행동과 현재 상태 확인 | 안심 대시보드 `/trainee` |
| 내 학습 | 과정 선택과 학습 재개 | 나의 과정 `/trainee/my-course`, 이어서 학습 `/trainee/learning`, 콘텐츠 `/trainee/contents` |
| 과제·평가 | 제출·응시·평가 참여 | 과제 `/trainee/assignment`, 시험 `/trainee/exam`, 설문 `/trainee/survey`, 평가 안내 `/trainee/evaluations` |
| 출결·이수 | 수료에 필요한 기록 확인 | 출결 `/trainee/attendance`, 이수 `/trainee/completion-management` |
| AI 학습지원 | 즉시 질문과 사람의 도움 연결 | AI 튜터 `/trainee/ai/qna`, 강사 질문 `/trainee/qna`, 튜터링 `/trainee/qna/tutoring`, 맞춤 추천 `/trainee/ai/curriculum` |
| 성장·피드백 | 기록을 성장 흐름으로 확인 | 성장 리포트 `/trainee/growth` |
| 취업·포트폴리오 | 직무 탐색부터 사후관리까지 연결 | 취업 준비 홈 `/trainee/career`, 직무 로드맵 `/trainee/ai/roadmap` |
| 공지·알림 | 기관 안내와 개인 알림 확인 | 공지 `/trainee/notice`, 알림함 `/trainee/alarm` |
| 마이페이지 | 계정·개인 정보 관리 | 우측 계정 아이콘 `/trainee/my-info` |

## 구현 상태

### A — 실제 데이터·기능 연결

- 홈: `/trainee`
- 과정·콘텐츠·진도: `/trainee/my-course`, `/trainee/learning`, `/trainee/contents`, `/trainee/contents/{id}/play`
- 과제: `/trainee/assignment`와 제출·파일 경로
- 시험: `/trainee/exam`, 응시·답안·제출 경로
- 설문: `/trainee/survey`와 응답 제출 경로
- 출결·이수·이수증: `/trainee/attendance`, `/trainee/completion-management`
- AI 튜터·강사 전달: `/trainee/ai/qna`
- 일반 Q&A·튜터링: `/trainee/qna`, `/trainee/qna/tutoring`
- 맞춤 커리큘럼·직무 로드맵: `/trainee/ai/curriculum`, `/trainee/ai/roadmap`
- 공지·알림함: `/trainee/notice`, `/trainee/alarm`
- 내 정보: `/trainee/my-info`

일부 A 화면은 로컬 프로필에서 실제 데이터가 0건일 때 `SampleScreenData` 예시를 사용한다. 해당 화면은 공통 `예시` 배너로 실제 데이터와 구분한다.

### B — 화면과 사용자 흐름 확정, 추가 데이터 연동 대상

- 평가 안내: `/trainee/evaluations`
  - 실제 과제·시험·설문으로 이동
  - 강의평가·강사평가가 들어갈 영역 확보
- 성장 리포트: `/trainee/growth`
  - 실제 학습·출결·평가·튜터링 화면으로 이동
  - 주간 변화와 통합 피드백 영역 확보
- 취업·포트폴리오 허브: `/trainee/career`
  - 실제 직무 로드맵·과제 결과·튜터링으로 이동
  - 포트폴리오·지원 기업·면접·사후관리 영역 확보

B 화면에는 가짜 수치나 가짜 활동을 넣지 않는다.

### C — IA에서만 위치 확보

- 온라인 코딩 테스트와 결과 리포트
- 강의평가·강사평가 저장 및 집계
- 코드 질문·첨삭
- 개인 맞춤 성장 추천
- 포트폴리오 저장·공개
- 지원 기업·면접·취업 상태 저장
- 수료 후 사후관리 기록
- 긴급 공지 실시간 전송·확인 추적
- 웹캠·화면·OTP·안면인식 기반 시험 감독
- 수강생용 AI 이탈 위험 분석
- 라이브 화상강의

## 유지해야 하는 기존 경로

기존 Controller가 소유한 `/trainee/**` URL은 삭제하거나 리다이렉트하지 않는다. 새 GNB는 해당 경로를 그대로 연결한다. 특히 시험 응시, 과제 제출, 설문 응답, 콘텐츠 진도 API, 알림 읽음 처리처럼 POST/API 동작이 연결된 URL은 변경하지 않는다.

## 중복·레거시 정리 원칙

- `/trainee/ai/qna`는 AI 즉시 답변, `/trainee/qna`는 강사에게 저장되는 질문으로 명칭을 구분한다.
- `/trainee/ai/roadmap`은 구현 URL을 유지하되 IA상 `취업·포트폴리오` 아래에 둔다.
- 설문은 기존 URL을 유지하되 IA상 `과제·평가` 아래에 둔다.
- Controller 매핑이 없는 `continue-learning.html`, `play-class*.html`, `grading-modal-result.html` 등의 정적 템플릿은 현재 GNB에 노출하지 않는다. 삭제 여부는 별도 레거시 정리 단계에서 판단한다.
