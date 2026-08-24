# team/main 통합 보고서 (2026-08-24)

## 1. 병합 전 우리 HEAD

- 작업 브랜치: `integration-trainee-management-20260823`
- 병합 전 기준 HEAD: `859e9040f29c7803958ae88433b4efd5af47acd3`
- 로컬 참고자료 `outputs/`, `참고문서/`는 추적하지 않았고 그대로 보존했다.
- `main` 전환, `main` 수정, push, rebase, force 작업은 수행하지 않았다.

## 2. checkpoint commit

- 커밋: `5c50c43fbfdfc474d149dfd409d5e157841c2bfb`
- 메시지: `feat: checkpoint integrated cms and trainee lxp work`
- 132개 프로젝트 파일, 7,031줄 추가를 안전하게 저장했다.
- 수강생 UX/IA, 관리자 IA, 과정 공개 CMS, 신청·상담, 기업·기관 CMS, 후기 CMS와 관련 테스트를 포함한다.

## 3. team/main HEAD

- remote: `team = https://github.com/min03027/samsung_axi_2nd.git`
- fetch 후 HEAD: `8153befa9563f786ce52ef1e20aa2b847bf62a3c`
- 공통 merge-base: `859e9040f29c7803958ae88433b4efd5af47acd3`
- merge-base 이후 team/main 신규 커밋: 48개

## 4. team 신규 변경

48개 신규 커밋은 모두 2026-08-24 김지민 작성으로 확인했다. 주요 흐름은 다음과 같다.

- 취업캠퍼스 구조, 성과·파트너·시설·지원 UI와 공개 내비게이션
- 실제 수료생 인터뷰 27건, 카드·필터·상세·관련 과정 연결
- 몰입클라쓰의 과정 탐색, 추천, 신청 사용자 여정
- 비즈워크래프트의 조직 유형별 프로그램·사례·AX 업무 활용 흐름
- 공개 LXP 진입 화면과 정적 자산 운영/배포 구조

최신 커밋은 `8153bef feat: AX 실행 구조와 업무 활용 흐름 추가`이며, 취업캠퍼스와 공개 콘텐츠는 MIN 구현을 source of truth로 판단했다.

## 5. 실제 conflict 파일

미해결 충돌은 0개다. 실제 수동 해결 대상은 다음 12개였다.

1. `src/main/resources/static/v2/assets/application-flow.js`
2. `src/main/resources/static/v2/assets/components.css`
3. `src/main/resources/static/v2/assets/course-filter.js`
4. `src/main/resources/static/v2/assets/shell.js` → `page-section-navigation.js` rename 충돌
5. `src/main/resources/static/v2/index.html`
6. `src/main/resources/static/v2/site/biz/index.html`
7. `src/main/resources/static/v2/site/campus/counsel.html`
8. `src/main/resources/static/v2/site/campus/index.html`
9. `src/main/resources/static/v2/site/campus/reviews.html` add/add 충돌
10. `src/main/resources/static/v2/site/class/apply.html`
11. `src/main/resources/static/v2/site/class/course.html`
12. `src/main/resources/static/v2/site/class/index.html`

## 6. 충돌 해결 원칙

- MIN 취업캠퍼스·인터뷰·비즈 공개 화면은 team/main을 우선했다.
- 수강생 `/trainee`, 관리자 IA, CoursePublication, 신청·상담, 기업·기관·일반 후기 CMS는 checkpoint 구현을 보존했다.
- 신청·상담 화면은 팀 UI를 유지하면서 실제 `/v2/api/public/**` 서버 API와 실제 접수번호를 사용하도록 결합했다.
- 공통 내비게이션은 팀의 `page-section-navigation.js` rename을 채택하고 양쪽 메뉴 경로를 합쳤다.
- 29개 팀 원본 텍스트 자산은 내용 변경 없이 CRLF만 LF로 정규화했고 `git diff --cached --check`를 통과시켰다.

## 7. MIN 취업 코드 보존 여부

보존했다. 취업캠퍼스, 파트너 롤링, 성과 표현, 27개 인터뷰 디렉터리·필터·상세, 관련 과정 연결과 시설/지원 페이지는 team/main 버전을 선택했다. 우리 CMS가 MIN 공개 취업 페이지를 대체하지 않는다.

## 8. 다른 팀원 LXP 코드 보존 여부

team/main의 공개 LXP 진입 화면과 정적 진입 자산을 유지했다. 동시에 우리 `/trainee` 실제 컨트롤러·템플릿·데이터 대시보드, 학습·출결·이수 흐름은 삭제하거나 정적 화면으로 교체하지 않았다. 다른 팀원 담당 기능 ID의 백엔드 구현 파일은 충돌 대상이 아니었고 전체 회귀 테스트로 유지 여부를 확인했다.

## 9. 우리 기능 보존 여부

다음을 유지했다.

- 수강생 홈 UX/IA, 실제 데이터 대시보드, 이어서 학습, 출결, 이수, 진도 보고서
- 관리자 통합 IA와 학생 케어·다이어리·follow-up 구조
- `CoursePublication` 기반 과정 CMS와 모집 상태/공개 API
- `CourseApplication`, `ConsultationRequest` 실제 접수 및 관리자 처리
- `PartnerOrganization`, `CoursePartner` 구조형 CMS
- `StudentReview` 동의·노출·과정 연결 기반 일반 후기 CMS

## 10. 삭제/통합한 중복 기능

- 팀 신청 폼의 로컬 전용 접수 흐름은 실제 서버 접수 API로 통합했다.
- 팀 상담 폼의 로컬 생성 접수번호는 실제 서버 접수번호로 교체했다.
- `shell.js`는 팀 rename 결과인 `page-section-navigation.js` 하나로 통합하고 참조를 갱신했다.
- 공개 취업 인터뷰는 MIN의 정적 편집 콘텐츠를 사용한다. `StudentReview`는 과정 연결·동의 관리가 필요한 일반 후기 CMS로 범위를 분리해 동일 수료생 데이터를 이중 저장하지 않는다.
- 팀 파트너 로고/마케팅 표시는 MIN 공개 화면이 담당하고, `PartnerOrganization`은 과정 연결과 관리자 CMS용 구조 데이터로 분리했다.

## 11. Entity/Route 중복 검사

- team/main 신규 변경은 정적 V2 자산 중심이며 신규 Java Entity/Repository/Controller를 추가하지 않았다.
- `PartnerOrganization`, `CoursePartner`, `StudentReview`, `CoursePublication`, `CourseApplication`, `ConsultationRequest`와 동일 테이블/Entity는 team/main에 없다.
- 공개 취업 인터뷰 정적 경로와 `/v2/api/reviews`, `/admin/reviews`는 역할과 라우트가 다르다.
- 조직 공개 API/관리자 CMS, 신청·상담 공개 API/관리자 화면도 중복 Spring mapping이 없다.
- 로컬 기동에서 52개 JPA Repository 스캔, H2 스키마 생성, Spring context 구성이 정상 완료되어 duplicate mapping/bean/JPA mapping 오류가 없음을 확인했다.

## 12. 테스트 결과

- `testClasses`: 성공
- 핵심 통합 테스트: 성공
- 전체 Gradle 테스트: 86 suites, 444 tests, failures 0, errors 0, skipped 0
- 병합 전 443건보다 신청·상담 UI와 실제 API 연결을 검증하는 테스트 1건을 추가했다.
- `git ls-files -u`: 결과 없음
- `git diff --cached --check`: 통과

## 13. 브라우저 검수

현재 세션에서 연결 가능한 브라우저가 0개라 1440px/390px 시각 검수는 완료로 주장하지 않는다.

대신 실제 서버에서 다음을 확인했다.

- `/` → `/v2/index.html` 정상 redirect
- 홈페이지, 취업캠퍼스, 몰입클라쓰, 과정 상세, 신청, 상담 정적 경로: HTTP 200
- `/trainee`와 관리자 과정/신청/상담/기업·기관/후기 경로: 비로그인 HTTP 302 로그인 보호
- 자동 렌더링/보안/역할 경계 테스트 통과

## 14. 남은 위험

- `[수동 확인 필요]` 1440px/390px 공개·관리자·수강생 화면의 실제 시각/클릭 검수
- MIN의 27개 취업 인터뷰는 정적 편집 콘텐츠이고 일반 후기 CMS는 DB 콘텐츠다. 현재 범위는 분리되어 있지만 장기적으로 한 관리자 정책에서 운영하려면 콘텐츠 이전 기준이 필요하다.
- 공개 파트너 마케팅 자산과 구조형 Organization CMS를 향후 하나의 노출 정책으로 연결할지 제품 결정이 필요하다.
- 프로젝트 기존 경고인 Spring Security `AntPathRequestMatcher` 사용 중단 예정 경고와 Commons Logging 중복 경고는 이번 병합 범위에서 변경하지 않았다.

## 15. 최종 integration commit

- 예정 메시지: `merge: integrate team main with trainee management work`
- 이 문서는 해당 merge commit에 포함된다. 커밋 자체의 해시는 자기 참조로 문서 안에 고정할 수 없으므로 `git log -1 --oneline`에서 확인한다.
- 커밋은 `integration-trainee-management-20260823`에만 생성하며 push하지 않는다.
