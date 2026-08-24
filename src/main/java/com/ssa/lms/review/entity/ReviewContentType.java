package com.ssa.lms.review.entity;

public enum ReviewContentType {
    TEXT("텍스트 후기"),
    INTERVIEW("인터뷰"),
    VIDEO("영상 후기"),
    EMPLOYMENT_SUCCESS("취업 성공사례");

    private final String label;

    ReviewContentType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
