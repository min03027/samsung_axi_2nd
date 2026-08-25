package com.ssa.lms.content.entity;

import com.ssa.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공용 콘텐츠 원본의 변경 불변 스냅샷. */
@Entity
@Table(name = "content_library_version", uniqueConstraints = {
        @UniqueConstraint(name = "uk_content_library_version", columnNames = {"library_item_id", "version_no"})
}, indexes = {
        @Index(name = "idx_content_library_version_created", columnList = "library_item_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentLibraryVersion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "library_item_id", nullable = false)
    private ContentLibraryItem libraryItem;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

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

    @Column(name = "industry_tags", length = 500)
    private String industryTags;

    @Column(name = "change_summary", nullable = false, columnDefinition = "TEXT")
    private String changeSummary;

    @Column(name = "synced_content_count", nullable = false)
    private int syncedContentCount;

    @Builder
    private ContentLibraryVersion(ContentLibraryItem libraryItem, int versionNo, ContentType type,
                                  String title, String description, String fileUrl,
                                  String originalFileName, Long fileSize, String mimeType,
                                  Integer durationSeconds, Integer pageCount, String industryTags,
                                  String changeSummary, int syncedContentCount) {
        this.libraryItem = libraryItem;
        this.versionNo = versionNo;
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
        this.changeSummary = changeSummary;
        this.syncedContentCount = syncedContentCount;
    }

    public static ContentLibraryVersion snapshot(ContentLibraryItem item, String changeSummary,
                                                 int syncedContentCount) {
        return ContentLibraryVersion.builder()
                .libraryItem(item)
                .versionNo(item.getCurrentVersion())
                .type(item.getType())
                .title(item.getTitle())
                .description(item.getDescription())
                .fileUrl(item.getFileUrl())
                .originalFileName(item.getOriginalFileName())
                .fileSize(item.getFileSize())
                .mimeType(item.getMimeType())
                .durationSeconds(item.getDurationSeconds())
                .pageCount(item.getPageCount())
                .industryTags(item.getIndustryTags())
                .changeSummary(changeSummary)
                .syncedContentCount(syncedContentCount)
                .build();
    }
}
