package com.ssa.lms.organization.entity;

public enum OrganizationExposureSite {
    MAIN("통합 홈"),
    CAMPUS("AI 취업캠퍼스"),
    CLASS("몰입클라쓰"),
    BIZ("기업교육");

    private final String label;

    OrganizationExposureSite(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
