package com.ssa.lms.exam.service;

import com.ssa.lms.auth.IdentityVerificationException;
import com.ssa.lms.auth.IdentityVerificationService;
import com.ssa.lms.auth.VerifyRequest;
import com.ssa.lms.course.service.CourseQueryService;
import com.ssa.lms.exam.dto.AttemptQuestionRow;
import com.ssa.lms.exam.dto.AttemptResultView;
import com.ssa.lms.exam.dto.AttemptView;
import com.ssa.lms.exam.dto.ExamTakeRow;
import com.ssa.lms.exam.entity.*;
import com.ssa.lms.exam.entity.ExamAttempt.AttemptStatus;
import com.ssa.lms.exam.repository.AnswerRepository;
import com.ssa.lms.exam.repository.ExamAttemptRepository;
import com.ssa.lms.exam.repository.ExamRefRepository;
import com.ssa.lms.exam.repository.ExamTakeRepository;
import com.ssa.lms.identity.policy.PrecheckPolicy;
import com.ssa.lms.proctor.entity.ExamEventLog;
import com.ssa.lms.proctor.service.ExamEventLogService;
import com.ssa.lms.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 시험 응시/제출 서비스 — 내역서 필수 요건이 가장 많이 걸린 자리.
 *
 * <p>지켜야 하는 불변식 3가지</p>
 * <ol>
 *   <li><b>본인인증</b> — {@code Exam.requireIdentityVerification=true} 면 인증 없이는 회차 자체가 만들어지지 않는다.
 *       최근 {@value #VERIFY_VALID_MINUTES}분 이내 인증 이력이 있으면 재인증을 면제한다.</li>
 *   <li><b>서버 타이머</b> — 마감 시각은 시작 시점에 서버가 계산해 {@code ExamAttempt.expiresAt} 에 박는다.
 *       제출 마감 판정은 오직 이 컬럼으로만 한다. 화면 타이머는 표시용이라 조작돼도 결과가 달라지지 않는다.</li>
 *   <li><b>출제 문항의 단일 진실은 {@code Exam.examQuestions}</b> — 응시 경로에서 {@code ExamQuestionRule} 은
 *       절대 읽지 않는다. 규칙은 확정 전 조건일 뿐이라 응시 때마다 문항이 달라져 3년 재현이 깨진다.</li>
 * </ol>
 *
 * <p>수동 채점(서술형/코딩)과 Grade 반영은 다음 슬라이스(시험 채점) 담당이라 여기서 하지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExamAttemptService {

    /** 최근 본인인증 유효시간(분). 이 안이면 시험 입장 시 재인증을 면제한다. */
    public static final int VERIFY_VALID_MINUTES = 30;

    /** 최근 인증 이력으로 면제된 경우의 수단 코드. 신규 인증(PASSWORD 등)과 구분해 감사에 남긴다. */
    public static final String METHOD_RECENT = "RECENT";

    /** 훈련생에게 보이는 시험 상태. DRAFT(작성중)/ARCHIVED(보관)는 노출하지 않는다. */
    private static final List<Exam.ExamStatus> VISIBLE_STATUSES =
            List.of(Exam.ExamStatus.SCHEDULED, Exam.ExamStatus.OPEN, Exam.ExamStatus.CLOSED);

    /** 문제 세트 배정용 난수. 예측 가능한 시드를 쓰면 배정이 새 나가므로 SecureRandom 을 쓴다. */
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private static final DateTimeFormatter LIST_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final DateTimeFormatter FULL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ExamTakeRepository examTakeRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final AnswerRepository answerRepository;
    private final ExamRefRepository examRefRepository;
    private final CourseQueryService courseQueryService;
    private final IdentityVerificationService identityVerificationService;
    private final ExamEventLogService examEventLogService;

    /**
     * 사전점검 게이트 — <b>필수 주입</b>.
     *
     * <p>optional 로 두면 "빈이 없으면 건너뛴다" 는 fail-open 이 되어, 빈 누락 하나로
     * 모든 감독 시험이 무방비로 열린다. 필수로 두어 <b>애플리케이션 기동 자체가 실패</b>하게 한다
     * — 조용히 열리는 것보다 뜨지 않는 편이 안전하다.</p>
     */
    private final ExamStartGate startGate;
    private final AutoGrader autoGrader;

    /* ===================== 목록 ===================== */

    /**
     * 응시 가능 시험 목록.
     *
     * readOnly 가 아닌 이유: 조회하는 김에 "기간 내 재접속 없이 만료된 진행중 회차"를 자동 제출로 닫는다.
     * 안 닫으면 IN_PROGRESS 가 영원히 남아 재응시 횟수를 잡아먹는다.
     */
    @Transactional
    public List<ExamTakeRow> availableExams(Long userId) {
        LocalDateTime now = LocalDateTime.now();

        List<Long> courseIds = myCourseIds(userId);
        if (courseIds.isEmpty()) {
            return List.of();
        }
        List<Exam> exams = examTakeRepository.findVisibleByCourses(courseIds, VISIBLE_STATUSES);
        if (exams.isEmpty()) {
            return List.of();
        }

        List<Long> examIds = exams.stream().map(Exam::getId).toList();
        // 세트별로 센 뒤 "대표 세트"(가장 작은 번호) 값을 카드에 쓴다 — 응시자는 한 세트만 풀기 때문.
        SetStats stats = SetStats.of(examTakeRepository.countExamQuestions(examIds));
        Map<Long, String> typeTexts = toTypeTextMap(examTakeRepository.countExamQuestionTypes(examIds), stats);

        Map<Long, List<ExamAttempt>> byExam = new HashMap<>();
        for (ExamAttempt attempt : examAttemptRepository.findByUserAndExams(userId, examIds)) {
            if (attempt.getStatus() == AttemptStatus.IN_PROGRESS && attempt.isExpired(now)) {
                finish(attempt, now, AttemptStatus.AUTO_SUBMITTED, null);
            }
            byExam.computeIfAbsent(attempt.getExam().getId(), k -> new ArrayList<>()).add(attempt);
        }

        List<ExamTakeRow> rows = new ArrayList<>();
        for (Exam exam : exams) {
            rows.add(toRow(exam, byExam.getOrDefault(exam.getId(), List.of()),
                    stats.representativeCount(exam.getId()),
                    stats.setCount(exam.getId()),
                    typeTexts.getOrDefault(exam.getId(), "-"), now));
        }
        return rows;
    }

    /* ===================== 응시 시작 ===================== */

    /**
     * 응시 시작(또는 이어하기).
     *
     * @param credential 본인인증 자격값(비밀번호). 최근 인증 이력이 있으면 없어도 된다.
     * @return 응시 회차 id
     */
    @Transactional
    public Long start(Long examId, Long userId, String verifyMethod, String credential,
                      String ip, String userAgent) {
        return start(examId, userId, verifyMethod, credential, ip, userAgent, null);
    }

    /**
     * 사전점검(신분확인 + 웹캠)을 거친 시험의 입장.
     *
     * <p>{@code precheckSessionId} 가 있으면 {@link ExamStartGate} 로 서버 검증을 한 번 더 한다.
     * 화면의 배지·URL 파라미터는 믿지 않는다 — 게이트는 여기서만 통과한다.
     * 통과 후 attempt 생성 → ENTER 기록 <b>순서는 그대로</b> 두고, 세션 연결만 뒤에 붙인다.</p>
     */
    @Transactional
    public Long start(Long examId, Long userId, String verifyMethod, String credential,
                      String ip, String userAgent, Long precheckSessionId) {
        LocalDateTime now = LocalDateTime.now();

        Exam exam = examTakeRepository.findWithExamQuestions(examId)
                .orElseThrow(() -> new ExamTakeException("NOT_FOUND", "시험을 찾을 수 없습니다."));

        ensureEnrolled(userId, exam);

        if (exam.getStatus() == Exam.ExamStatus.DRAFT || exam.getStatus() == Exam.ExamStatus.ARCHIVED) {
            throw new ExamTakeException("NOT_OPEN", "아직 공개되지 않은 시험입니다.");
        }
        if (!exam.isWithinWindow(now)) {
            throw new ExamTakeException("OUT_OF_WINDOW",
                    "응시 가능 기간이 아닙니다. (" + LIST_FORMAT.format(exam.getWindowStart())
                            + " ~ " + LIST_FORMAT.format(exam.getWindowEnd()) + ")");
        }
        if (exam.getStatus() == Exam.ExamStatus.CLOSED) {
            throw new ExamTakeException("CLOSED", "종료 처리된 시험입니다.");
        }
        // 규칙(ExamQuestionRule)만 있고 확정 문항이 0건이면 응시 진입 자체를 막는다.
        // 여기서 규칙을 보고 즉석에서 문항을 뽑으면 응시자마다 문제가 달라져 재현이 불가능해진다.
        if (exam.getExamQuestions().isEmpty()) {
            throw new ExamTakeException("NOT_READY",
                    "출제 문항이 확정되지 않은 시험입니다. 담당 강사에게 문의하세요.");
        }

        // 진행 중이던 회차 처리 — 만료됐으면 자동 제출로 닫고, 살아 있으면 이어하기
        List<ExamAttempt> attempts =
                examAttemptRepository.findByExamIdAndUserIdOrderByAttemptNoDesc(examId, userId);
        ExamAttempt inProgress = attempts.stream()
                .filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS)
                .findFirst().orElse(null);

        if (inProgress != null) {
            if (inProgress.isExpired(now)) {
                finish(inProgress, now, AttemptStatus.AUTO_SUBMITTED, ip);
            } else {
                /* 감독+QR 시험의 이어하기는 최초 입장 때 통과한 QR 승인을 그대로 인정한다.
                   여기서 비밀번호를 다시 물으면 네트워크가 끊겼다 돌아온 응시자가 막힌다.

                   ★ 그리고 <b>본인확인 시각을 건드리지 않는다</b> (P1).
                   이어하기는 신분증을 다시 검토하지도, 게이트를 다시 통과하지도 않는다.
                   그런데 예전에는 markIdentityVerified(now, ID_CARD_QR) 로 현재 시각을 덮어써서
                   ① 실제 최초 본인확인 시각이 사라지고
                   ② 재검증한 적 없는데 방금 검증한 것처럼 감사 데이터가 남고
                   ③ 이어갈 때마다 시각이 갱신돼 증거로서 의미를 잃었다.
                   최초 attempt 생성 때 저장된 값이 진짜 근거이므로 그대로 보존한다.
                   (스냅샷 시각을 복사해 채워 넣지도 않는다 — 그것도 사실이 아니다.)

                   비밀번호 인증 시험은 이어하기 때 실제로 재확인을 수행하므로
                   기존 verifyIdentity() 와 갱신 동작을 그대로 둔다. */
                if (!requiresGate(exam)) {
                    VerifyOutcome resume = verifyIdentity(userId, verifyMethod, credential,
                            exam.isRequireIdentityVerification());
                    if (resume.at() != null) {
                        inProgress.markIdentityVerified(resume.at(), resume.method());
                    }
                }
                examEventLogService.append(inProgress, ExamEventLog.EventType.RESUME,
                        "이어하기 (회차 " + inProgress.getAttemptNo() + ")", ip);
                return inProgress.getId();
            }
        }

        /* ★ 서버 입장 게이트 (LXP-015/018) — <b>새 회차를 만들 때만</b> 실행한다 (P0-B).
           신분확인 승인·유효시간·웹캠 점검 신선도를 서버가 확인한다.
           실패하면 여기서 끝난다 — attempt 를 만들지 않는다.

           <b>왜 진행 회차 처리 뒤인가</b><br>
           게이트가 앞에 있으면, 최초 입장 때 이미 승인을 통과해 정상 응시 중이던 사람이
           네트워크가 끊겼다 돌아왔을 때 "승인 30분 만료" 나 "웹캠 점검 15분 경과" 로
           <b>이미 진행 중인 자기 회차</b>까지 막혔다. 시험 도중 쫓겨나는 셈이다.
           이어하기의 본인확인 근거는 최초 입장 때 통과한 승인이고, 그 증거는
           ExamIdentityVerification 스냅샷에 이미 남아 있다.

           위 이어하기 분기는 살아 있는 IN_PROGRESS 회차에서만 return 하므로,
           만료돼 AUTO_SUBMITTED 로 닫힌 회차나 제출이 끝난 회차는 여기로 내려와
           <b>새 회차로서 게이트를 다시 탄다</b>. 회차 조회는 (examId, userId) 로 한정되어
           남의 회차나 다른 시험의 회차를 이어받을 수 없다. */
        startGate.check(examId, userId, precheckSessionId);

        long used = examAttemptRepository.countByExamIdAndUserId(examId, userId);
        int max = maxAttempts(exam);
        // 사전 모의 테스트는 응시 환경 적응이 목적이라 횟수를 세지 않는다.
        if (!exam.isPracticeMode() && used >= max) {
            throw new ExamTakeException("NO_ATTEMPT_LEFT",
                    exam.isRetakeAllowed()
                            ? "재응시 가능 횟수(" + max + "회)를 모두 사용했습니다."
                            : "재응시가 허용되지 않는 시험입니다.");
        }

        /* 감독 + QR 신분확인 대상 시험은 <b>운영진이 승인한 QR 신분확인</b>이 본인확인 근거다.
           여기서 비밀번호를 또 요구하면 QR 흐름을 다 마친 응시자가 입장하지 못한다.
           그 외 시험은 기존 비밀번호 재확인 흐름을 그대로 쓴다. */
        VerifyOutcome verified;
        if (requiresGate(exam)) {
            verified = new VerifyOutcome(LocalDateTime.now(), METHOD_ID_CARD);
        } else {
            verified = verifyIdentity(userId, verifyMethod, credential,
                    exam.isRequireIdentityVerification());
            if (exam.isRequireIdentityVerification() && verified.at() == null) {
                // verifyIdentity 가 이미 막지만, 요건이 요건인 만큼 마지막 방어선을 하나 더 둔다.
                throw ExamTakeException.identityRequired("응시 전 본인인증이 필요합니다.");
            }
        }

        User user = examRefRepository.findUser(userId)
                .orElseThrow(() -> new ExamTakeException("NOT_FOUND", "사용자를 찾을 수 없습니다."));

        // ★ 문제 세트 배정 — 여기서 한 번만 뽑아 회차에 박는다.
        //   이어하기·재조회 때 다시 뽑으면 새로고침마다 문항이 바뀌고 3년 재현도 깨진다.
        int assignedSetNo = assignSet(exam);

        ExamAttempt attempt = ExamAttempt.builder()
                .exam(exam)
                .user(user)
                .attemptNo((int) used + 1)
                .assignedSetNo(assignedSetNo)
                .startedAt(now)
                // ★ 마감 시각은 서버가 계산해 박는다. 이후 판정은 오직 이 값으로만 한다.
                .expiresAt(now.plusMinutes(exam.getTimeLimitMin()))
                .status(AttemptStatus.IN_PROGRESS)
                .identityVerifiedAt(verified.at())
                .identityVerifyMethod(verified.method())
                .ip(ip)
                .userAgent(truncate(userAgent, 255))
                .build();
        examAttemptRepository.save(attempt);

        examEventLogService.append(attempt, ExamEventLog.EventType.ENTER,
                "응시 시작 (회차 " + attempt.getAttemptNo() + ", 문제세트 " + assignedSetNo
                        + ", 본인인증=" + (verified.method() == null ? "면제" : verified.method()) + ")", ip);

        /* attempt 가 생긴 뒤에 신분확인 세션을 연결한다 (LXP-016 조회용).
           세션을 먼저 attempt 에 묶으려 하면 "attempt 를 만들려면 세션이 필요하고
           세션을 묶으려면 attempt 가 필요한" 순환이 된다. */
        startGate.linkAttempt(precheckSessionId, attempt);
        return attempt.getId();
    }

    /**
     * 문제 세트 무작위 배정.
     *
     * <p>실제로 편성된 세트 번호({@link Exam#availableSetNos()}) 중에서만 고른다.
     * 선언된 세트 수를 그대로 믿고 뽑으면 규칙 확정이 덜 된 빈 세트가 배정돼
     * "문항 0개인 시험"을 보게 된다. 세트가 하나뿐이면 항상 그 하나가 나오므로
     * 세트 기능이 없던 때와 동작이 같다(하위호환).</p>
     */
    private int assignSet(Exam exam) {
        List<Integer> setNos = exam.availableSetNos();
        if (setNos.isEmpty()) {
            return 1;
        }
        if (setNos.size() == 1) {
            return setNos.get(0);
        }
        return setNos.get(RANDOM.nextInt(setNos.size()));
    }

    /* ===================== 응시 화면 ===================== */

    /**
     * 응시 화면 모델. 만료된 회차면 여기서 자동 제출로 닫고 그 상태를 그대로 돌려준다
     * (컨트롤러가 status != IN_PROGRESS 를 보고 결과 화면으로 보낸다).
     */
    @Transactional
    public AttemptView loadAttempt(Long attemptId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        ExamAttempt attempt = attemptWithQuestions(attemptId);
        ensureOwner(attempt, userId);

        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS && attempt.isExpired(now)) {
            finish(attempt, now, AttemptStatus.AUTO_SUBMITTED, null);
        }

        Exam exam = attempt.getExam();
        Map<Long, Answer> saved = new HashMap<>();
        for (Answer a : answerRepository.findAllByAttemptId(attemptId)) {
            saved.put(a.getQuestion().getId(), a);
        }

        List<ExamQuestion> ordered = orderQuestions(exam, attempt);
        // 보기는 별도 쿼리로 한 번에 초기화한다 (문항 컬렉션과 겹쳐 fetch 하면 카테시안 곱).
        List<Long> questionIds = ordered.stream().map(eq -> eq.getQuestion().getId()).toList();
        if (!questionIds.isEmpty()) {
            examTakeRepository.findQuestionsWithChoices(questionIds);
        }

        List<AttemptQuestionRow> questions = new ArrayList<>();
        int no = 0;
        for (ExamQuestion eq : ordered) {
            questions.add(AttemptQuestionRow.of(eq, ++no, saved.get(eq.getQuestion().getId())));
        }

        long remain = attempt.getExpiresAt() == null
                ? 0L
                : Math.max(0L, Duration.between(now, attempt.getExpiresAt()).getSeconds());

        return new AttemptView(
                String.valueOf(attempt.getId()),
                String.valueOf(exam.getId()),
                exam.getExamName(),
                exam.getCourse() == null ? "-" : exam.getCourse().getCourseName(),
                exam.getNote(),
                attempt.getAttemptNo(),
                maxAttempts(exam),
                exam.isPracticeMode(),
                attempt.getAssignedSetNo(),
                exam.availableSetNos().size(),
                attempt.getStatus().name(),
                format(attempt.getExpiresAt(), FULL_FORMAT),
                FULL_FORMAT.format(now),
                remain,
                exam.isBlockTabSwitch(),
                exam.isBlockCopyPaste(),
                exam.isProctorEnabled(),
                exam.isRequireWebcam(),
                format(attempt.getIdentityVerifiedAt(), FULL_FORMAT),
                attempt.getIdentityVerifyMethod(),
                questions);
    }

    /* ===================== 답안 임시저장 ===================== */

    /**
     * 답안 임시저장. (attempt, question) 유니크라 항상 같은 행을 upsert 한다 — 행이 늘어나면 안 된다.
     * 제출 여부는 Answer 가 아니라 ExamAttempt.status 로만 판정하므로 여기서 상태를 바꾸지 않는다.
     *
     * @return 저장 시각
     */
    @Transactional
    public LocalDateTime saveAnswer(Long attemptId, Long userId, Long questionId,
                                    Long choiceId, String answerText) {
        LocalDateTime now = LocalDateTime.now();
        ExamAttempt attempt = attemptWithQuestions(attemptId);
        ensureOwner(attempt, userId);

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new ExamTakeException("ALREADY_SUBMITTED", "이미 종료된 응시 회차입니다.");
        }
        if (attempt.isExpired(now)) {
            // 서버 시계 기준으로 이미 마감. 화면 타이머가 뭐라 하든 저장하지 않는다.
            throw new ExamTakeException("EXPIRED", "제한시간이 만료되어 답안을 저장할 수 없습니다.");
        }

        // 배정된 세트 밖의 문항은 거부한다 — questionId 만 바꿔 다른 세트 문항을 긁어가지 못하게.
        ExamQuestion examQuestion = questionsOf(attempt).stream()
                .filter(eq -> eq.getQuestion().getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ExamTakeException("BAD_QUESTION", "이 회차에 배정되지 않은 문항입니다."));
        Question question = examQuestion.getQuestion();

        QuestionChoice choice = null;
        if (choiceId != null) {
            choice = question.getChoices().stream()
                    .filter(c -> c.getId().equals(choiceId))
                    .findFirst()
                    .orElseThrow(() -> new ExamTakeException("BAD_CHOICE", "이 문항의 보기가 아닙니다."));
        }
        String text = (answerText == null || answerText.isBlank()) ? null : answerText;

        Answer answer = answerRepository.findByAttemptIdAndQuestionId(attemptId, questionId).orElse(null);
        if (answer == null) {
            answerRepository.save(Answer.builder()
                    .attempt(attempt)
                    .question(question)
                    .choice(choice)
                    .answerText(text)
                    .savedAt(now)
                    .build());
        } else {
            answer.updateAnswer(choice, text, now);
        }
        return now;
    }

    /* ===================== 제출 ===================== */

    /**
     * 최종 제출. 만료 후 들어온 제출은 {@code AUTO_SUBMITTED} 로 기록하고 결과에 그 사실을 담아 돌려준다.
     * 이미 종료된 회차에 다시 들어오면 에러 대신 기존 결과를 돌려준다(멱등) — 네트워크 재시도 때문.
     */
    @Transactional
    public AttemptResultView submit(Long attemptId, Long userId, String ip) {
        LocalDateTime now = LocalDateTime.now();
        ExamAttempt attempt = attemptWithQuestions(attemptId);
        ensureOwner(attempt, userId);

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return resultView(attempt);
        }
        AttemptStatus status = attempt.isExpired(now)
                ? AttemptStatus.AUTO_SUBMITTED
                : AttemptStatus.SUBMITTED;
        return finish(attempt, now, status, ip);
    }

    /** 제출 결과 조회 (결과 화면 새로고침용). */
    @Transactional(readOnly = true)
    public AttemptResultView loadResult(Long attemptId, Long userId) {
        ExamAttempt attempt = attemptWithQuestions(attemptId);
        ensureOwner(attempt, userId);
        return resultView(attempt);
    }

    /* ===================== 부정행위 이벤트 ===================== */

    /**
     * 화면이 보낸 이벤트를 append-only 로 기록.
     * 알 수 없는 타입은 저장하지 않고 false 를 돌려준다(클라이언트 입력을 그대로 믿지 않는다).
     */
    @Transactional
    public boolean recordEvent(Long attemptId, Long userId, String eventType, String detail, String ip) {
        ExamAttempt attempt = examAttemptRepository.findWithExam(attemptId)
                .orElseThrow(() -> new ExamTakeException("NOT_FOUND", "응시 회차를 찾을 수 없습니다."));
        ensureOwner(attempt, userId);

        ExamEventLog.EventType type = examEventLogService.parseType(eventType);
        if (type == null) {
            return false;
        }
        examEventLogService.append(attempt, type, detail, ip);
        return true;
    }

    /* ===================== 내부 ===================== */

    /** 자동 채점 + 상태 확정 + 퇴장 로그. 제출/자동제출 두 경로가 공유한다. */
    private AttemptResultView finish(ExamAttempt attempt, LocalDateTime now,
                                     AttemptStatus status, String ip) {
        Exam exam = attempt.getExam();

        Map<Long, Answer> answers = new HashMap<>();
        for (Answer a : answerRepository.findAllByAttemptId(attempt.getId())) {
            answers.put(a.getQuestion().getId(), a);
        }

        int autoScore = 0;
        int answered = 0;
        boolean manualPending = false;

        for (ExamQuestion eq : questionsOf(attempt)) {
            Question question = eq.getQuestion();
            Answer answer = answers.get(question.getId());
            if (answer != null && (answer.getChoice() != null
                    || (answer.getAnswerText() != null && !answer.getAnswerText().isBlank()))) {
                answered++;
            }
            if (!question.isAutoGradable()) {
                // 서술형/코딩 — 다음 슬라이스(시험 채점)에서 사람이 채점한다.
                manualPending = true;
                continue;
            }
            AutoGrader.Result result = autoGrader.grade(question, answer, eq.resolveScore());
            autoScore += result.score();
            if (answer != null) {
                answer.gradeAuto(result.correct(), result.score(), now);
            }
        }

        attempt.submit(now, status);
        // 수동 채점이 남아 있으면 passScore 를 넘기지 않는다 — 합격 여부를 미리 확정하면 안 되기 때문.
        attempt.applyScore(autoScore, null, manualPending ? null : exam.getPassScore());

        examEventLogService.append(attempt, ExamEventLog.EventType.EXIT,
                status == AttemptStatus.AUTO_SUBMITTED
                        ? "제한시간 만료 — 서버 자동 제출"
                        : "응시자 최종 제출",
                ip);

        return buildResult(attempt, exam, answered, manualPending);
    }

    private AttemptResultView resultView(ExamAttempt attempt) {
        Exam exam = attempt.getExam();
        int answered = 0;
        boolean manualPending = false;
        Map<Long, Answer> answers = new HashMap<>();
        for (Answer a : answerRepository.findAllByAttemptId(attempt.getId())) {
            answers.put(a.getQuestion().getId(), a);
        }
        List<ExamQuestion> mine = questionsOf(attempt);
        for (ExamQuestion eq : mine) {
            Answer answer = answers.get(eq.getQuestion().getId());
            if (answer != null && (answer.getChoice() != null
                    || (answer.getAnswerText() != null && !answer.getAnswerText().isBlank()))) {
                answered++;
            }
            // 수동 채점 대기 = "사람이 채점해야 하는 문항인데 아직 점수가 없다".
            // 유형만 보고 판정하면(예전 구현) 채점이 끝나도 영원히 대기로 남는다.
            // 그러면 성적 공개를 AFTER_GRADING 으로 둔 시험이 채점 후에도 계속 가려져
            // HIDDEN 과 구분이 없어진다. 채점 슬라이스의 판정(ExamGradingService.recalc)과 같은 규칙이다.
            if (!eq.getQuestion().isAutoGradable() && (answer == null || answer.getScore() == null)) {
                manualPending = true;
            }
        }
        return buildResult(attempt, exam, answered, manualPending);
    }

    /**
     * 결과 화면 모델.
     *
     * <p><b>성적 비공개면 점수를 DTO 단계에서 아예 비운다.</b> 템플릿에서 숨기기만 하면
     * 인라인 JS/모델 직렬화로 값이 HTML 에 남는다. 화면 조건문은 마지막 방어선이지 첫 방어선이 아니다.
     * 관리자·강사는 이 경로를 타지 않는다 (채점 화면은 grading 슬라이스 소관이고 항상 점수를 보여준다).</p>
     */
    private AttemptResultView buildResult(ExamAttempt attempt, Exam exam,
                                          int answered, boolean manualPending) {
        boolean visible = exam.isResultVisibleToTrainee(LocalDateTime.now(), manualPending);
        return new AttemptResultView(
                String.valueOf(attempt.getId()),
                exam.getExamName(),
                attempt.getStatus().name(),
                attempt.getStatus() == AttemptStatus.AUTO_SUBMITTED,
                format(attempt.getSubmittedAt(), FULL_FORMAT),
                visible ? attempt.getAutoScore() : null,
                visible ? attempt.getTotalScore() : null,
                visible ? exam.getTotalScore() : null,
                visible ? exam.getPassScore() : null,
                visible ? attempt.getPassed() : null,
                manualPending,
                answered,
                questionsOf(attempt).size(),
                visible,
                visible ? null : exam.resultHiddenMessage(),
                exam.isPracticeMode(),
                attempt.getAssignedSetNo());
    }

    /**
     * 본인인증 판정.
     *
     * 순서가 중요하다: 최근 인증 면제를 먼저 보고, 그 다음에 자격값 검증을 한다.
     * required=true 인데 둘 다 없으면 여기서 막혀 회차가 만들어지지 않는다 (내역서 요건).
     */
    private VerifyOutcome verifyIdentity(Long userId, String method, String credential, boolean required) {
        LocalDateTime now = LocalDateTime.now();

        Optional<LocalDateTime> last = identityVerificationService.lastVerifiedAt(userId);
        if (last.isPresent() && last.get().isAfter(now.minusMinutes(VERIFY_VALID_MINUTES))) {
            return new VerifyOutcome(last.get(), METHOD_RECENT);
        }
        if (credential == null || credential.isBlank()) {
            if (required) {
                throw ExamTakeException.identityRequired(
                        "응시 전 본인인증이 필요합니다. (최근 " + VERIFY_VALID_MINUTES + "분 이내 인증 이력 없음)");
            }
            return new VerifyOutcome(null, null);
        }
        try {
            String used = identityVerificationService.verify(userId, new VerifyRequest(
                    (method == null || method.isBlank()) ? VerifyRequest.METHOD_PASSWORD : method.strip(),
                    credential));
            return new VerifyOutcome(now, used);
        } catch (IdentityVerificationException e) {
            throw ExamTakeException.identityRequired("본인인증에 실패했습니다. " + e.getMessage());
        }
    }

    /** QR 신분확인으로 본인확인을 대신한 경우의 수단 코드 (ExamAttempt.identityVerifyMethod). */
    private static final String METHOD_ID_CARD = "ID_CARD_QR";

    private record VerifyOutcome(LocalDateTime at, String method) {
    }

    /**
     * 사전점검 게이트 포트.
     *
     * <p>exam 모듈이 identity 모듈을 직접 알지 않도록 인터페이스로 끊는다.
     * 구현이 없으면(null) 기존 흐름 그대로 — 비감독 시험과 기존 비밀번호 확인은 영향받지 않는다.</p>
     */
    /**
     * 게이트가 반드시 필요한 시험인가.
     * 판정은 {@link PrecheckPolicy} 한 곳에만 둔다 — 목록 DTO·게이트·서비스가 어긋나면
     * "목록은 사전점검으로 보내는데 게이트는 통과시키는" 구멍이 생긴다.
     */
    private static boolean requiresGate(Exam exam) {
        return PrecheckPolicy.requiresPrecheck(exam);
    }

    /** 화면·컨트롤러가 같은 판정을 쓰도록 노출한다. 판정 로직을 컨트롤러에 복제하지 않는다. */
    @Transactional(readOnly = true)
    public boolean requiresPrecheck(Long examId) {
        return examTakeRepository.findWithExamQuestions(examId)
                .map(PrecheckPolicy::requiresPrecheck).orElse(false);
    }

    public interface ExamStartGate {
        /** 통과하지 못하면 {@link ExamTakeException} 을 던진다. */
        void check(Long examId, Long userId, Long precheckSessionId);

        void linkAttempt(Long precheckSessionId, ExamAttempt attempt);
    }

    /**
     * 이 회차에 실제로 출제된 문항 = <b>배정된 세트의 문항만</b>.
     *
     * <p>채점·결과 집계도 전부 이 목록으로 해야 한다. 시험 전체 문항을 훑으면
     * 배정되지 않은 세트의 서술형이 "미채점"으로 잡혀 합격 판정이 영원히 미정으로 남는다.</p>
     */
    private List<ExamQuestion> questionsOf(ExamAttempt attempt) {
        return attempt.getExam().questionsOfSet(attempt.getAssignedSetNo());
    }

    /**
     * 출제 순서. randomOrder=true 면 섞되 <b>회차 id 를 시드로 고정</b>한다.
     * 매번 다시 섞으면 새로고침마다 문항 순서가 바뀌어 응시자가 자기 답안을 찾지 못한다.
     */
    private List<ExamQuestion> orderQuestions(Exam exam, ExamAttempt attempt) {
        List<ExamQuestion> ordered = new ArrayList<>(questionsOf(attempt));
        ordered.sort(Comparator.comparing(ExamQuestion::getSeq));
        if (exam.isRandomOrder()) {
            Collections.shuffle(ordered, new Random(attempt.getId()));
        }
        return ordered;
    }

    private ExamTakeRow toRow(Exam exam, List<ExamAttempt> attempts, int questionCount,
                              int setCount, String typeText, LocalDateTime now) {
        boolean practice = exam.isPracticeMode();
        int max = maxAttempts(exam);
        int used = attempts.size();

        ExamAttempt inProgress = attempts.stream()
                .filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS)
                .findFirst().orElse(null);
        ExamAttempt lastFinished = attempts.stream()
                .filter(a -> a.getStatus() == AttemptStatus.SUBMITTED
                        || a.getStatus() == AttemptStatus.AUTO_SUBMITTED)
                .reduce((a, b) -> b).orElse(null);

        boolean ready = questionCount > 0;
        boolean beforeWindow = now.isBefore(exam.getWindowStart());
        boolean afterWindow = now.isAfter(exam.getWindowEnd()) || exam.getStatus() == Exam.ExamStatus.CLOSED;

        String status;
        String blockReason = null;
        if (inProgress != null) {
            status = "in_progress";
        } else if (beforeWindow) {
            status = "scheduled";
            blockReason = "응시 시작 전입니다.";
        } else if (afterWindow) {
            status = "ended";
            blockReason = "응시 기간이 종료되었습니다.";
        } else if (!practice && used >= max) {
            // 모의 테스트는 횟수를 세지 않으므로 여기서 막지 않는다 (항상 다시 응시 가능).
            status = "completed";
            blockReason = "응시 가능 횟수를 모두 사용했습니다.";
        } else {
            status = "available";
        }
        if (blockReason == null && !ready) {
            blockReason = "출제 문항이 확정되지 않았습니다. 담당 강사에게 문의하세요.";
        }
        boolean startable = ready && blockReason == null;

        return new ExamTakeRow(
                String.valueOf(exam.getId()),
                exam.getExamName(),
                exam.getCourse() == null ? "" : String.valueOf(exam.getCourse().getId()),
                exam.getCourse() == null ? "-" : exam.getCourse().getCourseName(),
                exam.getSession() == null ? "-" : exam.getSession().getName(),
                format(exam.getWindowStart(), LIST_FORMAT),
                format(exam.getWindowEnd(), LIST_FORMAT),
                exam.getTimeLimitMin(),
                questionCount,
                typeText,
                practice ? "무제한 (모의 테스트)"
                        : (exam.isRetakeAllowed() ? "가능(최대 " + max + "회)" : "불가"),
                used,
                practice ? Math.max(max, used + 1) : max,
                status,
                exam.getNote(),
                exam.isRequireIdentityVerification(),
                PrecheckPolicy.requiresPrecheck(exam),
                inProgress == null ? null : String.valueOf(inProgress.getId()),
                lastFinished == null ? null : String.valueOf(lastFinished.getId()),
                ready,
                startable,
                blockReason,
                practice,
                setCount,
                exam.resultReleaseText());
    }

    /** 수강생 명단은 A 의 계약(CourseQueryService)으로만 얻는다. A 리포지토리를 직접 쓰지 않는다. */
    private List<Long> myCourseIds(Long userId) {
        List<Long> result = new ArrayList<>();
        for (var course : examRefRepository.findAllCourses()) {
            if (courseQueryService.findUserIdsByCourseId(course.getId()).contains(userId)) {
                result.add(course.getId());
            }
        }
        return result;
    }

    private void ensureEnrolled(Long userId, Exam exam) {
        Long courseId = exam.getCourse() == null ? null : exam.getCourse().getId();
        if (courseId == null
                || !courseQueryService.findUserIdsByCourseId(courseId).contains(userId)) {
            throw new AccessDeniedException("수강 중인 과정의 시험이 아닙니다.");
        }
    }

    /** 남의 응시 회차 접근은 403. 본인 확인은 모든 진입점에서 반드시 통과해야 한다. */
    private void ensureOwner(ExamAttempt attempt, Long userId) {
        if (attempt.getUser() == null || !attempt.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("본인의 응시 회차가 아닙니다.");
        }
    }

    private ExamAttempt attemptWithQuestions(Long attemptId) {
        return examAttemptRepository.findWithExamQuestions(attemptId)
                .orElseThrow(() -> new ExamTakeException("NOT_FOUND", "응시 회차를 찾을 수 없습니다."));
    }

    private int maxAttempts(Exam exam) {
        if (!exam.isRetakeAllowed()) {
            return 1;
        }
        return exam.getMaxAttempts() == null ? 1 : Math.max(1, exam.getMaxAttempts());
    }

    private static String format(LocalDateTime at, DateTimeFormatter formatter) {
        return at == null ? null : formatter.format(at);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 시험별 세트 통계 — [examId, setNo, count] 집계 결과를 담는다.
     *
     * <p>목록 카드는 "응시자 한 명이 실제로 푸는 문항 수"를 보여줘야 하므로 세트 합계가 아니라
     * <b>대표 세트</b>(가장 작은 세트 번호) 값을 쓴다. 아직 어느 세트가 배정될지 모르는 시점이라
     * 어느 하나를 골라야 하는데, 세트들은 같은 규칙으로 뽑혀 문항 수가 같은 것이 정상이다.</p>
     */
    private record SetStats(Map<Long, Integer> representativeSetNo, Map<Long, Integer> countBySet,
                            Map<Long, Integer> setCount) {

        static SetStats of(List<Object[]> rows) {
            Map<Long, Integer> repSet = new HashMap<>();
            Map<Long, Integer> counts = new HashMap<>();
            Map<Long, Integer> sets = new HashMap<>();
            for (Object[] row : rows) {
                Long examId = (Long) row[0];
                int setNo = row[1] == null ? 1 : ((Number) row[1]).intValue();
                int count = ((Number) row[2]).intValue();
                sets.merge(examId, 1, Integer::sum);
                Integer current = repSet.get(examId);
                if (current == null || setNo < current) {
                    repSet.put(examId, setNo);
                    counts.put(examId, count);
                }
            }
            return new SetStats(repSet, counts, sets);
        }

        int representativeCount(Long examId) {
            return countBySet.getOrDefault(examId, 0);
        }

        int representativeSet(Long examId) {
            return representativeSetNo.getOrDefault(examId, 1);
        }

        int setCount(Long examId) {
            return Math.max(1, setCount.getOrDefault(examId, 1));
        }
    }

    /**
     * [examId, setNo, questionType, count] 를 "객관식 + 주관식" 형태 문구로 접는다.
     * 대표 세트의 행만 본다 — 세트를 다 합치면 "객관식 + 객관식" 처럼 중복 라벨이 나온다.
     */
    private Map<Long, String> toTypeTextMap(List<Object[]> rows, SetStats stats) {
        Map<Long, List<String>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long examId = (Long) row[0];
            int setNo = row[1] == null ? 1 : ((Number) row[1]).intValue();
            if (setNo != stats.representativeSet(examId)) {
                continue;
            }
            Question.QuestionType type = (Question.QuestionType) row[2];
            grouped.computeIfAbsent(examId, k -> new ArrayList<>())
                    .add(AttemptQuestionRow.typeLabel(type));
        }
        Map<Long, String> result = new HashMap<>();
        grouped.forEach((examId, labels) -> result.put(examId, String.join(" + ", labels)));
        return result;
    }
}
