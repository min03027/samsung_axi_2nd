package com.ssa.lms.organization.entity;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.course.entity.Course;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 과정과 기업/기관의 연결. 실제 취업 결과가 아니라 프로젝트·채용 연계 관계만 표현한다. */
@Entity
@Table(name = "course_partner", uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_partner", columnNames = {"course_id", "organization_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoursePartner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private PartnerOrganization organization;

    @Column(name = "project_participant", nullable = false)
    private boolean projectParticipant;

    @Column(name = "recruitment_linked", nullable = false)
    private boolean recruitmentLinked;

    @Builder
    private CoursePartner(Course course, PartnerOrganization organization,
                          boolean projectParticipant, boolean recruitmentLinked) {
        this.course = course;
        this.organization = organization;
        updateRelationship(projectParticipant, recruitmentLinked);
    }

    public void updateRelationship(boolean projectParticipant, boolean recruitmentLinked) {
        this.projectParticipant = projectParticipant;
        this.recruitmentLinked = recruitmentLinked;
    }
}
