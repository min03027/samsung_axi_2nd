package com.ssa.lms.admission.entity;

public enum ConsultationStatus {
    RECEIVED("담당자 미배정"),
    ASSIGNED("담당자 배정"),
    SCHEDULED("상담 예정"),
    IN_PROGRESS("상담 진행"),
    COMPLETED("상담 완료"),
    FOLLOW_UP("후속조치 필요"),
    CLOSED("종료");

    private final String label;

    ConsultationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
