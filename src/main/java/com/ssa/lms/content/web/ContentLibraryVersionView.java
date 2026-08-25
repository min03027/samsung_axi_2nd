package com.ssa.lms.content.web;

import com.ssa.lms.content.entity.ContentLibraryVersion;

import java.time.LocalDateTime;

/** 버전 이력 표시용 불변 DTO. */
public record ContentLibraryVersionView(
        Long id,
        Long libraryItemId,
        String libraryTitle,
        int versionNo,
        String typeLabel,
        String title,
        String originalFileName,
        String industryTags,
        String changeSummary,
        int syncedContentCount,
        Long changedBy,
        LocalDateTime changedAt
) {
    public static ContentLibraryVersionView of(ContentLibraryVersion version) {
        return new ContentLibraryVersionView(
                version.getId(), version.getLibraryItem().getId(), version.getLibraryItem().getTitle(),
                version.getVersionNo(), version.getType().getLabel(), version.getTitle(),
                version.getOriginalFileName(), version.getIndustryTags(), version.getChangeSummary(),
                version.getSyncedContentCount(), version.getCreatedBy(), version.getCreatedAt());
    }
}
