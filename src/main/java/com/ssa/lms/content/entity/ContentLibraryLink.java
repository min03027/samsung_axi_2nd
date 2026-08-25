package com.ssa.lms.content.entity;

import com.ssa.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 공용 원본과 과정에 실제 배치된 콘텐츠를 잇는 동기화 연결. */
@Entity
@Table(name = "content_library_link", uniqueConstraints = {
        @UniqueConstraint(name = "uk_content_library_link_content", columnNames = "content_id")
}, indexes = {
        @Index(name = "idx_content_library_link_item", columnList = "library_item_id, auto_sync")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentLibraryLink extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "library_item_id", nullable = false)
    private ContentLibraryItem libraryItem;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Column(name = "auto_sync", nullable = false)
    private boolean autoSync;

    @Column(name = "applied_version", nullable = false)
    private int appliedVersion;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Builder
    private ContentLibraryLink(ContentLibraryItem libraryItem, Content content, Boolean autoSync,
                               Integer appliedVersion) {
        this.libraryItem = libraryItem;
        this.content = content;
        this.autoSync = autoSync == null || autoSync;
        this.appliedVersion = appliedVersion != null ? appliedVersion : libraryItem.getCurrentVersion();
        this.lastSyncedAt = LocalDateTime.now();
    }

    public void markSynced(int versionNo) {
        this.appliedVersion = versionNo;
        this.lastSyncedAt = LocalDateTime.now();
    }

    public void changeAutoSync(boolean autoSync) {
        this.autoSync = autoSync;
    }
}
