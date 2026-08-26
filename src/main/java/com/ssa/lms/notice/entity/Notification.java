package com.ssa.lms.notice.entity;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * 알림(발송 단위).
 *
 * 매핑 근거: templates/admin/admin-07-notice/admin-alarm-add.html, admin-alarm.html
 *   (alarmTitle, alarmContent, prioritySelect, statusSelect, dueDate)
 *
 * 주의: 정적 파일명이 alram/arlam 으로 흔들려 있다. PLAN.md Phase 0 에서 alarm 으로 통일하기로
 * 했으므로, 패키지/클래스명은 표준어인 Notification 으로 가고 템플릿 경로만 alarm 을 쓴다.
 *
 * 수신자별 읽음 상태는 NotificationRecipient 가 갖는다.
 */
@Entity
@Table(
        name = "notification",
        indexes = {
                @Index(name = "idx_notification_send_at", columnList = "send_at"),
                @Index(name = "idx_notification_status", columnList = "status")
        }
)
@SQLDelete(sql = "UPDATE notification SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 10, nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20, nullable = false)
    private TargetType targetType;

    /** targetType=COURSE 면 course.id, USER 면 user.id, ALL 이면 null. */
    @Column(name = "target_ref_id")
    private Long targetRefId;

    /** 예약 발송 시각. 즉시 발송이면 생성 시각과 동일. */
    @Column(name = "send_at", nullable = false)
    private LocalDateTime sendAt;

    /** 화면의 dueDate. 알림이 가리키는 마감 기한(과제/시험 마감 등). */
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    /** 알림의 생성 출처. null 은 기존 일반 알림으로 간주한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_kind", length = 30)
    private NotificationKind kind;

    /** 공지·리포트·차시 등 원본 엔티티 id. 중복 자동 발송을 막는 키로 사용한다. */
    @Column(name = "source_ref_id")
    private Long sourceRefId;

    /** 사용자가 확인할 원본 화면 경로. */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    /** 로그인 직후 미확인 상태라면 팝업으로 노출할지 여부. */
    @Column(name = "popup_on_login", nullable = false, columnDefinition = "boolean default false")
    private boolean popupOnLogin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private NotificationStatus status;

    @Builder
    public Notification(String title, String content, Priority priority, TargetType targetType,
                        Long targetRefId, LocalDateTime sendAt, LocalDateTime dueDate,
                        User sender, NotificationStatus status, NotificationKind kind,
                        Long sourceRefId, String sourceUrl, boolean popupOnLogin) {
        this.title = title;
        this.content = content;
        this.priority = priority;
        this.targetType = targetType;
        this.targetRefId = targetRefId;
        this.sendAt = sendAt;
        this.dueDate = dueDate;
        this.sender = sender;
        this.status = status;
        this.kind = kind == null ? NotificationKind.GENERAL : kind;
        this.sourceRefId = sourceRefId;
        this.sourceUrl = sourceUrl;
        this.popupOnLogin = popupOnLogin;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
    }

    public void update(String title, String content, Priority priority,
                       TargetType targetType, Long targetRefId,
                       LocalDateTime sendAt, LocalDateTime dueDate) {
        this.title = title;
        this.content = content;
        this.priority = priority;
        this.targetType = targetType;
        this.targetRefId = targetRefId;
        this.sendAt = sendAt;
        this.dueDate = dueDate;
    }

    public void changeStatus(NotificationStatus status) {
        this.status = status;
    }

    /** 게시 공지의 수정 내용을 기존 알림에도 동기화한다. */
    public void syncNotice(String title, String content, Priority priority,
                           TargetType targetType, Long targetRefId,
                           LocalDateTime sendAt, String sourceUrl, boolean popupOnLogin) {
        this.title = title;
        this.content = content;
        this.priority = priority;
        this.targetType = targetType;
        this.targetRefId = targetRefId;
        this.sendAt = sendAt;
        this.sourceUrl = sourceUrl;
        this.popupOnLogin = popupOnLogin;
        this.status = NotificationStatus.SENT;
    }

    /** 게시 취소된 공지는 로그인 팝업과 알림 상세 진입 대상에서 제외한다. */
    public void withdrawNotice() {
        this.popupOnLogin = false;
        this.status = NotificationStatus.CANCELED;
    }

    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public enum TargetType {
        /** 전체 사용자. */
        ALL,
        /** 특정 과정 수강생. */
        COURSE,
        /** 특정 사용자 1명. */
        USER
    }

    public enum NotificationStatus {
        DRAFT,
        SCHEDULED,
        SENT,
        CANCELED
    }

    public enum NotificationKind {
        GENERAL,
        NOTICE,
        GROWTH_REPORT,
        REMINDER
    }
}
