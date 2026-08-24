package com.ssa.lms.course.web;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.CoursePublication;
import com.ssa.lms.course.entity.CourseStatus;
import com.ssa.lms.course.entity.PublicCourseCategory;
import com.ssa.lms.course.entity.PublicationSite;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 과정 등록/수정 폼 DTO.
 * courseCode 는 등록 시에만 입력받고(수정 불가 — B 계약 컬럼), 나머지는 공통 편집 대상이다.
 */
@Getter
@Setter
public class CourseForm {

    /** 과정 코드 — 형식: COURSE-2026-001 (등록 시 필수, 수정 시 표시만) */
    @NotBlank(message = "과정 코드를 입력하세요.")
    @Pattern(regexp = "[A-Za-z0-9-]{3,30}", message = "영문/숫자/하이픈 3~30자로 입력하세요.")
    private String courseCode;

    @NotBlank(message = "과정명을 입력하세요.")
    @Size(max = 200)
    private String courseName;

    @Size(max = 20)
    private String cohort;

    @Size(max = 50)
    private String category;

    private String description;

    @NotNull(message = "시작일을 입력하세요.")
    private LocalDate startDate;

    @NotNull(message = "종료일을 입력하세요.")
    private LocalDate endDate;

    @Min(value = 1, message = "정원은 1명 이상이어야 합니다.")
    private int capacity;

    @NotNull(message = "운영 상태를 선택하세요.")
    private CourseStatus status = CourseStatus.DRAFT;

    /** 이수 기준 진도율(%) */
    @Min(0) @Max(100)
    private int completionProgressRate = 80;

    /* ===== 홈페이지 공개 정보 — 비어 있어도 사전 상담 상태로 저장 가능 ===== */

    @Size(max = 500)
    private String oneLineIntroduction;

    private String audience;

    private String prerequisites;

    private LocalDate recruitmentStartDate;

    private LocalDate applicationDeadline;

    private LocalDate consultationDate;

    private LocalDate resultAnnouncementDate;

    private String selectionProcess;

    private String requiredDocuments;

    @Size(max = 200)
    private String educationTime;

    @Size(max = 200)
    private String educationMethod;

    @PositiveOrZero
    private Long tuitionFee;

    @PositiveOrZero
    private Long selfPayment;

    @PositiveOrZero
    private Long governmentSupport;

    @PositiveOrZero
    private Long additionalCost;

    private String mentors;

    private String projectPartners;

    /** 기업·기관 마스터에서 선택한 구조화된 프로젝트 참여사. */
    private Set<Long> projectPartnerOrganizationIds = new LinkedHashSet<>();

    @Pattern(regexp = "^$|https?://.+", message = "데모 링크는 http:// 또는 https:// 주소로 입력하세요.")
    @Size(max = 500)
    private String demoUrl;

    private boolean publicVisible;

    private PublicationSite publicationSite = PublicationSite.CLASS;

    private PublicCourseCategory publicCategory;

    @PositiveOrZero
    private int displayOrder;

    private boolean featured;

    /** 종료일이 시작일 이후인지 (@AssertTrue 검증) */
    @AssertTrue(message = "종료일은 시작일과 같거나 이후여야 합니다.")
    public boolean isPeriodValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "신청 마감일은 모집 시작일과 같거나 이후여야 합니다.")
    public boolean isRecruitmentPeriodValid() {
        return recruitmentStartDate == null || applicationDeadline == null
                || !applicationDeadline.isBefore(recruitmentStartDate);
    }

    @AssertTrue(message = "모집·선발 일정은 모집 시작 → 신청 마감 → 상담/면접 → 발표 순서로 입력하세요.")
    public boolean isSelectionScheduleValid() {
        if (applicationDeadline != null && consultationDate != null
                && consultationDate.isBefore(applicationDeadline)) {
            return false;
        }
        return consultationDate == null || resultAnnouncementDate == null
                || !resultAnnouncementDate.isBefore(consultationDate);
    }

    /** 신규 엔티티로 변환 (등록용). */
    public Course toNewCourse() {
        return Course.builder()
                .courseCode(courseCode)
                .courseName(courseName)
                .cohort(cohort)
                .category(category)
                .description(description)
                .startDate(startDate)
                .endDate(endDate)
                .capacity(capacity)
                .status(status)
                .completionProgressRate(completionProgressRate)
                .build();
    }

    public CoursePublication toNewPublication(Course course) {
        return CoursePublication.builder()
                .course(course)
                .oneLineIntroduction(oneLineIntroduction)
                .audience(audience)
                .prerequisites(prerequisites)
                .recruitmentStartDate(recruitmentStartDate)
                .applicationDeadline(applicationDeadline)
                .consultationDate(consultationDate)
                .resultAnnouncementDate(resultAnnouncementDate)
                .selectionProcess(selectionProcess)
                .requiredDocuments(requiredDocuments)
                .educationTime(educationTime)
                .educationMethod(educationMethod)
                .tuitionFee(tuitionFee)
                .selfPayment(selfPayment)
                .governmentSupport(governmentSupport)
                .additionalCost(additionalCost)
                .mentors(mentors)
                .projectPartners(projectPartners)
                .demoUrl(demoUrl)
                .publicVisible(publicVisible)
                .publicationSite(publicationSite)
                .publicCategory(publicCategory)
                .displayOrder(displayOrder)
                .featured(featured)
                .build();
    }

    public void applyTo(CoursePublication publication) {
        publication.update(oneLineIntroduction, audience, prerequisites,
                recruitmentStartDate, applicationDeadline, consultationDate, resultAnnouncementDate,
                selectionProcess, requiredDocuments, educationTime, educationMethod,
                tuitionFee, selfPayment, governmentSupport, additionalCost,
                mentors, projectPartners, demoUrl, publicVisible,
                publicationSite, publicCategory, displayOrder, featured);
    }

    /** 기존 과정 값으로 폼을 채운다 (수정 화면 초기값). */
    public static CourseForm from(Course course) {
        return from(course, null);
    }

    public static CourseForm from(Course course, CoursePublication publication) {
        CourseForm form = new CourseForm();
        form.courseCode = course.getCourseCode();
        form.courseName = course.getCourseName();
        form.cohort = course.getCohort();
        form.category = course.getCategory();
        form.description = course.getDescription();
        form.startDate = course.getStartDate();
        form.endDate = course.getEndDate();
        form.capacity = course.getCapacity();
        form.status = course.getStatus();
        form.completionProgressRate = course.getCompletionProgressRate();
        if (publication != null) {
            form.oneLineIntroduction = publication.getOneLineIntroduction();
            form.audience = publication.getAudience();
            form.prerequisites = publication.getPrerequisites();
            form.recruitmentStartDate = publication.getRecruitmentStartDate();
            form.applicationDeadline = publication.getApplicationDeadline();
            form.consultationDate = publication.getConsultationDate();
            form.resultAnnouncementDate = publication.getResultAnnouncementDate();
            form.selectionProcess = publication.getSelectionProcess();
            form.requiredDocuments = publication.getRequiredDocuments();
            form.educationTime = publication.getEducationTime();
            form.educationMethod = publication.getEducationMethod();
            form.tuitionFee = publication.getTuitionFee();
            form.selfPayment = publication.getSelfPayment();
            form.governmentSupport = publication.getGovernmentSupport();
            form.additionalCost = publication.getAdditionalCost();
            form.mentors = publication.getMentors();
            form.projectPartners = publication.getProjectPartners();
            form.demoUrl = publication.getDemoUrl();
            form.publicVisible = publication.isPublicVisible();
            form.publicationSite = publication.getPublicationSite();
            form.publicCategory = publication.getPublicCategory();
            form.displayOrder = publication.getDisplayOrder();
            form.featured = publication.isFeatured();
        } else {
            form.publicationSite = PublicationSite.CLASS;
        }
        return form;
    }
}
