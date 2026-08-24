package com.ssa.lms.review.entity;

public enum ReviewStatus {
    ACTIVE("사용"),
    INACTIVE("사용 중지"),
    ARCHIVED("보관");

    private final String label;

    ReviewStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
