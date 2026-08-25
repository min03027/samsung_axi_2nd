package com.ssa.lms.organization.entity;

public enum OrganizationExposurePosition {
    PARTNER_ROLLING("협력기관 롤링"),
    CLIENT_ROLLING("고객사 롤링"),
    COURSE_PROJECT("과정 프로젝트"),
    PROJECT_SHOWCASE("발표회·프로젝트"),
    CAREER_OUTCOME("취업 성과");

    private final String label;

    OrganizationExposurePosition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
