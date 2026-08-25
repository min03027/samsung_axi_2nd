package com.ssa.lms.content.repository;

import com.ssa.lms.content.entity.ContentLibraryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentLibraryItemRepository extends JpaRepository<ContentLibraryItem, Long> {

    List<ContentLibraryItem> findAllByOrderByUpdatedAtDescIdDesc();
}
