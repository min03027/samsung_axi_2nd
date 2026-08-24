package com.ssa.lms.review.service;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.organization.entity.PartnerOrganization;
import com.ssa.lms.organization.repository.PartnerOrganizationRepository;
import com.ssa.lms.review.entity.*;
import com.ssa.lms.review.repository.StudentReviewRepository;
import com.ssa.lms.review.web.PublicReviewView;
import com.ssa.lms.review.web.ReviewForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final StudentReviewRepository reviewRepository;
    private final CourseRepository courseRepository;
    private final PartnerOrganizationRepository organizationRepository;

    public List<StudentReview> findAll(String query, Long courseId, ReviewContentType contentType,
                                       ReviewStatus status, Boolean publicVisible) {
        String keyword = normalize(query);
        return reviewRepository.findAllByOrderByFeaturedDescDisplayOrderAscIdDesc().stream()
                .filter(review -> keyword.isBlank()
                        || contains(review.getTitle(), keyword)
                        || contains(review.getContent(), keyword)
                        || contains(review.getAuthorDisplayName(), keyword)
                        || contains(review.getJobTitle(), keyword)
                        || review.getEmploymentOrganization() != null
                        && contains(review.getEmploymentOrganization().getName(), keyword))
                .filter(review -> courseId == null || review.getCourse() != null
                        && review.getCourse().getId().equals(courseId))
                .filter(review -> contentType == null || review.getContentType() == contentType)
                .filter(review -> status == null || review.getStatus() == status)
                .filter(review -> publicVisible == null || review.isPublicVisible() == publicVisible)
                .toList();
    }

    public StudentReview get(Long id) {
        return reviewRepository.findOneById(id).orElseThrow(() -> new ReviewNotFoundException(id));
    }

    public ReviewForm formForEdit(Long id) { return ReviewForm.from(get(id)); }

    @Transactional
    public Long create(ReviewForm form) {
        StudentReview saved = reviewRepository.save(StudentReview.builder()
                .title(form.getTitle()).content(form.getContent())
                .authorDisplayName(form.getAuthorDisplayName()).authorDisplayType(form.getAuthorDisplayType())
                .contentType(form.getContentType()).course(course(form.getCourseId()))
                .cohortSnapshot(form.getCohortSnapshot()).completionYear(form.getCompletionYear())
                .employmentOrganization(organization(form.getEmploymentOrganizationId()))
                .jobTitle(form.getJobTitle()).employed(form.isEmployed())
                .preTrainingSituation(form.getPreTrainingSituation()).courseExperience(form.getCourseExperience())
                .projectExperience(form.getProjectExperience()).employmentJourney(form.getEmploymentJourney())
                .currentRoleDetail(form.getCurrentRoleDetail()).imageUrl(form.getImageUrl()).videoUrl(form.getVideoUrl())
                .publicVisible(form.isPublicVisible()).exposureSites(form.getExposureSites())
                .exposurePositions(form.getExposurePositions()).displayOrder(form.getDisplayOrder())
                .featured(form.isFeatured()).homepagePublicationConsent(form.isHomepagePublicationConsent())
                .imagePublicationConsent(form.isImagePublicationConsent())
                .employmentPublicationConsent(form.isEmploymentPublicationConsent())
                .videoPublicationConsent(form.isVideoPublicationConsent())
                .publicationConsentRecordedAt(form.isHomepagePublicationConsent() ? LocalDateTime.now() : null)
                .internalNote(form.getInternalNote()).status(form.getStatus()).build());
        return saved.getId();
    }

    @Transactional
    public void update(Long id, ReviewForm form) {
        StudentReview review = get(id);
        LocalDateTime consentAt = review.getPublicationConsentRecordedAt();
        if (form.isHomepagePublicationConsent() && consentAt == null) consentAt = LocalDateTime.now();
        review.update(form.getTitle(), form.getContent(), form.getAuthorDisplayName(),
                form.getAuthorDisplayType(), form.getContentType(), course(form.getCourseId()),
                form.getCohortSnapshot(), form.getCompletionYear(), organization(form.getEmploymentOrganizationId()),
                form.getJobTitle(), form.isEmployed(), form.getPreTrainingSituation(), form.getCourseExperience(),
                form.getProjectExperience(), form.getEmploymentJourney(), form.getCurrentRoleDetail(),
                form.getImageUrl(), form.getVideoUrl(), form.isPublicVisible(), form.getExposureSites(),
                form.getExposurePositions(), form.getDisplayOrder(), form.isFeatured(),
                form.isHomepagePublicationConsent(), form.isImagePublicationConsent(),
                form.isEmploymentPublicationConsent(), form.isVideoPublicationConsent(), consentAt,
                form.getInternalNote(), form.getStatus());
    }

    public List<PublicReviewView> publicReviews(ReviewExposureSite site,
                                                ReviewExposurePosition position,
                                                Long courseId, Long organizationId,
                                                String jobTitle, Integer completionYear,
                                                ReviewContentType contentType, Boolean featured) {
        String jobKeyword = normalize(jobTitle);
        return reviewRepository
                .findByStatusAndPublicVisibleTrueAndHomepagePublicationConsentTrueOrderByFeaturedDescDisplayOrderAscIdDesc(
                        ReviewStatus.ACTIVE).stream()
                .filter(review -> site == null || review.getExposureSites().contains(site))
                .filter(review -> position == null || review.getExposurePositions().contains(position))
                .filter(review -> courseId == null || review.getCourse() != null
                        && review.getCourse().getId().equals(courseId))
                .filter(review -> organizationId == null || review.isEmploymentPublicationConsent()
                        && review.getEmploymentOrganization() != null
                        && review.getEmploymentOrganization().getId().equals(organizationId))
                .filter(review -> jobKeyword.isBlank() || review.isEmploymentPublicationConsent()
                        && contains(review.getJobTitle(), jobKeyword))
                .filter(review -> completionYear == null || completionYear.equals(review.getCompletionYear()))
                .filter(review -> contentType == null || review.getContentType() == contentType)
                .filter(review -> featured == null || review.isFeatured() == featured)
                .map(PublicReviewView::of)
                .toList();
    }

    public Optional<PublicReviewView> publicReview(Long id) {
        return reviewRepository.findOneById(id)
                .filter(review -> review.getStatus() == ReviewStatus.ACTIVE)
                .filter(StudentReview::isPublicVisible)
                .filter(StudentReview::isHomepagePublicationConsent)
                .map(PublicReviewView::of);
    }

    private Course course(Long id) {
        if (id == null) return null;
        return courseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("과정을 찾을 수 없습니다. id=" + id));
    }

    private PartnerOrganization organization(Long id) {
        if (id == null) return null;
        return organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("기업·기관을 찾을 수 없습니다. id=" + id));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private boolean contains(String value, String keyword) {
        return value != null && normalize(value).contains(keyword);
    }
}
