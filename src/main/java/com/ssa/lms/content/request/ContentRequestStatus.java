package com.ssa.lms.content.request;

public enum ContentRequestStatus {
    RECEIVED("접수"),
    REVIEWING("검토 중"),
    FULFILLED("제공 완료"),
    REJECTED("반려");

    private final String label;

    ContentRequestStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
