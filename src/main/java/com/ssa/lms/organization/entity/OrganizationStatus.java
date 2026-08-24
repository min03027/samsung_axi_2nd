package com.ssa.lms.organization.entity;

public enum OrganizationStatus {
    ACTIVE("사용"),
    INACTIVE("사용 중지"),
    ARCHIVED("보관");

    private final String label;

    OrganizationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
