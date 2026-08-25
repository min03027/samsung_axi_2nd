package com.ssa.lms.course.entity;

import com.ssa.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 기존 {@link Course} 한 건에 대응하는 홈페이지 공개·모집 정보.
 * 과정 자체를 중복 생성하지 않고 내부 운영 데이터와 공개 가능한 데이터의 경계만 분리한다.
 */
@Entity
@Table(name = "course_publication", uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_publication_course", columnNames = "course_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoursePublication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "one_line_introduction", length = 500)
    private String oneLineIntroduction;

    @Column(columnDefinition = "TEXT")
    private String audience;

    @Column(columnDefinition = "TEXT")
    private String prerequisites;

    @Enumerated(EnumType.STRING)
    @Column(name = "recruitment_status", nullable = false, length = 30)
    private RecruitmentStatus recruitmentStatus;

    @Column(name = "recruitment_start_date")
    private LocalDate recruitmentStartDate;

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    @Column(name = "consultation_date")
    private LocalDate consultationDate;

    @Column(name = "result_announcement_date")
    private LocalDate resultAnnouncementDate;

    @Column(name = "selection_process", columnDefinition = "TEXT")
    private String selectionProcess;

    @Column(name = "required_documents", columnDefinition = "TEXT")
    private String requiredDocuments;

    @Column(name = "education_time", length = 200)
    private String educationTime;

    @Column(name = "education_method", length = 200)
    private String educationMethod;

    @Column(name = "tuition_fee")
    private Long tuitionFee;

    @Column(name = "self_payment")
    private Long selfPayment;

    @Column(name = "government_support")
    private Long governmentSupport;

    @Column(name = "additional_cost")
    private Long additionalCost;

    @Column(columnDefinition = "TEXT")
    private String mentors;

    @Column(name = "project_partners", columnDefinition = "TEXT")
    private String projectPartners;

    @Column(name = "demo_url", length = 500)
    private String demoUrl;

    @Column(name = "public_visible", nullable = false)
    private boolean publicVisible;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_site", length = 30)
    private PublicationSite publicationSite;

    @Enumerated(EnumType.STRING)
    @Column(name = "public_category", length = 30)
    private PublicCourseCategory publicCategory;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean featured;

    @Builder
    private CoursePublication(Course course, String oneLineIntroduction, String audience,
                              String prerequisites, RecruitmentStatus recruitmentStatus,
                              LocalDate recruitmentStartDate, LocalDate applicationDeadline,
                              LocalDate consultationDate, LocalDate resultAnnouncementDate,
                              String selectionProcess, String requiredDocuments,
                              String educationTime, String educationMethod,
                              Long tuitionFee, Long selfPayment, Long governmentSupport, Long additionalCost,
                              String mentors, String projectPartners, String demoUrl,
                              boolean publicVisible, PublicationSite publicationSite,
                              PublicCourseCategory publicCategory, int displayOrder, boolean featured) {
        this.course = course;
        this.recruitmentStatus = recruitmentStatus != null
                ? recruitmentStatus : RecruitmentStatus.PRE_CONSULTATION;
        update(oneLineIntroduction, audience, prerequisites, recruitmentStartDate,
                applicationDeadline, consultationDate, resultAnnouncementDate,
                selectionProcess, requiredDocuments, educationTime, educationMethod,
                tuitionFee, selfPayment, governmentSupport, additionalCost,
                mentors, projectPartners, demoUrl, publicVisible, publicationSite,
                publicCategory, displayOrder, featured);
    }

    public void update(String oneLineIntroduction, String audience, String prerequisites,
                       LocalDate recruitmentStartDate, LocalDate applicationDeadline,
                       LocalDate consultationDate, LocalDate resultAnnouncementDate,
                       String selectionProcess, String requiredDocuments,
                       String educationTime, String educationMethod,
                       Long tuitionFee, Long selfPayment, Long governmentSupport, Long additionalCost,
                       String mentors, String projectPartners, String demoUrl,
                       boolean publicVisible, PublicationSite publicationSite,
                       PublicCourseCategory publicCategory, int displayOrder, boolean featured) {
        this.oneLineIntroduction = oneLineIntroduction;
        this.audience = audience;
        this.prerequisites = prerequisites;
        this.recruitmentStartDate = recruitmentStartDate;
        this.applicationDeadline = applicationDeadline;
        this.consultationDate = consultationDate;
        this.resultAnnouncementDate = resultAnnouncementDate;
        this.selectionProcess = selectionProcess;
        this.requiredDocuments = requiredDocuments;
        this.educationTime = educationTime;
        this.educationMethod = educationMethod;
        this.tuitionFee = tuitionFee;
        this.selfPayment = selfPayment;
        this.governmentSupport = governmentSupport;
        this.additionalCost = additionalCost;
        this.mentors = mentors;
        this.projectPartners = projectPartners;
        this.demoUrl = demoUrl;
        this.publicVisible = publicVisible;
        this.publicationSite = publicationSite;
        this.publicCategory = publicCategory;
        this.displayOrder = Math.max(displayOrder, 0);
        this.featured = featured;
    }

    public void changeRecruitmentStatus(RecruitmentStatus recruitmentStatus) {
        this.recruitmentStatus = recruitmentStatus;
    }
}
