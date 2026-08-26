package com.ssa.lms.completion.entity;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.course.entity.Course;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 과정별 이수 기준. 자동 판정({@code CompletionService.evaluate})이 이 기준으로 이수/미이수를 정한다.
 *
 * <p>과정(Course)에는 이미 {@code completionProgressRate}(진도율 기준)가 있으나, 출결·성적 기준까지
 * 담으려면 공통 엔티티(Course) 스키마 변경이 필요하다. 병렬 작업 중 공통 엔티티 변경을 피하기 위해
 * 이수 기준을 이 트랙(completion) 소유 엔티티로 분리한다. 기본 진도 기준은 Course 값을 초기값으로 쓴다.</p>
 */
@Entity
@Table(name = "completion_criteria", uniqueConstraints = {
        @UniqueConstraint(name = "uk_completion_criteria_course", columnNames = "course_id")
})
@SQLDelete(sql = "UPDATE completion_criteria SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompletionCriteria extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    /** 이수 최소 진도율(%). */
    @Column(name = "min_progress_rate", nullable = false)
    private int minProgressRate;

    /** 이수 최소 출석률(%). */
    @Column(name = "min_attendance_rate", nullable = false)
    private int minAttendanceRate;

    /** 확정된 평가 점수의 최소 평균. null 이면 테스트 점수를 판정에서 제외한다. */
    @Column(name = "min_average_score")
    private Integer minAverageScore;

    /**
     * 성적(평가) 이수 요건 반영 여부. B의 성적 도메인(GradeQueryService)이 통합되기 전에는
     * 기본 false 로 두고 진도+출결만으로 판정한다. true 로 켜면 성적 요건 미충족 시 미이수.
     */
    @Column(name = "require_grade_pass", nullable = false)
    private boolean requireGradePass;

    @Column(length = 300)
    private String note;

    @Builder
    private CompletionCriteria(Course course, int minProgressRate, int minAttendanceRate,
                               Integer minAverageScore, boolean requireGradePass, String note) {
        this.course = course;
        this.minProgressRate = minProgressRate;
        this.minAttendanceRate = minAttendanceRate;
        this.minAverageScore = minAverageScore;
        this.requireGradePass = requireGradePass;
        this.note = note;
    }

    public void update(int minProgressRate, int minAttendanceRate, Integer minAverageScore,
                       boolean requireGradePass, String note) {
        this.minProgressRate = minProgressRate;
        this.minAttendanceRate = minAttendanceRate;
        this.minAverageScore = minAverageScore;
        this.requireGradePass = requireGradePass;
        this.note = note;
    }
}
