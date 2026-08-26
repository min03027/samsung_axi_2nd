package com.ssa.lms.notice.service;

import com.ssa.lms.assignment.entity.CourseAssignment;
import com.ssa.lms.assignment.repository.CourseAssignmentRepository;
import com.ssa.lms.course.service.CourseQueryService;
import com.ssa.lms.course.entity.Session;
import com.ssa.lms.course.repository.SessionRepository;
import com.ssa.lms.exam.entity.Exam;
import com.ssa.lms.exam.repository.ExamAttemptRepository;
import com.ssa.lms.exam.repository.ExamRepository;
import com.ssa.lms.notice.entity.Notification;
import com.ssa.lms.notice.entity.ReminderLog;
import com.ssa.lms.notice.entity.ReminderSetting;
import com.ssa.lms.notice.repository.NotificationRecipientRepository;
import com.ssa.lms.notice.repository.NotificationRepository;
import com.ssa.lms.notice.repository.ReminderLogRepository;
import com.ssa.lms.notice.entity.NotificationRecipient;
import com.ssa.lms.survey.entity.Survey;
import com.ssa.lms.survey.repository.SurveyRepository;
import com.ssa.lms.survey.repository.SurveyResponseRepository;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 미제출·미응시·미응답자 독려 알림.
 *
 * <p>앨리스 항목의 "학습알림 — 24시간 전 / 1시간 전 리마인드", "미참여자 독려" 요건이다.</p>
 *
 * <p><b>인앱 알림 + 메일 두 갈래로 나간다.</b> 인앱({@link Notification})은 LMS 에 들어와야 보이고,
 * 독려는 안 들어온 사람에게 보내는 것이라 인앱만으로는 도달하지 않는다. {@link #notify} 에서
 * 같은 문구를 {@link ReminderMailSender} 로 한 번 더 보낸다.
 * 메일 발송은 기본적으로 꺼져 있고({@code lms.mail.enabled=false}) 로그만 남는다 —
 * 켜는 방법과 SMTP 주입 방식은 {@link ReminderMailSender} 주석 참고.</p>
 *
 * <p><b>중복 발송 방지:</b> 스케줄러가 주기적으로 도는데 기록이 없으면 같은 사람에게
 * 같은 알림이 계속 쌓인다. {@link ReminderLog} 에 (사용자, 종류, 대상, 단계)를 남겨
 * 한 단계는 딱 한 번만 나가게 한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private static final DateTimeFormatter DUE_FORMAT =
            DateTimeFormatter.ofPattern("MM월 dd일 HH:mm");

    private final CourseAssignmentRepository courseAssignmentRepository;
    private final ExamRepository examRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final CourseQueryService courseQueryService;
    private final SessionRepository sessionRepository;
    private final ReminderMailSender mailSender;
    private final ReminderSettingService settingService;

    /**
     * 마감 임박 대상에게 리마인드를 보낸다.
     *
     * @param now   기준 시각 (테스트에서 주입할 수 있게 인자로 받는다)
     * @param stage 어느 단계인지 — 스케줄러가 24h/1h 각각 호출한다
     * @return 보낸 알림 수
     */
    @Transactional
    public int remindDue(LocalDateTime now, ReminderLog.ReminderStage stage) {
        ReminderSetting cfg = settingService.current();

        // 관리자가 전체를 껐으면 아무것도 보내지 않는다
        if (!cfg.isEnabled()) {
            return 0;
        }

        /*
         * 시점을 코드가 아니라 **관리자 설정**에서 읽는다.
         * 예전에는 24h/1h/3일이 여기 박혀 있어, 기관 운영 방식이 달라도 바꾸려면
         * 코드를 고쳐 재배포해야 했다.
         */
        Duration lead = switch (stage) {
            case BEFORE_24H -> Duration.ofHours(cfg.getFirstNoticeHours());
            case BEFORE_1H -> Duration.ofHours(cfg.getSecondNoticeHours());
            case OVERDUE -> Duration.ZERO;
        };

        // 마감이 [now+lead, now+lead+주기) 구간에 드는 것만 — 스케줄러 주기와 맞춘다.
        // 구간으로 잡지 않고 "남은 시간 <= lead" 로 하면 그 이후 모든 주기에서 계속 잡힌다.
        LocalDateTime from = stage == ReminderLog.ReminderStage.OVERDUE
                ? now.minusDays(cfg.getOverdueDays()) : now.plus(lead);
        LocalDateTime to = stage == ReminderLog.ReminderStage.OVERDUE
                ? now : now.plus(lead).plusHours(1);

        /*
         * 1차와 2차 간격이 1시간 안쪽이면 같은 마감이 두 구간에 다 걸려 두 번 간다.
         * ReminderLog 가 단계별로 중복을 막지만 단계가 다르면 못 막는다.
         */
        if (stage == ReminderLog.ReminderStage.BEFORE_1H
                && cfg.getFirstNoticeHours() - cfg.getSecondNoticeHours() < 1) {
            return 0;
        }

        int sent = 0;
        if (cfg.isAssignmentEnabled()) sent += remindAssignments(now, stage, from, to);
        if (cfg.isExamEnabled()) sent += remindExams(now, stage, from, to);
        if (cfg.isSurveyEnabled()) sent += remindSurveys(now, stage, from, to);
        if (cfg.isLessonEnabled() && stage != ReminderLog.ReminderStage.OVERDUE) {
            sent += remindLessons(now, stage, from, to);
        }
        return sent;
    }

    /* ===== 수업 시작 24시간·1시간 전 ===== */

    private int remindLessons(LocalDateTime now, ReminderLog.ReminderStage stage,
                              LocalDateTime from, LocalDateTime to) {
        List<Session> targets = sessionRepository.findScheduledBetween(from.toLocalDate(), to.toLocalDate())
                .stream()
                .filter(s -> {
                    LocalDateTime startsAt = LocalDateTime.of(s.getLessonDate(), s.getLessonStartTime());
                    return !startsAt.isBefore(from) && startsAt.isBefore(to);
                }).toList();
        if (targets.isEmpty()) return 0;
        List<Long> ids = targets.stream().map(Session::getId).toList();
        Map<Long, Set<Long>> alreadySent = toPairMap(
                reminderLogRepository.findSentPairs(ReminderLog.ReminderType.LESSON, ids, stage));
        int sent = 0;
        for (Session lesson : targets) {
            Set<Long> skip = alreadySent.getOrDefault(lesson.getId(), Set.of());
            LocalDateTime startsAt = LocalDateTime.of(lesson.getLessonDate(), lesson.getLessonStartTime());
            Long courseId = lesson.getSubject().getCourse().getId();
            for (Long userId : courseQueryService.findUserIdsByCourseId(courseId)) {
                if (skip.contains(userId)) continue;
                String prefix = stage == ReminderLog.ReminderStage.BEFORE_1H
                        ? "[수업 1시간 전] " : "[수업 24시간 전] ";
                String content = "%s 과정의 %s 수업이 %s에 시작합니다. 학습 화면에서 준비 내용을 확인해 주세요."
                        .formatted(lesson.getSubject().getCourse().getCourseName(), lesson.getName(),
                                startsAt.format(DUE_FORMAT));
                sent += notify(userId, ReminderLog.ReminderType.LESSON, lesson.getId(), stage, now,
                        prefix + lesson.getName(), content);
            }
        }
        return sent;
    }

    /* ===== 과제 미제출 ===== */

    private int remindAssignments(LocalDateTime now, ReminderLog.ReminderStage stage,
                                  LocalDateTime from, LocalDateTime to) {
        List<CourseAssignment> targets = courseAssignmentRepository.findDueBetween(from, to);
        if (targets.isEmpty()) {
            return 0;
        }
        List<Long> ids = targets.stream().map(CourseAssignment::getId).toList();
        Map<Long, Set<Long>> submitted = toPairMap(courseAssignmentRepository.findSubmittedPairs(ids));
        Map<Long, Set<Long>> alreadySent = toPairMap(
                reminderLogRepository.findSentPairs(ReminderLog.ReminderType.ASSIGNMENT, ids, stage));

        int sent = 0;
        for (CourseAssignment ca : targets) {
            Set<Long> done = submitted.getOrDefault(ca.getId(), Set.of());
            Set<Long> skip = alreadySent.getOrDefault(ca.getId(), Set.of());
            String title = ca.getAssignment().getTitle();
            for (Long userId : courseQueryService.findUserIdsByCourseId(ca.getCourse().getId())) {
                if (done.contains(userId) || skip.contains(userId)) {
                    continue;
                }
                sent += notify(userId, ReminderLog.ReminderType.ASSIGNMENT, ca.getId(), stage, now,
                        subject(stage, "과제", title),
                        body(stage, "과제", title, ca.getEndAt()));
            }
        }
        return sent;
    }

    /* ===== 시험 미응시 ===== */

    private int remindExams(LocalDateTime now, ReminderLog.ReminderStage stage,
                            LocalDateTime from, LocalDateTime to) {
        List<Exam> targets = examRepository.findWindowEndBetween(from, to);
        if (targets.isEmpty()) {
            return 0;
        }
        List<Long> ids = targets.stream().map(Exam::getId).toList();
        Map<Long, Set<Long>> attempted = toPairMap(examAttemptRepository.findAttemptedPairs(ids));
        Map<Long, Set<Long>> alreadySent = toPairMap(
                reminderLogRepository.findSentPairs(ReminderLog.ReminderType.EXAM, ids, stage));

        int sent = 0;
        for (Exam exam : targets) {
            if (exam.getCourse() == null) {
                continue;
            }
            Set<Long> done = attempted.getOrDefault(exam.getId(), Set.of());
            Set<Long> skip = alreadySent.getOrDefault(exam.getId(), Set.of());
            for (Long userId : courseQueryService.findUserIdsByCourseId(exam.getCourse().getId())) {
                if (done.contains(userId) || skip.contains(userId)) {
                    continue;
                }
                sent += notify(userId, ReminderLog.ReminderType.EXAM, exam.getId(), stage, now,
                        subject(stage, "시험", exam.getExamName()),
                        body(stage, "시험", exam.getExamName(), exam.getWindowEnd()));
            }
        }
        return sent;
    }

    /* ===== 설문 미응답 ===== */

    private int remindSurveys(LocalDateTime now, ReminderLog.ReminderStage stage,
                              LocalDateTime from, LocalDateTime to) {
        List<Survey> targets = surveyRepository.findEndingBetween(from, to);
        if (targets.isEmpty()) {
            return 0;
        }
        List<Long> ids = targets.stream().map(Survey::getId).toList();
        Map<Long, Set<Long>> responded = toPairMap(surveyResponseRepository.findRespondedPairs(ids));
        Map<Long, Set<Long>> alreadySent = toPairMap(
                reminderLogRepository.findSentPairs(ReminderLog.ReminderType.SURVEY, ids, stage));

        int sent = 0;
        for (Survey survey : targets) {
            // 익명 설문은 누가 응답했는지 알 수 없어 미응답자를 특정할 수 없다.
            if (survey.isAnonymous() || survey.getCourse() == null) {
                continue;
            }
            Set<Long> done = responded.getOrDefault(survey.getId(), Set.of());
            Set<Long> skip = alreadySent.getOrDefault(survey.getId(), Set.of());
            for (Long userId : courseQueryService.findUserIdsByCourseId(survey.getCourse().getId())) {
                if (done.contains(userId) || skip.contains(userId)) {
                    continue;
                }
                sent += notify(userId, ReminderLog.ReminderType.SURVEY, survey.getId(), stage, now,
                        subject(stage, "설문", survey.getTitle()),
                        body(stage, "설문", survey.getTitle(), survey.getEndAt()));
            }
        }
        return sent;
    }

    /* ===== 공통 ===== */

    /**
     * 알림 1건 생성 + 발송 기록 + 메일 발송.
     *
     * <p><b>인앱이 먼저, 메일이 나중이다.</b> 메일은 부가 경로라 실패해도 인앱 알림은 이미
     * 저장돼 있어야 한다. 메일 예외는 {@link ReminderMailSender} 안에서 전부 삼켜지지만,
     * 혹시 새어 나오더라도 이 사람 이후의 발송이 멈추지 않도록 여기서 한 번 더 막는다.</p>
     *
     * <p><b>반환값에 메일 성패는 반영하지 않는다.</b> 이 값은 "리마인드가 나갔는가"를 세는 것이고
     * 기준은 인앱 알림이다. 메일이 실패해도 재발송 대상이 되면 인앱 알림만 중복으로 쌓인다.</p>
     */
    private int notify(Long userId, ReminderLog.ReminderType type, Long targetRefId,
                       ReminderLog.ReminderStage stage, LocalDateTime now,
                       String title, String content) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            return 0;
        }
        Notification notification = notificationRepository.save(Notification.builder()
                .title(title)
                .content(content)
                .priority(stage == ReminderLog.ReminderStage.BEFORE_1H
                        ? Notification.Priority.URGENT : Notification.Priority.HIGH)
                .targetType(Notification.TargetType.USER)
                .targetRefId(userId)
                .sendAt(now)
                .sender(user.get())   // 시스템 발송이지만 sender 가 필수라 수신자 본인으로 둔다
                .status(Notification.NotificationStatus.SENT)
                .kind(Notification.NotificationKind.REMINDER)
                .sourceRefId(targetRefId)
                .sourceUrl(type == ReminderLog.ReminderType.LESSON
                        ? "/trainee/learning" : "/trainee/evaluations")
                .build());

        recipientRepository.save(NotificationRecipient.builder()
                .notification(notification).user(user.get()).build());

        reminderLogRepository.save(ReminderLog.builder()
                .user(user.get()).reminderType(type).targetRefId(targetRefId)
                .stage(stage).sentAt(now).build());

        // 메일은 인앱 알림·발송기록이 확정된 뒤에 보낸다. 메일 주소가 없는 계정은
        // ReminderMailSender 가 NO_ADDRESS 로 걸러내고 인앱만 나간 상태로 끝난다.
        try {
            mailSender.send(user.get(), title, content);
        } catch (RuntimeException e) {
            log.error("독려 메일 발송 중 예기치 못한 오류 — 인앱 알림은 정상 발송됨. userId={}", userId, e);
        }
        return 1;
    }

    private String subject(ReminderLog.ReminderStage stage, String kind, String name) {
        return switch (stage) {
            case BEFORE_24H -> "[마감 24시간 전] " + kind + " · " + name;
            case BEFORE_1H -> "[마감 임박] " + kind + " · " + name;
            case OVERDUE -> "[미제출 안내] " + kind + " · " + name;
        };
    }

    private String body(ReminderLog.ReminderStage stage, String kind, String name, LocalDateTime due) {
        String dueText = due == null ? "-" : due.format(DUE_FORMAT);
        return switch (stage) {
            case BEFORE_24H -> name + " " + kind + "의 마감이 하루 남았습니다. (마감 " + dueText + ")";
            case BEFORE_1H -> name + " " + kind + "의 마감이 1시간 남았습니다. (마감 " + dueText + ")";
            case OVERDUE -> name + " " + kind + "을(를) 아직 제출하지 않았습니다. (마감 " + dueText + ")";
        };
    }

    /** [키, 사용자id] 쌍 목록을 키별 사용자 집합으로 모은다. */
    private Map<Long, Set<Long>> toPairMap(List<Object[]> rows) {
        Map<Long, Set<Long>> map = new HashMap<>();
        for (Object[] row : rows) {
            map.computeIfAbsent(((Number) row[0]).longValue(), k -> new HashSet<>())
                    .add(((Number) row[1]).longValue());
        }
        return map;
    }
}
