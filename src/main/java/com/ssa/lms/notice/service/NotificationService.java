package com.ssa.lms.notice.service;

import com.ssa.lms.course.service.CourseQueryService;
import com.ssa.lms.course.repository.CourseInstructorRepository;
import com.ssa.lms.notice.dto.NotificationForm;
import com.ssa.lms.notice.dto.NotificationListRow;
import com.ssa.lms.notice.dto.NotificationSearchCond;
import com.ssa.lms.notice.dto.LoginNoticePopup;
import com.ssa.lms.notice.entity.Notification;
import com.ssa.lms.notice.entity.NotificationRecipient;
import com.ssa.lms.notice.entity.Notice;
import com.ssa.lms.notice.repository.NotificationRecipientRepository;
import com.ssa.lms.notice.repository.NotificationRepository;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 알림 서비스.
 *
 * 발송 시점에 수신 대상자를 행으로 펼친다(fan-out). 나중에 수강생이 추가돼도 과거 알림이
 * 소급 발송되지 않아야 하기 때문 (NotificationRecipient 주석의 설계 결정).
 *
 * 대상자 산출은 A의 CourseQueryService 계약만 호출한다 (a-requests.md P0-4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final CourseQueryService courseQueryService;
    private final UserRepository userRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final ReminderMailSender mailSender;

    public LoginNoticePopup findLoginPopup(Long userId) {
        if (userId == null) return null;
        return recipientRepository.findUnreadLoginPopups(userId, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(r -> new LoginNoticePopup(
                        r.getNotification().getId(),
                        r.getNotification().getTitle(),
                        r.getNotification().getContent(),
                        safeTraineeUrl(r.getNotification().getSourceUrl())))
                .orElse(null);
    }

    /**
     * 알림 내역 목록. 수신자 수/읽음 수는 페이지에 담긴 id 묶음으로 한 번에 집계한다
     * (행마다 조회하면 N+1 — QuestionService.search 와 같은 방식).
     *
     * @param viewerId 로그인 사용자. 본인의 읽음 상태를 "읽음/읽지않음"으로 표시한다.
     */
    public Page<NotificationListRow> search(NotificationSearchCond cond, Long viewerId, Pageable pageable) {
        Page<Notification> page = notificationRepository.search(
                cond.priorityOrNull(), cond.fromOrNull(), cond.toOrNull(), cond.keywordOrNull(), pageable);

        List<Long> ids = page.getContent().stream().map(Notification::getId).toList();
        Map<Long, long[]> counts = countMap(ids);
        Set<Long> myUnread = new HashSet<>();
        Set<Long> myAny = new HashSet<>();
        if (!ids.isEmpty() && viewerId != null) {
            for (NotificationRecipient r : recipientRepository.findByNotificationIdsAndUserId(ids, viewerId)) {
                Long nid = r.getNotification().getId();
                myAny.add(nid);
                if (r.getReadAt() == null) {
                    myUnread.add(nid);
                }
            }
        }

        return page.map(n -> {
            long[] c = counts.getOrDefault(n.getId(), new long[]{0L, 0L});
            String readStatus = !myAny.contains(n.getId()) ? "-"
                    : (myUnread.contains(n.getId()) ? "읽지않음" : "읽음");
            return NotificationListRow.of(n, readStatus, c[0], c[1]);
        });
    }

    public NotificationListRow getRow(Long id, Long viewerId) {
        Notification n = getOrThrow(id);
        Map<Long, long[]> counts = countMap(List.of(id));
        long[] c = counts.getOrDefault(id, new long[]{0L, 0L});
        String readStatus = "-";
        if (viewerId != null) {
            List<NotificationRecipient> mine =
                    recipientRepository.findByNotificationIdsAndUserId(List.of(id), viewerId);
            if (!mine.isEmpty()) {
                readStatus = mine.get(0).getReadAt() == null ? "읽지않음" : "읽음";
            }
        }
        return NotificationListRow.of(n, readStatus, c[0], c[1]);
    }

    public NotificationForm loadForm(Long id) {
        return NotificationForm.from(getOrThrow(id));
    }

    public Notification getOrThrow(Long id) {
        return notificationRepository.findWithSenderById(id)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다. id=" + id));
    }

    /**
     * 내가 받은 알림 — 훈련생 알림함용.
     *
     * <p>{@link #search} 는 관리자용이라 전체를 돌려준다. 훈련생에게 그걸 쓰면
     * 남의 알림까지 보인다. 그래서 수신자(NotificationRecipient) 기준으로 따로 조회한다.</p>
     */
    public List<NotificationListRow> findForRecipient(Long userId, NotificationSearchCond cond,
                                                      Pageable pageable) {
        String keyword = cond == null ? null : cond.keywordOrNull();
        return recipientRepository.findMine(userId, pageable).stream()
                .filter(r -> keyword == null
                        || r.getNotification().getTitle().toLowerCase().contains(keyword.toLowerCase()))
                .map(r -> NotificationListRow.of(
                        r.getNotification(),
                        r.getReadAt() == null ? "읽지않음" : "읽음",
                        1L,
                        r.getReadAt() == null ? 0L : 1L))
                .toList();
    }

    @Transactional
    public Long create(NotificationForm form, Long senderId, Role role) {
        assertCanSend(form.toTargetType(), form.getTargetRefId(), senderId, role);
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("발신자를 찾을 수 없습니다. id=" + senderId));

        Notification notification = Notification.builder()
                .title(form.getTitle())
                .content(form.getContent())
                .priority(form.toPriority())
                .targetType(form.toTargetType())
                .targetRefId(form.getTargetRefId())
                .sendAt(form.resolveSendAt())
                .dueDate(form.getDueDate())
                .sender(sender)
                .status(form.toStatus())
                .build();

        notificationRepository.save(notification);

        // 발송 상태로 저장된 것만 수신자를 펼친다. 예약/임시저장은 아직 대상 확정 전이다.
        if (notification.getStatus() == Notification.NotificationStatus.SENT) {
            fanOut(notification);
        }
        return notification.getId();
    }

    /** 게시 공지를 인앱 알림으로 펼치고, 선택한 경우 같은 대상에게 이메일도 보낸다. */
    @Transactional
    public void dispatchNotice(Notice notice) {
        if (notice == null || notice.getId() == null || notice.getPublishedAt() == null
                || notificationRepository.existsByKindAndSourceRefId(
                        Notification.NotificationKind.NOTICE, notice.getId())) {
            return;
        }
        Notification notification = notificationRepository.save(Notification.builder()
                .title("[공지] " + notice.getTitle())
                .content(notice.getContent())
                .priority(notice.isPinned() ? Notification.Priority.HIGH : Notification.Priority.NORMAL)
                .targetType(notice.getCourse() == null
                        ? Notification.TargetType.ALL : Notification.TargetType.COURSE)
                .targetRefId(notice.getCourse() == null ? null : notice.getCourse().getId())
                .sendAt(notice.getPublishedAt())
                .sender(notice.getAuthor())
                .status(Notification.NotificationStatus.SENT)
                .kind(Notification.NotificationKind.NOTICE)
                .sourceRefId(notice.getId())
                .sourceUrl("/trainee/notice/" + notice.getId())
                .popupOnLogin(notice.isPopupOnLogin())
                .build());
        fanOut(notification);
        if (notice.isEmailNotify()) {
            for (Long userId : resolveTargets(notification)) {
                userRepository.findById(userId)
                        .ifPresent(user -> mailSender.send(user, notification.getTitle(), notification.getContent()));
            }
        }
    }

    /** 성장 리포트처럼 개인에게 자동 생성되는 인앱 알림과 이메일을 함께 보낸다. */
    @Transactional
    public Long dispatchPersonal(User user, String title, String content,
                                 Notification.NotificationKind kind, String sourceUrl,
                                 boolean email) {
        Notification notification = notificationRepository.save(Notification.builder()
                .title(title).content(content).priority(Notification.Priority.NORMAL)
                .targetType(Notification.TargetType.USER).targetRefId(user.getId())
                .sendAt(LocalDateTime.now()).sender(user)
                .status(Notification.NotificationStatus.SENT)
                .kind(kind).sourceUrl(safeTraineeUrl(sourceUrl)).build());
        recipientRepository.save(NotificationRecipient.builder()
                .notification(notification).user(user).build());
        if (email) mailSender.send(user, title, content);
        return notification.getId();
    }

    @Transactional
    public void update(Long id, NotificationForm form, Long actorId, Role role) {
        Notification n = getOrThrow(id);
        assertCanEdit(n, actorId, role);
        assertCanSend(form.toTargetType(), form.getTargetRefId(), actorId, role);
        boolean wasSent = n.getStatus() == Notification.NotificationStatus.SENT;
        n.update(form.getTitle(), form.getContent(), form.toPriority(),
                form.toTargetType(), form.getTargetRefId(), form.resolveSendAt(), form.getDueDate());
        n.changeStatus(form.toStatus());

        // 예약 → 발송으로 넘어간 순간에만 fan-out 한다. 이미 발송된 건은 수신자를 다시 만들지 않는다
        // (유니크 제약 uk_notification_recipient 위반 + 읽음 상태 초기화 방지).
        if (!wasSent && n.getStatus() == Notification.NotificationStatus.SENT) {
            fanOut(n);
        }
    }

    /** 화면의 "읽음처리" / "확인 처리" 버튼 — 로그인 사용자 본인 수신 건만 읽음 표시. */
    @Transactional
    public int markRead(List<Long> notificationIds, Long userId) {
        if (notificationIds == null || notificationIds.isEmpty() || userId == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int changed = 0;
        for (NotificationRecipient r :
                recipientRepository.findByNotificationIdsAndUserId(notificationIds, userId)) {
            if (r.getReadAt() == null) {
                r.markRead(now);
                changed++;
            }
        }
        return changed;
    }

    /** 선택 삭제 (soft delete — @SQLDelete). 수신자 행은 이력이라 남긴다. */
    @Transactional
    public void delete(List<Long> ids, Long actorId, Role role) {
        for (Long id : ids) assertCanEdit(getOrThrow(id), actorId, role);
        notificationRepository.deleteAllById(ids);
    }

    /* ===== 내부 ===== */

    /** 수신 대상자를 행으로 펼친다. 이미 만들어진 수신자는 건너뛴다. */
    /**
     * 예약 발송 처리 — 스케줄러가 호출한다.
     *
     * <p>지금까지 {@code sendAt} 은 저장만 되고 시각이 도래해도 아무 일이 없었다.
     * 화면에서 "예약"으로 저장한 알림이 영영 나가지 않는 상태였다.</p>
     *
     * <p>한 건 실패가 나머지를 막지 않도록 알림마다 독립적으로 처리한다.
     * 실패한 건은 SCHEDULED 로 남아 다음 주기에 다시 시도된다.</p>
     *
     * @return 실제로 발송된 건수
     */
    @Transactional
    public int dispatchDue(LocalDateTime now) {
        List<Notification> due = notificationRepository.findDueScheduled(
                Notification.NotificationStatus.SCHEDULED, now);
        int sent = 0;
        for (Notification n : due) {
            try {
                fanOut(n);
                n.markSent();
                sent++;
            } catch (RuntimeException e) {
                // 이 건만 건너뛴다. 상태를 SCHEDULED 로 두어 다음 주기에 재시도된다.
                log.warn("예약 알림 발송 실패 — id={}, 사유={}", n.getId(), e.getMessage());
            }
        }
        return sent;
    }

    private void fanOut(Notification notification) {
        List<Long> targetUserIds = resolveTargets(notification);
        if (targetUserIds.isEmpty()) {
            return;
        }
        Set<Long> already = new HashSet<>();
        for (NotificationRecipient r : recipientRepository.findByNotificationIds(List.of(notification.getId()))) {
            already.add(r.getUser().getId());
        }
        for (Long userId : targetUserIds) {
            if (already.contains(userId)) {
                continue;
            }
            userRepository.findById(userId).ifPresent(u ->
                    recipientRepository.save(NotificationRecipient.builder()
                            .notification(notification).user(u).build()));
        }
    }

    private List<Long> resolveTargets(Notification n) {
        return switch (n.getTargetType()) {
            case ALL -> userRepository.findAll().stream().map(User::getId).toList();
            case COURSE -> n.getTargetRefId() == null
                    ? List.of()
                    : courseQueryService.findUserIdsByCourseId(n.getTargetRefId());
            case USER -> n.getTargetRefId() == null ? List.of() : List.of(n.getTargetRefId());
        };
    }

    private String safeTraineeUrl(String sourceUrl) {
        return sourceUrl != null && sourceUrl.startsWith("/trainee/") ? sourceUrl : "/trainee/alarm";
    }

    /** id → [수신자 수, 읽은 수] */
    private Map<Long, long[]> countMap(List<Long> ids) {
        Map<Long, long[]> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (Object[] row : notificationRepository.countRecipients(ids)) {
            long total = ((Number) row[1]).longValue();
            long read = row[2] == null ? 0L : ((Number) row[2]).longValue();
            map.put((Long) row[0], new long[]{total, read});
        }
        return Collections.unmodifiableMap(map);
    }

    private void assertCanEdit(Notification notification, Long actorId, Role role) {
        if (role == Role.ADMIN) return;
        if (!notification.getSender().getId().equals(actorId)) {
            throw new AccessDeniedException("본인이 작성한 알림만 수정할 수 있습니다.");
        }
        assertCanSend(notification.getTargetType(), notification.getTargetRefId(), actorId, role);
    }

    /** 강사는 담당 과정 수강생에게만 알림을 보낼 수 있다. */
    private void assertCanSend(Notification.TargetType type, Long targetRefId, Long actorId, Role role) {
        if (role == Role.ADMIN) return;
        if (role != Role.INSTRUCTOR || type != Notification.TargetType.COURSE || targetRefId == null
                || !courseInstructorRepository.existsByCourseIdAndInstructorId(targetRefId, actorId)) {
            throw new AccessDeniedException("강사는 담당 과정에만 알림을 보낼 수 있습니다.");
        }
    }
}
