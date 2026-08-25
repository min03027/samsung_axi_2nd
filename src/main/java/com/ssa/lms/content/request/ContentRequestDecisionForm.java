package com.ssa.lms.content.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ContentRequestDecisionForm {
    @NotNull(message = "공용 원본을 선택하세요.")
    private Long libraryItemId;
    private Long sessionId;
    private Integer orderNo = 1;
    private Boolean required = Boolean.TRUE;
    private Boolean autoSync = Boolean.TRUE;
    @Size(max = 2000)
    private String note;
}
