package com.ssa.lms.identity.support;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.exam.entity.Exam;
import com.ssa.lms.exam.entity.ExamAttempt;
import com.ssa.lms.exam.repository.ExamAttemptRepository;
import com.ssa.lms.exam.repository.ExamRepository;
import com.ssa.lms.identity.entity.ExamIdentityDocument;
import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.repository.ExamIdentityDocumentRepository;
import com.ssa.lms.identity.repository.ExamIdentityAuditLogRepository;
import com.ssa.lms.identity.repository.ExamIdentityVerificationRepository;
import com.ssa.lms.identity.repository.ExamIdentitySessionRepository;
import com.ssa.lms.identity.repository.ExamIdentityTokenRepository;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.proctor.repository.ExamEventLogRepository;
import com.ssa.lms.identity.policy.PrecheckPolicy;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.course.service.CourseQueryService;
import com.ssa.lms.user.repository.UserRepository;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 신분확인 테스트 fixture.
 *
 * <p>시드 데이터에 조건이 맞는 시험이 있기를 기대하고 <b>조용히 건너뛰지 않는다</b>(지적 8).
 * 필요한 조건(감독 ON + 본인확인 ON, 응시 기간 안, 문항 확정)을 직접 만들어 준다.</p>
 */
@Component
public class IdentityTestFixture {

    private final ExamRepository examRepository;
    private final ExamAttemptRepository attemptRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ExamIdentitySessionRepository sessionRepository;
    private final ExamIdentityDocumentRepository documentRepository;
    private final ExamEventLogRepository eventLogRepository;
    private final ExamIdentityTokenRepository tokenRepository;
    private final ExamIdentityAuditLogRepository auditRepository;
    private final ExamIdentityVerificationRepository verificationRepository;
    private final com.ssa.lms.user.repository.AccessLogRepository accessLogRepository;
    private final ExamIdentityService identityService;
    private final CourseQueryService courseQueryService;

    public IdentityTestFixture(ExamRepository examRepository, ExamAttemptRepository attemptRepository,
                               CourseRepository courseRepository,
                               UserRepository userRepository,
                               ExamIdentitySessionRepository sessionRepository,
                               ExamIdentityDocumentRepository documentRepository,
                               ExamEventLogRepository eventLogRepository,
                               ExamIdentityTokenRepository tokenRepository,
                               ExamIdentityAuditLogRepository auditRepository,
                               ExamIdentityVerificationRepository verificationRepository,
                               com.ssa.lms.user.repository.AccessLogRepository accessLogRepository,
                               ExamIdentityService identityService,
                               CourseQueryService courseQueryService) {
        this.examRepository = examRepository;
        this.attemptRepository = attemptRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.documentRepository = documentRepository;
        this.eventLogRepository = eventLogRepository;
        this.tokenRepository = tokenRepository;
        this.auditRepository = auditRepository;
        this.verificationRepository = verificationRepository;
        this.accessLogRepository = accessLogRepository;
        this.identityService = identityService;
        this.courseQueryService = courseQueryService;
    }

    public record Ctx(Long examId, Long userId, Long adminId, Long instructorId) {
    }

    /**
     * 감독 + 본인확인이 켜진 시험을 시드에서 찾아 준다.
     *
     * <p>시드에 없으면 테스트를 건너뛰는 대신 <b>즉시 실패</b>시킨다 — 조건이 사라졌다는 뜻이고,
     * 그 사실을 조용히 넘기면 게이트가 실제로 검증되지 않는다.</p>
     */
    @Transactional
    public Ctx newProctoredExamAndTrainee() {
        Exam exam = examRepository.findAll().stream()
                .filter(e -> e.isProctorEnabled() && e.isRequireIdentityVerification())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "감독+본인확인이 켜진 시험이 시드에 없습니다. 게이트를 검증할 수 없으므로 실패로 처리합니다."));

        /* 게이트까지 도달하려면 응시 기간 안이어야 한다. 기간 밖이면 OUT_OF_WINDOW 로 먼저 막혀
           "왜 막혔는지" 를 검증할 수 없다. 조용히 건너뛰는 대신 조건을 직접 만든다 (지적 8). */
        ReflectionTestUtils.setField(exam, "windowStart", LocalDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(exam, "windowEnd", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(exam, "status", Exam.ExamStatus.OPEN);
        /* 재응시를 허용해 둔다. 각 응시가 자기 검증 증거에 연결되는지 보려면
           두 번째 회차가 실제로 만들어져야 한다 (P0-1). */
        ReflectionTestUtils.setField(exam, "retakeAllowed", true);
        ReflectionTestUtils.setField(exam, "maxAttempts", 3);

        User trainee = userRepository.findByLoginId("trainee1").orElseThrow();
        Long adminId = userRepository.findByLoginId("admin").map(User::getId).orElseThrow();
        Long instructorId = userRepository.findByLoginId("instructor1").map(User::getId).orElse(adminId);

        /* 테스트끼리 (exam, user) 를 공유하면 세션 재사용 정책 때문에 앞 테스트의 상태가
           그대로 새어 들어온다. 남아 있던 세션을 지우고 깨끗한 상태에서 시작한다. */
        sessionRepository.findTopByExamIdAndUserIdOrderByIdDesc(exam.getId(), trainee.getId())
                .ifPresent(old -> {
                    /* FK 순서: 세션이 current/face document 를 잡고, 토큰·문서가 세션을 잡는다.
                       세션의 문서 참조를 먼저 끊은 뒤 토큰 → 문서 → 세션 순으로 지운다. */
                    ReflectionTestUtils.setField(old, "currentDocument", null);
                    ReflectionTestUtils.setField(old, "faceCheckDocument", null);
                    sessionRepository.saveAndFlush(old);

                    tokenRepository.deleteAll(tokenRepository.findAllBySessionId(old.getId()));
                    tokenRepository.flush();
                    /* 증거 스냅샷이 문서를 FK 로 잡으므로 문서보다 <b>먼저</b> 지운다. */
                    verificationRepository.deleteAll(
                            verificationRepository.findBySessionIdOrderByIdAsc(old.getId()));
                    verificationRepository.flush();
                    documentRepository.deleteAll(
                            documentRepository.findBySessionIdOrderByIdDesc(old.getId()));
                    documentRepository.flush();
                    auditRepository.deleteAll(auditRepository.findBySessionIdOrderByIdDesc(old.getId()));
                    sessionRepository.delete(old);
                });
        /* 앞 테스트가 남긴 IN_PROGRESS attempt 가 있으면 start() 가 이어하기로 빠져
           게이트를 타지 않는다. 회차도 함께 정리한다. */
        attemptRepository.findByExamIdAndUserIdOrderByAttemptNoDesc(exam.getId(), trainee.getId())
                .forEach(a -> {
                    /* 이벤트 로그가 attempt 를 FK 로 잡고 있어 먼저 지워야 한다. */
                    eventLogRepository.deleteAll(
                            eventLogRepository.findByAttemptIdOrderByOccurredAtAsc(a.getId()));
                    /* 증거 스냅샷이 attempt 를 FK 로 잡고 있어 먼저 지운다 (P0-1). */
                    verificationRepository.findByAttemptId(a.getId())
                            .ifPresent(verificationRepository::delete);
                    verificationRepository.flush();
                    attemptRepository.delete(a);
                });
        eventLogRepository.flush();
        sessionRepository.flush();

        return new Ctx(exam.getId(), trainee.getId(), adminId, instructorId);
    }

    /** 감독이 꺼진 시험 — 기존 흐름 회귀 검증용. */
    @Transactional
    public Exam plainExam() {
        return examRepository.findAll().stream()
                .filter(e -> !(e.isProctorEnabled() && e.isRequireIdentityVerification()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("비감독 시험이 시드에 없습니다."));
    }

    /** 이 시험의 담당 강사인가. 시드 구성에 따라 달라지므로 테스트가 분기할 수 있게 노출한다. */
    @Transactional
    public boolean isExamInstructor(Long examId, Long userId) {
        return examRepository.findById(examId)
                .map(Exam::getInstructor)
                .map(i -> i.getId().equals(userId))
                .orElse(false);
    }

    /**
     * 이 시험의 담당 강사를 <b>명시적으로</b> 지정한다 (P1-1).
     *
     * <p>시드 데이터가 우연히 담당이냐 아니냐에 따라 테스트 분기가 갈리면, "비담당 강사 차단"
     * assertion 이 아예 실행되지 않는 날이 생긴다. 조건을 직접 만든다.</p>
     */
    @Transactional
    public void assignInstructor(Long examId, Long instructorId) {
        Exam exam = examRepository.findById(examId).orElseThrow();
        User u = userRepository.findById(instructorId).orElseThrow();
        ReflectionTestUtils.setField(exam, "instructor", u);
        examRepository.flush();
    }

    /** 지정한 강사가 <b>담당이 아니도록</b> 만든다. 다른 사용자를 담당으로 박는다. */
    @Transactional
    public void assignOtherInstructor(Long examId, Long notThisUserId) {
        Exam exam = examRepository.findById(examId).orElseThrow();
        User other = userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(notThisUserId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("다른 사용자가 없어 비담당 조건을 만들 수 없습니다."));
        ReflectionTestUtils.setField(exam, "instructor", other);
        examRepository.flush();
        if (other.getId().equals(notThisUserId)) {
            throw new IllegalStateException("비담당 조건 구성에 실패했습니다.");
        }
    }

    public Long instructor1Id() {
        return userRepository.findByLoginId("instructor1")
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("instructor1 계정이 시드에 없습니다."));
    }

    /* ===================== 실제 서비스 경로로 제출 =====================
       ReflectionTestUtils 로 상태를 주입하지 않는다. 사용자가 실제로 거치는
       서비스 공개 메서드를 그대로 호출한다 (지적 11). */

    /** 단색 도형 JPEG — 실제 신분증·얼굴 이미지를 저장소에 넣지 않는다. */
    public static MockMultipartFile imageFile(String name) {
        try {
            BufferedImage img = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(240, 240, 245));
            g.fillRect(0, 0, 800, 500);
            g.setColor(new Color(20, 40, 90));
            g.drawRect(20, 20, 760, 460);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", out);
            return new MockMultipartFile("file", name, "image/jpeg", out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * EXIF(APP1) 세그먼트를 넣은 JPEG. 실제 촬영 사진이 아니라 단색 도형 + 가짜 EXIF 다.
     * 저장본에서 이 세그먼트가 사라지는지 확인하는 용도다.
     */
    public static byte[] jpegWithExif() {
        byte[] base = rawJpeg();
        /* SOI(FFD8) 바로 뒤에 APP1 "Exif\0\0" 세그먼트를 끼워 넣는다. */
        byte[] payload = "Exif\u0000\u0000FAKE-GPS-DATA".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int segLen = payload.length + 2;
        byte[] out = new byte[base.length + 4 + payload.length];
        out[0] = base[0];
        out[1] = base[1];
        out[2] = (byte) 0xFF;
        out[3] = (byte) 0xE1;
        int i = 4;
        out[i++] = (byte) ((segLen >> 8) & 0xFF);
        out[i++] = (byte) (segLen & 0xFF);
        System.arraycopy(payload, 0, out, i, payload.length);
        i += payload.length;
        System.arraycopy(base, 2, out, i, base.length - 2);
        return out;
    }

    private static byte[] rawJpeg() {
        try {
            BufferedImage img = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(230, 235, 245));
            g.fillRect(0, 0, 800, 500);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 주어진 바이트로 신분증을 올린다. */
    public void uploadIdCardBytes(Long sessionId, byte[] bytes) {
        ExamIdentitySession s = sessionRepository.findById(sessionId).orElseThrow();
        ExamIdentityService.IssuedToken t =
                identityService.issueToken(sessionId, s.getUser().getId(), "127.0.0.1");
        identityService.submitIdCard(t.rawToken(),
                new MockMultipartFile("file", "id.jpg", "image/jpeg", bytes), "127.0.0.1");
    }

    /** 현재 신분증 문서 id (트랜잭션 안에서 읽는다 — LazyInit 회피). */
    @Transactional
    public Long currentIdDocumentId(Long sessionId) {
        ExamIdentitySession s = sessionRepository.findById(sessionId).orElseThrow();
        return s.getCurrentDocument() == null ? null : s.getCurrentDocument().getId();
    }

    /** 현재 얼굴 사진 문서 id (없으면 null). */
    @Transactional
    public Long faceDocumentId(Long sessionId) {
        ExamIdentitySession s = sessionRepository.findById(sessionId).orElseThrow();
        return s.getFaceCheckDocument() == null ? null : s.getFaceCheckDocument().getId();
    }

    /** 문서의 저장 키 (테스트 검증용). */
    @Transactional
    public String storageKeyOf(Long documentId) {
        return documentRepository.findById(documentId).orElseThrow().getStorageKey();
    }

    /** 문서 메타 (크기, 해시). */
    @Transactional
    public long[] sizeOf(Long documentId) {
        ExamIdentityDocument d = documentRepository.findById(documentId).orElseThrow();
        return new long[]{d.getSizeBytes()};
    }

    @Transactional
    public String sha256Of(Long documentId) {
        return documentRepository.findById(documentId).orElseThrow().getSha256();
    }

    @Transactional
    public boolean isPurged(Long documentId) {
        return documentRepository.findById(documentId).orElseThrow().isPurged();
    }

    @Transactional
    public LocalDateTime purgedAtOf(Long documentId) {
        return documentRepository.findById(documentId).orElseThrow().getPurgedAt();
    }

    /** 보존기간이 지난 것으로 만든다. */
    @Transactional
    public void expirePurge(Long documentId) {
        ExamIdentityDocument d = documentRepository.findById(documentId).orElseThrow();
        ReflectionTestUtils.setField(d, "purgeAfter", LocalDateTime.now().minusDays(1));
    }

    /** QR 토큰을 발급받아 모바일 업로드 경로로 신분증을 올린다. */
    public void uploadIdCard(Long sessionId) {
        ExamIdentitySession s = sessionRepository.findById(sessionId).orElseThrow();
        ExamIdentityService.IssuedToken t =
                identityService.issueToken(sessionId, s.getUser().getId(), "127.0.0.1");
        identityService.submitIdCard(t.rawToken(), imageFile("id.jpg"), "127.0.0.1");
    }

    /** 사전점검 경로로 얼굴 확인용 사진을 올린다 (동의 포함). */
    public void uploadFaceCheck(Long sessionId) {
        ExamIdentitySession s = sessionRepository.findById(sessionId).orElseThrow();
        identityService.submitFaceCheck(sessionId, s.getUser().getId(),
                imageFile("face.jpg"), true, "face-consent-v1", "127.0.0.1");
    }

    public Long trainee1Id() {
        return userRepository.findByLoginId("trainee1").orElseThrow().getId();
    }

    /** 감독+본인확인 시험 (응시 기간·상태를 테스트가 쓸 수 있게 맞춰 둔다). */
    @Transactional
    public Exam proctoredExam() {
        Exam e = examRepository.findAll().stream()
                .filter(PrecheckPolicy::requiresPrecheck)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("감독+본인확인 시험이 시드에 없습니다."));
        ReflectionTestUtils.setField(e, "windowStart", LocalDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(e, "windowEnd", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(e, "status", Exam.ExamStatus.OPEN);
        return e;
    }

    /**
     * 지정한 플래그 조합의 시험을 만들어 준다.
     * 시드에 그 조합이 없으면 <b>기존 시험의 플래그를 조정</b>해 조건을 직접 구성한다
     * (조용히 건너뛰지 않는다).
     */
    @Transactional
    public Exam examWith(boolean proctor, boolean identity) {
        Exam e = examRepository.findAll().stream()
                .filter(x -> x.isProctorEnabled() == proctor && x.isRequireIdentityVerification() == identity)
                .findFirst()
                .orElseGet(() -> {
                    Exam any = examRepository.findAll().stream()
                            .filter(x -> !PrecheckPolicy.requiresPrecheck(x))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("조정할 시험이 없습니다."));
                    ReflectionTestUtils.setField(any, "proctorEnabled", proctor);
                    ReflectionTestUtils.setField(any, "requireIdentityVerification", identity);
                    return any;
                });
        return e;
    }

    /**
     * 지금 <b>실제로 시작 가능한</b> 일반 시험을 만든다 (P1-6).
     *
     * <p>기존 회귀 테스트는 "IDENTITY_REQUIRED 만 아니면 통과" 라서, 응시 기간이 지났든
     * 문항이 없든 전부 초록불이었다. 시작이 진짜 되는지 보려면 조건을 직접 갖춰야 한다.</p>
     *
     * @param requireIdentity true 면 비밀번호 본인인증이 필요한 시험으로 만든다
     */
    @Transactional
    public Exam startablePlainExam(Long userId, boolean requireIdentity) {
        Exam base = examRepository.findById(proctoredExam().getId()).orElseThrow();
        Long courseId = base.getCourse() == null ? null : base.getCourse().getId();

        /* 같은 과정(= 같은 수강생) + <b>확정 문항이 있는</b> 시험이어야 실제로 시작된다.
           문항이 없으면 NOT_READY 로 먼저 막혀 회귀를 검증할 수 없다. */
        Exam target = examRepository.findAll().stream()
                .filter(e -> !e.getId().equals(base.getId()))
                .filter(e -> e.getCourse() != null && e.getCourse().getId().equals(courseId))
                .filter(e -> !examRepository.findWithQuestions(e.getId())
                        .map(x -> x.getExamQuestions().isEmpty()).orElse(true))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "같은 과정에 확정 문항이 있는 다른 시험이 없어 일반 시험 회귀를 검증할 수 없습니다."));

        ReflectionTestUtils.setField(target, "proctorEnabled", false);
        ReflectionTestUtils.setField(target, "requireIdentityVerification", requireIdentity);
        ReflectionTestUtils.setField(target, "windowStart", LocalDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(target, "windowEnd", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(target, "status", Exam.ExamStatus.OPEN);
        ReflectionTestUtils.setField(target, "retakeAllowed", true);
        ReflectionTestUtils.setField(target, "maxAttempts", 5);
        examRepository.flush();

        /* 앞 테스트가 남긴 회차를 지운다 — 남아 있으면 이어하기로 빠져 start() 가 새로 만들지 않는다. */
        attemptRepository.findByExamIdAndUserIdOrderByAttemptNoDesc(target.getId(), userId)
                .forEach(a -> {
                    eventLogRepository.deleteAll(
                            eventLogRepository.findByAttemptIdOrderByOccurredAtAsc(a.getId()));
                    verificationRepository.findByAttemptId(a.getId())
                            .ifPresent(verificationRepository::delete);
                    verificationRepository.flush();
                    attemptRepository.delete(a);
                });
        eventLogRepository.flush();
        attemptRepository.flush();
        return target;
    }

    /**
     * 최근 본인인증 이력을 지운다.
     *
     * <p>{@code verifyIdentity()} 는 최근 인증 이력이 있으면 비밀번호를 <b>보지 않고</b> 통과시킨다.
     * 앞 테스트가 남긴 이력이 있으면 "틀린 비밀번호는 실패한다" 가 우연히 성공한다.</p>
     */
    @Transactional
    public void clearRecentIdentityVerification(Long userId) {
        accessLogRepository.deleteAll(
                accessLogRepository.findAll().stream()
                        .filter(l -> userId.equals(l.getUserId()))
                        .filter(l -> l.getType() == com.ssa.lms.user.entity.AccessLog.Type.IDENTITY_VERIFY)
                        .toList());
        accessLogRepository.flush();
    }

    /** 이 시험 과정을 수강하지 않는 사용자. */
    @Transactional
    public Long userNotEnrolledIn(Long examId) {
        Exam e = examRepository.findById(examId).orElseThrow();
        Long courseId = e.getCourse() == null ? null : e.getCourse().getId();
        var enrolled = courseId == null ? java.util.Set.<Long>of()
                : java.util.Set.copyOf(courseQueryService.findUserIdsByCourseId(courseId));
        return userRepository.findAll().stream()
                .map(User::getId)
                .filter(id -> !enrolled.contains(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("비수강 사용자를 찾지 못했습니다."));
    }

    /** 서비스의 수강 검증을 그대로 거쳐 세션을 연다. */
    public ExamIdentitySession openSessionAs(Long examId, Long userId) {
        return identityService.openSession(examId, userId, "127.0.0.1");
    }

    /** 해당 (시험, 사용자) 세션을 정리한다. */
    @Transactional
    public void clearSessions(Long examId, Long userId) {
        sessionRepository.findTopByExamIdAndUserIdOrderByIdDesc(examId, userId).ifPresent(old -> {
            ReflectionTestUtils.setField(old, "currentDocument", null);
            ReflectionTestUtils.setField(old, "faceCheckDocument", null);
            sessionRepository.saveAndFlush(old);
            tokenRepository.deleteAll(tokenRepository.findAllBySessionId(old.getId()));
            tokenRepository.flush();
            documentRepository.deleteAll(documentRepository.findBySessionIdOrderByIdDesc(old.getId()));
            documentRepository.flush();
            auditRepository.deleteAll(auditRepository.findBySessionIdOrderByIdDesc(old.getId()));
            sessionRepository.delete(old);
            sessionRepository.flush();
        });
    }

    /** 다른 시험에 특정 사용자 소유 세션을 만든다 — (exam,user) 유니크 제약 회피용. */
    @Transactional
    public ExamIdentitySession sessionOwnedByOnOtherExam(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        for (Exam e : examRepository.findAll()) {
            if (sessionRepository.findTopByExamIdAndUserIdOrderByIdDesc(e.getId(), userId).isEmpty()) {
                return sessionRepository.saveAndFlush(ExamIdentitySession.builder()
                        .exam(e).user(user).createdIp("127.0.0.1").build());
            }
        }
        throw new IllegalStateException("빈 시험이 없어 타인 세션을 만들지 못했습니다.");
    }

    /** 수강 검증을 우회해 특정 사용자 소유 세션을 만든다 — 소유자 권한 테스트 전용. */
    @Transactional
    public ExamIdentitySession sessionOwnedBy(Long examId, Long userId) {
        Exam exam = examRepository.findById(examId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        return sessionRepository.save(ExamIdentitySession.builder()
                .exam(exam).user(user).createdIp("127.0.0.1").build());
    }

    /* ===================== 상태 만들기 (구조 세팅용) ===================== */

    @Transactional
    public ExamIdentityDocument saveDoc(Long sessionId, ExamIdentityDocument.Kind kind) {
        ExamIdentitySession s = sessionRepository.findById(sessionId).orElseThrow();
        return documentRepository.save(ExamIdentityDocument.builder()
                .session(s).kind(kind)
                .storageKey("test/" + kind + "-" + System.nanoTime())
                .contentType("image/jpeg").sizeBytes(1024).sha256("h")
                .width(800).height(500).uploadedAt(LocalDateTime.now()).build());
    }

    /** 신분증 제출 상태로 만든다 (파일 저장소를 타지 않는다). */
    @Transactional
    public void submitIdCard(Long sessionId) {
        ExamIdentitySession s = sessionRepository.findById(sessionId).orElseThrow();
        s.attachIdCard(saveDoc(sessionId, ExamIdentityDocument.Kind.ID_CARD), LocalDateTime.now());
    }

    /** 얼굴 확인용 사진을 붙인다. */
    @Transactional
    public void attachFace(Long sessionId) {
        ExamIdentitySession s = sessionRepository.findById(sessionId).orElseThrow();
        s.attachFaceCheck(saveDoc(sessionId, ExamIdentityDocument.Kind.FACE_CHECK),
                LocalDateTime.now(), "face-consent-v1");
    }

    /** 웹캠 점검 통과를 기록한다. */
    @Transactional
    public void markWebcam(Long sessionId) {
        sessionRepository.findById(sessionId).orElseThrow().markWebcamChecked(LocalDateTime.now());
    }

    /** 승인까지 끝난 세션을 만든다 — 얼굴 사진 + 신분증 + 웹캠 점검 포함. */
    @Transactional
    public ExamIdentitySession approvedSession(Ctx c) {
        ExamIdentitySession s = sessionRepository.findTopByExamIdAndUserIdOrderByIdDesc(c.examId(), c.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "세션을 먼저 열어야 합니다 — openSession() 을 호출하세요."));
        if (s.getStatus() != ExamIdentitySession.Status.PENDING) {
            throw new IllegalStateException(
                    "approvedSession() 은 PENDING 세션에서만 시작할 수 있습니다. 현재: " + s.getStatus());
        }
        uploadIdCard(s.getId());
        uploadFaceCheck(s.getId());
        identityService.markWebcamChecked(s.getId(), c.userId());
        identityService.approve(s.getId(), c.adminId(), "ADMIN", "127.0.0.1");
        return sessionRepository.findById(s.getId()).orElseThrow();
    }

    /**
     * 응시를 제출 상태로 끝낸다 — 다음 start() 가 "이어하기" 로 빠지지 않게 한다.
     * 재응시 시나리오를 만들려면 첫 회차가 실제로 종료돼 있어야 한다.
     */
    @Transactional
    public void finishAttempt(Long attemptId) {
        ExamAttempt a = attemptRepository.findById(attemptId).orElseThrow();
        a.submit(LocalDateTime.now(), ExamAttempt.AttemptStatus.SUBMITTED);
        attemptRepository.flush();
    }

    /** 증거 스냅샷 개수 — 중복 생성 여부 검증용. */
    @Transactional
    public int verificationCountOf(Long sessionId) {
        return verificationRepository.findBySessionIdOrderByIdAsc(sessionId).size();
    }

    @Transactional
    public ExamAttempt attemptOf(Long attemptId) {
        return attemptRepository.findById(attemptId).orElseThrow();
    }

    /** 승인 유효시간을 강제로 지나게 만든다. */
    @Transactional
    public void expireApproval(Long sessionId) {
        ExamIdentitySession s = sessionRepository.findById(sessionId).orElseThrow();
        ReflectionTestUtils.setField(s, "approvalExpiresAt", LocalDateTime.now().minusMinutes(1));
    }

    /** 응시 제한 시간이 지나 회차 자체가 만료된 상태로 만든다. */
    @Transactional
    public void expireAttempt(Long attemptId) {
        ExamAttempt a = attemptRepository.findById(attemptId).orElseThrow();
        ReflectionTestUtils.setField(a, "expiresAt", LocalDateTime.now().minusMinutes(1));
        attemptRepository.flush();
    }

    /** 웹캠 점검 시각을 오래된 것으로 만든다. */
    @Transactional
    public void staleWebcam(Long sessionId) {
        ExamIdentitySession s = sessionRepository.findById(sessionId).orElseThrow();
        ReflectionTestUtils.setField(s, "webcamCheckedAt",
                LocalDateTime.now().minusMinutes(ExamIdentitySession.WEBCAM_VALID_MINUTES + 5));
    }
}
