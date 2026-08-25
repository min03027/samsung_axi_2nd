package com.ssa.lms.content.entity;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.Session;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 학습 콘텐츠 (VOD/문서). 과정(Course) 필수 · 차시(Session) 선택으로 연결된다.
 *
 * <ul>
 *   <li>과정(course_id) 은 필수 — 내역서 요건상 학습 데이터는 과정 단위로 수집된다.</li>
 *   <li>차시(session_id) 는 선택 — 차시에 배치되지 않은(과정 공용) 콘텐츠도 허용한다.</li>
 *   <li>업로드 파일은 {@code fileUrl}(서비스 경로) 로 접근한다. 원본명/크기/MIME 은 별도 보관.</li>
 *   <li>진도(Progress) 는 콘텐츠 단위로 기록된다.</li>
 *   <li>Content↔Exam: <b>Exam 이 content_id(nullable) 를 갖는 방향</b>(B→A 의존, a-requests.md P2).
 *       따라서 이 엔티티는 Exam 을 참조하지 않는다.</li>
 * </ul>
 *
 * soft delete 적용 (3년 보존 요건) — 삭제 시 is_deleted 마킹만.
 */
@Entity
@Table(name = "content")
@SQLDelete(sql = "UPDATE content SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    /** 차시 — 미배치(과정 공용) 콘텐츠는 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /* ===== 업로드 파일 메타 ===== */

    /** 재생/열람 서비스 경로 (예: /content/files/2026/uuid.mp4) */
    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "original_file_name", length = 300)
    private String originalFileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 150)
    private String mimeType;

    /* ===== 진도 판정 기준값 ===== */

    /** VIDEO 재생 길이(초) — 진도율 계산 기준 */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** DOCUMENT 총 페이지 수 — 진도율 계산 기준 */
    @Column(name = "page_count")
    private Integer pageCount;

    /* ===== 배치/상태 ===== */

    /** 차시(또는 과정) 내 정렬 순서 (1부터) */
    @Column(name = "order_no", nullable = false)
    private int orderNo;

    /** 이수 판정 시 반드시 완료해야 하는 콘텐츠인지 (ProgressQueryService#hasCompletedAll 기준) */
    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status;

    @Builder
    private Content(Course course, Session session, ContentType type, String title, String description,
                    String fileUrl, String originalFileName, Long fileSize, String mimeType,
                    Integer durationSeconds, Integer pageCount, Integer orderNo,
                    Boolean required, ContentStatus status) {
        this.course = course;
        this.session = session;
        this.type = type;
        this.title = title;
        this.description = description;
        this.fileUrl = fileUrl;
        this.originalFileName = originalFileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.durationSeconds = durationSeconds;
        this.pageCount = pageCount;
        this.orderNo = orderNo != null ? orderNo : 1;
        this.required = required == null || required;
        this.status = status != null ? status : ContentStatus.ACTIVE;
    }

    /** 메타 정보 수정 (파일 교체가 없는 경우). */
    public void update(Session session, String title, String description,
                       Integer durationSeconds, Integer pageCount,
                       Integer orderNo, Boolean required, ContentStatus status) {
        this.session = session;
        this.title = title;
        this.description = description;
        this.durationSeconds = durationSeconds;
        this.pageCount = pageCount;
        if (orderNo != null) this.orderNo = orderNo;
        if (required != null) this.required = required;
        if (status != null) this.status = status;
    }

    /** 업로드 파일 교체. */
    public void replaceFile(String fileUrl, String originalFileName, Long fileSize, String mimeType) {
        this.fileUrl = fileUrl;
        this.originalFileName = originalFileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
    }

    /**
     * 공용 콘텐츠 원본의 새 버전을 이 과정 콘텐츠에 반영한다.
     * 콘텐츠 id와 과정/차시 연결은 유지하므로 기존 진도와 학습 이력이 끊기지 않는다.
     */
    public void syncFromLibrary(ContentType type, String title, String description,
                                String fileUrl, String originalFileName, Long fileSize, String mimeType,
                                Integer durationSeconds, Integer pageCount) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.fileUrl = fileUrl;
        this.originalFileName = originalFileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.durationSeconds = durationSeconds;
        this.pageCount = pageCount;
    }

    public void changeStatus(ContentStatus status) {
        this.status = status;
    }

    /** 진도율 기준값(VIDEO=길이초, DOCUMENT=페이지수). 없으면 0. */
    public int progressDenominator() {
        if (type == ContentType.VIDEO) {
            return durationSeconds != null ? durationSeconds : 0;
        }
        return pageCount != null ? pageCount : 0;
    }
}
