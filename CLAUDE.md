# Samsung Academy LXP — 개발 가이드

K-디지털 트레이닝 훈련기관 학습데이터관리시스템(LMS). 기능 구현은 대부분 완료됐고, 현재 **통합 디버깅 + 온프레미스 배포/운영** 단계다.
**개발은 1인 체제** (과거 2인 협업의 흔적이 주석·문서에 남아 있음 — "개발자 A/B", docs/b-audit-reply.md 등은 역사 기록. 완료된 협업 문서는 삭제됨 — 필요 시 git 히스토리 참조).

## 기술 스택

- Java 17, Spring Boot 3.x (Security, Data JPA), Thymeleaf SSR, PostgreSQL 16 (로컬 개발은 인메모리 H2), Gradle
- 배포: Docker Compose (app + PostgreSQL + cloudflared) — 사내 Windows PC + WSL2, https://lms.samsungax.com 외부 공개. **`v*` 태그 푸시로 자동 배포** — 절차는 `docs/deploy-guide.md`, 서버 런북은 `docs/deploy-remote-2026-07-29.md` (구버전: deploy-onprem.md)
- 패키지 루트 `com.ssa.lms`, 도메인 구조: `<domain>/{entity,repository,service,web}` — auth, user, course, content, attendance, completion, exam, assignment, grading, proctor, support, notice, survey, dashboard, common, config

## 빌드/실행

- **주의: 저장소가 OneDrive 안이라 그냥 빌드하면 build/ 잠금으로 깨진다.** 모든 gradle 명령에 init 스크립트를 붙일 것:
  `./gradlew test --init-script C:\Temp\lxp-offline-build.gradle` (스크립트가 없으면: `allprojects { layout.buildDirectory.set(new File("C:/Temp/lxp-course-build/" + project.name)) }` 내용으로 생성)
- JDK 17: `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot` (bash: `export JAVA_HOME=...`)
- 실행: `./gradlew bootRun --init-script ...` → http://localhost:8080, 프로필 `local`(H2) 시드 계정 admin / instructor1 / trainee1 (pw `1234`)
- 프로필: `local`(H2+시드) / `dev`(로컬 PostgreSQL) / `prod`(도커 배포용, 시드 없음 — AdminAccountInitializer 가 admin 부트스트랩)
- 도커: `.env` 작성(.env.example 참고) 후 `docker compose up -d --build`. **`down -v` 는 데이터(볼륨) 삭제 — 운영에서 금지**
- 테스트: `./gradlew test --init-script ...` (169+건, 커밋 전 필수 통과)

## Git

- **푸시는 반드시 https://github.com/woongscoding/axi_project (사용자 소유) 로만 한다.**
- **⚠ 리모트 이름이 PC 마다 다르다. 푸시 전에 `git remote -v` 로 확인할 것.**

  | 환경 | 정본(axi_project) | 금지(mina-2026-ai) |
  |---|---|---|
  | 사무실 PC | `origin` | `mina-old` |
  | **Mac (`~/Desktop/test/samsung-lxp`)** | **`a`** | **`origin` ← 여기선 origin 이 금지 대상** |

  Mac 에서 `git push origin main` 을 그대로 치면 **금지 저장소로 나간다.**
  Mac 에서는 항상 `git push a main` / `git push a v0.1.x-draft`.
  `docs/deploy-guide.md` 는 사무실 PC 기준으로 쓰여 있어 `origin` 이라고 돼 있다 — 이 표를 우선한다.
- 1인 개발이므로 main 직접 커밋 허용. 규모 있는 작업(병렬 세션·실험)은 `feat/*` 브랜치 + worktree(`C:\work\` — OneDrive 밖이라 init 스크립트 불필요).
- 커밋 메시지 한글, 작은 단위 유지.

## 배포 (`docs/deploy-guide.md` 요약 — 원문이 기준)

운영 주소 **https://lms.samsungax.com** — 수강생이 실제로 쓰는 서버다.

- **main 푸시 = 코드 공유** (서버 영향 없음, 자유롭게)
- **`v*` 태그 푸시 = 배포 결정** — 이 순간 GitHub Actions 가 테스트 → 통과 시 self-hosted 러너가
  서버에서 해당 태그를 checkout + `docker compose up -d --build` → `/login` 200 헬스체크.
  **빌드 중 3~5분 서비스 순단**이 있으므로 배포 타이밍은 한마디 하고 진행한다.

```bash
./gradlew test --init-script ...          # 1) 로컬 테스트 통과 확인 (실패하면 어차피 배포 안 됨)
git push a main                            # 2) 코드 공유 (Mac 기준. 사무실 PC 는 origin)
git tag | sort -V | tail -1                # 3) 마지막 태그 확인 → 다음 번호로
git tag v0.1.3-draft && git push a v0.1.3-draft   # 4) 이 순간 배포 시작
```

진행 상황·로그는 저장소 **Actions 탭**. 테스트 실패 시 배포되지 않는다(안전장치).

**배포 관련 금지사항**

1. **서버에 직접 들어가 파일을 고치지 말 것** — 다음 배포의 `checkout --force` 로 전부 사라진다. 수정은 반드시 git 을 거친다.
2. **`docker compose down -v` 절대 금지** — DB·업로드 볼륨이 삭제된다.
3. **`.env`·API 키·비밀번호 커밋 금지.** private 저장소여도 git 히스토리에 영원히 남는다.
   비밀값은 서버 `.env` 에만 두고, 새 항목은 `.env.example` 에 **빈 항목만** 추가한다.
4. **새 환경변수를 쓰는 기능은 값이 없어도 앱이 뜨게 만들 것** — 기능만 꺼지도록 기본값 처리
   (AI 기능 방식 참고: `AiConfig` 가 키 없으면 안내 구현을 꽂고 기동 로그를 남긴다).
   서버 `.env` 반영은 서버 관리자에게 요청.

**롤백** — 직전 태그로 Actions 수동 실행(Run workflow), 또는 revert 커밋 + 새 태그.

## 반드시 지킬 규칙 (실제 사고에서 나온 것)

1. **PostgreSQL 전용 500 함정**: 검색 JPQL 의 `(:param is null or ...)` null 파라미터는 H2 에선 통과하지만 PostgreSQL 에서 bytea 추론으로 터진다.
   - 문자열: `lower(concat('%', cast(:kw as string), '%'))` 처럼 **concat 안에 cast**
   - 날짜: `(cast(:from as timestamp) is null or n.createdAt >= :from)` — **null 판정 쪽에 cast** (비교식 쪽에 넣으면 컬럼 타입 추론이 깨져 역효과)
   - JDBC URL 에 `stringtype=unspecified` 금지 (enum is null 판정이 깨짐)
   - 새 검색 쿼리 추가 시 점검: `grep -rn "concat('%', :" --include=*Repository.java` 에 cast 누락이 없어야 함
2. **Thymeleaf 3.1 예약어**: `th:each` 변수명으로 `session`/`request`/`response` 금지 — 렌더 도중 예외인데 응답이 이미 200 이라 **HTML 이 조용히 잘린다** (차시 루프는 `lesson` 사용)
3. **렌더 테스트는 status 200 으로 부족** — 잘린 응답도 200 이므로 반드시 `</html>` 포함까지 검증 (CourseDetailRenderTest 참고)
4. **화면 데이터 주입 스크립트 순서**: `window._server*Rows` 대입 스크립트는 소비하는 `const ... = window._server*Rows || [더미]` 스크립트보다 **앞에** 둘 것 (뒤에 두면 서버 데이터가 영원히 더미에 가려짐)
5. **window.open 팝업 화면**의 폼 제출은 서버 리다이렉트가 팝업 안에 렌더된다 — fetch 제출 후 `opener.location.reload() + window.close()` 패턴 사용 (admin-alarm-add.html 참고)
6. 공통 엔티티는 `common.entity.BaseEntity` 상속, soft delete 는 `@SQLDelete`+`@SQLRestriction` (3년 보존 요건 — 물리 DELETE 금지)
7. 개인정보 컬럼(email/phone/birthDate 등)은 `CryptoConverter`(AES-256/GCM) — **암호문 저장이라 해당 컬럼으로 DB 검색 불가**(검색은 loginId/name), 비밀번호는 bcrypt. 운영 키 `LMS_CRYPTO_SECRET` 은 변경 금지
8. UI 텍스트 한국어. 기존 화면 구조 유지하며 Thymeleaf 속성만 추가하는 방식. 미구현 화면 링크는 404 대신 `alert('준비 중인 기능입니다.')` 처리(fragments 3종에서만 관리)
9. 공통 레이아웃은 `templates/fragments/{admin,instructor,trainee}.html` — 메뉴/링크 수정은 이 파일에서만 하면 전 페이지 반영

## 현재 남은 작업 (참고)

- 미구현: 강사 튜터링·알림함 화면, 콘텐츠 버전관리(H-2), 이수증 에디터(I-9), 동시접속 방지(A-9), 본인인증 외부연동(기관 결정)
- 기관 결정 대기: 에스컬레이션 7건 (3중 모니터링·안면인식·화상강의 등 — docs/b-audit-reply.md §4)
- 운영: 모니터링(actuator), Flyway 전환 — docs/deploy-onprem.md §F (HTTPS 는 Cloudflare Tunnel 로 해결됨)

## 참고 문서

- `docs/deploy-guide.md` — **배포 절차 (팀 공용, 이것이 기준)** — 태그 푸시 자동배포
- `docs/deploy-remote-2026-07-29.md` — 서버 내부 구성·트러블슈팅 (서버 관리자용)
- `docs/deploy-onprem.md` — 구버전 수동 배포 런북 (백업·복구 절차는 아직 유효)
- `docs/requirements-audit-v2-2026-07-27.md` — 요구사항 감사 (별첨4+지원서 41항목)
- `docs/기능소개-작성초안.md` + `docs/screenshots/` — 정부 제출용 기능 소개 초안
- `docs/내역서-작성초안.md` — 양식3 내역서 초안
- `메뉴구성도_IA - 김민아.xlsx` — 메뉴 IA·권한 매트릭스·데이터 필드 정의
