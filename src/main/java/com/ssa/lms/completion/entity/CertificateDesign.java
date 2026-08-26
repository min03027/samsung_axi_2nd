package com.ssa.lms.completion.entity;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.course.entity.Course;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 과정별 공식 이수증 디자인 설정. 관리자만 수정하며 관리자·훈련생 PDF 발급에서 함께 사용한다. */
@Entity
@Table(name = "course_certificate_design", uniqueConstraints = {
        @UniqueConstraint(name = "uk_certificate_design_course", columnNames = "course_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateDesign extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 20)
    private String preset;

    @Column(nullable = false, length = 40)
    private String title;

    @Column(nullable = false, length = 80)
    private String issuer;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Column(name = "accent_color", nullable = false, length = 7)
    private String accentColor;

    @Column(name = "show_birth", nullable = false)
    private boolean showBirth;

    @Column(name = "show_period", nullable = false)
    private boolean showPeriod;

    @Column(name = "show_metrics", nullable = false)
    private boolean showMetrics;

    @Column(name = "show_seal", nullable = false)
    private boolean showSeal;

    public static CertificateDesign create(Course course) {
        CertificateDesign design = new CertificateDesign();
        design.course = course;
        return design;
    }

    public void update(String preset, String title, String issuer, String statement, String accentColor,
                       boolean showBirth, boolean showPeriod, boolean showMetrics, boolean showSeal) {
        this.preset = preset;
        this.title = title;
        this.issuer = issuer;
        this.statement = statement;
        this.accentColor = accentColor;
        this.showBirth = showBirth;
        this.showPeriod = showPeriod;
        this.showMetrics = showMetrics;
        this.showSeal = showSeal;
    }
}
