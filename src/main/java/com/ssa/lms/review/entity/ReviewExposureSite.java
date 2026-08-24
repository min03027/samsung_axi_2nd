package com.ssa.lms.review.entity;

public enum ReviewExposureSite {
    MAIN("통합 홈"),
    CAMPUS("AI 취업캠퍼스"),
    CLASS("몰입클라쓰");

    private final String label;

    ReviewExposureSite(String label) { this.label = label; }
    public String getLabel() { return label; }
}
