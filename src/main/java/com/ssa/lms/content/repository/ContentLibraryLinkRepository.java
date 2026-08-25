package com.ssa.lms.content.repository;

import com.ssa.lms.content.entity.ContentLibraryLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentLibraryLinkRepository extends JpaRepository<ContentLibraryLink, Long> {

    List<ContentLibraryLink> findByLibraryItemIdOrderByIdAsc(Long libraryItemId);

    List<ContentLibraryLink> findByLibraryItemIdAndAutoSyncTrueOrderByIdAsc(Long libraryItemId);

    Optional<ContentLibraryLink> findByContentId(Long contentId);

    long countByLibraryItemId(Long libraryItemId);

    long countByLibraryItemIdAndAutoSyncTrue(Long libraryItemId);
}
