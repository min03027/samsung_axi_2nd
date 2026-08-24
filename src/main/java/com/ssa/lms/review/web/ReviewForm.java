package com.ssa.lms.review.web;

import com.ssa.lms.review.entity.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter @Setter
public class ReviewForm {
    @NotBlank(message = "후기 제목을 입력하세요.") @Size(max = 300)
    private String title;
    @NotBlank(message = "후기 내용을 입력하세요.")
    private String content;
    @NotBlank(message = "작성자 표시명을 입력하세요.") @Size(max = 100)
    private String authorDisplayName;
    @NotNull private ReviewAuthorDisplayType authorDisplayType = ReviewAuthorDisplayType.MASKED;
    @NotNull private ReviewContentType contentType = ReviewContentType.TEXT;
    private Long courseId;
    @Size(max = 30) private String cohortSnapshot;
    @Min(1900) @Max(2100) private Integer completionYear;
    private Long employmentOrganizationId;
    @Size(max = 200) private String jobTitle;
    private boolean employed;
    private String preTrainingSituation;
    private String courseExperience;
    private String projectExperience;
    private String employmentJourney;
    private String currentRoleDetail;
    @Pattern(regexp = "^$|https?://.+", message = "이미지 주소는 http:// 또는 https://로 입력하세요.") @Size(max = 1000)
    private String imageUrl;
    @Pattern(regexp = "^$|https?://.+", message = "영상 주소는 http:// 또는 https://로 입력하세요.") @Size(max = 1000)
    private String videoUrl;
    private boolean publicVisible;
    private Set<ReviewExposureSite> exposureSites = new LinkedHashSet<>();
    private Set<ReviewExposurePosition> exposurePositions = new LinkedHashSet<>();
    @PositiveOrZero private int displayOrder;
    private boolean featured;
    private boolean homepagePublicationConsent;
    private boolean imagePublicationConsent;
    private boolean employmentPublicationConsent;
    private boolean videoPublicationConsent;
    private String internalNote;
    @NotNull private ReviewStatus status = ReviewStatus.ACTIVE;

    public static ReviewForm from(StudentReview review) {
        ReviewForm form = new ReviewForm();
        form.title = review.getTitle(); form.content = review.getContent();
        form.authorDisplayName = review.getAuthorDisplayName(); form.authorDisplayType = review.getAuthorDisplayType();
        form.contentType = review.getContentType(); form.courseId = review.getCourse() == null ? null : review.getCourse().getId();
        form.cohortSnapshot = review.getCohortSnapshot(); form.completionYear = review.getCompletionYear();
        form.employmentOrganizationId = review.getEmploymentOrganization() == null ? null : review.getEmploymentOrganization().getId();
        form.jobTitle = review.getJobTitle(); form.employed = review.isEmployed();
        form.preTrainingSituation = review.getPreTrainingSituation(); form.courseExperience = review.getCourseExperience();
        form.projectExperience = review.getProjectExperience(); form.employmentJourney = review.getEmploymentJourney();
        form.currentRoleDetail = review.getCurrentRoleDetail(); form.imageUrl = review.getImageUrl(); form.videoUrl = review.getVideoUrl();
        form.publicVisible = review.isPublicVisible(); form.exposureSites = new LinkedHashSet<>(review.getExposureSites());
        form.exposurePositions = new LinkedHashSet<>(review.getExposurePositions()); form.displayOrder = review.getDisplayOrder();
        form.featured = review.isFeatured(); form.homepagePublicationConsent = review.isHomepagePublicationConsent();
        form.imagePublicationConsent = review.isImagePublicationConsent();
        form.employmentPublicationConsent = review.isEmploymentPublicationConsent();
        form.videoPublicationConsent = review.isVideoPublicationConsent();
        form.internalNote = review.getInternalNote(); form.status = review.getStatus();
        return form;
    }
}
