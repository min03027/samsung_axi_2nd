package com.ssa.lms.content.request;

import com.ssa.lms.content.entity.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ContentRequestForm {
    @NotNull(message = "과정을 선택하세요.")
    private Long courseId;
    private ContentType preferredType;
    @NotBlank(message = "필요한 콘텐츠 주제를 입력하세요.")
    @Size(max = 200)
    private String title;
    @NotBlank(message = "요청 사유를 입력하세요.")
    @Size(max = 3000)
    private String reason;
}
