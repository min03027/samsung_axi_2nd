package com.ssa.lms.content.request;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.content.entity.Content;
import com.ssa.lms.content.entity.ContentLibraryItem;
import com.ssa.lms.content.entity.ContentType;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "content_request", indexes = {
        @Index(name = "idx_content_request_trainee", columnList = "trainee_id, created_at"),
        @Index(name = "idx_content_request_course_status", columnList = "course_id, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentRequest extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainee_id", nullable = false)
    private User trainee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_type", length = 20)
    private ContentType preferredType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_item_id")
    private ContentLibraryItem libraryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fulfilled_content_id")
    private Content fulfilledContent;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Builder
    private ContentRequest(User trainee, Course course, ContentType preferredType,
                           String title, String reason) {
        this.trainee = trainee;
        this.course = course;
        this.preferredType = preferredType;
        this.title = title;
        this.reason = reason;
        this.status = ContentRequestStatus.RECEIVED;
    }

    public void startReview(User staff) {
        if (status != ContentRequestStatus.RECEIVED) {
            throw new IllegalStateException("접수 상태의 요청만 검토할 수 있습니다.");
        }
        this.assignedTo = staff;
        this.status = ContentRequestStatus.REVIEWING;
    }

    public void fulfill(User staff, ContentLibraryItem item, Content content, String note) {
        if (status == ContentRequestStatus.FULFILLED || status == ContentRequestStatus.REJECTED) {
            throw new IllegalStateException("이미 처리가 끝난 요청입니다.");
        }
        this.assignedTo = staff;
        this.libraryItem = item;
        this.fulfilledContent = content;
        this.decisionNote = note;
        this.status = ContentRequestStatus.FULFILLED;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject(User staff, String note) {
        if (status == ContentRequestStatus.FULFILLED || status == ContentRequestStatus.REJECTED) {
            throw new IllegalStateException("이미 처리가 끝난 요청입니다.");
        }
        this.assignedTo = staff;
        this.decisionNote = note;
        this.status = ContentRequestStatus.REJECTED;
        this.decidedAt = LocalDateTime.now();
    }
}
