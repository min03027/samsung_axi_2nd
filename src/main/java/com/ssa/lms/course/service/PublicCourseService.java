package com.ssa.lms.course.service;

import com.ssa.lms.course.entity.PublicationSite;
import com.ssa.lms.course.entity.RecruitmentStatus;
import com.ssa.lms.course.repository.CoursePublicationRepository;
import com.ssa.lms.course.web.PublicCourseView;
import com.ssa.lms.organization.entity.CoursePartner;
import com.ssa.lms.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 모집중이면서 공개 승인을 받은 과정만 홈페이지 DTO로 변환한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicCourseService {

    private final CoursePublicationRepository coursePublicationRepository;
    private final CourseInstructorService courseInstructorService;
    private final CurriculumService curriculumService;
    private final OrganizationService organizationService;

    public List<PublicCourseView> publishedCourses(PublicationSite site) {
        return coursePublicationRepository.findPublished(
                        RecruitmentStatus.RECRUITING, site, PublicationSite.ALL).stream()
                .map(publication -> PublicCourseView.of(publication,
                        courseInstructorService.instructorsOf(publication.getCourse().getId()),
                        curriculumService.curriculum(publication.getCourse().getId()),
                        structuredProjectPartners(publication.getCourse().getId(), publication.getProjectPartners())))
                .toList();
    }

    public Optional<PublicCourseView> publishedCourse(Long courseId, PublicationSite site) {
        return coursePublicationRepository.findPublishedByCourseId(
                        courseId, RecruitmentStatus.RECRUITING, site, PublicationSite.ALL)
                .map(publication -> PublicCourseView.of(publication,
                        courseInstructorService.instructorsOf(courseId),
                        curriculumService.curriculum(courseId),
                        structuredProjectPartners(courseId, publication.getProjectPartners())));
    }

    private String structuredProjectPartners(Long courseId, String legacyValue) {
        String linked = organizationService.coursePartners(courseId).stream()
                .filter(CoursePartner::isProjectParticipant)
                .map(link -> link.getOrganization().getName())
                .distinct()
                .collect(java.util.stream.Collectors.joining(" · "));
        return linked.isBlank() ? legacyValue : linked;
    }
}
