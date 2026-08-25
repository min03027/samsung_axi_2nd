package com.ssa.lms.review.entity;

public enum ReviewExposurePosition {
    HOMEPAGE_FEATURED("통합 홈 대표 후기"),
    CAMPUS_REVIEWS("취업캠퍼스 후기"),
    COURSE_DETAIL("과정 상세 후기"),
    CAREER_STORIES("취업 성공사례");

    private final String label;

    ReviewExposurePosition(String label) { this.label = label; }
    public String getLabel() { return label; }
}
