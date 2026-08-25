package com.ssa.lms.content.web;

import com.ssa.lms.content.entity.ContentLibraryItem;

import java.time.LocalDateTime;

/** 공용 콘텐츠 목록 표시용 DTO. */
public record ContentLibraryItemView(
        Long id,
        String type,
        String typeLabel,
        String title,
        String description,
        String originalFileName,
        String industryTags,
        String status,
        String statusLabel,
        int currentVersion,
        long linkedContentCount,
        long autoSyncCount,
        LocalDateTime updatedAt
) {
    public static ContentLibraryItemView of(ContentLibraryItem item, long linkedContentCount,
                                            long autoSyncCount) {
        return new ContentLibraryItemView(
                item.getId(), item.getType().name(), item.getType().getLabel(), item.getTitle(),
                item.getDescription(), item.getOriginalFileName(), item.getIndustryTags(),
                item.getStatus().name(), item.getStatus().getLabel(), item.getCurrentVersion(),
                linkedContentCount, autoSyncCount, item.getUpdatedAt() != null ? item.getUpdatedAt() : item.getCreatedAt());
    }
}
