package com.ssa.lms.organization.web;

import com.ssa.lms.organization.entity.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
public class OrganizationForm {

    @NotBlank(message = "기업·기관명을 입력하세요.")
    @Size(max = 200)
    private String name;

    @NotNull(message = "기관 유형을 선택하세요.")
    private OrganizationType type = OrganizationType.COMPANY;

    @Size(max = 500)
    private String oneLineDescription;

    private String detailedDescription;

    @Pattern(regexp = "^$|https?://.+", message = "로고 주소는 http:// 또는 https://로 입력하세요.")
    @Size(max = 1000)
    private String logoUrl;

    @Pattern(regexp = "^$|https?://.+", message = "홈페이지 주소는 http:// 또는 https://로 입력하세요.")
    @Size(max = 1000)
    private String websiteUrl;

    private Set<OrganizationRelationshipType> relationshipTypes = new LinkedHashSet<>();
    private Set<Long> projectCourseIds = new LinkedHashSet<>();
    private Set<Long> recruitmentCourseIds = new LinkedHashSet<>();
    private boolean homepageExposure;
    private Set<OrganizationExposureSite> exposureSites = new LinkedHashSet<>();
    private Set<OrganizationExposurePosition> exposurePositions = new LinkedHashSet<>();

    @PositiveOrZero
    private int displayOrder;

    private String internalNote;

    @NotNull(message = "상태를 선택하세요.")
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    public static OrganizationForm from(PartnerOrganization organization,
                                        java.util.List<CoursePartner> coursePartners) {
        OrganizationForm form = new OrganizationForm();
        form.name = organization.getName();
        form.type = organization.getType();
        form.oneLineDescription = organization.getOneLineDescription();
        form.detailedDescription = organization.getDetailedDescription();
        form.logoUrl = organization.getLogoUrl();
        form.websiteUrl = organization.getWebsiteUrl();
        form.relationshipTypes = new LinkedHashSet<>(organization.getRelationshipTypes());
        form.homepageExposure = organization.isHomepageExposure();
        form.exposureSites = new LinkedHashSet<>(organization.getExposureSites());
        form.exposurePositions = new LinkedHashSet<>(organization.getExposurePositions());
        form.displayOrder = organization.getDisplayOrder();
        form.internalNote = organization.getInternalNote();
        form.status = organization.getStatus();
        form.projectCourseIds = coursePartners.stream()
                .filter(CoursePartner::isProjectParticipant)
                .map(link -> link.getCourse().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        form.recruitmentCourseIds = coursePartners.stream()
                .filter(CoursePartner::isRecruitmentLinked)
                .map(link -> link.getCourse().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return form;
    }
}
