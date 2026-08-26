package com.ssa.lms.completion.entity;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * 수강생의 과정 이수 판정 결과. 자동 판정(진도+출결+성적)으로 {@link CompletionResult} 를 산정하고,
 * 관리자가 {@link ConfirmStatus#CONFIRMED} 로 최종 확정하면 이수증 발급 대상이 된다.
 *
 * <p>(course_id, user_id) 단위 1행. 판정 근거(진도율/출석률/성적충족)를 스냅샷으로 남긴다(3년 보존, soft delete).</p>
 */
@Entity
@Table(name = "completion", uniqueConstraints = {
        @UniqueConstraint(name = "uk_completion_course_user", columnNames = {"course_id", "user_id"})
})
@SQLDelete(sql = "UPDATE completion SET is_deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Completion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User trainee;

    /* ===== 판정 근거 스냅샷 ===== */
    @Column(name = "progress_rate", nullable = false)
    private int progressRate;

    @Column(name = "attendance_rate", nullable = false)
    private int attendanceRate;

    /** 판정 당시 확정된 평가의 평균 점수. 점수 기준 미적용 또는 확정 성적 없음이면 null. */
    @Column(name = "average_score")
    private Double averageScore;

    /** 성적 요건 충족 여부. 성적 기준 미반영(requireGradePass=false)이면 null. */
    @Column(name = "grades_confirmed")
    private Boolean gradesConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompletionResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirm_status", nullable = false, length = 20)
    private ConfirmStatus confirmStatus;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Builder
    private Completion(Course course, User trainee, int progressRate, int attendanceRate,
                       Double averageScore, Boolean gradesConfirmed, CompletionResult result, ConfirmStatus confirmStatus,
                       LocalDateTime evaluatedAt) {
        this.course = course;
        this.trainee = trainee;
        this.progressRate = progressRate;
        this.attendanceRate = attendanceRate;
        this.averageScore = averageScore;
        this.gradesConfirmed = gradesConfirmed;
        this.result = result != null ? result : CompletionResult.PENDING;
        this.confirmStatus = confirmStatus != null ? confirmStatus : ConfirmStatus.EXPECTED;
        this.evaluatedAt = evaluatedAt;
    }

    /** 자동 판정 결과/근거 갱신. 이미 확정(CONFIRMED)된 건은 확정 상태를 유지한다. */
    public void applyEvaluation(int progressRate, int attendanceRate, Double averageScore, Boolean gradesConfirmed,
                                CompletionResult result, LocalDateTime evaluatedAt) {
        this.progressRate = progressRate;
        this.attendanceRate = attendanceRate;
        this.averageScore = averageScore;
        this.gradesConfirmed = gradesConfirmed;
        this.result = result;
        this.evaluatedAt = evaluatedAt;
        if (this.confirmStatus != ConfirmStatus.CONFIRMED) {
            this.confirmStatus = ConfirmStatus.EXPECTED;
        }
    }

    public void confirm(LocalDateTime at) {
        this.confirmStatus = ConfirmStatus.CONFIRMED;
        this.confirmedAt = at;
    }

    /** 확정 상태 직접 변경(빠른 상태변경). CONFIRMED 로 바꿀 때만 확정 시각을 남긴다. */
    public void changeConfirmStatus(ConfirmStatus status, LocalDateTime at) {
        this.confirmStatus = status;
        this.confirmedAt = status == ConfirmStatus.CONFIRMED ? at : null;
    }

    /** 이수증 발급 가능 여부 — 이수(PASS) + 확정(CONFIRMED). */
    public boolean isCertificateIssuable() {
        return result == CompletionResult.PASS && confirmStatus == ConfirmStatus.CONFIRMED;
    }
}
