package com.ssa.lms.notice.entity;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림 수신자별 상태. 화면 admin-alarm.html 의 markReadBtn / 읽음 배지에 대응.
 *
 * 발송 시점에 수신 대상자를 확정해 행으로 펼친다(fan-out). 나중에 수강생이 추가돼도
 * 과거 알림이 소급 발송되지 않아야 하기 때문이다.
 */
@Entity
@Table(
        name = "notification_recipient",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_recipient", columnNames = {"notification_id", "user_id"}),
        indexes = @Index(name = "idx_recipient_user_read", columnList = "user_id, read_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationRecipient extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    public NotificationRecipient(Notification notification, User user) {
        this.notification = notification;
        this.user = user;
    }

    public void markRead(LocalDateTime at) {
        if (this.readAt == null) {
            this.readAt = at;
        }
    }

    /** 관리자가 기존 공지를 새 로그인 팝업으로 전환했을 때 다시 확인할 수 있게 한다. */
    public void resetUnread() {
        this.readAt = null;
    }
}
