package com.ssa.lms.content.web;

import com.ssa.lms.content.entity.ContentLibraryItem;
import com.ssa.lms.content.entity.ContentLibraryStatus;
import com.ssa.lms.content.entity.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 공용 콘텐츠 원본 등록 및 새 버전 발행 폼. */
@Getter
@Setter
public class ContentLibraryForm {

    @NotNull(message = "콘텐츠 유형을 선택하세요.")
    private ContentType type;

    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 200, message = "제목은 200자 이내여야 합니다.")
    private String title;

    private String description;

    private Integer durationSeconds;

    private Integer pageCount;

    @Size(max = 500, message = "산업·직무 태그는 500자 이내여야 합니다.")
    private String industryTags;

    @NotNull(message = "운영 상태를 선택하세요.")
    private ContentLibraryStatus status = ContentLibraryStatus.PUBLISHED;

    @Size(max = 1000, message = "변경 내용은 1,000자 이내여야 합니다.")
    private String changeSummary;

    public static ContentLibraryForm from(ContentLibraryItem item) {
        ContentLibraryForm form = new ContentLibraryForm();
        form.type = item.getType();
        form.title = item.getTitle();
        form.description = item.getDescription();
        form.durationSeconds = item.getDurationSeconds();
        form.pageCount = item.getPageCount();
        form.industryTags = item.getIndustryTags();
        form.status = item.getStatus();
        return form;
    }
}
