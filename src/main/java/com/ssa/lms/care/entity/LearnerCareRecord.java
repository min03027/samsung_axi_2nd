package com.ssa.lms.care.entity;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 학습일지에서 상담·수료 후 추적까지 한 학생의 케어 흐름을 잇는 기록. */
@Entity
@Table(name = "learner_care_record", indexes = {
        @Index(name = "idx_care_trainee_created", columnList = "trainee_id, created_at"),
        @Index(name = "idx_care_status_followup", columnList = "status, follow_up_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearnerCareRecord extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainee_id", nullable = false)
    private User trainee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 30)
    private RecordType recordType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CareStatus status;

    @Column(nullable = false, length = 160)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(name = "follow_up_at")
    private LocalDateTime followUpAt;

    @Builder
    public LearnerCareRecord(User trainee, User author, RecordType recordType, CareStatus status,
                             String subject, String content, String result, LocalDateTime followUpAt) {
        this.trainee = trainee;
        this.author = author;
        this.recordType = recordType;
        this.status = status;
        this.subject = subject;
        this.content = content;
        this.result = result;
        this.followUpAt = followUpAt;
    }

    public void updateFollowUp(CareStatus status, String result, LocalDateTime followUpAt) {
        this.status = status;
        this.result = result;
        this.followUpAt = followUpAt;
    }

    public enum RecordType {
        LEARNING_JOURNAL("학습일지"), CONSULTATION("상담"), POST_COMPLETION("수료 후 추적");
        private final String label;
        RecordType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum CareStatus {
        OBSERVATION("관찰 필요"), SCHEDULED("상담 예정"), IN_PROGRESS("상담 진행"),
        COMPLETED("조치 완료"), NORMALIZED("정상화");
        private final String label;
        CareStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
}
