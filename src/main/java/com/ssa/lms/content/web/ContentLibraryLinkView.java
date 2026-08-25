package com.ssa.lms.content.web;

import com.ssa.lms.content.entity.ContentLibraryLink;

import java.time.LocalDateTime;

/** 공용 원본이 배치된 과정·차시 연결 표시용 DTO. */
public record ContentLibraryLinkView(
        Long id,
        Long contentId,
        String courseName,
        String sessionName,
        boolean autoSync,
        int appliedVersion,
        int currentVersion,
        boolean updateAvailable,
        LocalDateTime lastSyncedAt
) {
    public static ContentLibraryLinkView of(ContentLibraryLink link) {
        var content = link.getContent();
        return new ContentLibraryLinkView(
                link.getId(), content.getId(), content.getCourse().getCourseName(),
                content.getSession() != null ? content.getSession().getName() : "과정 공용",
                link.isAutoSync(), link.getAppliedVersion(), link.getLibraryItem().getCurrentVersion(),
                link.getAppliedVersion() < link.getLibraryItem().getCurrentVersion(), link.getLastSyncedAt());
    }
}
