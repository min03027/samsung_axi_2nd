package com.ssa.lms.completion.web;

import com.ssa.lms.completion.entity.Completion;

import java.time.LocalDateTime;

/**
 * 이수 관리 화면(admin-attendance-graduate) 행 뷰. open-in-view=false 이므로
 * 지연 로딩 필드는 반드시 트랜잭션 안({@code CompletionService})에서 채워 전달한다.
 */
public record CompletionView(
        Long id,
        String traineeName,
        String birthDate,
        String courseLabel,
        int progressRate,
        int attendanceRate,
        Double averageScore,
        Boolean gradesConfirmed,
        String result,
        String resultLabel,
        String confirmStatus,
        String confirmStatusLabel,
        LocalDateTime confirmedAt,
        boolean certificateIssuable
) {
    public static CompletionView of(Completion c) {
        String cohort = c.getCourse().getCohort();
        String courseLabel = c.getCourse().getCourseName() + (cohort != null ? " / " + cohort : "");
        return new CompletionView(
                c.getId(),
                c.getTrainee().getName(),
                c.getTrainee().getBirthDate(),
                courseLabel,
                c.getProgressRate(),
                c.getAttendanceRate(),
                c.getAverageScore(),
                c.getGradesConfirmed(),
                c.getResult().name(),
                c.getResult().getLabel(),
                c.getConfirmStatus().name(),
                c.getConfirmStatus().getLabel(),
                c.getConfirmedAt(),
                c.isCertificateIssuable()
        );
    }
}
