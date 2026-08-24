package com.ssa.lms.admission.repository;

import com.ssa.lms.admission.entity.ConsultationRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsultationRequestRepository extends JpaRepository<ConsultationRequest, Long> {

    boolean existsByEmailFingerprint(String emailFingerprint);

    boolean existsByPhoneFingerprint(String phoneFingerprint);

    @EntityGraph(attributePaths = {"course", "assignedTo", "matchedUser"})
    List<ConsultationRequest> findAllByOrderBySubmittedAtDesc();

    @Override
    @EntityGraph(attributePaths = {"course", "assignedTo", "matchedUser"})
    Optional<ConsultationRequest> findById(Long id);
}
