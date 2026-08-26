package com.ssa.lms.completion.repository;

import com.ssa.lms.completion.entity.CertificateDesign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificateDesignRepository extends JpaRepository<CertificateDesign, Long> {

    Optional<CertificateDesign> findByCourseId(Long courseId);
}
