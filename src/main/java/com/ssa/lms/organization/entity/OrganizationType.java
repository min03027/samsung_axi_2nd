package com.ssa.lms.organization.entity;

public enum OrganizationType {
    COMPANY("기업"),
    PUBLIC_INSTITUTION("공공기관"),
    EDUCATIONAL_INSTITUTION("교육기관"),
    ASSOCIATION("협회·단체"),
    PARTNER("파트너"),
    OTHER("기타");

    private final String label;

    OrganizationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
