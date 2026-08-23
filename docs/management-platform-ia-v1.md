# 통합 관리자 플랫폼 IA v1

기준일: 2026-08-23

## 원칙

- 강사와 관리자는 하나의 관리 플랫폼 IA와 공통 navigation을 사용한다.
- 실제 접근 권한과 데이터 범위는 기존 `ADMIN`, `INSTRUCTOR` 보안 경계와 서비스 범위를 유지한다.
- 기존 URL, POST 동작, DB, Service, Repository는 변경하지 않는다.
- 신규 B 화면에는 가짜 학생, 가짜 상담, 가짜 평가 수치를 넣지 않는다.
- 운영관리자와 최고관리자의 세부 구분은 다음 권한 매트릭스 단계에서 확정한다.

## 최종 상위 IA

| 상위 영역 | 목적 | 관리자 대표 경로 | 강사 대표 경로 |
|---|---|---|---|
| 대시보드 | 오늘 운영·담당 업무와 리스크 확인 | `/admin` | `/instructor` |
| 교육 운영 | 과정, 일정, 콘텐츠, 출결·이수 운영 | `/admin/courses` | `/instructor/courses` |
| 학습·평가 | 과제, 시험, 채점, 감독과 학습진단 | `/admin/evaluation/**` | `/instructor/assignments`, `/instructor/grading`, `/instructor/proctor/**` |
| 수강생 케어 | 학생 확인, 질문·튜터링, 다이어리와 후속조치 | `/admin/care` | `/instructor/care` |
| 소통·교육품질 | 공지·알림, 설문, 강의·강사평가와 품질 | `/admin/notice`, `/admin/quality` | `/instructor/notice`, `/instructor/quality` |
| 성과관리 | 이탈, 통계·리포트, 취업·사후관리 | `/admin/analytics/dropout` | 권한 매트릭스 확정 후 노출 |
| 시스템 | 가입승인, 계정·권한, 데이터·HRD, 설정·로그 | `/admin/users/**`, `/admin/admins` | 노출하지 않음 |

## 역할별 사용 범위

### 강사

- 본인 담당 과정, 학생, 일정, 콘텐츠
- 담당 과정 출결·이수
- 담당 과제·시험 채점, 피드백과 감독
- 담당 학생 Q&A·튜터링
- 담당 과정 공지와 교육품질 결과
- 학생 다이어리와 후속조치는 담당 학생 범위로 확장

### 운영관리자

현재 코드에는 별도 Role이 없으므로 `ADMIN` 범위에 포함된다.

- 전체 또는 배정된 복수 과정 운영
- 학생·강사·과정, 출결·이수, 평가, 상담, 공지·설문
- 통합 리포트, 취업·사후관리, 데이터 연계
- 실제 범위 제한 방식은 다음 권한 매트릭스에서 결정

### 최고관리자

- 현재 `Role.ADMIN`이며 `SuperAdminPolicy`가 기본 로그인 아이디 `admin`에 일부 계정정보 변경 권한을 추가한다.
- 전체 교육 품질, 관리자 계정·권한, 데이터·HRD, 설정·로그 사용
- 최고관리자 전용 메뉴 표시 여부와 API 권한은 다음 단계에서 결정

## 기존 Route 매핑

### 관리자

| 기능 | 기존 경로 | 상태 |
|---|---|---|
| 홈 대시보드 | `/admin` | A |
| 가입 승인 | `/admin/users/pending` 및 승인·반려 POST | A |
| 수강생·강사 관리 | `/admin/users/trainees`, `/admin/users/instructors`, 수정·상태·삭제·접속이력 | A |
| 관리자 계정 | `/admin/admins/**`, `/admin/accounts/{id}/credentials` | A |
| 과정 CRUD | `/admin/courses/**` | A |
| 과목·차시 | `/admin/courses/{courseId}/subjects/**`, `/sessions/**` | A |
| 강사 배정 | `/admin/courses/{courseId}/instructors/**` | A |
| 수강 승인 | `/admin/enrollments/**`, `/admin/courses/{courseId}/enrollments/**` | A |
| 일정 | `/admin/courses/schedule` | A |
| 콘텐츠 | `/instructor/contents/**` | A, 관리자도 접근 가능 |
| 문제은행 | `/admin/evaluation/questions/**` | A |
| 과제·제출·채점 | `/admin/evaluation/assignments/**` | A |
| 시험 | `/admin/evaluation/exams/**` | A |
| 시험 채점·성적 | `/admin/evaluation/grading/**` | A |
| 시험 감독·녹화 | `/admin/evaluation/monitoring/**` | A |
| 출결 | `/admin/attendance/**` | A |
| 이수·수료증 | `/admin/completion/**` | A |
| Q&A·튜터링 | `/admin/support/**` | A, 관리자·강사 공용 |
| 공지 | `/admin/notice/**` | A |
| 개인 알림 | `/admin/notice/alarms/**` | A |
| 리마인드 설정 | `/admin/settings/reminder` | A |
| 설문·결과 엑셀 | `/admin/survey/**` | A |
| 이탈 예측·분석 | `/admin/analytics/dropout` | A |
| 내 정보 | `/admin/my-info` 및 비밀번호 변경 | A |

### 강사

| 기능 | 기존 경로 | 상태 |
|---|---|---|
| 홈 대시보드 | `/instructor` | A |
| 담당 과정·상세 | `/instructor/courses`, `/instructor/courses/{id}` | A |
| 담당 학생 | `/instructor/trainees` | A |
| 일정 | `/instructor/scheduler` | A |
| 콘텐츠 CRUD | `/instructor/contents/**` | A |
| 과제 채점 | `/instructor/assignments/**` | A |
| 시험 채점·성적 | `/instructor/grading/**` | A |
| 시험 감독·녹화 | `/instructor/proctor/**` | A |
| 출결 | `/instructor/attendance` | A |
| 이수 | `/instructor/graduate` | A |
| 담당 과정 공지 | `/instructor/notice/**` | A |
| Q&A·튜터링 | `/admin/support/**` | A, 담당 범위 제한 |
| AI 학습진단 | `/instructor/ai/diagnosis` | A |
| 내 정보 | `/instructor/my-info` 및 비밀번호 변경 | A |

## A/B/C 분류

### A — 실제 기능 연결

- 위 관리자·강사 Route 표의 전체 기능
- 관리자 대시보드 전체 범위 집계
- 강사 대시보드 담당 과정 범위 집계
- 콘텐츠와 Q&A·튜터링 공용 기능
- 중요 공지 고정 및 수강생 로그인 팝업

### B — 화면 구조 확정, 저장 데이터 연결 전

- 수강생 케어 워크스페이스: `/admin/care`, `/instructor/care`
- 학생 다이어리: `/admin/care/diary`, `/instructor/care/diary`
- 상담·후속조치: `/admin/care/follow-ups`, `/instructor/care/follow-ups`
- 교육품질·강사평가: `/admin/quality`, `/instructor/quality`
- 통합 학생 상세: 케어 워크스페이스 안에 위치 확보
- 강사 상세 평가 추이: 교육품질 화면 안에 위치 확보

### C — IA에서만 위치 확보

- 콘텐츠 재사용, 버전관리, 수정 이력
- 코딩 테스트와 자동채점
- 학생 다이어리·후속조치 저장 및 변경 이력
- 강의평가·강사평가 저장·익명성·집계
- 긴급공지 실시간 전송, 예약, 확인 추적
- 취업·포트폴리오·기업·면접·사후관리
- 통합 통계와 Excel/PDF 리포트
- 고용24·HRD 업로드, 동기화, 검증과 오류관리
- 관리자 활동·변경·보안·접속 로그 통합
- AI 위험학생 탐지 고도화
- 웹캠·화면·OTP·안면인식 기반 감독

## 중복 정리 원칙

- 기존 관리자·강사 URL은 삭제하거나 리다이렉트하지 않는다.
- 공통 navigation만 하나로 합치고 각 역할의 기존 컨트롤러와 템플릿을 유지한다.
- 콘텐츠 `/instructor/contents/**`와 지원 `/admin/support/**`의 기존 공용 사용을 유지한다.
- 관리자와 강사에 중복된 과정·출결·평가 컨트롤러 통합은 세부 권한 매트릭스 이후 별도 단계에서 판단한다.
- 컨트롤러에 연결되지 않은 과거 정적 템플릿은 이번 단계에서 삭제하지 않는다.

## 다음 권한 매트릭스에서 결정할 항목

1. 운영관리자를 새 Role로 만들지, `ADMIN` 내부 권한 묶음으로 둘지
2. 최고관리자 판별을 로그인 아이디 정책으로 유지할지
3. 메뉴 표시 권한과 API 실행 권한을 어떤 단위로 나눌지
4. 과정·기수·반·학생별 접근 범위를 누가 배정하는지
5. 강사의 평가 생성·수정·공개 권한 범위
6. 학생 다이어리의 작성·열람·수정·삭제 및 민감 기록 권한
7. 후속조치 담당자 변경과 완료 승인 권한
8. 강사평가 익명성, 최소 응답 수와 공개 범위
9. 긴급공지 작성·승인·발송·확인 추적 권한
10. 리포트 다운로드와 개인정보 포함 범위
11. HRD 연계와 데이터 오류 수정 권한
12. 관리자 활동 로그 열람·보존·반출 권한
