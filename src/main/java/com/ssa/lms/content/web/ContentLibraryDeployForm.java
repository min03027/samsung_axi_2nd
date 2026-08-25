package com.ssa.lms.content.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 공용 콘텐츠를 과정·차시에 배치하는 폼. */
@Getter
@Setter
public class ContentLibraryDeployForm {

    @NotNull(message = "배치할 과정을 선택하세요.")
    private Long courseId;

    private Long sessionId;

    @Min(value = 1, message = "정렬 순서는 1 이상이어야 합니다.")
    private Integer orderNo = 1;

    private Boolean required = Boolean.TRUE;

    private Boolean autoSync = Boolean.TRUE;
}
