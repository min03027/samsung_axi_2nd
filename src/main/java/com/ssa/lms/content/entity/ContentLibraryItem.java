package com.ssa.lms.content.entity;

import com.ssa.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 여러 과정에서 재사용하는 공용 콘텐츠 원본.
 *
 * <p>과정에 배치된 {@link Content}는 학습·진도 FK를 유지하고, 이 원본의 새 버전이 발행되면
 * {@link ContentLibraryLink}를 통해 연결된 콘텐츠에 메타와 파일 참조가 동기화된다.</p>
 */
@Entity
@Table(name = "content_library_item", indexes = {
        @Index(name = "idx_content_library_status", columnList = "status, updated_at"),
        @Index(name = "idx_content_library_type", columnList = "type")
})
@SQLDelete(sql = "UPDATE content_library_item SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND row_version = ?")
@SQLRestriction("is_deleted = false")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentLibraryItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "original_file_name", length = 300)
    private String originalFileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 150)
    private String mimeType;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "page_count")
    private Integer pageCount;

    /** 쉼표로 구분한 산업·직무 태그. 외부 수요 데이터가 연결되기 전에도 업데이트 맥락을 남긴다. */
    @Column(name = "industry_tags", length = 500)
    private String industryTags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentLibraryStatus status;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Builder
    private ContentLibraryItem(ContentType type, String title, String description,
                               String fileUrl, String originalFileName, Long fileSize, String mimeType,
                               Integer durationSeconds, Integer pageCount, String industryTags,
                               ContentLibraryStatus status) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.fileUrl = fileUrl;
        this.originalFileName = originalFileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.durationSeconds = durationSeconds;
        this.pageCount = pageCount;
        this.industryTags = industryTags;
        this.status = status != null ? status : ContentLibraryStatus.PUBLISHED;
        this.currentVersion = 1;
    }

    /** 새 원본 버전을 발행하고 버전 번호를 반환한다. */
    public int publish(ContentType type, String title, String description,
                       String fileUrl, String originalFileName, Long fileSize, String mimeType,
                       Integer durationSeconds, Integer pageCount, String industryTags,
                       ContentLibraryStatus status) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.fileUrl = fileUrl;
        this.originalFileName = originalFileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.durationSeconds = durationSeconds;
        this.pageCount = pageCount;
        this.industryTags = industryTags;
        this.status = status != null ? status : this.status;
        this.currentVersion += 1;
        return this.currentVersion;
    }

    public void changeStatus(ContentLibraryStatus status) {
        this.status = status;
    }
}
