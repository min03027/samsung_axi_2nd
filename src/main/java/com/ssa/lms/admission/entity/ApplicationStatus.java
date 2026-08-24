package com.ssa.lms.admission.entity;

public enum ApplicationStatus {
    RECEIVED("접수"),
    REVIEWING("검토중"),
    CONSULTATION_REQUIRED("상담 필요"),
    APPROVED("승인"),
    ON_HOLD("보류"),
    REJECTED("거절"),
    REGISTERED("등록 완료");

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
