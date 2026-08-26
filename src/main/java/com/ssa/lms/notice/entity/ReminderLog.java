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
 * 독려·리마인드 알림 발송 기록.
 *
 * <p><b>왜 필요한가:</b> 스케줄러가 주기적으로 돌면서 미제출자를 찾아 알림을 보내는데,
 * 기록이 없으면 주기마다 같은 사람에게 같은 알림이 계속 쌓인다. 마감 24시간 전 알림을
 * 1시간 주기로 돌리면 하루에 24번 가는 셈이다.</p>
 *
 * <p>(사용자, 종류, 대상, 단계) 조합으로 유일하게 걸어서, 한 단계는 딱 한 번만 나가게 한다.</p>
 *
 * <p><b>soft delete 를 걸지 않는다.</b> 발송 기록은 지울 일이 없고, {@code @SQLDelete} 를
 * 걸면 유니크 제약과 충돌해 재발송이 영영 막힌다(과제·설문·문제은행에서 실제로 터진 함정).</p>
 */
@Entity
@Table(
        name = "reminder_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reminder_log",
                columnNames = {"user_id", "reminder_type", "target_ref_id", "stage"}),
        indexes = @Index(name = "idx_reminder_log_sent", columnList = "sent_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReminderLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", length = 20, nullable = false)
    private ReminderType reminderType;

    /** reminderType 에 따라 course_assignment.id / exam.id / survey.id. */
    @Column(name = "target_ref_id", nullable = false)
    private Long targetRefId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 20, nullable = false)
    private ReminderStage stage;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Builder
    public ReminderLog(User user, ReminderType reminderType, Long targetRefId,
                       ReminderStage stage, LocalDateTime sentAt) {
        this.user = user;
        this.reminderType = reminderType;
        this.targetRefId = targetRefId;
        this.stage = stage;
        this.sentAt = sentAt;
    }

    public enum ReminderType {
        /** 과제 미제출 */
        ASSIGNMENT,
        /** 시험 미응시 */
        EXAM,
        /** 설문 미응답 */
        SURVEY,
        /** 반복 수업 시작 알림 */
        LESSON
    }

    /** 앨리스 항목의 "24시간 전 / 1시간 전 리마인드" + 마감 후 독려. */
    public enum ReminderStage {
        BEFORE_24H,
        BEFORE_1H,
        /** 마감이 지났는데도 미제출 — 독려. */
        OVERDUE
    }
}
