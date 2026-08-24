package com.ssa.lms.review.entity;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.organization.entity.PartnerOrganization;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** 내부 교육평가와 분리된 공개 홈페이지용 수강생·수료생 후기. */
@Entity
@Table(name = "student_review", indexes = {
        @Index(name = "ix_student_review_public", columnList = "status, public_visible, display_order"),
        @Index(name = "ix_student_review_course", columnList = "course_id"),
        @Index(name = "ix_student_review_organization", columnList = "employment_organization_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentReview extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "author_display_name", nullable = false, length = 100)
    private String authorDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_display_type", nullable = false, length = 30)
    private ReviewAuthorDisplayType authorDisplayType;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 30)
    private ReviewContentType contentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "cohort_snapshot", length = 30)
    private String cohortSnapshot;

    @Column(name = "completion_year")
    private Integer completionYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employment_organization_id")
    private PartnerOrganization employmentOrganization;

    @Column(name = "job_title", length = 200)
    private String jobTitle;

    @Column(name = "employed", nullable = false)
    private boolean employed;

    @Column(name = "pre_training_situation", columnDefinition = "TEXT")
    private String preTrainingSituation;

    @Column(name = "course_experience", columnDefinition = "TEXT")
    private String courseExperience;

    @Column(name = "project_experience", columnDefinition = "TEXT")
    private String projectExperience;

    @Column(name = "employment_journey", columnDefinition = "TEXT")
    private String employmentJourney;

    @Column(name = "current_role_detail", columnDefinition = "TEXT")
    private String currentRoleDetail;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "video_url", length = 1000)
    private String videoUrl;

    @Column(name = "public_visible", nullable = false)
    private boolean publicVisible;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "review_exposure_site", joinColumns = @JoinColumn(name = "review_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "exposure_site", length = 30)
    private Set<ReviewExposureSite> exposureSites = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "review_exposure_position", joinColumns = @JoinColumn(name = "review_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "exposure_position", length = 40)
    private Set<ReviewExposurePosition> exposurePositions = new LinkedHashSet<>();

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "homepage_publication_consent", nullable = false)
    private boolean homepagePublicationConsent;

    @Column(name = "image_publication_consent", nullable = false)
    private boolean imagePublicationConsent;

    @Column(name = "employment_publication_consent", nullable = false)
    private boolean employmentPublicationConsent;

    @Column(name = "video_publication_consent", nullable = false)
    private boolean videoPublicationConsent;

    @Column(name = "publication_consent_recorded_at")
    private LocalDateTime publicationConsentRecordedAt;

    @Column(name = "internal_note", columnDefinition = "TEXT")
    private String internalNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;

    @Builder
    private StudentReview(String title, String content, String authorDisplayName,
                          ReviewAuthorDisplayType authorDisplayType, ReviewContentType contentType,
                          Course course, String cohortSnapshot, Integer completionYear,
                          PartnerOrganization employmentOrganization, String jobTitle, boolean employed,
                          String preTrainingSituation, String courseExperience, String projectExperience,
                          String employmentJourney, String currentRoleDetail,
                          String imageUrl, String videoUrl, boolean publicVisible,
                          Set<ReviewExposureSite> exposureSites,
                          Set<ReviewExposurePosition> exposurePositions, int displayOrder, boolean featured,
                          boolean homepagePublicationConsent, boolean imagePublicationConsent,
                          boolean employmentPublicationConsent, boolean videoPublicationConsent,
                          LocalDateTime publicationConsentRecordedAt, String internalNote, ReviewStatus status) {
        update(title, content, authorDisplayName, authorDisplayType, contentType,
                course, cohortSnapshot, completionYear, employmentOrganization, jobTitle, employed,
                preTrainingSituation, courseExperience, projectExperience, employmentJourney,
                currentRoleDetail, imageUrl, videoUrl, publicVisible, exposureSites, exposurePositions,
                displayOrder, featured, homepagePublicationConsent, imagePublicationConsent,
                employmentPublicationConsent, videoPublicationConsent, publicationConsentRecordedAt,
                internalNote, status);
    }

    public void update(String title, String content, String authorDisplayName,
                       ReviewAuthorDisplayType authorDisplayType, ReviewContentType contentType,
                       Course course, String cohortSnapshot, Integer completionYear,
                       PartnerOrganization employmentOrganization, String jobTitle, boolean employed,
                       String preTrainingSituation, String courseExperience, String projectExperience,
                       String employmentJourney, String currentRoleDetail,
                       String imageUrl, String videoUrl, boolean publicVisible,
                       Set<ReviewExposureSite> exposureSites,
                       Set<ReviewExposurePosition> exposurePositions, int displayOrder, boolean featured,
                       boolean homepagePublicationConsent, boolean imagePublicationConsent,
                       boolean employmentPublicationConsent, boolean videoPublicationConsent,
                       LocalDateTime publicationConsentRecordedAt, String internalNote, ReviewStatus status) {
        this.title = title.trim();
        this.content = content.trim();
        this.authorDisplayName = authorDisplayName.trim();
        this.authorDisplayType = authorDisplayType;
        this.contentType = contentType;
        this.course = course;
        this.cohortSnapshot = blankToNull(cohortSnapshot);
        this.completionYear = completionYear;
        this.employmentOrganization = employmentOrganization;
        this.jobTitle = blankToNull(jobTitle);
        this.employed = employed;
        this.preTrainingSituation = blankToNull(preTrainingSituation);
        this.courseExperience = blankToNull(courseExperience);
        this.projectExperience = blankToNull(projectExperience);
        this.employmentJourney = blankToNull(employmentJourney);
        this.currentRoleDetail = blankToNull(currentRoleDetail);
        this.imageUrl = blankToNull(imageUrl);
        this.videoUrl = blankToNull(videoUrl);
        this.publicVisible = publicVisible;
        this.exposureSites.clear();
        if (exposureSites != null) this.exposureSites.addAll(exposureSites);
        this.exposurePositions.clear();
        if (exposurePositions != null) this.exposurePositions.addAll(exposurePositions);
        this.displayOrder = Math.max(displayOrder, 0);
        this.featured = featured;
        this.homepagePublicationConsent = homepagePublicationConsent;
        this.imagePublicationConsent = imagePublicationConsent;
        this.employmentPublicationConsent = employmentPublicationConsent;
        this.videoPublicationConsent = videoPublicationConsent;
        this.publicationConsentRecordedAt = publicationConsentRecordedAt;
        this.internalNote = blankToNull(internalNote);
        this.status = status == null ? ReviewStatus.ACTIVE : status;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
