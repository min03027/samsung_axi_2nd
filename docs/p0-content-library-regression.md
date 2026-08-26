# P0-03 공용 콘텐츠 라이브러리·버전·동기화 회귀 검증

## 체크리스트 요구

- 기준 문서: `참고문서/체크리스트/구현체크리스트_2차_20260822_정리본.xlsx`
- 기준 행: `LXP 잔여 작업`의 `공용 콘텐츠 라이브러리·버전·동기화`
- 관련 기능: LXP-027, LXP-030, LXP-031, LXP-032, LXP-033, LXP-034
- 요구 흐름: 원본 등록 → 과정 배치 → 새 버전 → 연결 과정 반영
- 기존 판정: 구현

| 기능 ID | 원 요구 | 현재 구현 |
|---|---|---|
| LXP-027 | 공용 원본 등록·검색·관리 | Item 저장과 목록/등록/상세 화면 |
| LXP-030 | 원본 수정·파일 교체·새 버전 | 원본 publish와 선택적 파일 교체 |
| LXP-031 | 과거 버전 보존 | Version 불변 스냅샷 |
| LXP-032 | 과정별 적용 버전·동기화 시각 | Link의 appliedVersion, lastSyncedAt, updateAvailable |
| LXP-033 | 자동 동기화 | PUBLISHED 발행 시 autoSync 연결 갱신 |
| LXP-034 | 버전 이력 | 원본+버전 번호 고유 제약과 이력 화면 |

## 기존 구조

### Library Item

- Entity/Table: `ContentLibraryItem` / `content_library_item`
- 최신 원본 메타데이터, 파일 참조, 상태와 `currentVersion`을 보유한다.
- 원본 수정은 같은 Item의 현재 상태를 갱신하면서 revision 정수를 1 증가시킨다.

### Version

- Entity/Table: `ContentLibraryVersion` / `content_library_version`
- 각 발행 시점의 제목, 설명, 파일 참조, 학습 메타데이터, 변경 요약을 스냅샷으로 보존한다.
- `(library_item_id, version_no)` 고유 제약으로 중복 버전을 막는다.
- `createdAt`과 감사 필드 `createdBy`를 상속한다. 로그인 감사 컨텍스트가 없으면 작성자 값은 비어 있을 수 있다.

### Course Link

- Entity/Table: `ContentLibraryLink` / `content_library_link`
- Library Item과 수강생이 보는 실제 `Content`를 연결한다.
- `content_id` 고유 제약, `autoSync`, `appliedVersion`, `lastSyncedAt`을 보유한다.
- 동기화는 Content 행을 새로 만들지 않고 기존 Content의 메타데이터와 파일 참조만 갱신한다.

## 실제 E2E 흐름

`P0ContentLibraryRegressionIntegrationTest`가 demo 프로필과 명시적 demo seed ON 조건에서 다음 흐름을 하나의 트랜잭션 테스트로 검증한다.

1. Demo Instructor와 `DEMO-AI-DATA-001`을 실제 Repository에서 조회한다.
2. 자체 제작 텍스트 파일 `[DEMO] 데이터 전처리 가이드`를 원본 v1로 등록한다.
3. Item과 v1을 flush/clear 후 DB에서 다시 조회한다.
4. 자동 동기화를 끈 상태로 Demo Course에 배치하고 Content와 Link를 재조회한다.
5. 같은 과정·차시에 재배치하면 명시적 validation error가 발생하며 Content와 Link 수가 늘지 않는지 확인한다.
6. Demo Trainee의 새 Content 진도를 완료 상태로 만든다.
7. 원본 제목과 설명을 수정하고 `전처리 실습 설명 및 예제 수정` 요약으로 v2를 발행한다.
8. 수동 연결이 v1을 유지하면서 `updateAvailable=true`인지 확인한다.
9. 최신 버전을 적용하고 같은 v2를 한 번 더 적용한다.
10. Link는 v2, Content는 최신 제목을 사용하며 Content/Link/Version 수는 증가하지 않는지 확인한다.
11. 기존 Progress ID, 완료 상태와 100% 진도가 보존되는지 확인한다.
12. Demo Trainee의 실제 학습 콘텐츠 조회에서 최신 제목이 반환되는지 확인한다.

## Demo 데이터

- Instructor: `demo_instructor`
- Trainee: `demo_trainee`
- Admin: `demo_admin`
- Course code: `DEMO-AI-DATA-001`
- 기존 Course Content: P0-02가 만든 문서형 콘텐츠 2개
- 회귀 검증 원본: `[DEMO] 데이터 전처리 가이드` (테스트 트랜잭션에서 생성하고 롤백)
- 업로드 경로: 운영 저장소가 아닌 OS 임시 디렉터리

P0-03은 demo initializer에 영구 Library Item을 추가하지 않는다. 다음 P0에서 필요하면 동일한 테스트 fixture를 재사용할 수 있다.

## Route/API

| Action | Route | Service | DB |
|---|---|---|---|
| 목록/검색 | `GET /instructor/content-library` | `list`, `dashboard` | Item, Link, Version 조회 |
| 원본 등록 | `POST /instructor/content-library` | `create` | Item + v1 |
| 상세 | `GET /instructor/content-library/{id}` | `view`, `versions`, `links` | Item + Version + Link |
| 새 버전 발행 | `POST /instructor/content-library/{id}` | `publish` | Item 갱신 + Version 추가 + 선택적 자동 동기화 |
| 과정 배치 | `POST /instructor/content-library/{id}/deploy` | `deploy` | Content + Link |
| 수동 반영 | `POST /instructor/content-library/{id}/links/{linkId}/sync` | `syncNow` | 기존 Content + Link 갱신 |
| 자동 반영 설정 | `POST /instructor/content-library/{id}/links/{linkId}/auto-sync` | `changeAutoSync` | Link 설정, 필요 시 즉시 갱신 |
| 수강생 조회 | `GET /trainee/contents` | `myLearningContents` | 승인/수료 과정의 ACTIVE Content + Progress |

별도 JSON API가 아니라 Spring MVC form submit과 서버 렌더링 화면으로 구성되어 있다. 성공은 redirect, validation 실패는 같은 form 화면, 권한 위반은 Spring Security의 접근 거부로 처리한다.

## DB 관계

```text
ContentLibraryItem 1 ── N ContentLibraryVersion
ContentLibraryItem 1 ── N ContentLibraryLink 1 ── 1 Content
User 1 ── N Progress N ── 1 Content
Course 1 ── N Content
```

- Link가 배치 Content를 참조하므로 원본과 과정 배치 관계를 추적할 수 있다.
- Progress는 Link나 Version이 아니라 Content ID를 참조한다.
- 동기화가 Content ID를 유지하므로 기존 Progress가 삭제·중복되지 않는다.

## 버전 정책

- semantic version 대신 1부터 증가하는 revision 정수를 사용한다.
- 새 발행은 기존 Version 행을 수정하지 않고 새 스냅샷을 추가한다.
- 교체 파일이 없으면 최신 원본은 이전 파일 참조를 유지하며 v1 스냅샷도 그대로 남는다.
- 변경 요약은 `changeSummary` 필드를 재사용한다.

## 동기화 정책

- `autoSync=true`: PUBLISHED 새 버전 발행 트랜잭션에서 연결된 모든 Content를 갱신한다.
- `autoSync=false`: Link의 `appliedVersion`이 남아 상세 화면에 업데이트 필요 상태를 표시하고 강사가 수동 적용한다.
- 수동 재적용은 기존 Content와 Link를 갱신할 뿐 새 행이나 Version을 만들지 않는다.
- 등록, 배치, 발행, 수동 적용은 각각 Service의 `@Transactional` 범위에서 처리된다.

## 권한

감사에서 과정 종속 작업에 담당 강사 검사가 없고 배치 화면에 전체 과정이 노출되는 연결 공백을 발견했다. 프로젝트의 과정 템플릿과 콘텐츠 요청 처리 정책을 기준으로 다음 경계를 적용했다.

- ADMIN: 전체 과정 선택·배치·동기화 가능
- INSTRUCTOR: 전체 공용 라이브러리 조회·원본 등록 가능, 담당 Course에만 기존 콘텐츠 승격·배치·수동 동기화·자동 동기화 설정 가능
- 배치 화면의 Course와 Session 선택지도 담당 과정으로 제한
- 콘텐츠 요청 제공 흐름도 같은 actor를 Library Service에 전달

## 테스트

### 기존 회귀

- `ContentLibraryServiceTest`
- `ContentLibraryRenderTest`
- `ContentRenderTest`
- `ContentRequestServiceTest`
- `LxpContentOperationsRenderTest`
- `P0DemoDataEnabledIntegrationTest`
- `P0DemoDataDisabledIntegrationTest`

### 신규 E2E

- `P0ContentLibraryRegressionIntegrationTest`
  - DB 원본 등록과 재조회
  - Course 배치와 Link 저장
  - 중복 배치 차단
  - v1/v2 이력과 변경 요약
  - 수동 updateAvailable 상태
  - 최신 버전 적용과 재적용 멱등성
  - 기존 Progress 보존
  - Demo Trainee 최신 Content 조회
  - 담당/비담당 Course 권한
  - Demo Instructor/Trainee 실제 로그인과 화면 HTTP 200

### 전체 회귀 결과

- 총 793 tests
- failures: 0
- errors: 0
- skipped: 0
- `BUILD SUCCESSFUL`

## 브라우저 QA

- demo/H2 서버는 포트 18080에서 기동에 성공했다.
- 실제 HTTP form login과 역할별 redirect를 검증했다.
- Demo Instructor의 `/instructor/content-library`: HTTP 200
- Demo Trainee의 `/trainee/contents`: HTTP 200
- 현재 Codex 세션에서 연결 가능한 브라우저가 0개여서 1440px/390px 육안 QA는 수행하지 못했다.
- 브라우저 미검수는 코드 구현 판정과 분리한다.

## 발견한 회귀

- Library Domain과 원본→배치→버전→동기화 흐름 자체의 회귀는 없었다.
- 과정 종속 작업의 강사 담당 범위 검사가 빠진 권한 연결 공백이 있었다.
- 라이브러리 화면에는 DB가 비었을 때 실데이터처럼 보이는 sample fallback이 없다.

## 수정 사항

- Library Service의 과정 선택지와 과정 종속 쓰기 작업에 actor 기반 접근 검사를 추가했다.
- Library Controller와 콘텐츠 요청 흐름에서 현재 로그인 사용자를 Service로 전달했다.
- 기존 테스트 호출을 명시적 Admin actor 사용으로 갱신했다.
- P0 요구 16개 항목을 포괄하는 demo/H2 통합 회귀 테스트를 추가했다.

## 무결성 및 삭제 정책

- Version은 Item FK와 item/version 고유 제약을 사용한다.
- Link는 Item FK, Content FK와 content 고유 제약을 사용한다.
- 동일 Item·Course·Session 배치는 Service validation으로 차단한다.
- Item은 soft delete이며 Version/Link에 cascade delete가 선언되어 있지 않다.
- 현재 사용자 경로는 Item 물리 삭제 대신 ARCHIVED 상태 변경을 사용하므로 운영 Course Content와 Progress를 연쇄 삭제하지 않는다.
- 이번 작업은 삭제 workflow를 추가하거나 기존 Course/Content 데이터를 초기화하지 않았다.

## 남은 범위

- 실제 브라우저 1440px 및 390px 육안 QA
- 실제 브라우저에서 파일 선택을 포함한 전체 form submit 시연
- P0-04에서 재사용할 Library Content는 현재 테스트 fixture로 재현 가능하지만 demo initializer에는 상시 seed하지 않음

## 판정

- CODE_STATUS: IMPLEMENTED
- P0_STATUS: PARTIAL (실제 브라우저 육안 QA만 남음)
