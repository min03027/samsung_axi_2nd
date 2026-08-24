package com.ssa.lms.review.entity;

public enum ReviewAuthorDisplayType {
    PUBLIC_NAME("입력한 이름 공개"),
    MASKED("가운데 글자 마스킹"),
    ANONYMOUS("익명");

    private final String label;

    ReviewAuthorDisplayType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
