package com.ssa.lms.organization.repository;

import com.ssa.lms.organization.entity.OrganizationStatus;
import com.ssa.lms.organization.entity.PartnerOrganization;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerOrganizationRepository extends JpaRepository<PartnerOrganization, Long> {

    boolean existsByNormalizedName(String normalizedName);
    boolean existsByNormalizedNameAndIdNot(String normalizedName, Long id);
    boolean existsByWebsiteDomain(String websiteDomain);
    boolean existsByWebsiteDomainAndIdNot(String websiteDomain, Long id);

    @EntityGraph(attributePaths = {"relationshipTypes", "exposureSites", "exposurePositions"})
    List<PartnerOrganization> findAllByOrderByDisplayOrderAscNameAsc();

    @EntityGraph(attributePaths = {"relationshipTypes", "exposureSites", "exposurePositions"})
    List<PartnerOrganization> findByStatusAndHomepageExposureTrueOrderByDisplayOrderAscNameAsc(
            OrganizationStatus status);

    @EntityGraph(attributePaths = {"relationshipTypes", "exposureSites", "exposurePositions"})
    Optional<PartnerOrganization> findOneById(Long id);
}
