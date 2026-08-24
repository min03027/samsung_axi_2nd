# 재택 배포 운영 런북 (2026-07-29 작성)

> 온프레미스 서버를 외부 공개(Cloudflare Tunnel)하고, 재택에서 원격 배포할 수 있도록 구성한 결과 정리.
> 기존 `docs/deploy-onprem.md` 런북의 **후속편**. 사내망 접속 방식이 폐기되고 터널 방식으로 전환됨.

---

## 0. 한 장 요약 — 매일 쓰는 것만

```
[집] 코드 수정 → 테스트 → 커밋 → push(main + 태그)
        ↓
     GitHub
        ↓   ← 자동 아님. 아래를 직접 실행해야 반영됨
[RDP] Win+R → mstsc /v:100.79.114.29  (계정 DESKTOP-0PSG0O1\1)
        ↓
[WSL] Win+R → wsl
      cd ~/lxp
      git fetch --tags
      git checkout <태그>
      docker compose up -d --build      # 3~5분, 이 동안 서비스 잠깐 끊김
        ↓
[확인] https://lms.samsungax.com
```

---

## 1. 접속 정보

| 항목 | 값 |
|---|---|
| 서비스 주소 | https://lms.samsungax.com |
| 서버 Tailscale IP | `100.79.114.29` |
| Tailscale 계정 | mapcw99lol@gmail.com |
| Windows 계정 | `DESKTOP-0PSG0O1\1` (Administrators) |
| 서버 OS | Windows 10 Pro + WSL2 Ubuntu 24.04 |
| WSL 사용자 | `sejong` |
| 코드 위치 | `~/lxp` (WSL 홈) |
| Cloudflare 계정 | Sejong074@gmail.com |
| 터널 이름 / ID | `lxp` / `0a77c661-e608-4852-888c-9492778334e7` |
| 도메인 등록처 | 후이즈 (2025-12-30 등록, 2030 만료) |
| 네임서버 | davina / rustam .ns.cloudflare.com |

**비밀번호는 이 문서에 적지 않음.** 별도 비밀번호 관리자에 보관:
- Windows 계정 `1` 비밀번호 (RDP 로그인용, 2026-07-29 설정)
- WSL `sejong` sudo 비밀번호 (2026-07-29 재설정)
- `.env` 전체 (`LMS_CRYPTO_SECRET`, `LMS_DB_PASSWORD`, `LMS_ADMIN_INIT_PASSWORD`, `TUNNEL_TOKEN`)

---

## 2. 구축한 구조

### 2-1. 왜 이 구조가 됐나

사무실 망이 3개로 분리되어 상호 라우팅이 안 됨:
- 서버 `192.168.123.x` (유선)
- 내 노트북 `192.168.0.x` (Wi-Fi)
- 수강생 PC `10.0.57.x`

서버 PC는 이동 불가, 무선 어댑터 없음, 노트북은 랜포트 없음.
→ **사내망 접속 포기, Cloudflare Tunnel로 외부 공개 전환.**

### 2-2. 트래픽 경로

```
수강생 브라우저
   ↓ https
Cloudflare 엣지 (icn05/icn06, 서울)
   ↓ 터널 (아웃바운드 연결, 포트포워딩 불필요)
서버 WSL: cloudflared 컨테이너
   ↓ 도커 내부 네트워크
app 컨테이너 :8080
```

포인트: **서버가 밖으로 나가는 연결**이라 공유기 설정도, 방화벽 인바운드도 필요 없음.
그래서 기존에 등록한 Windows 방화벽 8080 인바운드 규칙은 **외부 서비스용으로는 이제 불필요** (정리 대상, 공격면 축소).

### 2-3. 관리 경로 (별개)

```
집 노트북 → Tailscale(VPN) → 서버 Windows → RDP 화면 → WSL 터미널
```

수강생은 이 경로를 안 지나감. 순수 관리자 원격 접속용.

---

## 3. docker-compose.yml 의 cloudflared

기존 `db`, `app` 아래에 추가됨 (2026-07-29부터 저장소에 정식 포함):

```yaml
  cloudflared:
    image: cloudflare/cloudflared:latest
    restart: unless-stopped
    command: tunnel --no-autoupdate run
    environment:
      TUNNEL_TOKEN: ${TUNNEL_TOKEN:?TUNNEL_TOKEN 을 .env 에 설정하세요}
    depends_on:
      - app
```

`.env` 에 `TUNNEL_TOKEN=eyJ...` 필요. (git에 안 올라감 — `.gitignore`)
로컬에서 터널 없이 compose 리허설만 할 때는 `TUNNEL_TOKEN=dummy` 로 두면 app/db 는 뜨고 cloudflared 만 재시작 루프를 돈다.

---

## 4. Cloudflare 대시보드 설정 위치

UI가 개편되어 예전 문서와 이름이 다름. **헤맸던 부분이라 기록.**

Zero Trust > Networks > **Tunnels & Mesh** > `lxp` 클릭

| 탭 | 용도 |
|---|---|
| Overview | 터널 상태(Healthy) · Connector 확인 |
| **Published application routes** | ✅ **여기가 예전 "Public Hostname"** — 공개 서비스 설정 |
| Hostname routes (Beta) | ❌ 사설망용. Cloudflare One Client 설치 필요. 우리 용도 아님 |
| CIDR routes | ❌ 사설 IP 대역 라우팅용 |

### 현재 설정된 라우트

| 항목 | 값 |
|---|---|
| Subdomain | `lms` |
| Domain | `samsungax.com` |
| **Path** | **비워둠** (⚠️ 여기 값 넣으면 404) |
| Type | `HTTP` |
| URL | `app:8080` |

`app:8080` 의 `app` 은 compose 서비스명, `8080` 은 **컨테이너 내부 포트** (호스트 포트 아님).

저장하면 DNS에 `lms` CNAME(프록시 ON)이 자동 생성됨.

---

## 5. 트러블슈팅 기록 (실제로 겪은 것)

### 5-1. `/login` 404 + 로그가 아예 안 남음

- **원인:** 7/20부터 WSL 8080을 선점하던 잔재 프로세스 (`~/app/samsung-portal` 의 `demo-0.0.1-SNAPSHOT.jar`)
- **조치:** kill → 해당 디렉토리 `_ARCHIVED-` 로 개명 → `docker compose up -d --force-recreate`
- **교훈:** 서버 PC에서 8080 쓰는 다른 프로세스 절대 띄우지 말 것

### 5-2. `DNS_PROBE_FINISHED_NXDOMAIN`

- **원인:** Published application routes 저장 안 됨 → DNS 레코드 미생성
- **진단:** `nslookup lms.samsungax.com 1.1.1.1` → NXDOMAIN
- **참고:** 루트 도메인은 정상이었음 (`nslookup -type=NS samsungax.com 1.1.1.1` → Cloudflare NS 확인)

### 5-3. Cloudflare 404 (앱까지 안 감)

- **원인:** **Path 필드에 실수로 `HTTP` 를 입력.** `lms.samsungax.com/HTTP` 만 매칭되어 `/login` 이 안 걸림
- **구분법:** 응답 헤더에 `server: cloudflare` 만 있고 `JSESSIONID` 등 Spring 헤더가 없으면 앱까지 안 간 것
- **조치:** Path 비우고 Type을 `HTTP` 로 지정

### 5-4. WSL에서 `curl: (6) Could not resolve host`

- **원인:** WSL이 쓰는 Windows DNS 프록시(`10.255.255.254`)가 NXDOMAIN을 캐싱
- **특징:** `nslookup ... 1.1.1.1` 은 되는데 curl만 실패
- **조치:** PowerShell `ipconfig /flushdns` → 안 되면 `wsl --shutdown` 후 재시작
- **우회 테스트:** `curl -I --resolve lms.samsungax.com:443:104.21.6.35 https://lms.samsungax.com/login`
  → DNS 건너뛰고 직접 접속. **서비스 정상 여부 판별용으로 유용**
- **주의:** `wsl --shutdown` 하면 컨테이너도 내려감 (자동 복구되지만 `docker compose ps` 확인 필요)

### 5-5. RDP 로그인 실패

- **원인:** Windows 계정 `1` 에 실질 비밀번호가 없었음. **빈 비밀번호 계정은 RDP 원천 차단**
- **조치:** `net user 1 <비밀번호>` 설정 → `netplwiz` 로 자동 로그인 설정 (짝으로 해야 함)
- **netplwiz 체크박스가 안 보일 때:**
  ```powershell
  reg add "HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion\PasswordLess\Device" /v DevicePasswordLessBuildVersion /t REG_DWORD /d 0 /f
  ```
- **로그인 형식:** `1` 이 안 되면 `DESKTOP-0PSG0O1\1`

---

## 6. 배포 절차 (상세)

### 6-1. 집에서 (개발 PC)

```bash
# OneDrive 안이라 init-script 필수
./gradlew test --init-script C:\Temp\lxp-offline-build.gradle

git add .
git commit -m "커밋 메시지(한글)"
git tag v0.1.x-draft
git push origin main
git push origin v0.1.x-draft     # ⚠️ 태그는 별도 push. 빼먹으면 서버가 태그를 못 찾음
```

**푸시 대상은 min03027/samsung_axi_2nd 하나뿐이다.** 예전 저장소 리모트가 남아 있으면 제거할 것.

push 후 `git status` 가 `up to date with 'origin/main'` 인지 확인.

### 6-2. 서버에서

```
Win+R → mstsc /v:100.79.114.29
계정: DESKTOP-0PSG0O1\1
```

RDP 화면 안에서 `Win+R` → `wsl`:

```bash
cd ~/lxp
git status                  # 서버에서 임시 수정한 파일 없는지 확인
git fetch --tags
git checkout v0.1.x-draft
docker compose up -d --build

docker compose ps
docker compose logs --tail=50 app     # "Started LmsApplication"
git log --oneline -1                  # 집에서 본 커밋 해시와 일치하는지
```

### 6-3. 확인

브라우저 → https://lms.samsungax.com
cloudflared는 재시작되지 않으므로 app만 올라오면 즉시 반영.

---

## 7. 자주 하는 실수

| 증상 | 원인 |
|---|---|
| 서버에 코드가 안 바뀜 | push 안 했거나, **태그 push를 빼먹음** |
| `git pull` 이 안 됨 | 서버가 **detached HEAD**(태그 체크아웃) 상태. `fetch --tags` + `checkout` 을 쓸 것 |
| checkout이 충돌로 막힘 | 서버에서 임시 수정한 파일 존재. `git checkout -- <파일>` 로 원복 |
| 코드 바꿨는데 반영 안 됨 | 빌드 캐시. `docker compose build --no-cache app && docker compose up -d` |
| 새 환경변수 추가했는데 앱이 안 뜸 | **`.env` 는 git에 없음.** 서버 `.env` 를 직접 수정해야 함 |
| PowerShell에서 리눅스 명령이 안 됨 | 프롬프트 확인. `PS C:\>` = Windows / `sejong@...$` = WSL |

**프롬프트 구분**

| 프롬프트 | 어디 | 쓰는 명령 |
|---|---|---|
| `sejong@DESKTOP-0PSG0O1:~$` | WSL | `docker compose`, `git`, `curl`, `nano` |
| `PS C:\...>` | PowerShell | `tailscale`, `net user`, `ipconfig`, `wsl --shutdown` |

---

## 8. 절대 금지

- **`docker compose down -v`** — 볼륨(DB·업로드 전체) 삭제. 중지는 `docker compose down` 까지만
- **min03027/samsung_axi_2nd 외의 저장소로 push**
- **서버 PC에서 8080 쓰는 다른 프로세스 실행** (선점 사고 이력)
- **`LMS_CRYPTO_SECRET` 변경** — 기존 개인정보 복호화 불가

---

## 9. 미완료 과제

### 9-1. 코드 (재택 가능) — 우선순위 높음

**① 프록시 헤더 + 세션 쿠키** (`application-prod.yml`)

```yaml
server:
  forward-headers-strategy: framework
  servlet:
    session:
      cookie:
        secure: true
        http-only: true
        same-site: lax
```

현재 응답 쿠키가 `JSESSIONID=...; Path=/; HttpOnly` 로, **`Secure`·`SameSite` 없음.**
admin 로그인은 정상 동작하므로 차단 요소는 아니나, 세션 쿠키가 http로도 전송될 여지가 있어 보안상 필요.

> ⚠️ **`secure: true` 를 켜면 LAN IP(`http://192.168.123.19:8080`) 접속에서 로그인 불가.**
> `http://localhost:8080` 은 예외적으로 허용됨.
> Cloudflare 무료 플랜은 요청당 **업로드 100MB 제한**이 있어 VOD 등 대용량은 터널로 못 올림
> → 관리자가 서버 PC 앞에서 `localhost:8080` 직결로 업로드하는 운영 방침 확정 필요 (**미결**)

**② 데모 계정 initializer** (설계만 됨, 미결)
- UserService 경유로 bcrypt 처리
- `@ConditionalOnProperty` 로 on/off
- loginId 존재 시 skip (멱등성)

**③ A-4 E2E 재검증** — https 주소로 전체 흐름
admin 로그인만 확인됨. 아래는 미검증:
- 회원가입 → 승인 대기 차단 → admin 승인 → 훈련생 로그인
- 강사 권한 포함 3권한

### 9-2. 서버 (다음 방문 또는 RDP)

- **전원 설정** — 설정 > 시스템 > 전원 > 화면/절전 **"안 함"** ⚠️ **미확인**
- **Docker Desktop 자동 시작** — Settings > General > "Start Docker Desktop when you sign in" ⚠️ **미확인**
- **재부팅 생존 테스트** ⚠️ **미실시** — 자동 로그인 → Docker 자동 시작 → 컨테이너 복구가 실제로 도는지 검증 안 됨. 정전·Windows 업데이트 재부팅 시 서비스가 안 살아날 위험
- **A-6 백업 cron** (3년 보존 요건) — 미실시
- **`.dockerignore` 임시 수정분 원복 확인** — `git status` 로 점검
- 방화벽 8080 인바운드 규칙 — 터널 방식이라 불필요, 정리 검토

### 9-3. 보안/운영

- **Cloudflare 계정(Sejong074@gmail.com) 비밀번호 변경 + 2FA + 이메일 변경** ⚠️ **미실시**
  인수받은 계정이라 퇴사자 접근 가능성. 이 계정이 DNS·트래픽 통제권을 가짐
- **후이즈 계정 인수 여부 확인** — 안 됐으면 도메인 갱신 불가 리스크
- admin 비밀번호 강도 점검 (인터넷 공개 상태)
- Cloudflare Bot Fight Mode 활성화 검토
- 후이즈에서 임시로 산 도메인 환불
- `.env` 백업본에 `TUNNEL_TOKEN` 반영 확인

### 9-4. 명의 이전 (김민아님과 별도 안건)

- samsungax.com 도메인 + Cloudflare 계정 → 회사 명의
- Tailscale 계정(mapcw99lol@gmail.com) → 인수인계 시 이전 또는 기기 해제
- 루트 도메인(samsungax.com)을 LXP로 돌릴지 결정 — 현재 기존 레코드 그대로 둠

---

## 10. 참고: 상태 확인 명령

```bash
# WSL
cd ~/lxp
docker compose ps
docker compose logs -f cloudflared        # "Registered tunnel connection" x4 = 정상
docker compose logs --tail=50 app | grep ERROR
git log --oneline -1
curl -sI http://localhost:8080/login | head -1     # 앱 자체 정상 여부

# PowerShell
tailscale ip -4
tailscale status
tailscale debug prefs | findstr ForceDaemon        # true = unattended 정상
```

정상 상태의 cloudflared 로그:
```
INF Registered tunnel connection connIndex=0 ... location=icn05 protocol=quic
INF Registered tunnel connection connIndex=1 ... location=icn06 protocol=quic
INF Registered tunnel connection connIndex=2 ... location=icn05 protocol=quic
INF Registered tunnel connection connIndex=3 ... location=icn06 protocol=quic
```
(로그가 멈춘 것처럼 보여도 정상 — 트래픽 없으면 조용함. `Ctrl+C` 로 빠져나와도 컨테이너는 계속 동작)

---

## 11. CI/CD — GitHub Actions 자동 배포 (2026-07-29 도입)

`.github/workflows/deploy.yml`. **집에서는 태그 푸시만 하면 됨** — §6-2 의 RDP 수동 배포는 러너 장애 시의 비상 수단으로 강등.

```
[집] git push origin main && git push origin v0.1.x-draft
        ↓ 자동
GitHub 호스트 러너: ./gradlew test  (실패 시 배포 중단)
        ↓ 통과 시 자동
서버 self-hosted 러너: cd ~/lxp → fetch --tags → checkout --force <태그> → compose up -d --build
        ↓ 자동
/login 이 200 반환할 때까지 대기(최대 150초) → 실패 시 app 로그를 Actions 화면에 출력
```

- 진행 상황·로그: GitHub 저장소 → **Actions 탭**
- `checkout --force` 라서 **서버 수기 수정은 배포 때마다 날아감** (원래 금지 원칙을 강제하는 효과)
- `.env` 는 지금처럼 서버에만 둠 — 러너가 서버 안에서 돌므로 GitHub 에 비밀값 불필요
- 수동 재배포: Actions 탭 → deploy → Run workflow (태그 선택)

### 러너 설치 (서버 WSL, 최초 1회)

1. GitHub 저장소 → Settings → Actions → Runners → **New self-hosted runner** → Linux x64
   — 화면에 나오는 다운로드 URL·`--token` 값 사용 (토큰 유효 1시간)
2. WSL 에서:
   ```bash
   mkdir ~/actions-runner && cd ~/actions-runner
   curl -o runner.tar.gz -L <화면의 다운로드 URL>
   tar xzf runner.tar.gz
   ./config.sh --url https://github.com/min03027/samsung_axi_2nd --token <토큰> \
     --name lxp-server --labels lxp --unattended
   sudo ./svc.sh install sejong     # systemd 서비스 등록 (재부팅 생존)
   sudo ./svc.sh start
   sudo ./svc.sh status             # active (running) 확인
   ```
3. **재부팅 생존 전제**: Windows 자동 로그인 + Docker Desktop 자동 시작 + WSL 기동.
   WSL 배포판이 안 떠 있으면 러너도 없음 → Windows 시작 프로그램(`shell:startup`)에
   `wsl.exe -d Ubuntu-24.04 --exec true` 바로가기를 두면 로그인 시 배포판이 깨어남.
   (§9-2 재부팅 생존 테스트에 러너 생존 확인 포함할 것)

### 러너 상태 확인 / 장애 시

```bash
sudo ~/actions-runner/svc.sh status    # 러너 서비스
systemctl is-system-running            # WSL systemd 동작 여부 (degraded 도 OK)
```

- GitHub → Settings → Actions → Runners 에서 `lxp-server` 가 **Idle** 이면 정상, Offline 이면 위 명령으로 서비스 확인
- 러너가 죽어도 §6-2 수동 배포는 그대로 가능 (같은 ~/lxp 를 쓰므로 충돌 없음)
