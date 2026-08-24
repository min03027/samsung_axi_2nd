package com.ssa.lms.review.web;

import com.ssa.lms.review.entity.ReviewAuthorDisplayType;
import com.ssa.lms.review.entity.StudentReview;

public record PublicReviewView(
        Long id, String title, String content, String authorDisplayName,
        String contentType, String contentTypeLabel,
        Long courseId, String courseName, String cohort, Integer completionYear,
        Long organizationId, String employmentCompany, String jobTitle, boolean employed,
        String preTrainingSituation, String courseExperience, String projectExperience,
        String employmentJourney, String currentRoleDetail,
        String imageUrl, String videoUrl, boolean featured, int displayOrder,
        String detailUrl, String courseUrl
) {
    public static PublicReviewView of(StudentReview review) {
        boolean employmentAllowed = review.isEmploymentPublicationConsent();
        boolean imageAllowed = review.isImagePublicationConsent();
        boolean videoAllowed = review.isVideoPublicationConsent();
        Long courseId = review.getCourse() == null ? null : review.getCourse().getId();
        String cohort = review.getCohortSnapshot() != null ? review.getCohortSnapshot()
                : review.getCourse() == null ? null : review.getCourse().getCohort();
        return new PublicReviewView(
                review.getId(), review.getTitle(), review.getContent(), displayName(review),
                review.getContentType().name(), review.getContentType().getLabel(),
                courseId, review.getCourse() == null ? null : review.getCourse().getCourseName(),
                cohort, review.getCompletionYear(),
                employmentAllowed && review.getEmploymentOrganization() != null
                        ? review.getEmploymentOrganization().getId() : null,
                employmentAllowed && review.getEmploymentOrganization() != null
                        ? review.getEmploymentOrganization().getName() : null,
                employmentAllowed ? review.getJobTitle() : null,
                employmentAllowed && review.isEmployed(), review.getPreTrainingSituation(), review.getCourseExperience(),
                review.getProjectExperience(), employmentAllowed ? review.getEmploymentJourney() : null,
                employmentAllowed ? review.getCurrentRoleDetail() : null,
                imageAllowed ? review.getImageUrl() : null,
                videoAllowed ? review.getVideoUrl() : null,
                review.isFeatured(), review.getDisplayOrder(),
                "/v2/site/campus/review-detail.html?id=" + review.getId(),
                courseId == null ? null : "/v2/site/class/course.html?courseId=" + courseId);
    }

    private static String displayName(StudentReview review) {
        if (review.getAuthorDisplayType() == ReviewAuthorDisplayType.ANONYMOUS) return "익명";
        String name = review.getAuthorDisplayName();
        if (review.getAuthorDisplayType() == ReviewAuthorDisplayType.PUBLIC_NAME || name.length() < 2) return name;
        if (name.length() == 2) return name.substring(0, 1) + "○";
        return name.substring(0, 1) + "○".repeat(name.length() - 2) + name.substring(name.length() - 1);
    }
}
