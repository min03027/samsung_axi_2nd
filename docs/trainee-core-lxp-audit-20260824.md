# 수강생 핵심 LXP 기능 감사 (2026-08-24)

## 감사 기준

- 기준 파일: `참고문서/체크리스트/구현체크리스트_2차_20260822.xlsx`
- 기준 브랜치: `integration-trainee-management-20260823`
- 기준 커밋: `859e9040f29c7803958ae88433b4efd5af47acd3`
- 대상: LXP-053, 056, 058, 059, 060, 076, 086, 087
- 원칙: 화면 존재가 아니라 실제 DB 조회, 본인/담당 범위, 빈 상태, 다운로드 결과를 기준으로 판정

## 기능별 판정

### LXP-053 — 대시보드 시각화 및 엑셀 다운로드

- 원문 요구: 학생 학습 데이터를 대시보드에서 한눈에 시각화하고 엑셀 파일로 다운로드할 수 있어야 한다.
- 기존 상태: 관리자·강사 대시보드는 실제 과정 평균 진도와 기간 경과, 이수 요건을 시각화했으나 대시보드 지표 전용 엑셀 다운로드가 없었다.
- 조치: 기존 `DashboardMetricsService` 계산을 재사용해 전체/선택 분반의 과정별 수강 인원, 평균 진도, 기간 경과, 진도 격차를 xlsx로 내려받도록 추가했다.
- 최종 판정: **구현**
- 근거: `/admin/dashboard/learning-report.xlsx`, `/instructor/dashboard/learning-report.xlsx`

### LXP-056 — 분반별 대시보드 및 비교 보고서

- 원문 요구: 분반을 선택해 분반별 대시보드를 확인하고 보고서를 다운로드해 분반 데이터를 비교·분석할 수 있어야 한다.
- 기존 상태: 관리자는 전체 과정, 강사는 담당 과정의 차트를 보지만 분반 선택과 비교 보고서가 없었다.
- 조치: 관리자 전체 분반/강사 담당 분반만 선택 가능한 보고서 선택기와 전체 비교/단일 분반 xlsx를 추가했다. 강사는 미담당 분반 id를 직접 넣어도 조회되지 않는다.
- 최종 판정: **부분 구현**
- 남은 범위: 화면 차트 자체를 선택 분반으로 즉시 필터링하는 대시보드 상호작용과 분석 결과에서 독려 업무로 연결되는 운영 흐름은 없다.

### LXP-058 — 훈련생 내 대시보드

- 원문 요구: 훈련생이 본인의 학습 현황을 파악해 주도적으로 학습할 수 있는 내 대시보드가 있어야 한다.
- 기존 상태: `/trainee`에서 본인 수강 과정, 진도, 권장 진도, 출석, 과제·시험·설문, 오늘 일정, 코치 피드백을 실제 서비스 조회로 집계한다.
- 조치: 과정별 이어서 학습 링크가 실제 다음 콘텐츠를 가리키도록 정확성을 보완했다.
- 최종 판정: **구현**
- 근거: `TraineeDashboardService`, `DashboardMetricsService.paceOf`

### LXP-059 — 수강 과목 확인 및 이어서 학습

- 원문 요구: 수강 중인 과목을 바로 확인하고 이어서 학습으로 다음 학습 자료에 원클릭 이동할 수 있어야 한다.
- 기존 상태: 수강 과정 카드는 있었지만 모든 과정의 버튼이 공통 `/trainee/learning`으로 이동해 선택 과정이 바뀔 수 있었고, 다음 콘텐츠로 직접 이동하지 않았다.
- 조치: 진행 중 콘텐츠를 우선하고, 없으면 첫 미완료 콘텐츠를 선택하는 실제 DB 기반 다음 학습 계산을 추가했다. 홈 과정 카드와 안심 CTA는 해당 콘텐츠 재생/열람으로 직접 이동하며, 차시별 학습 화면에도 같은 이어서 학습 카드를 표시한다.
- 최종 판정: **구현**
- 근거: `ProgressService.nextLearningContent`, `TraineeDashboardService.continueLearningHref`

### LXP-060 — 본인 출석 현황

- 원문 요구: 훈련생이 본인의 출석 현황을 직접 확인해 학습 참여도를 관리할 수 있어야 한다.
- 기존 상태: `/trainee/attendance`가 인증 사용자 id로 과정별 출결률과 날짜별 상태를 조회하지만 데이터가 없으면 샘플 출결을 표시했다.
- 조치: 샘플 대체를 제거하고 본인 실제 출결만 표시하며, 없을 때는 빈 상태를 보여주도록 했다.
- 최종 판정: **구현**
- 근거: `AttendanceService.traineeAttendance(user.getId())`

### LXP-076 — 학습 현황 데이터 분석 기반 통합 관리

- 원문 요구: 훈련생 학습 현황 데이터를 분석해 대시보드로 시각화하고 통합적으로 관리할 수 있어야 한다.
- 기존 상태: 관리자·강사 대시보드에 실제 평균 진도, 기간 경과, 이수 요건 및 운영 KPI가 있으나 모든 학습 도메인을 하나의 분석 모델로 통합하지는 않는다.
- 조치: 기존 실제 진도 지표를 분반별 보고서로 내보내 비교할 수 있도록 보강했다.
- 최종 판정: **부분 구현**
- 남은 범위: 출결·평가·과제·콘텐츠 활동을 동일 분반/훈련생 축으로 결합한 통합 분석과 후속 관리 이력.

### LXP-086 — 다양한 학습 데이터 수집과 지속·즉각 개선 체계

- 원문 요구: 진도, 과제 제출, 테스트 성적 등 다양한 데이터를 수집해 훈련과정을 지속·즉각 개선하는 관리체계가 있어야 한다.
- 기존 상태: 진도, 출결, 과제, 시험, 성적, 이수 데이터는 각 도메인에 실제 저장되며 일부가 역할별 대시보드에 집계된다.
- 조치: 이번 범위에서는 기존 진도 지표 다운로드와 실제 데이터 빈 상태 정확성만 보강했다.
- 최종 판정: **부분 구현**
- 남은 범위: 지표 이상 감지 → 조치/피드백 → 개선 결과를 추적하는 닫힌 운영 루프와 과정 개선 이력.

### LXP-087 — 세부 학습 활동 데이터 수집

- 원문 요구: 진행률, 테스트 점수, 퀴즈/실습 평균, 푼 문제 수, 코드 실행/제출 수, 과제 제출 여부 등 다양한 데이터를 수집해야 한다.
- 기존 상태: 진행률, 시험/성적, 과제 제출은 저장하지만 퀴즈/실습 평균, 푼 문제 수, 코드 실행/제출 수를 수집하는 확정 데이터 모델은 확인되지 않았다.
- 조치: 존재하지 않는 지표를 가짜 값으로 추가하지 않았다.
- 최종 판정: **부분 구현**
- 남은 범위: 문제 풀이 및 코드 실행 이벤트 모델·저장·집계. 해당 백엔드가 생기기 전에는 구현으로 판정할 수 없다.

## 실제 데이터 정확성 정리

- `/trainee/contents`, `/trainee/learning`, 콘텐츠 재생/열람 및 진도 API는 샘플 id/고정 진도를 사용하지 않는다.
- `/trainee/attendance`는 인증 사용자 본인의 실제 출결만 표시한다.
- `/trainee/completion-management`는 본인의 실제 이수 정보만 표시하고, 비소유 이수증은 404로 처리한다.
- 데이터가 없으면 임의 숫자나 행을 만들지 않고 기존 빈 상태를 표시한다.
- 관리자 보고서는 전체 과정, 강사 보고서는 담당 과정만 조회한다.

## 변경 파일

- `src/main/java/com/ssa/lms/content/service/ProgressService.java`
- `src/main/java/com/ssa/lms/content/web/TraineeLearningController.java`
- `src/main/java/com/ssa/lms/content/web/ProgressApiController.java`
- `src/main/java/com/ssa/lms/attendance/web/TraineeAttendanceController.java`
- `src/main/java/com/ssa/lms/completion/web/TraineeCompletionController.java`
- `src/main/java/com/ssa/lms/dashboard/service/TraineeDashboardService.java`
- `src/main/java/com/ssa/lms/dashboard/service/DashboardLearningReportService.java`
- `src/main/java/com/ssa/lms/dashboard/web/DashboardLearningReportController.java`
- `src/main/java/com/ssa/lms/web/ModuleHomeController.java`
- `src/main/resources/templates/trainee/contents.html`
- `src/main/resources/templates/trainee/learning.html`
- `src/main/resources/templates/trainee/play-video.html`
- `src/main/resources/templates/trainee/play-document.html`
- `src/main/resources/templates/trainee/attendance.html`
- `src/main/resources/templates/trainee/completion-management.html`
- `src/main/resources/templates/admin/index.html`
- `src/main/resources/templates/instructor/index.html`
- 관련 자동 테스트 4개

## 의도적으로 제외한 범위

- LXP-003, 005, 009, 010, 013–021, 125, 127, 140–149
- AI 고도화, 커리어/보상, 감독·얼굴·OTP, 채팅, HRD 연계
- 일일 마감 정리 기능
- 신규 문제 풀이/코드 실행 백엔드

커밋, push, merge는 수행하지 않았다.
