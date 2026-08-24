package com.ssa.lms.review.repository;

import com.ssa.lms.review.entity.ReviewStatus;
import com.ssa.lms.review.entity.StudentReview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentReviewRepository extends JpaRepository<StudentReview, Long> {

    @EntityGraph(attributePaths = {"course", "employmentOrganization", "exposureSites", "exposurePositions"})
    List<StudentReview> findAllByOrderByFeaturedDescDisplayOrderAscIdDesc();

    @EntityGraph(attributePaths = {"course", "employmentOrganization", "exposureSites", "exposurePositions"})
    List<StudentReview> findByStatusAndPublicVisibleTrueAndHomepagePublicationConsentTrueOrderByFeaturedDescDisplayOrderAscIdDesc(
            ReviewStatus status);

    @EntityGraph(attributePaths = {"course", "employmentOrganization", "exposureSites", "exposurePositions"})
    Optional<StudentReview> findOneById(Long id);
}
