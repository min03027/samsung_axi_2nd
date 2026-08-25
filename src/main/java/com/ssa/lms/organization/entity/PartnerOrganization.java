package com.ssa.lms.organization.entity;

import com.ssa.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/** 홈페이지·과정·향후 취업 연계에서 재사용하는 기업/기관 마스터. */
@Entity
@Table(name = "partner_organization", uniqueConstraints = {
        @UniqueConstraint(name = "uk_partner_organization_name", columnNames = "normalized_name"),
        @UniqueConstraint(name = "uk_partner_organization_domain", columnNames = "website_domain")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerOrganization extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OrganizationType type;

    @Column(name = "one_line_description", length = 500)
    private String oneLineDescription;

    @Column(name = "detailed_description", columnDefinition = "TEXT")
    private String detailedDescription;

    @Column(name = "logo_url", length = 1000)
    private String logoUrl;

    @Column(name = "website_url", length = 1000)
    private String websiteUrl;

    @Column(name = "website_domain", length = 255)
    private String websiteDomain;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "organization_relationship_type",
            joinColumns = @JoinColumn(name = "organization_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", length = 40)
    private Set<OrganizationRelationshipType> relationshipTypes = new LinkedHashSet<>();

    @Column(name = "homepage_exposure", nullable = false)
    private boolean homepageExposure;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "organization_exposure_site",
            joinColumns = @JoinColumn(name = "organization_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "exposure_site", length = 30)
    private Set<OrganizationExposureSite> exposureSites = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "organization_exposure_position",
            joinColumns = @JoinColumn(name = "organization_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "exposure_position", length = 40)
    private Set<OrganizationExposurePosition> exposurePositions = new LinkedHashSet<>();

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "internal_note", columnDefinition = "TEXT")
    private String internalNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationStatus status;

    @Builder
    private PartnerOrganization(String name, String normalizedName, OrganizationType type,
                                String oneLineDescription, String detailedDescription,
                                String logoUrl, String websiteUrl, String websiteDomain,
                                Set<OrganizationRelationshipType> relationshipTypes,
                                boolean homepageExposure, Set<OrganizationExposureSite> exposureSites,
                                Set<OrganizationExposurePosition> exposurePositions, int displayOrder,
                                String internalNote, OrganizationStatus status) {
        update(name, normalizedName, type, oneLineDescription, detailedDescription,
                logoUrl, websiteUrl, websiteDomain, relationshipTypes, homepageExposure,
                exposureSites, exposurePositions, displayOrder, internalNote, status);
    }

    public void update(String name, String normalizedName, OrganizationType type,
                       String oneLineDescription, String detailedDescription,
                       String logoUrl, String websiteUrl, String websiteDomain,
                       Set<OrganizationRelationshipType> relationshipTypes,
                       boolean homepageExposure, Set<OrganizationExposureSite> exposureSites,
                       Set<OrganizationExposurePosition> exposurePositions, int displayOrder,
                       String internalNote, OrganizationStatus status) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.type = type;
        this.oneLineDescription = blankToNull(oneLineDescription);
        this.detailedDescription = blankToNull(detailedDescription);
        this.logoUrl = blankToNull(logoUrl);
        this.websiteUrl = blankToNull(websiteUrl);
        this.websiteDomain = blankToNull(websiteDomain);
        this.relationshipTypes.clear();
        this.relationshipTypes.addAll(copy(relationshipTypes));
        this.homepageExposure = homepageExposure;
        this.exposureSites.clear();
        this.exposureSites.addAll(copy(exposureSites));
        this.exposurePositions.clear();
        this.exposurePositions.addAll(copy(exposurePositions));
        this.displayOrder = Math.max(displayOrder, 0);
        this.internalNote = blankToNull(internalNote);
        this.status = status == null ? OrganizationStatus.ACTIVE : status;
    }

    private static <T> Set<T> copy(Set<T> source) {
        return source == null ? new LinkedHashSet<>() : new LinkedHashSet<>(source);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
