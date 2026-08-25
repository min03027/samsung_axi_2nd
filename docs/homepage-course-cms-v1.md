# 홈페이지 과정 CMS v1

## 1. 기존 구조 분석

- `Course`는 과정 코드·과정명뿐 아니라 `cohort`, 교육 시작/종료일, 정원과 운영 상태를 가진다. 현재 구조상 순수 과정 마스터가 아니라 **과정+모집 회차(Offering)** 역할이다.
- 별도 Cohort/기수 엔티티는 없다. 기수는 `Course.cohort` 문자열이다.
- `Subject → Session`이 실제 커리큘럼과 차시를 관리한다.
- `CourseInstructor`가 실제 INSTRUCTOR 계정과 과정의 담당 관계를 관리한다.
- `Enrollment`가 수강신청과 승인 상태를 관리하며 `CourseStatus.RECRUITING`인 과정만 신청을 허용한다.
- 관리자 `/admin/courses`에 기존 CRUD, 커리큘럼, 강사 배정, 신청 승인 기능이 이미 있다.
- 공개 홈페이지의 과정 목록·상세·신청 연결은 `static/v2` HTML, `course-data.js`, `course-filter.js`의 정적 값이었다. 관리자 DB와 연결되지 않았다.
- local은 Hibernate `create-drop`, dev/prod는 `ddl-auto:update`를 사용한다. Flyway/Liquibase는 아직 없다.

## 2. 재사용한 모델

- 과정/모집 회차 원본: `Course`
- 주차별 교육 구성: `Subject`, `Session`
- 담당 교사: `CourseInstructor`
- 실제 수강신청: `Enrollment`
- 기존 관리자 보안 경계: `/admin/**`의 `ADMIN`

동일 개념의 Course Entity를 만들지 않았다. 관리자는 기존 과정 화면에서 한 번만 등록한다.

## 3. 신규·변경 모델

### 신규 `CoursePublication`

`Course`와 1:1로 연결되는 공개·모집 정보다. 내부 운영 정보와 공개 가능한 정보의 경계를 만들되 과정을 중복 등록하지 않는다.

- 기본 공개 정보: 한줄소개, 교육 대상, 선수지식
- 모집 정보: 공개 모집 상태, 모집 시작일, 신청 마감일, 상담일, 발표일
- 선발 정보: 선발 절차, 필수 서류
- 교육 공개 정보: 교육 시간, 교육 방법
- 비용: 수강료, 본인부담금, 정부지원금, 추가 비용
- 강사진/프로젝트: 공개 멘토 소개, 참여사, 데모 URL
- 노출 제어: 노출 여부, 노출 사이트, 공개 분류, 정렬 순서, 대표 과정

### 기존 `CourseStatus` 확장

기존 `CLOSED(폐강)`의 의미를 바꾸지 않고 `RECRUITMENT_CLOSED(모집마감)`를 추가했다. 기존 데이터의 의미가 뒤집히는 것을 막기 위한 호환 조치다.

### 장기 모델 판단

같은 과정이 여러 기수로 반복되면 장기적으로 `CourseMaster + CourseOffering` 분리가 적절하다. 하지만 현재 모든 시험·과제·출결·Enrollment가 `Course`를 직접 참조하므로 v1에서 분리하면 회귀 범위가 지나치게 커진다. v1은 기존 Course를 회차 원본으로 유지하고 공개 프로필만 분리한다.

## 4. 과정 필드의 공개/내부 구분

| 구분 | 필드/모델 | 공개 여부 |
| --- | --- | --- |
| 내부 식별 | Course ID, courseCode, completionProgressRate | INTERNAL |
| 공통 운영 | courseName, cohort, category, startDate, endDate, capacity | 필요한 값만 PUBLIC |
| 커리큘럼 | Subject, Session | PUBLIC DTO로 필요한 항목만 전달 |
| 담당 교사 | CourseInstructor | 강사 이름만 PUBLIC |
| 공개 프로필 | CoursePublication | PUBLIC 대상 |
| 수강신청/승인 | Enrollment | INTERNAL |

복잡한 field-level permission은 만들지 않고 공개 API DTO에 허용 필드만 명시하는 방식으로 경계를 둔다.

## 5. 모집 상태와 전환 규칙

공개 모집 상태 `RecruitmentStatus`:

`PRE_CONSULTATION → RECRUITING → CLOSED → IN_PROGRESS → COMPLETED`

- 사전 상담 → 모집중만 준비도 검증을 수행한다.
- 모든 전환은 바로 다음 상태로만 가능하다.
- 모집중 전환 시 내부 `CourseStatus`도 `RECRUITING`으로 맞춘다.
- 모집마감은 `CourseStatus.RECRUITMENT_CLOSED`, 진행중은 `IN_PROGRESS`, 종료는 `COMPLETED`로 맞춘다.
- 기존 폐강 `CourseStatus.CLOSED`는 별도 의미로 보존한다.

## 6. 모집중 전환 필수 검증

- 과정명
- 과정 한줄소개
- 교육 대상
- 모집 시작일
- 신청 마감일
- 모집 시작일 ≤ 신청 마감일 ≤ 교육 시작일
- 교육 시작일/종료일
- 모집 정원
- 교육 시간
- 교육 방법
- 수강료/본인부담금/정부지원금/추가 비용(무료도 0 입력)
- 홈페이지 노출 사이트
- 홈페이지 공개 과정 분류
- 실제 담당 교사 `CourseInstructor` 1명 이상

신청·상담 URL은 시스템의 기존 경로에서 courseId로 생성하므로 별도 필수 문자열로 중복 저장하지 않는다. 누락 시 상태를 유지하고 상세 화면에 누락 항목을 표시한다.

## 7. 홈페이지 노출 규칙

몰입클라쓰 공개 목록 조건:

```text
CoursePublication.recruitmentStatus = RECRUITING
AND CoursePublication.publicVisible = true
AND publicationSite IN (CLASS, ALL)
```

- 대표 과정 우선, 정렬 순서, 교육 시작일 순으로 정렬한다.
- 비노출, 사전 상담, 모집마감, 진행중, 종료 과정은 공개 모집 API에서 제외한다.
- 삭제된 Course는 기존 soft delete 제약을 그대로 적용받는다.

## 8. 관리자 화면

기존 `/admin/courses/new`, `/admin/courses/{id}/edit`, `/admin/courses/{id}`를 재사용한다.

등록/수정 폼 구역:

1. 기본 정보
2. 모집 일정
3. 선발 정보
4. 교육 운영
5. 비용·지원
6. 강사진
7. 프로젝트·성과
8. 홈페이지 노출
9. 과정 상태

상세 상단에는 현재 모집 상태, 홈페이지 노출 여부, 준비도와 다음 상태 전환 버튼을 표시한다. 강사는 기존 `/admin/**` 보안 경계에 따라 접근할 수 없다.

## 9. 공개 데이터 매핑

| 관리자 값 | 공개 화면 |
| --- | --- |
| Course.courseName | 과정 카드/상세 제목 |
| oneLineIntroduction | 카드 요약, 상세 Hero |
| RecruitmentStatus | 모집 배지 |
| applicationDeadline | D-day |
| Course.startDate/endDate | 교육 기간 |
| educationTime/method | 주요 정보 |
| 비용 4종 | 비용·지원 영역 |
| CourseInstructor 이름 | 담당 교사 |
| Course.capacity | 모집 정원 |
| Subject/Session | 상세 커리큘럼 |
| projectPartners/demoUrl/mentors | 프로젝트·성과 영역 |

기존 랜딩의 Hero, 색상, 카드 클래스와 레이아웃은 유지하고 목록 컨테이너와 상세 텍스트만 공개 API 응답으로 치환한다.

## 10. Route/API

### 관리자

- `GET /admin/courses/new`
- `POST /admin/courses`
- `GET /admin/courses/{id}/edit`
- `POST /admin/courses/{id}`
- `GET /admin/courses/{id}`
- `POST /admin/courses/{id}/publication/status?status=...`

### 공개 읽기 전용

- `GET /v2/api/courses`
- `GET /v2/api/courses/{courseId}`

`/v2/**`는 기존 Security 설정에서 공개 경로다. 쓰기 API는 만들지 않았다.

### 공개 화면 연결

- 목록: `/v2/site/class/index.html`
- 상세: `/v2/site/class/course.html?courseId={id}`
- 신청: `/v2/site/class/apply.html?courseId={id}`
- 상담: `/v2/site/campus/counsel.html?courseId={id}`

신청/상담은 과정명을 연결하지만 실제 접수 저장은 이번 과정 CMS 범위가 아니므로 기존처럼 준비 중 상태를 유지한다.

## 11. 테스트 범위

- 과정 등록과 공개 프로필 동시 저장
- 과정/공개 프로필 수정
- 신규 공개 프로필의 사전 상담 기본 상태
- 필수값 누락 시 모집중 전환 거절
- 정상 입력과 담당 교사 배정 후 모집중 전환
- 모집중 → 모집마감 순차 전환
- 비노출/모집마감 과정 공개 API 제외
- 모집중+노출 ON 과정 공개 API 표시
- 강사의 관리자 모집 상태 변경 403
- 기존 관리자 Course CRUD와 공개 랜딩 렌더링 회귀
- 전체 Gradle 테스트

최종 실행 결과(2026-08-23):

- `./gradlew.bat test --rerun-tasks --no-daemon`
- 테스트 스위트 81개, 테스트 417개
- 실패 0, 오류 0, 건너뜀 0
- 공개 과정 JavaScript 3개 문법 검사 통과
- 인앱 브라우저 연결 대상이 없어 실제 1440px/390px 시각·Console·Network 검수는 수동 확인 대상으로 남김

## 12. 향후 확장

1. Flyway 도입 후 `CourseMaster + CourseOffering` 정규화 여부 결정
2. 실제 신청/상담 저장과 담당자 처리 흐름 연결
3. CAMPUS 사이트의 공개 과정 목록 연결
4. 대표 이미지/SEO/공개 미리보기 및 예약 공개
5. 후기 관리 CMS
6. 기업·기관 관리 CMS

후속 우선순위는 **후기 관리보다 기업·기관 관리가 먼저**다. 과정 CMS의 공개 과정과 기업 프로젝트 참여사/채용·협력 기관을 구조적으로 연결한 뒤 후기 공개 동의와 승인 흐름을 붙이는 편이 중복 데이터가 적다.
