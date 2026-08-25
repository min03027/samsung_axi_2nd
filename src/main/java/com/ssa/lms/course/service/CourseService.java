package com.ssa.lms.course.service;

import com.ssa.lms.course.entity.*;
import com.ssa.lms.course.repository.CourseInstructorRepository;
import com.ssa.lms.course.repository.CoursePublicationRepository;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.SubjectRepository;
import com.ssa.lms.course.web.CourseForm;
import com.ssa.lms.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 과정(Course) CRUD. 과목/차시 구성은 {@link CurriculumService},
 * 강사 배정은 {@link CourseInstructorService}, 수강신청은 {@code EnrollmentService} 가 담당한다.
 *
 * <p>과정 코드(courseCode)는 등록 시에만 지정하며 이후 변경하지 않는다 (B 계약 컬럼).
 * 삭제는 soft delete (@SQLDelete) — 3년 보존 요건.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final SubjectRepository subjectRepository;
    private final CoursePublicationRepository coursePublicationRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final OrganizationService organizationService;

    /** 시작일 최신순 전체 과정 (삭제된 것 제외 — @SQLRestriction). */
    public List<Course> findAll() {
        return courseRepository.findAllByOrderByStartDateDesc();
    }

    /**
     * 과정별 과목 개수 맵 (courseId → count).
     * OSIV 비활성 환경에서 목록 뷰가 lazy 컬렉션을 건드리지 않도록 서비스에서 미리 집계한다.
     */
    public Map<Long, Long> subjectCounts(List<Course> courses) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Course c : courses) {
            counts.put(c.getId(), subjectRepository.countByCourseId(c.getId()));
        }
        return counts;
    }

    public Course get(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException(id));
    }

    @Transactional
    public Long create(CourseForm form) {
        if (courseRepository.existsByCourseCode(form.getCourseCode())) {
            throw new DuplicateCourseCodeException(form.getCourseCode());
        }
        Course saved = courseRepository.save(form.toNewCourse());
        coursePublicationRepository.save(form.toNewPublication(saved));
        organizationService.syncCourseProjectPartners(saved, form.getProjectPartnerOrganizationIds());
        return saved.getId();
    }

    @Transactional
    public void update(Long id, CourseForm form) {
        Course course = get(id);
        course.update(form.getCourseName(), form.getCohort(), form.getCategory(),
                form.getDescription(), form.getStartDate(), form.getEndDate(),
                form.getCapacity(), form.getCompletionProgressRate());
        CoursePublication publication = coursePublicationRepository.findByCourseId(id)
                .orElseGet(() -> form.toNewPublication(course));
        form.applyTo(publication);
        coursePublicationRepository.save(publication);
        organizationService.syncCourseProjectPartners(course, form.getProjectPartnerOrganizationIds());
    }

    @Transactional
    public void changeStatus(Long id, CourseStatus status) {
        get(id).changeStatus(status);
    }

    public Optional<CoursePublication> publicationOf(Long courseId) {
        return coursePublicationRepository.findByCourseId(courseId);
    }

    public CourseForm formForEdit(Long courseId) {
        Course course = get(courseId);
        CourseForm form = CourseForm.from(course, coursePublicationRepository.findByCourseId(courseId).orElse(null));
        form.setProjectPartnerOrganizationIds(organizationService.coursePartners(courseId).stream()
                .filter(com.ssa.lms.organization.entity.CoursePartner::isProjectParticipant)
                .map(link -> link.getOrganization().getId())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
        return form;
    }

    /** 사전 상담에서 모집중으로 전환하기 전 관리자에게 보여 줄 누락 항목. */
    public List<String> recruitmentReadiness(Long courseId) {
        Course course = get(courseId);
        CoursePublication publication = coursePublicationRepository.findByCourseId(courseId).orElse(null);
        List<String> missing = new java.util.ArrayList<>();

        if (isBlank(course.getCourseName())) missing.add("과정명");
        if (course.getStartDate() == null || course.getEndDate() == null) missing.add("교육 기간");
        if (course.getCapacity() < 1) missing.add("모집 정원");

        if (publication == null) {
            missing.add("홈페이지 공개 정보 저장");
            return List.copyOf(missing);
        }
        if (isBlank(publication.getOneLineIntroduction())) missing.add("과정 한줄소개");
        if (isBlank(publication.getAudience())) missing.add("교육 대상");
        if (publication.getRecruitmentStartDate() == null) missing.add("모집 시작일");
        if (publication.getApplicationDeadline() == null) missing.add("신청 마감일");
        if (publication.getRecruitmentStartDate() != null && publication.getApplicationDeadline() != null
                && publication.getApplicationDeadline().isBefore(publication.getRecruitmentStartDate())) {
            missing.add("올바른 모집 기간");
        }
        if (publication.getApplicationDeadline() != null && course.getStartDate() != null
                && publication.getApplicationDeadline().isAfter(course.getStartDate())) {
            missing.add("교육 시작일 이전 신청 마감일");
        }
        if (isBlank(publication.getEducationTime())) missing.add("교육 시간");
        if (isBlank(publication.getEducationMethod())) missing.add("교육 방법");
        if (publication.getTuitionFee() == null || publication.getSelfPayment() == null
                || publication.getGovernmentSupport() == null || publication.getAdditionalCost() == null) {
            missing.add("비용·지원 정보");
        }
        if (publication.getPublicationSite() == null) missing.add("노출 사이트");
        if (publication.getPublicCategory() == null) missing.add("홈페이지 과정 분류");
        if (!courseInstructorRepository.existsByCourseId(courseId)) missing.add("담당 교사");
        return List.copyOf(missing);
    }

    /** 공개 모집 상태는 정해진 순서로만 이동하고 내부 운영 상태도 같은 단계로 맞춘다. */
    @Transactional
    public void changeRecruitmentStatus(Long courseId, RecruitmentStatus target) {
        Course course = get(courseId);
        CoursePublication publication = coursePublicationRepository.findByCourseId(courseId)
                .orElseThrow(() -> new RecruitmentReadinessException(List.of("홈페이지 공개 정보 저장")));
        RecruitmentStatus current = publication.getRecruitmentStatus();
        if (current.next() != target) {
            throw new IllegalStateException(current.getLabel() + "에서 " + target.getLabel()
                    + " 상태로 바로 변경할 수 없습니다.");
        }
        if (target == RecruitmentStatus.RECRUITING) {
            List<String> missing = recruitmentReadiness(courseId);
            if (!missing.isEmpty()) {
                throw new RecruitmentReadinessException(missing);
            }
        }
        publication.changeRecruitmentStatus(target);
        switch (target) {
            case RECRUITING -> course.changeStatus(CourseStatus.RECRUITING);
            case CLOSED -> course.changeStatus(CourseStatus.RECRUITMENT_CLOSED);
            case IN_PROGRESS -> course.changeStatus(CourseStatus.IN_PROGRESS);
            case COMPLETED -> course.changeStatus(CourseStatus.COMPLETED);
            case PRE_CONSULTATION -> course.changeStatus(CourseStatus.DRAFT);
        }
    }

    public RecruitmentStatus nextRecruitmentStatus(Long courseId) {
        return coursePublicationRepository.findByCourseId(courseId)
                .map(CoursePublication::getRecruitmentStatus)
                .orElse(RecruitmentStatus.PRE_CONSULTATION)
                .next();
    }

    @Transactional
    public void delete(Long id) {
        courseRepository.delete(get(id));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
