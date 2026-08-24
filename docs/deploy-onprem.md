# 온프레미스 배포 런북 — 현장 배포용

> **확정 환경** (2026-07-28 김민아님 확인): 사내 Windows PC + WSL2 Ubuntu 24.04.4 + Docker Desktop.
> i7-11700F / RAM 7.7GB(WSL2) / 디스크 1TB / Docker 29.6.2 / compose 사용 가능 / 인터넷 가능 / PostgreSQL 16.14 기설치(5432, **사용 안 함** — §주의 참고).
> 절차는 2026-07-28 로컬 리허설로 전 과정 검증됨(가입→승인→로그인 E2E, 31화면 — 기준 커밋 fc94eb6, 태그 v0.1.0-draft).
> 모든 명령은 **WSL2 Ubuntu 터미널**에서 실행한다 (Windows PowerShell 아님).

---

## A. 현장 도착 후 순서 (이 순서대로만)

### A-0. 시작 전 확인 (2분)

```bash
docker version          # Client/Server 둘 다 나오면 OK (Docker Desktop 켜져 있어야 함)
docker compose version
curl -sI https://github.com | head -1   # 인터넷/GitHub 접근 확인
```

### A-1. 코드 받기

**반드시 WSL 홈(`~`)에 clone** — `/mnt/c/...`(Windows 폴더)에 받으면 빌드가 매우 느려짐.

```bash
cd ~
git clone https://github.com/min03027/samsung_axi_2nd.git lxp
cd lxp
git checkout v0.1.0-draft   # 배포 기준 태그 (최신 main 으로 하려면 이 줄 생략)
```

- **공개 저장소라 별도 인증 없이 clone 된다** (예전 private 저장소 시절 필요하던 Personal Access Token 절차는 불필요).

### A-2. 환경 파일(.env) 작성 — ★제일 중요

```bash
cp .env.example .env
openssl rand -base64 32   # 두 번 실행해서 DB 비번용·암호화 키용 랜덤값 생성
nano .env
```

| 키 | 넣을 값 |
|---|---|
| `LMS_DB_PASSWORD` | 랜덤값 1 |
| `LMS_CRYPTO_SECRET` | 랜덤값 2 — **개인정보 암호화 키. 한번 정하면 영구 고정, 분실 시 개인정보 복호화 불가** |
| `LMS_ADMIN_INIT_PASSWORD` | 초기 admin 비밀번호 (직접 정한 값) |
| `LMS_HTTP_PORT` | 8080 (그대로) |

**★ 작성 직후 `.env` 내용 전체를 안전한 곳(개인 비밀번호 관리자 등)에 백업.** 특히 CRYPTO_SECRET.

```bash
chmod 600 .env    # 다른 계정이 못 읽게 — 여기에 DB 비번과 API 키가 들어 있다
```

### A-2-1. AI 기능 키 (선택 — 없어도 앱은 정상 기동)

AI 기능(학습 도우미·커리큘럼 추천·학습진단·과제 초안·직무 로드맵)을 쓰려면 키가 필요하다.
**키가 없으면 그 기능만 꺼진 채 뜨고, 화면에는 안내 문구가 나온다.** 앱 기동은 막지 않는다.

| 키 | 넣을 값 | 없으면 |
|---|---|---|
| `LMS_AI_ENABLED` | `true` | AI 기능 전체 꺼짐 |
| `ANTHROPIC_API_KEY` | console.anthropic.com → API Keys → Create Key | 위와 같음 |
| `LMS_AI_DAILY_LIMIT` | `200` (하루 전체 호출 상한) | — |
| `LMS_AI_DAILY_LIMIT_PER_USER` | `20` (1인 상한) | — |
| `WORKNET_API_KEY` | data.go.kr → 워크넷 채용정보 (즉시 발급) | 로드맵이 AI 일반 지식으로 대체됨 |

**비용 상한을 반드시 확인할 것.** 위 두 한도가 그날 나갈 수 있는 최대치를 정한다.
Anthropic 콘솔에서 **결제 한도(spend limit)** 도 함께 걸어 두면 이중으로 막힌다.

**키는 절대 커밋되지 않는다** — `.gitignore`(git), `.dockerignore`(이미지 빌드) 양쪽에서 막고 있다.
컨테이너에는 `docker compose` 가 호스트의 `.env` 를 읽어 **환경변수로만** 넣어 준다.

기동 후 로그로 확인:
```bash
docker compose logs app | grep '\[AI\]'
# [AI] 활성 — model=claude-sonnet-5 ...   ← 키 인식됨
# [AI] 비활성 — ... API 키가 비어 있음      ← 아직 안 들어감
```

**키를 바꾼 뒤에는 재시작이 필요하다** (기동 시 한 번만 읽는다):
```bash
nano .env && docker compose up -d    # 재빌드 불필요
```

### A-3. 기동

```bash
docker compose up -d --build    # 최초 빌드 5~10분 (gradle 의존성 다운로드)
docker compose ps               # app·db 둘 다 Up(healthy) 확인
docker compose logs -f app      # "Started LmsApplication" 보이면 Ctrl+C
```

### A-4. 배포 검증 (리허설 때 통과한 체크 그대로)

1. 그 PC 브라우저에서 `http://localhost:8080` → 로그인 화면 뜨는지
2. `admin` / (A-2에서 정한 비밀번호) 로그인 → 대시보드
3. 회원가입(훈련생) → 로그아웃 → 가입 계정 로그인 시도 → **"승인 대기" 로 차단되는지**
4. admin 으로 가입 승인 → 다시 로그인 → 훈련생 홈 진입
5. **사내 다른 PC** 에서 `http://<서버PC IP>:8080` 접속 (IP 확인: PowerShell 에서 `ipconfig` → IPv4)
   - 안 되면 → B-2 방화벽 미설정이 원인 (사내 접속엔 공유기 설정 불필요)

### A-5. Windows 설정 4종 (김민아님과 함께, 10분)

1. **절전 끄기**: 설정 > 시스템 > 전원 — 화면/절전 "안 함"
2. **Docker Desktop 자동 시작**: Docker Desktop Settings > General > "Start Docker Desktop when you sign in" ✔
   (+ 가능하면 Windows 자동 로그인 — 재부팅 후 로그인 전엔 Docker 가 안 뜸. 컨테이너는 `restart: unless-stopped` 라 Docker 만 뜨면 자동 복구)
3. **방화벽 8080 허용** (관리자 PowerShell):
   ```powershell
   New-NetFirewallRule -DisplayName "LXP 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
   ```
4. **PC IP 고정**: 공유기 DHCP 예약 또는 Windows 수동 IP — 공유기 비번을 모르면 일단 현재 IP 를 기록해두고 추후 처리 (IP 바뀌면 사용자 접속 주소가 바뀜)

### A-6. 백업 cron 등록 (5분 — 3년 보존 요건)

```bash
mkdir -p ~/backup
cat > ~/lxp-backup.sh <<'EOF'
#!/bin/sh
docker exec lxp-db-1 pg_dump -U lms lms | gzip > ~/backup/lms-$(date +%F).sql.gz
docker run --rm -v lxp_lxp-uploads:/data -v ~/backup:/out alpine tar czf /out/uploads-$(date +%F).tar.gz /data 2>/dev/null
find ~/backup -name "*.gz" -mtime +30 -delete
EOF
chmod +x ~/lxp-backup.sh
(crontab -l 2>/dev/null; echo "0 4 * * * $HOME/lxp-backup.sh") | crontab -
~/lxp-backup.sh && ls -la ~/backup   # 1회 수동 실행으로 동작 확인
```

※ 컨테이너 이름이 `lxp-db-1` 이 아니면 `docker compose ps` 로 실제 이름 확인해 교체.
※ WSL2 cron 은 Windows 재부팅 후 자동 시작 안 될 수 있음 — `sudo service cron start` 필요 시 실행 (또는 Windows 작업 스케줄러로 `wsl ~/lxp-backup.sh` 등록이 더 확실).

### A-7. (선택) 재택 원격 접속 — Tailscale

공유기 설정 없이 재택 작업용: 서버 PC 와 내 PC 양쪽에 https://tailscale.com 설치 → 같은 계정 로그인 → 서버 PC 의 Tailscale IP 로 접속(SSH/브라우저). 사내 사용자 서비스와는 무관(사내는 LAN IP 로 직접 접속).

---

## B. 주의사항 (현장에서 헷갈리기 쉬운 것)

- **기설치 PostgreSQL(5432)은 무시** — 우리 compose 가 DB 컨테이너를 따로 띄우고 외부 포트를 안 열어 충돌 없음. 램 절약하려면: `sudo systemctl disable --now postgresql` (선택)
- **데이터 저장 위치** — 컨테이너가 아니라 도커 볼륨(`lxp-dbdata`·`lxp-uploads`, WSL2 가상 디스크 내). 앱 재시작·업데이트에도 유지됨
- **`docker compose down -v` 절대 금지** — `-v` 는 볼륨(DB·업로드 전체) 삭제. 중지는 `docker compose down` 까지만
- clone 위치는 `~`(WSL 홈). `/mnt/c` 금지 (빌드 극악 느림)
- PC 재부팅/절전 = 서비스 중단. A-5 설정이 안전장치

## C. 운영 명령 모음

```bash
cd ~/lxp
docker compose ps                # 상태 확인
docker compose logs -f app       # 앱 로그 (에러 확인: | grep ERROR)
docker compose restart app       # 앱만 재시작
docker compose down              # 전체 중지 (데이터 유지)
docker compose up -d             # 재기동

# 업데이트 배포 (코드 갱신 시)
git pull && docker compose up -d --build

# DB 복구 (백업에서)
gunzip -c ~/backup/lms-YYYY-MM-DD.sql.gz | docker exec -i lxp-db-1 psql -U lms lms
```

## D. 트러블슈팅

| 증상 | 확인/조치 |
|---|---|
| `docker: command not found` | Docker Desktop 이 꺼져 있음 — Windows 에서 실행 |
| 앱 컨테이너 재시작 반복 | `docker compose logs app` — `.env` 값 누락(DB_PASSWORD/CRYPTO_SECRET) 이 대부분 |
| localhost 는 되는데 다른 PC 접속 불가 | A-5 ③ 방화벽 규칙 누락 |
| 화면 500 | `docker compose logs app \| grep ERROR` — PostgreSQL 전용 이슈 가능성, 로그 들고 개발 세션에 문의 |
| clone 시 인증 실패 | PAT 토큰 권한/만료 확인 |
| 빌드 중 메모리 부족 | 다른 프로그램 종료 후 재시도 (WSL2 램 7.7GB) |

## E. GitHub 접근 불가(폐쇄망) 대비 — 이번엔 해당 없음(인터넷 가능 확인됨)

로컬에서 이미지를 말아 파일로 전송하는 방식:

```bash
# 로컬(Windows)
docker compose build
docker save samsung-lxp-app postgres:16-alpine | gzip > lxp-images.tar.gz
# USB/공유폴더로 서버 PC 에 전달 후 (서버)
docker load < lxp-images.tar.gz && docker compose up -d
```

## F. 이후 과제 (초안 운영 시작 후)

- HTTPS: 사내 인증서 + nginx 리버스 프록시 (내역서 E-4)
- 서버 전용 머신 전환 (대표 승인 후 — Windows 병행 사용의 24/7 한계 해소)
- 모니터링: actuator health + 알림 (B 와 build.gradle 합의 필요)
- 스키마 안정화 후 ddl-auto: update → validate + Flyway 전환
- 외부(재택 훈련생) 서비스 개방 시: 공유기 포트포워딩 또는 터널 + HTTPS 필수
