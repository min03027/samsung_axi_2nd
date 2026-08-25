package com.ssa.lms.course.service;

import java.util.List;

/** 사전 상담 과정을 모집중으로 전환하기 전에 필요한 공개 정보가 부족할 때 발생한다. */
public class RecruitmentReadinessException extends IllegalStateException {

    private final List<String> missingItems;

    public RecruitmentReadinessException(List<String> missingItems) {
        super("모집중 전환에 필요한 정보가 부족합니다: " + String.join(", ", missingItems));
        this.missingItems = List.copyOf(missingItems);
    }

    public List<String> getMissingItems() {
        return missingItems;
    }
}
