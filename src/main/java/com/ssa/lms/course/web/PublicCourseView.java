package com.ssa.lms.course.web;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.CoursePublication;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 홈페이지에 공개해도 되는 과정 정보만 모은 응답 DTO. 내부 관리 필드는 포함하지 않는다. */
public record PublicCourseView(
        Long id,
        String courseName,
        String cohort,
        String category,
        String categoryKey,
        String categoryLabel,
        String oneLineIntroduction,
        String audience,
        String prerequisites,
        String recruitmentStatus,
        String recruitmentStatusLabel,
        LocalDate recruitmentStartDate,
        LocalDate applicationDeadline,
        Long dDay,
        LocalDate consultationDate,
        LocalDate resultAnnouncementDate,
        String selectionProcess,
        String requiredDocuments,
        LocalDate educationStartDate,
        LocalDate educationEndDate,
        String educationTime,
        String educationMethod,
        int capacity,
        Long tuitionFee,
        Long selfPayment,
        Long governmentSupport,
        Long additionalCost,
        List<String> instructors,
        String mentors,
        String projectPartners,
        String demoUrl,
        boolean featured,
        List<SubjectView> curriculum,
        String applicationUrl,
        String consultationUrl
) {
    public static PublicCourseView of(CoursePublication publication,
                                      List<InstructorView> instructors,
                                      List<SubjectView> curriculum,
                                      String projectPartners) {
        Course course = publication.getCourse();
        LocalDate deadline = publication.getApplicationDeadline();
        Long dDay = deadline == null ? null : Math.max(0L, ChronoUnit.DAYS.between(LocalDate.now(), deadline));
        String categoryKey = publication.getPublicCategory() == null
                ? "short" : publication.getPublicCategory().getKey();
        String categoryLabel = publication.getPublicCategory() == null
                ? "과정" : publication.getPublicCategory().getLabel();
        return new PublicCourseView(
                course.getId(), course.getCourseName(), course.getCohort(),
                course.getCategory(), categoryKey, categoryLabel,
                publication.getOneLineIntroduction(), publication.getAudience(), publication.getPrerequisites(),
                publication.getRecruitmentStatus().name(), publication.getRecruitmentStatus().getLabel(),
                publication.getRecruitmentStartDate(), deadline, dDay,
                publication.getConsultationDate(), publication.getResultAnnouncementDate(),
                publication.getSelectionProcess(), publication.getRequiredDocuments(),
                course.getStartDate(), course.getEndDate(), publication.getEducationTime(),
                publication.getEducationMethod(), course.getCapacity(), publication.getTuitionFee(),
                publication.getSelfPayment(), publication.getGovernmentSupport(), publication.getAdditionalCost(),
                instructors.stream().map(InstructorView::name).toList(), publication.getMentors(),
                projectPartners, publication.getDemoUrl(), publication.isFeatured(),
                curriculum, "/v2/site/class/apply.html?courseId=" + course.getId(),
                "/v2/site/campus/counsel.html?courseId=" + course.getId());
    }
}
