package com.ssa.lms.organization.entity;

public enum OrganizationRelationshipType {
    AGREEMENT("협약"),
    LICENSE("라이선스"),
    EDUCATION("교육"),
    RECRUITMENT("채용"),
    PRESENTATION("발표회"),
    RESIDENCY("입주"),
    PUBLIC_INSTITUTION("공공기관");

    private final String label;

    OrganizationRelationshipType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
