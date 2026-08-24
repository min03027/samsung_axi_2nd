package com.ssa.lms.admission.repository;

import com.ssa.lms.admission.entity.CourseApplication;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseApplicationRepository extends JpaRepository<CourseApplication, Long> {

    boolean existsByCourseIdAndEmailFingerprint(Long courseId, String emailFingerprint);

    boolean existsByCourseIdAndPhoneFingerprint(Long courseId, String phoneFingerprint);

    @EntityGraph(attributePaths = {"course", "assignedTo", "matchedUser"})
    List<CourseApplication> findAllByOrderBySubmittedAtDesc();

    @Override
    @EntityGraph(attributePaths = {"course", "assignedTo", "matchedUser"})
    Optional<CourseApplication> findById(Long id);
}
