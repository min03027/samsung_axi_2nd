package com.ssa.lms.content.request;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentRequestRepository extends JpaRepository<ContentRequest, Long> {
    List<ContentRequest> findByTraineeIdOrderByCreatedAtDesc(Long traineeId);
    List<ContentRequest> findAllByOrderByCreatedAtDesc();
    List<ContentRequest> findByCourseIdInOrderByCreatedAtDesc(List<Long> courseIds);
}
