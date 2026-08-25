package com.ssa.lms.course.entity;

/** 홈페이지 모집 회차의 공개 상태. 내부 교육 운영 상태({@link CourseStatus})와 구분한다. */
public enum RecruitmentStatus {
    PRE_CONSULTATION("사전 상담"),
    RECRUITING("모집중"),
    CLOSED("모집마감"),
    IN_PROGRESS("진행중"),
    COMPLETED("종료");

    private final String label;

    RecruitmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public RecruitmentStatus next() {
        return switch (this) {
            case PRE_CONSULTATION -> RECRUITING;
            case RECRUITING -> CLOSED;
            case CLOSED -> IN_PROGRESS;
            case IN_PROGRESS -> COMPLETED;
            case COMPLETED -> null;
        };
    }
}
