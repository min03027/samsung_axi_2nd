package com.ssa.lms.organization.web;

import com.ssa.lms.organization.entity.PartnerOrganization;

import java.util.List;

/** 내부 메모와 과정 운영 정보가 포함되지 않는 홈페이지 공개 DTO. */
public record PublicOrganizationView(
        Long id,
        String name,
        String type,
        String typeLabel,
        String oneLineDescription,
        String logoUrl,
        String websiteUrl,
        List<String> relationshipTypes,
        int displayOrder
) {
    public static PublicOrganizationView of(PartnerOrganization organization) {
        return new PublicOrganizationView(
                organization.getId(), organization.getName(), organization.getType().name(),
                organization.getType().getLabel(), organization.getOneLineDescription(),
                organization.getLogoUrl(), organization.getWebsiteUrl(),
                organization.getRelationshipTypes().stream()
                        .map(type -> type.getLabel()).sorted().toList(),
                organization.getDisplayOrder());
    }
}
