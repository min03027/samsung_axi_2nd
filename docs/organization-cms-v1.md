# 기업·기관 통합 CMS v1

## 1. 범위

이 문서는 `공통-004 기업·기관 관리`와 `취업-015 기관·기업 롤링`의 1차 구현을 설명한다. 기업·기관 마스터를 한 번 등록해 홈페이지와 과정 프로젝트에서 재사용하되, 학생의 실제 취업 결과 및 외부 채용공고와는 분리한다.

## 2. 기존 기업 데이터 분석

| 기존 위치 | 구현 상태 | 문제 |
|---|---|---|
| `static/v2/site/campus/index.html` PARTNERS | 기관명 텍스트 칩 하드코딩 | 로고·설명·관계·노출 순서 관리 불가 |
| `static/v2/site/biz/index.html` CLIENTS | 익명 기업명 텍스트 칩 하드코딩 | 공개 계약 여부와 관리자 승인 흐름 없음 |
| `CoursePublication.projectPartners` | 과정마다 `TEXT` 자유입력 | 동일 기업 반복 및 표기 불일치 가능 |
| `JobPosting.companyName` | 외부 채용공고 원본 문자열 | 파트너 관계나 학생 취업 결과가 아님 |
| 수강생 취업 화면 | 정보 구조만 존재 | 실제 취업 성과 엔티티로 사용하지 않음 |

기존 코드에는 기업 로고 저장소나 기업 마스터 엔티티가 없었다. `JobPosting`은 수집된 공고의 근거 보존용이므로 기업 마스터로 변경하지 않았다.

## 3. 최종 데이터 모델

### PartnerOrganization

기업·공공기관·교육기관·협회·파트너를 함께 관리하는 공통 마스터다.

- 기본정보: 기관명, 정규화명, 유형, 한줄/상세 설명, 로고 URL, 홈페이지 URL·도메인
- 관계: 복수 관계유형
- 홈페이지: 공개 여부, 복수 노출 사이트, 복수 노출 위치, 정렬 순서
- 내부관리: 관리자 메모, 사용 상태
- 중복 방지: 정규화된 기관명과 홈페이지 도메인에 유니크 제약 적용

### CoursePartner

`Course`와 `PartnerOrganization` 사이의 연결 엔티티다.

- 한 과정과 한 기관의 조합은 한 건만 존재한다.
- `projectParticipant`: 프로젝트 참여사 여부
- `recruitmentLinked`: 채용 연계 여부
- 같은 기관을 여러 과정에서 재사용할 수 있다.

`CoursePublication.projectPartners`는 기존 데이터 손실을 막기 위해 제거하지 않았다. 구조화 연결이 있으면 공개 과정 DTO에서 연결 기관명을 우선 사용하고, 없을 때만 기존 문자열을 fallback으로 사용한다.

## 4. 기업·기관 유형

- 기업
- 공공기관
- 교육기관
- 협회·단체
- 파트너
- 기타

기업과 기관을 별도 테이블로 나누지 않아 같은 CMS와 공개 API를 사용한다.

## 5. 관계 유형

- 협약
- 라이선스
- 교육
- 채용
- 발표회
- 입주
- 공공기관

`@ElementCollection`의 enum 집합으로 저장해 한 기관에 여러 관계 유형을 부여한다.

## 6. 관리자 UI

사이드바 `사이트 운영 > 기업·기관 관리`에서 ADMIN만 접근한다.

- 목록, 기관명·설명·도메인 검색
- 기관 유형·관계 유형·상태·홈페이지 공개 여부 필터
- 등록, 수정, 상세보기
- 관계 유형 복수 선택
- 프로젝트 참여 과정과 채용 연계 과정을 별도 복수 선택
- 공개/비공개, 노출 사이트·위치, 정렬 순서 설정
- 내부 메모와 상태 관리

삭제는 1차 범위에서 제공하지 않는다. 공개 중지나 이력 보존은 `INACTIVE`, `ARCHIVED` 상태를 사용한다.

## 7. 홈페이지 연동

공개 조건은 다음과 같다.

```text
status = ACTIVE
AND homepageExposure = true
AND 요청 site 포함
AND 요청 position 포함
```

공개 API 결과는 `displayOrder`, 기관명 순으로 반환한다. 내부 메모와 과정 연결 정보는 공개 DTO에 포함하지 않는다.

- 취업캠퍼스: `CAMPUS + PARTNER_ROLLING`
- 기업교육: `BIZ + CLIENT_ROLLING`

두 홈페이지의 하드코딩 기업명은 제거하고 `public-organization-cms.js`가 공개 API를 호출한다. 로고가 없거나 이미지 로딩에 실패하면 기관명 텍스트로 대체한다.

## 8. 과정 연결

과정 등록·수정 화면의 `기업 프로젝트 참여사`는 기업·기관 마스터 복수 선택으로 변경했다. 저장 시 프로젝트 관계만 동기화하며, 같은 연결의 채용 연계 표시는 보존한다.

공개 과정 응답의 `projectPartners`는 다음 우선순위를 사용한다.

1. `CoursePartner.projectParticipant = true`인 기관명
2. 기존 `CoursePublication.projectPartners` 자유입력 값

이 방식으로 기존 공개 과정 데이터는 즉시 유실되지 않으며 관리자가 단계적으로 마스터에 연결할 수 있다.

## 9. 취업 데이터와의 경계

- `PartnerOrganization`: 협약·교육·프로젝트·채용 연계 관계
- `CoursePartner.recruitmentLinked`: 과정 단위 채용 연계 가능성
- `JobPosting`: 외부에서 수집한 개별 채용공고 원본
- 학생 실제 취업 결과: 향후 별도 취업/성과 엔티티로 구현

`PartnerOrganization`이나 `recruitmentLinked`만으로 학생이 해당 기업에 취업했다고 판단하지 않는다. 이번 작업은 `JobPosting`, 로드맵, 학생 취업 기능을 변경하지 않았다.

## 10. 이미지 처리

운영 파일 스토리지 전략이 확정되지 않아 서버 로컬 업로드 디렉터리를 새 영구 구조로 만들지 않았다. v1은 `http://` 또는 `https://` 로고 URL을 저장한다.

- URL이 없으면 기관명 표시
- 이미지 요청 실패 시 기관명 fallback
- 향후 object storage/CDN 도입 시 `logoUrl` 생성 단계만 교체 가능

실명 및 로고 공개 전에는 계약·사용권과 노출 범위를 운영자가 확인해야 한다.

## 11. API와 route

| 구분 | Method | Route | 권한/용도 |
|---|---|---|---|
| 관리자 목록 | GET | `/admin/organizations` | ADMIN, 검색·필터 |
| 관리자 등록 | GET/POST | `/admin/organizations/new`, `/admin/organizations` | ADMIN |
| 관리자 상세 | GET | `/admin/organizations/{id}` | ADMIN |
| 관리자 수정 | GET/POST | `/admin/organizations/{id}/edit`, `/admin/organizations/{id}` | ADMIN |
| 공개 목록 | GET | `/v2/api/organizations?site=...&position=...` | 비로그인 공개 |
| 공개 화면 | GET | `/v2/site/campus/index.html`, `/v2/site/biz/index.html` | 동적 롤링 |

## 12. 테스트

`OrganizationCmsFlowTest`에서 다음을 검증한다.

- 기업·기관 등록과 수정
- 관계 유형 복수 선택
- 프로젝트·채용 연계 과정 구분
- 공개/비공개, 사용 중지 제외
- 사이트·노출 위치 필터와 관리자 정렬
- 기관명·홈페이지 도메인 중복 차단
- 과정 CMS 선택형 연결과 공개 과정 DTO
- 강사 관리자 route 접근 차단
- 홈페이지 정적 기업명 제거 및 CMS 영역 연결

기존 `CourseCmsFlowTest`, `AdmissionFlowTest`와 전체 Gradle 테스트로 회귀를 확인한다.

## 13. 향후 확장

1. 실제 운영 기관·기업과 승인된 로고 데이터 등록
2. object storage/CDN 기반 로고 업로드
3. 사업자번호 또는 공공기관 식별자 기반 중복 보강
4. 학생 취업 결과 엔티티와 기업 마스터의 선택적 참조
5. 기업교육 문의·채용연계·발표회 모듈의 조직 마스터 재사용
6. `ddl-auto=update`에서 Flyway 마이그레이션으로 전환
