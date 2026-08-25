package com.ssa.lms.course.entity;

/** 과정 공개 대상 사이트. v1은 몰입클라쓰 목록을 먼저 연결한다. */
public enum PublicationSite {
    CLASS("몰입클라쓰"),
    CAMPUS("AI 취업캠퍼스"),
    ALL("전체 공개");

    private final String label;

    PublicationSite(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
