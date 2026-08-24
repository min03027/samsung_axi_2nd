package com.ssa.lms.identity.service;

import com.ssa.lms.exam.entity.Exam;
import com.ssa.lms.exam.entity.ExamAttempt;
import com.ssa.lms.exam.repository.ExamRefRepository;
import com.ssa.lms.course.service.CourseQueryService;
import com.ssa.lms.exam.repository.ExamRepository;
import com.ssa.lms.identity.dto.IdentityViews;
import com.ssa.lms.identity.entity.*;
import com.ssa.lms.identity.policy.PrecheckPolicy;
import com.ssa.lms.identity.repository.*;
import com.ssa.lms.storage.privatefile.PrivateFileStorage;
import com.ssa.lms.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * QR 기반 신분확인 흐름 (LXP-015 / LXP-016).
 *
 * <p>흐름: PC 세션 생성 → QR 토큰 발급 → 모바일이 토큰으로 업로드 → 운영진 승인/반려
 * → PC 가 상태를 폴링 → 승인된 경우에만 시험 입장.</p>
 *
 * <p><b>자동 승인 경로는 없다.</b> {@code approve()} 는 운영진 User 를 반드시 받는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamIdentityService {

    /** 토큰 엔트로피(바이트). 32B = 256bit — 요구된 128bit 이상. */
    private static final int TOKEN_BYTES = 32;

    /** 모바일 상태 조회 허용 시간(분). 업로드 TTL(10분)보다 길되 무제한은 아니다. */
    private static final int STATUS_VIEW_MINUTES = 60;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExamIdentitySessionRepository sessionRepository;
    private final ExamIdentityTokenRepository tokenRepository;
    private final ExamIdentityDocumentRepository documentRepository;
    private final ExamRepository examRepository;
    private final ExamRefRepository examRefRepository;
    private final ExamIdentityAuditLogRepository auditRepository;
    private final CourseQueryService courseQueryService;
    private final IdentitySessionCreator sessionCreator;
    private final ExamIdentityVerificationRepository verificationRepository;
    private final PrivateFileStorage storage;

    /* ===================== 세션 ===================== */

    /**
     * (시험, 사용자) 의 신분확인 세션을 가져오거나 새로 연다.
     * 이미 승인됐고 아직 유효하면 그대로 재사용한다 — 새로고침마다 다시 제출하게 만들지 않는다.
     */
    /**
     * 사전점검 세션 생성 전 검증 (지적 4).
     *
     * <p>화면 URL 의 examId 만 믿으면 아무 시험 id 나 넣어 세션을 만들고 운영진 대기열을
     * 오염시킬 수 있다. 시험 존재·사전점검 대상 여부·<b>수강 자격</b>을 서버가 확인한다.
     * 수강 판정은 기존 {@code CourseQueryService} 를 재사용해 시험 목록과 결과가 어긋나지 않게 한다.</p>
     *
     * <p>사전점검은 시험 시작 <b>전에</b> 미리 할 수 있어야 하므로 "응시 기간 안" 조건은 강제하지 않는다.
     * 대신 공개되지 않은/보관된 시험은 막는다.</p>
     */
    private Exam requirePrecheckable(Long examId, Long userId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IdentityAccessDeniedException("사전점검을 진행할 수 없는 시험입니다."));

        if (!PrecheckPolicy.requiresPrecheck(exam)) {
            /* 일반 시험·비밀번호 인증 시험은 이 화면 대상이 아니다.
               "이 시험은 대상이 아니다" 이상으로 시험 정보를 흘리지 않는다. */
            throw new IdentityAccessDeniedException("사전점검을 진행할 수 없는 시험입니다.");
        }
        if (exam.getStatus() == Exam.ExamStatus.DRAFT || exam.getStatus() == Exam.ExamStatus.ARCHIVED) {
            throw new IdentityAccessDeniedException("사전점검을 진행할 수 없는 시험입니다.");
        }

        Long courseId = exam.getCourse() == null ? null : exam.getCourse().getId();
        if (courseId == null || !courseQueryService.findUserIdsByCourseId(courseId).contains(userId)) {
            throw new IdentityAccessDeniedException("사전점검을 진행할 수 없는 시험입니다.");
        }
        return exam;
    }

    @Transactional
    public ExamIdentitySession openSession(Long examId, Long userId, String ip) {
        Exam exam0 = requirePrecheckable(examId, userId);
        LocalDateTime now = LocalDateTime.now();
        /* 동시에 두 요청이 들어와도 활성 세션이 하나만 남도록 행을 잠그고 읽는다. */
        Optional<ExamIdentitySession> existing = sessionRepository.lockLatest(examId, userId);

        if (existing.isPresent()) {
            ExamIdentitySession s = existing.get();

            /* 승인 유효시간이 지났으면 종결시키고 새로 연다 — 다시 신분확인을 받아야 한다. */
            if (s.isApprovalExpired(now)) {
                s.expire();
            }

            /* ★ 종결(EXPIRED)이 아니면 무조건 재사용한다.
               "제출할 수 있는 상태" 만 재사용하면 SUBMITTED·UNDER_REVIEW·APPROVED·REJECTED 에서
               새로고침할 때마다 새 PENDING 세션이 생긴다. 그러면 최종 반려도, 재제출 횟수 제한도
               F5 한 번으로 초기화된다. 최종 반려는 운영진이 풀어 주기 전까지 그대로 둔다. */
            if (!s.isTerminal()) {
                return s;
            }
            /* 만료됐으면 새 행을 만들지 않고 같은 행을 다시 연다 —
               (exam,user) 유니크 제약을 유지해야 동시 생성이 DB 에서 막힌다. */
            s.reopen();
            return s;
        }

        Exam exam = exam0;
        User user = examRefRepository.findUser(userId)
                .orElseThrow(() -> new IdentitySessionStateException("사용자를 찾을 수 없습니다."));

        /* 생성은 별도 트랜잭션에서 시도한다. 유니크 충돌로 지면 승자 행을 다시 읽는다.
           같은 트랜잭션에서 충돌시키면 영속성 컨텍스트가 깨져 재조회도 못 한다. */
        try {
            return sessionCreator.create(exam, user, ip);
        } catch (DataIntegrityViolationException e) {
            /* 동시 생성 경쟁에서 졌다 — (exam,user) 유니크 제약이 막아 줬으므로 승자 행을 읽는다.
               예외를 여기(트랜잭션 경계 바깥)에서 잡아야 내부 트랜잭션만 롤백된다. */
            return sessionRepository.findTopByExamIdAndUserIdOrderByIdDesc(examId, userId)
                    .orElseThrow(() -> new IdentitySessionStateException(
                            "세션 생성에 실패했습니다. 다시 시도해 주세요."));
        }
    }

    @Transactional(readOnly = true)
    public Optional<ExamIdentitySession> findSession(Long sessionId) {
        return sessionRepository.findById(sessionId);
    }

    /**
     * 시험 중·시험 후 조회 (LXP-016) — <b>이 응시가 통과한 증거</b>를 돌려준다.
     *
     * <p>세션이 아니라 증거 스냅샷을 준다. 세션은 재제출·만료로 값이 덮어써지므로,
     * 세션을 돌려주면 "1회차가 무엇을 근거로 입장했는가" 에 2회차 자료가 나온다 (P0-1).</p>
     */
    @Transactional(readOnly = true)
    public Optional<ExamIdentityVerification> findByAttempt(Long attemptId) {
        return verificationRepository.findByAttemptId(attemptId);
    }

    /* ===================== QR 토큰 ===================== */

    /**
     * 새 QR 토큰 발급. <b>이전에 살아 있던 토큰은 전부 폐기</b>한다.
     *
     * @return 원문 토큰. 이 값은 QR 에만 담기고 서버에는 해시만 남는다.
     */
    @Transactional
    public IssuedToken issueToken(Long sessionId, Long userId, String ip) {
        LocalDateTime now = LocalDateTime.now();
        /* 제출과 판정이 경쟁해도 한쪽 변경이 유실되지 않도록 세션 행을 잠근다 (P1-3). */
        ExamIdentitySession session = sessionRepository.lockById(sessionId)
                .orElseThrow(() -> new IdentitySessionStateException("신분확인 세션을 찾을 수 없습니다."));
        /* P0-1: 세션 id 는 연번이라 추측할 수 있다. 소유자 검증이 없으면 남의 QR 을
           재발급해 기존 토큰을 폐기시키는 서비스 거부가 가능하다. */
        requireOwner(session, userId);
        if (!session.acceptsSubmission()) {
            throw new IdentitySessionStateException(
                    "이미 판정이 끝난 세션입니다. 현재 상태: " + session.getStatus());
        }

        tokenRepository.findBySessionIdAndRevokedAtIsNull(sessionId)
                .forEach(t -> t.revoke(now));

        String raw = randomToken();
        ExamIdentityToken token = tokenRepository.save(ExamIdentityToken.builder()
                .session(session).tokenHash(sha256(raw)).issuedAt(now).issuedToIp(ip).build());

        audit(sessionId, null, userId, "TRAINEE", ExamIdentityAuditLog.Action.ISSUE_QR, null, ip);
        return new IssuedToken(raw, token.getExpiresAt(), token.remainingSeconds(now));
    }

    /** 토큰 원문으로 세션을 찾는다. 사용 불가면 사유를 담아 돌려준다. */
    @Transactional(readOnly = true)
    public TokenLookup lookup(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return new TokenLookup(null, ExamIdentityToken.Rejection.NOT_FOUND);
        }
        Optional<ExamIdentityToken> found = tokenRepository.findByTokenHash(sha256(rawToken));
        if (found.isEmpty()) {
            return new TokenLookup(null, ExamIdentityToken.Rejection.NOT_FOUND);
        }
        ExamIdentityToken token = found.get();
        return new TokenLookup(token, token.reject(LocalDateTime.now()));
    }

    /* ===================== 제출 ===================== */

    /**
     * 모바일에서 신분증 업로드.
     *
     * <p>성공해도 상태는 {@code SUBMITTED} 다. 승인은 운영진만 한다 —
     * 여기에 타이머 자동 승인을 넣으면 이 기능 전체가 의미를 잃는다.</p>
     */
    @Transactional
    public ExamIdentitySession submitIdCard(String rawToken, MultipartFile file, String ip) {
        LocalDateTime now = LocalDateTime.now();
        /* 검사와 소비 사이에 다른 요청이 끼어들지 못하도록 토큰 행을 잠그고 다시 판정한다. */
        ExamIdentityToken token = tokenRepository.lockByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new IdentitySessionStateException(
                        messageFor(ExamIdentityToken.Rejection.NOT_FOUND)));
        /* ★ 세션을 <b>먼저</b> 잠근다 (P1-3).
           token.reject() 안의 session.acceptsSubmission() 이 지연 로딩 프록시를 초기화하는데,
           그 시점에 읽은 값은 잠금 <b>이전</b> 스냅샷이다. Hibernate 는 이미 로딩된 엔티티를
           이후 잠금 조회로 덮어쓰지 않으므로, 그대로 두면 커밋 시 stale 값이 되돌아가
           동시에 들어온 얼굴 사진 포인터가 지워졌다 — 실제로 재현된 유실이다.
           getId() 는 FK 값이라 프록시를 초기화하지 않으므로 잠금 대상 id 로 안전하게 쓸 수 있다.
           잠금 순서는 항상 토큰 → 세션이라 교착이 생기지 않는다. */
        ExamIdentitySession session = sessionRepository.lockById(token.getSession().getId())
                .orElseThrow(() -> new IdentitySessionStateException("신분확인 세션을 찾을 수 없습니다."));

        ExamIdentityToken.Rejection rejection = token.reject(now);
        if (rejection != null) {
            throw new IdentitySessionStateException(messageFor(rejection));
        }

        PrivateFileStorage.Stored stored = storage.store(file, "id");
        deleteFileIfRollback(stored.storageKey());
        ExamIdentityDocument doc = documentRepository.save(ExamIdentityDocument.builder()
                .session(session)
                .kind(ExamIdentityDocument.Kind.ID_CARD)
                .storageKey(stored.storageKey())
                .contentType(stored.contentType())
                .sizeBytes(stored.sizeBytes())
                .sha256(stored.sha256())
                .width(stored.width())
                .height(stored.height())
                .uploadedAt(now)
                .uploadedFromIp(ip)
                .purgeAfter(now.plusDays(storage.retentionDays()))
                .build());

        session.attachIdCard(doc, now);
        token.consume(now);
        /* 모바일은 비로그인이라 actorUserId 가 없다 — 신원은 토큰이 보장한다. */
        audit(session.getId(), doc.getId(), session.getUser().getId(), "TRAINEE",
                ExamIdentityAuditLog.Action.SUBMIT_ID_CARD, null, ip);
        return session;
    }

    /**
     * 신분증 제출 + <b>안내 문구까지</b> 트랜잭션 안에서 만들어 돌려준다 (P1-2).
     *
     * <p>컨트롤러가 세션 엔티티를 받아 밖에서 읽으면 지연 로딩 프록시가 터진다.
     * 그리고 문구는 "얼굴 사진이 이미 있었는가" 에 따라 갈리므로 여기서 판정해야 한다.</p>
     */
    @Transactional
    public SubmitResult submitIdCardAndDescribe(String rawToken, MultipartFile file, String ip) {
        ExamIdentitySession s = submitIdCard(rawToken, file, ip);
        boolean complete = s.isSubmissionComplete();
        return new SubmitResult(
                s.getStatus().name(),
                mobileProgressLabel(s),
                s.getCurrentDocument() != null,
                s.getFaceCheckDocument() != null,
                complete,
                complete
                        ? "제출이 완료되었습니다. 운영진 검토를 기다려 주세요."
                        : "신분증 제출이 완료되었습니다. PC 시험 사전 점검 화면으로 돌아가 웹캠 얼굴 사진을 제출해 주세요.");
    }

    /** 모바일 업로드 결과 — 화면이 필요한 값만 담는다. */
    public record SubmitResult(String status, String statusLabel,
                               boolean hasIdCard, boolean hasFaceCheck,
                               boolean submissionComplete, String message) {
    }

    /**
     * 사전점검 통과 후 훈련생이 직접 촬영·제출한 얼굴 확인용 정지 이미지.
     * 자동 촬영이 아니라 사용자가 버튼을 눌러야만 호출된다.
     */
    @Transactional
    public ExamIdentitySession submitFaceCheck(Long sessionId, Long userId, MultipartFile file,
                                               boolean consent, String consentVersion, String ip) {
        LocalDateTime now = LocalDateTime.now();
        /* 제출과 판정이 경쟁해도 한쪽 변경이 유실되지 않도록 세션 행을 잠근다 (P1-3). */
        ExamIdentitySession session = sessionRepository.lockById(sessionId)
                .orElseThrow(() -> new IdentitySessionStateException("신분확인 세션을 찾을 수 없습니다."));
        requireOwner(session, userId);
        /* 동의는 화면 체크박스가 아니라 서버가 확인한다. 동의 없이 얼굴 이미지를 저장하면 안 된다. */
        if (!consent) {
            throw new IdentitySessionStateException("얼굴 확인용 사진 제출에는 별도 동의가 필요합니다.");
        }

        PrivateFileStorage.Stored stored = storage.store(file, "face");
        deleteFileIfRollback(stored.storageKey());
        ExamIdentityDocument doc = documentRepository.save(ExamIdentityDocument.builder()
                .session(session)
                .kind(ExamIdentityDocument.Kind.FACE_CHECK)
                .storageKey(stored.storageKey())
                .contentType(stored.contentType())
                .sizeBytes(stored.sizeBytes())
                .sha256(stored.sha256())
                .width(stored.width())
                .height(stored.height())
                .uploadedAt(now)
                .uploadedFromIp(ip)
                .purgeAfter(now.plusDays(storage.retentionDays()))
                .build());

        session.attachFaceCheck(doc, now, consentVersion);
        audit(session.getId(), doc.getId(), userId, "TRAINEE",
                ExamIdentityAuditLog.Action.SUBMIT_FACE_CHECK, "동의버전=" + consentVersion, ip);
        return session;
    }

    /* ===================== 운영진 판정 ===================== */

    @Transactional(readOnly = true)
    public List<ExamIdentitySession> queue() {
        return sessionRepository.findQueue(List.of(
                ExamIdentitySession.Status.SUBMITTED,
                ExamIdentitySession.Status.UNDER_REVIEW,
                ExamIdentitySession.Status.REJECTED,
                ExamIdentitySession.Status.RESUBMIT_REQUIRED,
                ExamIdentitySession.Status.APPROVED));
    }

    @Transactional
    public ExamIdentitySession markUnderReview(Long sessionId) {
        ExamIdentitySession s = requireSession(sessionId);
        if (s.getStatus() == ExamIdentitySession.Status.SUBMITTED) {
            s.markUnderReview();
        }
        return s;
    }

    @Transactional
    public ExamIdentitySession approve(Long sessionId, Long reviewerId, String role, String ip) {
        ExamIdentitySession s = requireSession(sessionId);
        requireStaffScope(s, reviewerId, role);
        s.approve(reviewer(reviewerId), LocalDateTime.now());
        audit(sessionId, s.getCurrentDocument() == null ? null : s.getCurrentDocument().getId(),
                reviewerId, role, ExamIdentityAuditLog.Action.APPROVE, null, ip);
        return s;
    }

    @Transactional
    public ExamIdentitySession reject(Long sessionId, Long reviewerId, String role,
                                      String reason, boolean requestResubmit, String ip) {
        ExamIdentitySession s = requireSession(sessionId);
        requireStaffScope(s, reviewerId, role);
        s.reject(reviewer(reviewerId), reason, requestResubmit, LocalDateTime.now());
        audit(sessionId, s.getCurrentDocument() == null ? null : s.getCurrentDocument().getId(),
                reviewerId, role,
                requestResubmit ? ExamIdentityAuditLog.Action.REQUEST_RESUBMIT
                                : ExamIdentityAuditLog.Action.REJECT,
                reason, ip);
        return s;
    }

    /**
     * 이미지 조회 — <b>객체 단위 권한</b> 검사 후 감사 기록.
     *
     * <p>URL 역할 검사만으로는 부족하다. documentId 를 하나씩 바꿔 가며 남의 신분증을
     * 훑는 접근을 여기서 막는다.</p>
     */
    @Transactional
    public ImageBytes readImageFor(Long documentId, Long viewerId, String role, String ip) {
        ExamIdentityDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IdentityAccessDeniedException("이미지를 찾을 수 없습니다."));

        requireStaffScope(doc.getSession(), viewerId, role);
        if (doc.isPurged()) {
            /* 파기된 자료는 410 으로 구분한다 — 없는 것과 지운 것은 다르다. */
            throw new IdentityGoneException("보존기간이 지나 파기된 이미지입니다.");
        }
        if (!storage.exists(doc.getStorageKey())) {
            throw new IdentityGoneException("파일이 저장소에 없습니다. 파기되었을 수 있습니다.");
        }

        byte[] bytes = storage.read(doc.getStorageKey());
        audit(doc.getSession().getId(), documentId, viewerId, role,
                ExamIdentityAuditLog.Action.VIEW_IMAGE, null, ip);
        return new ImageBytes(bytes, doc.getContentType());
    }

    /** 검토 시작 — GET 이 아니라 명시적 동작으로만 상태를 바꾼다 (P0-5). */
    @Transactional
    public ExamIdentitySession openReview(Long sessionId, Long reviewerId, String role, String ip) {
        ExamIdentitySession s = requireSession(sessionId);
        requireStaffScope(s, reviewerId, role);
        /* 완전 제출이 아니면 검토 시작 자체를 하지 않는다 (일부 제출 승인 방지). */
        if (s.getStatus() == ExamIdentitySession.Status.SUBMITTED && s.isReviewable()) {
            s.markUnderReview();
            audit(sessionId, null, reviewerId, role, ExamIdentityAuditLog.Action.OPEN_REVIEW, null, ip);
        }
        return s;
    }

    public record ImageBytes(byte[] bytes, String contentType) {
    }

    /** 사전점검 화면이 웹캠 통과를 서버에 알린다. 본인 세션만 기록한다. */
    @Transactional
    public void markWebcamChecked(Long sessionId, Long userId) {
        ExamIdentitySession s = requireSession(sessionId);
        requireOwner(s, userId);
        s.markWebcamChecked(LocalDateTime.now());
    }

    /* ===================== 입장 게이트 ===================== */

    /**
     * 시험 시작 시 서버가 부르는 판정. 화면 배지·URL 파라미터는 보지 않는다.
     *
     * @return 통과하면 세션, 아니면 사유를 담은 예외
     */
    @Transactional(readOnly = true)
    public ExamIdentitySession requireApproved(Long examId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        ExamIdentitySession s = sessionRepository.findTopByExamIdAndUserIdOrderByIdDesc(examId, userId)
                .orElseThrow(() -> new IdentitySessionStateException(
                        "신분 확인이 완료되지 않았습니다. 사전점검 화면에서 신분증을 제출해 주세요."));

        if (!s.getExam().getId().equals(examId) || !s.getUser().getId().equals(userId)) {
            throw new IdentitySessionStateException("다른 시험 또는 다른 사용자의 신분확인입니다.");
        }
        if (s.isApprovalExpired(now)) {
            throw new IdentitySessionStateException(
                    "신분 확인 승인이 만료되었습니다(" + ExamIdentitySession.APPROVAL_VALID_MINUTES
                            + "분). 사전점검 화면에서 다시 진행해 주세요.");
        }
        if (!s.isWebcamFresh(now, ExamIdentitySession.WEBCAM_VALID_MINUTES)
                && s.getStatus() == ExamIdentitySession.Status.APPROVED) {
            throw new IdentitySessionStateException(
                    "웹캠 연결 확인이 오래되었습니다(" + ExamIdentitySession.WEBCAM_VALID_MINUTES
                            + "분). 사전점검 화면에서 웹캠을 다시 확인해 주세요.");
        }
        if (!s.canEnter(now)) {
            throw new IdentitySessionStateException(switch (s.getStatus()) {
                case PENDING -> "아직 신분증이 제출되지 않았습니다.";
                case SUBMITTED, UNDER_REVIEW -> "운영진이 신분증을 검토 중입니다. 승인 후 입장할 수 있습니다.";
                case REJECTED -> "신분증이 최종 반려되었습니다. 사유: "
                        + (s.getDecisionReason() == null ? "(사유 없음)" : s.getDecisionReason());
                case RESUBMIT_REQUIRED -> "재제출이 요청되었습니다. 사유: "
                        + (s.getDecisionReason() == null ? "(사유 없음)" : s.getDecisionReason());
                case EXPIRED -> "신분확인 세션이 만료되었습니다. 다시 진행해 주세요.";
                default -> "신분 확인이 완료되지 않았습니다.";
            });
        }
        return s;
    }

    /**
     * 이 시험이 QR 신분확인 사전점검 대상인가.
     * 감독이 켜져 있고 본인확인이 필요한 시험만 대상으로 본다 — 나머지는 기존 흐름 유지.
     */
    @Transactional(readOnly = true)
    public boolean requiresPrecheck(Long examId) {
        return examRepository.findById(examId)
                .map(PrecheckPolicy::requiresPrecheck)
                .orElse(false);
    }

    /**
     * start() 성공 직후 — 이 응시가 통과한 <b>정확한 증거를 스냅샷</b>으로 남긴다 (P0-1).
     *
     * <p>세션은 (시험,사용자) 당 한 행이라 재제출·만료로 값이 덮어써진다. 그래서 세션에
     * attempt 를 하나만 달아 두던 이전 구조는 두 번째 응시를 <b>조용히 무시</b>했고,
     * LXP-016 의 사후 감사가 거짓이 됐다. 지금은 응시마다 별도 증거 행을 만든다.</p>
     *
     * <p>연결 직전에 소유자·시험·승인 상태를 다시 확인한다. 게이트 통과와 이 호출 사이에
     * 상태가 바뀌었을 수 있고, 승인되지 않은 세션의 증거가 남으면 감사가 거짓이 된다.</p>
     *
     * <p>이미 이 attempt 의 증거가 있으면 아무것도 하지 않는다(멱등). 기존 증거를
     * 덮어쓰거나 지우지 않는다 — 첫 이력이 사라지면 안 된다.</p>
     */
    @Transactional
    public ExamIdentityVerification linkAttemptToSession(Long sessionId, ExamAttempt attempt) {
        ExamIdentitySession s = requireSession(sessionId);

        if (!s.getUser().getId().equals(attempt.getUser().getId())) {
            throw new IdentityAccessDeniedException("다른 사용자의 신분확인 세션에는 연결할 수 없습니다.");
        }
        if (!s.getExam().getId().equals(attempt.getExam().getId())) {
            throw new IdentityAccessDeniedException("다른 시험의 신분확인 세션에는 연결할 수 없습니다.");
        }
        if (s.getStatus() != ExamIdentitySession.Status.APPROVED) {
            throw new IdentitySessionStateException(
                    "승인되지 않은 세션에는 응시 회차를 연결할 수 없습니다. 현재 상태: " + s.getStatus());
        }

        /* 같은 attempt 로 두 번 들어와도 증거는 하나 (멱등). */
        Optional<ExamIdentityVerification> existing =
                verificationRepository.findByAttemptId(attempt.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        ExamIdentityVerification v = verificationRepository.save(
                ExamIdentityVerification.snapshotOf(s, attempt, LocalDateTime.now()));
        audit(s.getId(), null, s.getUser().getId(), "TRAINEE",
                ExamIdentityAuditLog.Action.LINK_ATTEMPT,
                "attempt=" + attempt.getId() + " cycle=" + v.getCycleNo(), null);
        return v;
    }

    /* ===================== 보존기간 파기 (지적 9.3) ===================== */

    /**
     * 보존기간이 지난 문서의 <b>실제 파일</b>을 지운다.
     *
     * <p>삭제에 성공했거나 파일이 이미 없을 때만 {@code purgedAt} 을 찍는다 —
     * 실패를 성공으로 기록하면 "지웠다고 되어 있는데 파일은 남은" 상태가 된다.
     * 이미 파기된 문서는 건너뛰므로 여러 번 돌려도 결과가 같다(멱등).</p>
     *
     * @return 이번 실행에서 파기한 문서 수
     */
    @Transactional
    public int purgeExpiredDocuments() {
        LocalDateTime now = LocalDateTime.now();
        List<ExamIdentityDocument> targets = documentRepository.findPurgeTargets(now);
        int purged = 0;
        for (ExamIdentityDocument doc : targets) {
            boolean deleted = storage.delete(doc.getStorageKey());
            boolean gone = deleted || !storage.exists(doc.getStorageKey());
            if (!gone) {
                log.warn("보존기간 파기 실패 — 파일이 남아 있어 purgedAt 을 기록하지 않습니다: docId={}", doc.getId());
                continue;
            }
            doc.markPurged(now);
            audit(doc.getSession().getId(), doc.getId(), null, "SYSTEM",
                    ExamIdentityAuditLog.Action.PURGE, "보존기간 경과", null);
            purged++;
        }
        return purged;
    }

    /* ===================== 이미지 조회 ===================== */

    @Transactional(readOnly = true)
    public ExamIdentityDocument requireDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new IdentitySessionStateException("이미지를 찾을 수 없습니다."));
    }

    public byte[] readImage(ExamIdentityDocument doc) {
        if (doc.isPurged()) {
            throw new IdentitySessionStateException("보존기간이 지나 파기된 이미지입니다.");
        }
        return storage.read(doc.getStorageKey());
    }

    /* ===================== 내부 ===================== */

    /**
     * 파일을 저장한 뒤 트랜잭션이 롤백되면 파일만 남는다(고아 파일). 커밋 실패까지 잡으려면
     * 메서드 안 try/catch 로는 부족해서 트랜잭션 동기화에 삭제 콜백을 건다 (지적 9.1).
     */
    private void deleteFileIfRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    boolean removed = storage.delete(storageKey);
                    log.warn("트랜잭션이 커밋되지 않아 업로드 파일을 삭제했습니다: key={} removed={}",
                            storageKey, removed);
                }
            }
        });
    }

    /** 감사 기록. 실패해도 본 흐름을 막지 않되, 남기지 못한 사실은 로그로 남긴다. */
    private void audit(Long sessionId, Long documentId, Long actorUserId, String actorRole,
                       ExamIdentityAuditLog.Action action, String reason, String ip) {
        try {
            auditRepository.save(ExamIdentityAuditLog.builder()
                    .sessionId(sessionId).documentId(documentId)
                    .actorUserId(actorUserId).actorRole(actorRole)
                    .action(action).reason(reason)
                    .occurredAt(LocalDateTime.now()).ip(ip).build());
        } catch (Exception e) {
            log.warn("신분확인 감사 로그 기록 실패: session={} action={}", sessionId, action, e);
        }
    }

    /** 세션별 감사 이력 (LXP-016). */
    @Transactional(readOnly = true)
    public List<ExamIdentityAuditLog> auditTrail(Long sessionId, Long viewerId, String role) {
        requireStaffScope(requireSession(sessionId), viewerId, role);
        return auditRepository.findBySessionIdOrderByIdDesc(sessionId);
    }

    /**
     * 운영자 접근 범위 검사 (지적 6).
     *
     * <p><b>ADMIN</b> 은 전체 접근. <b>INSTRUCTOR</b> 는 <b>자신이 담당한 시험</b>만.
     * 역할만 보고 열어 주면 강사가 남의 과정 응시자 신분증을 훑을 수 있다.</p>
     */
    private static void requireStaffScope(ExamIdentitySession session, Long viewerId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("INSTRUCTOR".equals(role)) {
            User instructor = session.getExam().getInstructor();
            if (instructor != null && instructor.getId().equals(viewerId)) {
                return;
            }
            throw new IdentityAccessDeniedException("담당하지 않은 시험의 신분확인 자료입니다.");
        }
        throw new IdentityAccessDeniedException("신분증을 조회할 권한이 없습니다.");
    }

    /** 대기열·상세·판정에도 같은 범위를 적용한다. */
    @Transactional(readOnly = true)
    public void assertCanReview(Long sessionId, Long viewerId, String role) {
        requireStaffScope(requireSession(sessionId), viewerId, role);
    }

    /** 세션 소유자 확인. 남의 세션이면 화면에 아무것도 흘리지 않고 거부한다. */
    private static void requireOwner(ExamIdentitySession session, Long userId) {
        if (userId == null || session.getUser() == null
                || !session.getUser().getId().equals(userId)) {
            throw new IdentityAccessDeniedException("본인의 신분확인 세션이 아닙니다.");
        }
    }

    /**
     * 변경 경로용 세션 조회 — <b>행을 잠근다</b> (P1-3).
     *
     * <p>검토 시작·승인·반려·재제출 요청·attempt 연결이 모두 이 경로를 탄다. 잠그지 않으면
     * 훈련생 제출과 운영진 판정이 겹칠 때 한쪽 변경이 조용히 덮어써진다.</p>
     */
    private ExamIdentitySession requireSession(Long sessionId) {
        return sessionRepository.lockById(sessionId)
                .orElseThrow(() -> new IdentitySessionStateException("신분확인 세션을 찾을 수 없습니다."));
    }

    private User reviewer(Long reviewerId) {
        return examRefRepository.findUser(reviewerId)
                .orElseThrow(() -> new IdentitySessionStateException("검토자를 찾을 수 없습니다."));
    }

    private static String randomToken() {
        byte[] b = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 사용 불가", e);
        }
    }

    public static String messageFor(ExamIdentityToken.Rejection r) {
        return switch (r) {
            case NOT_FOUND -> "유효하지 않은 주소입니다. PC 화면에서 QR을 다시 확인해 주세요.";
            case EXPIRED -> "QR이 만료되었습니다. PC 화면에서 '새 QR 발급'을 눌러 주세요.";
            case REVOKED -> "이 QR은 더 이상 사용할 수 없습니다. PC 화면의 최신 QR을 사용해 주세요.";
            case USED_UP -> "이 QR의 제출 횟수를 모두 사용했습니다. PC 화면에서 새 QR을 발급해 주세요.";
            case SESSION_CLOSED -> "이미 판정이 끝난 신분확인입니다. PC 화면에서 상태를 확인해 주세요.";
        };
    }


    /* ===================== 화면용 조회 (트랜잭션 안에서 값만 뽑는다) ===================== */

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** PC 사전점검 진입 — 세션을 열고 화면에 필요한 값만 담아 돌려준다. */
    @Transactional
    public IdentityViews.Precheck openPrecheck(Long examId, Long userId, String ip) {
        ExamIdentitySession s = openSession(examId, userId, ip);
        return new IdentityViews.Precheck(s.getId(), examId, s.getExam().getExamName(),
                s.getUser().getName(), s.getStatus().name());
    }

    /** PC 폴링 — 본인 세션이 아니면 비어 있는 값을 준다. */
    @Transactional(readOnly = true)
    public Optional<IdentityViews.Status> statusOf(Long sessionId, Long userId) {
        return sessionRepository.findById(sessionId)
                .filter(s -> s.getUser().getId().equals(userId))
                .map(s -> new IdentityViews.Status(
                        s.getStatus().name(), progressLabel(s), s.canEnter(LocalDateTime.now()),
                        s.getDecisionReason(), s.getResubmitCount(),
                        s.getCurrentDocument() != null, s.getFaceCheckDocument() != null,
                        s.isSubmissionComplete(), s.getApprovalExpiresAt()));
    }

    /** 모바일 화면 — 토큰이 막혀 있으면 사유만 담아 돌려준다. */
    @Transactional(readOnly = true)
    public IdentityViews.Mobile describeMobile(String rawToken) {
        TokenLookup lookup = lookup(rawToken);
        if (lookup.rejection() != null) {
            return IdentityViews.Mobile.blocked(messageFor(lookup.rejection()), lookup.rejection().name());
        }
        ExamIdentityToken t = lookup.token();
        ExamIdentitySession s = t.getSession();
        return new IdentityViews.Mobile(false, null, null,
                s.getExam().getExamName(), s.getUser().getName(),
                s.getStatus().name(), mobileProgressLabel(s), s.getDecisionReason(),
                t.remainingSeconds(LocalDateTime.now()),
                s.getCurrentDocument() != null, s.getFaceCheckDocument() != null);
    }

    /** 모바일 폴링 — 판정이 끝나 토큰이 막혀도 결과는 보여줘야 한다. */
    /**
     * 모바일 상태 조회.
     *
     * <p>만료·폐기된 토큰으로 <b>무기한</b> 상태를 들여다볼 수 없게 한다 (지적 10).
     * 판정 결과는 보여줘야 하므로 업로드 권한(TTL 10분)보다는 길게, 그러나 무제한은 아니게
     * 발급 후 {@value #STATUS_VIEW_MINUTES}분까지만 허용한다.</p>
     */
    @Transactional(readOnly = true)
    public Optional<IdentityViews.Status> mobileStatus(String rawToken) {
        LocalDateTime now = LocalDateTime.now();
        return tokenRepository.findByTokenHash(sha256(rawToken))
                .filter(t -> t.getIssuedAt().isAfter(now.minusMinutes(STATUS_VIEW_MINUTES)))
                .map(ExamIdentityToken::getSession)
                .map(s -> new IdentityViews.Status(
                        s.getStatus().name(), mobileProgressLabel(s), s.canEnter(LocalDateTime.now()),
                        s.getDecisionReason(), s.getResubmitCount(),
                        s.getCurrentDocument() != null, s.getFaceCheckDocument() != null,
                        s.isSubmissionComplete(), s.getApprovalExpiresAt()));
    }

    /** 운영진 대기열. */
    /** 대기열 — 강사는 자신이 담당한 시험만 본다 (지적 6). */
    @Transactional(readOnly = true)
    public List<IdentityViews.Row> queueRows(Long viewerId, String role) {
        LocalDateTime now = LocalDateTime.now();
        return queue().stream()
                .filter(s -> canReview(s, viewerId, role))
                .map(s -> toRow(s, now))
                .toList();
    }

    private static boolean canReview(ExamIdentitySession s, Long viewerId, String role) {
        try {
            requireStaffScope(s, viewerId, role);
            return true;
        } catch (IdentityAccessDeniedException e) {
            return false;
        }
    }

    /**
     * 운영진 상세 — <b>조회만</b> 한다. GET 이 상태를 바꾸면 크롤러·프리페치·새로고침이
     * 검토 이력을 오염시킨다. 검토 시작은 {@link #openReview} 로 분리했다 (P0-5).
     */
    @Transactional(readOnly = true)
    public IdentityViews.Row detailRow(Long sessionId, Long viewerId, String role) {
        ExamIdentitySession s = requireSession(sessionId);
        requireStaffScope(s, viewerId, role);
        return toRow(s, LocalDateTime.now());
    }

    private IdentityViews.Row toRow(ExamIdentitySession s, LocalDateTime now) {
        LocalDateTime submitted = s.getCurrentDocument() == null ? null : s.getCurrentDocument().getUploadedAt();
        String waited = "\u2014";
        if (submitted != null && (s.getStatus() == ExamIdentitySession.Status.SUBMITTED
                || s.getStatus() == ExamIdentitySession.Status.UNDER_REVIEW)) {
            long min = java.time.Duration.between(submitted, now).toMinutes();
            waited = min < 1 ? "\ubc29\uae08" : min + "\ubd84";
        }
        return new IdentityViews.Row(
                s.getId(), s.getExam().getExamName(), s.getUser().getName(), s.getUser().getLoginId(),
                submitted == null ? "\u2014" : submitted.format(STAMP), waited,
                s.getStatus().name(), label(s.getStatus()), tone(s.getStatus()),
                s.getDecisionReason(), s.getResubmitCount(),
                s.getCurrentDocument() == null ? null : s.getCurrentDocument().getId(),
                s.getFaceCheckDocument() == null ? null : s.getFaceCheckDocument().getId(),
                verificationRepository.findBySessionIdOrderByIdAsc(s.getId()).stream()
                        .reduce((a, b) -> b)          /* 가장 최근 응시 */
                        .map(v -> v.getAttempt().getId()).orElse(null));
    }

    /**
     * 부분 제출을 <b>구분해서</b> 보여 주는 라벨 (P1-2).
     *
     * <p>도메인은 신분증·얼굴 중 하나만 들어오면 {@code PENDING} 을 유지한다. 그런데 화면은
     * {@code PENDING} 을 무조건 "미제출" 로 찍어서, 신분증을 낸 사람에게도 미제출이라고 보여 줬다.
     * 상태 enum 만으로는 부족하므로 두 포인터를 함께 본다.</p>
     */
    public static String progressLabel(ExamIdentitySession s) {
        boolean id = s.getCurrentDocument() != null;
        boolean face = s.getFaceCheckDocument() != null;
        return switch (s.getStatus()) {
            case PENDING -> partial(id, face);
            case RESUBMIT_REQUIRED -> id || face
                    ? "재제출 진행 중 — " + partial(id, face)
                    : "재제출 요청";
            default -> label(s.getStatus());
        };
    }

    /** PENDING 안의 세 가지 실제 상태. */
    private static String partial(boolean id, boolean face) {
        if (id && face) {
            /* 두 자료가 다 있는데 PENDING 이면 동의 증거가 빠진 것이다. */
            return "얼굴 사진 동의 증거가 필요합니다";
        }
        if (id) {
            return "신분증 제출 완료 — 얼굴 사진 미제출";
        }
        if (face) {
            return "얼굴 사진 제출 완료 — 신분증 미제출";
        }
        return "신분증 · 얼굴 사진 모두 미제출";
    }

    /** 모바일용 부분 제출 라벨. 운영진 문구만 모바일 표현으로 바꾼다. */
    public static String mobileProgressLabel(ExamIdentitySession s) {
        return switch (s.getStatus()) {
            case PENDING, RESUBMIT_REQUIRED -> progressLabel(s);
            default -> mobileLabel(s.getStatus());
        };
    }

    public static String label(ExamIdentitySession.Status s) {
        return switch (s) {
            case PENDING -> "\ubbf8\uc81c\ucd9c";
            case SUBMITTED -> "\ub450 \uc790\ub8cc \uc81c\ucd9c \uc644\ub8cc \u2014 \uc6b4\uc601\uc9c4 \uac80\ud1a0 \ub300\uae30";
            case UNDER_REVIEW -> "\uac80\ud1a0 \uc911";
            case APPROVED -> "\uc2b9\uc778";
            case REJECTED -> "\ucd5c\uc885 \ubc18\ub824";
            case RESUBMIT_REQUIRED -> "\uc7ac\uc81c\ucd9c \uc694\uccad";
            case EXPIRED -> "\ub9cc\ub8cc";
        };
    }

    public static String mobileLabelOf(ExamIdentitySession s) {
        return mobileProgressLabel(s);
    }

    static String mobileLabel(ExamIdentitySession.Status s) {
        return switch (s) {
            case UNDER_REVIEW -> "\uc6b4\uc601\uc9c4 \uac80\ud1a0 \uc911";
            case RESUBMIT_REQUIRED -> "\ubc18\ub824 \u2014 \uc7ac\uc81c\ucd9c \ud544\uc694";
            default -> label(s);
        };
    }

    /** 운영 화면 상태색 문법 (ok / warn / risk). */
    public static String tone(ExamIdentitySession.Status s) {
        return switch (s) {
            case APPROVED -> "ok";
            case SUBMITTED, UNDER_REVIEW -> "warn";
            case REJECTED, EXPIRED -> "risk";
            case RESUBMIT_REQUIRED -> "risk";
            case PENDING -> "";
        };
    }

    public record IssuedToken(String rawToken, LocalDateTime expiresAt, long remainingSeconds) {
    }

    public record TokenLookup(ExamIdentityToken token, ExamIdentityToken.Rejection rejection) {
    }
}
