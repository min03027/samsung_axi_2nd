# 공개 신청·사전상담 접수 및 관리자 처리 v1

## 1. 기존 구조

- 공개 `class/apply.html`, `campus/counsel.html`에는 입력 UI와 접수 완료 UI가 있었지만 서버 POST endpoint와 DB 저장은 없었다.
- 과정 신청 화면은 온라인 제출 버튼이 비활성화되어 있었고 상담 화면도 JS에서 제출을 중단했다.
- `Enrollment`는 로그인된 `TRAINEE User`와 `Course`를 연결하는 실제 수강 배정 모델이다. 공개 지원자는 아직 User/TRAINEE가 아니므로 지원서 저장에 재사용하지 않는다.
- 기존 `/admin/enrollments/pending`은 Enrollment 승인 화면이며 일부 적합도 표시는 mock 지원자 데이터다.
- 기존 `/admin/care/**`는 입학 후 학생 관리 IA이므로 입학 전 상담 데이터와 DB 모델을 공유하지 않는다.

## 2. 최종 데이터 모델

```text
Course ─ CoursePublication
  ├─ CourseApplication     공개 과정 지원서
  └─ ConsultationRequest   공개 사전상담 신청(과정 선택 상담은 Course nullable)

User (nullable matchedUser / assignedTo)
Enrollment (자동 생성하지 않음)
```

### CourseApplication

- 지원 과정, 접수번호, 이름, 생년월일, 이메일, 휴대전화
- 현재 상태, 희망 직무, 지원동기, 관련 경험, 보유 기술
- 내일배움카드, 기숙사 상담
- 처리 상태, 담당자, 처리 메모, 후속조치일, 최종 결과
- 개인정보 동의 시각·버전, 사실 확인 시각, 접수 시각
- 중복 후보와 정확히 일치한 기존 계정 연결

### ConsultationRequest

- 관심 과정(선택), 접수번호, 이름, 이메일, 휴대전화
- 상담 유형, 희망 날짜·시간, 연락 방법, 기숙사 관심, 문의 내용
- 상담 상태, 담당자, 상담 메모, 후속조치일, 최종 결과
- 개인정보 동의 시각·버전, 접수 시각
- 중복 후보와 정확히 일치한 기존 계정 연결

## 3. 공개 API

| Method | Route | 설명 |
|---|---|---|
| POST | `/v2/api/public/applications` | 공개 과정 지원서 접수 |
| POST | `/v2/api/public/consultations` | 공개 사전상담 접수 |
| GET | `/v2/api/public/consultations/courses/{courseId}` | 사전상담·모집중 공개 과정의 상담용 최소 정보 |

- 로그인 없이 JSON으로 제출한다.
- Bean Validation으로 필수값, 이메일, 휴대전화, 길이, 날짜, 동의를 검증한다.
- 서버가 `AXI-APP-*`, `AXI-CNS-*` 접수번호를 발급한다.
- 민감한 요청 본문은 로그로 기록하지 않는다.
- 공개 JSON endpoint만 CSRF 예외로 두며 관리자 처리 POST는 기존 CSRF 보호를 유지한다.

## 4. 개인정보

- 이메일·휴대전화는 기존 `CryptoConverter`로 AES-256-GCM 암호화 저장한다.
- 중복 판정에는 정규화한 연락처의 HMAC-SHA256 지문만 사용한다.
- 개인정보 수집·이용 동의가 없으면 서버가 접수를 거절한다.
- 동의 시각과 `PRIVACY-2026-08-V1` 버전을 저장한다.
- 마케팅 수신 동의는 현재 폼에 없으므로 임의로 추가하지 않았다.
- 보유기간 만료 자동 파기와 동의문 버전 관리 화면은 후속 범위다.

## 5. 신청 상태

`RECEIVED → REVIEWING / CONSULTATION_REQUIRED → APPROVED / ON_HOLD / REJECTED → REGISTERED`

- 운영 상황에 맞춰 관리자가 상태를 선택한다.
- `REGISTERED`는 승인 상태이면서 정확히 연결된 기존 계정이 있을 때만 선택할 수 있다.
- 승인 또는 등록 완료 표시만으로 User나 Enrollment를 생성하지 않는다.

## 6. 상담 상태

`RECEIVED → ASSIGNED → SCHEDULED → IN_PROGRESS → COMPLETED / FOLLOW_UP → CLOSED`

- 담당자, 처리 메모, 후속조치 예정일, 최종 결과를 함께 저장한다.
- 학생 다이어리와 분리되어 있으며 입학 후 이력 연결은 후속 범위다.

## 7. 관리자 화면

관리자 IA는 입학 후 수강생 케어가 아닌 `교육 운영 → 모집·신청 관리`에 배치했다.

- `/admin/admissions/applications`: 지원자 목록
- `/admin/admissions/applications/{id}`: 지원서 상세·상태·담당자·후속조치
- `/admin/admissions/consultations`: 상담 목록
- `/admin/admissions/consultations/{id}`: 상담 상세·상태·담당자·후속조치

강사는 지원자 개인정보 관리 화면에 접근할 수 없다.

## 8. Course 및 공개 정책

- 지원서는 `RecruitmentStatus.RECRUITING`, `publicVisible=true`, `PublicationSite.CLASS 또는 ALL` 과정만 접수한다.
- 과정 연결 상담은 `PRE_CONSULTATION` 또는 `RECRUITING`이며 공개된 과정만 접수한다.
- 과정 ID가 없는 일반 과정 선택 상담은 접수할 수 있다.
- 모집마감·진행중·종료·미공개 과정의 신규 지원/과정 연결 상담은 거절한다.
- 과정 상세의 `courseId`를 신청·상담 페이지까지 유지한다.

## 9. Enrollment와의 경계

- 공개 지원자는 `CourseApplication`이며 `User/TRAINEE`가 아니다.
- 관리자 승인도 선발 결과일 뿐 계정과 Enrollment를 자동 생성하지 않는다.
- 향후 `승인 → 계정 초대 → 회원가입/기존 계정 확인 → TRAINEE 승인 → Enrollment 생성` 절차를 별도 구현한다.
- 기존 `EnrollmentService`와 `/admin/enrollments/pending`은 변경하지 않았다.

## 10. 중복 및 기존 계정 확인

- 동일 과정의 이메일 또는 휴대전화 지문이 이미 있으면 중복 후보로 표시한다.
- 상담은 기존 상담 전체에서 같은 연락처가 있으면 확인 대상으로 표시한다.
- 기존 User와 이메일·휴대전화가 모두 일치하면 `matchedUser`로 연결한다.
- 한 항목만 일치하거나 기존 신청이 있으면 자동 병합하지 않고 관리자 확인 대상으로 남긴다.
- 현재 User 테이블에는 검색용 지문이 없어 제출 시 복호화 비교한다. 운영 규모 확장 전 User 연락처 지문 백필과 인덱스 도입이 필요하다.

## 11. 파일 및 스팸 방어

- 파일 업로드 인프라와 악성 파일 검사가 없어 v1에서는 첨부를 받지 않는다. 과정의 필수 서류 안내만 표시하고 접수 후 제출 방식을 안내한다.
- v1의 서버 방어는 타입·길이·형식·날짜·과정 상태 검증까지다.
- IP/연락처 기반 rate limit, CAPTCHA, WAF, 일회용 인증은 아직 구현하지 않았다. 운영 전 reverse proxy rate limit과 CAPTCHA를 우선 적용한다.

## 12. 테스트

- 공개 상담 정상 접수
- 공개 과정 신청 정상 접수
- 개인정보 미동의 거절
- 잘못된 courseId 거절
- 모집마감·미공개 과정 지원 거절
- 관리자 목록·상세 렌더링
- 신청·상담 상태 및 담당자 변경
- 동일 연락처 중복 후보 표시
- 강사 관리자 개인정보 화면 접근 거절
- Course CMS, 로그인/권한, Enrollment 회귀

최종 전체 Gradle 결과: 82 suites, 425 tests, failures 0, errors 0, skipped 0. `git diff --check`도 통과했다.

## 13. 향후 확장

1. 승인 지원자 계정 초대·기존 계정 확인 UI
2. 명시적 관리자 확인 후 Enrollment 생성
3. 파일 업로드·악성 파일 검사·보관 정책
4. User 연락처 지문 백필과 인덱스
5. 상담 이력의 입학 후 학생 프로필 연결
6. API rate limit·CAPTCHA·감사 로그
7. 개인정보 보유기간 만료 및 파기 배치
