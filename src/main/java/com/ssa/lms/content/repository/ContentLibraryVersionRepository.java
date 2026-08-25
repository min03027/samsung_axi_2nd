package com.ssa.lms.content.repository;

import com.ssa.lms.content.entity.ContentLibraryVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentLibraryVersionRepository extends JpaRepository<ContentLibraryVersion, Long> {

    List<ContentLibraryVersion> findByLibraryItemIdOrderByVersionNoDesc(Long libraryItemId);

    List<ContentLibraryVersion> findAllByOrderByCreatedAtDescIdDesc();

    long countByLibraryItemId(Long libraryItemId);
}
