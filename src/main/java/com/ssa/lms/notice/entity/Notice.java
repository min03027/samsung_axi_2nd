package com.ssa.lms.notice.entity;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 공지사항.
 *
 * 매핑 근거
 *  - static/js/notices.js noticeData (id, category, title, author, date, editDate, views)
 *  - templates/admin/admin-07-notice/notice-add.html, notice-detail.html
 *
 * 내역서 요건 "훈련생 유의사항 등재(정보제공)" 의 근거 화면이다.
 */
@Entity
@Table(
        name = "notice",
        indexes = {
                @Index(name = "idx_notice_course", columnList = "course_id"),
                @Index(name = "idx_notice_published", columnList = "published_at")
        }
)
@SQLDelete(sql = "UPDATE notice SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private NoticeCategory category;

    /** null = 전체 공지, 값 존재 = 해당 과정 수강생에게만 노출. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount;

    /** 상단 고정. */
    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    /** 게시 시각. null 이면 미게시(임시저장). */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 훈련생이 로그인한 직후 아직 읽지 않은 공지를 팝업으로 노출할지 여부. */
    @Column(name = "popup_on_login", nullable = false, columnDefinition = "boolean default false")
    private boolean popupOnLogin;

    /** 게시 시점에 대상 훈련생에게 이메일도 함께 보낼지 여부. */
    @Column(name = "email_notify", nullable = false, columnDefinition = "boolean default false")
    private boolean emailNotify;

    @OneToMany(mappedBy = "notice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NoticeAttachment> attachments = new ArrayList<>();

    @Builder
    public Notice(NoticeCategory category, Course course, String title, String content,
                  User author, boolean pinned, LocalDateTime publishedAt,
                  boolean popupOnLogin, boolean emailNotify) {
        this.category = category;
        this.course = course;
        this.title = title;
        this.content = content;
        this.author = author;
        this.pinned = pinned;
        this.publishedAt = publishedAt;
        this.popupOnLogin = popupOnLogin;
        this.emailNotify = emailNotify;
        this.viewCount = 0;
    }

    public void addAttachment(NoticeAttachment attachment) {
        this.attachments.add(attachment);
        attachment.assignNotice(this);
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

    public void update(NoticeCategory category, String title, String content, boolean pinned,
                       boolean popupOnLogin, boolean emailNotify) {
        this.category = category;
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.popupOnLogin = popupOnLogin;
        this.emailNotify = emailNotify;
    }

    /** 노출 범위 변경. null 이면 전체 공지. */
    public void changeCourse(Course course) {
        this.course = course;
    }

    /** 게시. 이미 게시된 글은 최초 게시 시각을 유지한다(수정 때마다 밀리면 안 된다). */
    public void publish(LocalDateTime at) {
        if (this.publishedAt == null) {
            this.publishedAt = at;
        }
    }

    /** 게시 취소(임시저장으로 되돌림). */
    public void unpublish() {
        this.publishedAt = null;
    }

    public void clearAttachments() {
        this.attachments.clear();
    }
}
