package com.ssa.lms.completion.web;

import com.ssa.lms.completion.entity.Completion;
import com.ssa.lms.completion.entity.CompletionResult;
import com.ssa.lms.completion.entity.ConfirmStatus;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.user.entity.User;

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
        return new CompletionView(
                c.getId(),
                c.getTrainee().getName(),
                c.getTrainee().getBirthDate(),
                courseLabel(c.getCourse()),
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

    /** 승인 수강생이 아직 자동 판정을 받지 않았을 때 관리자 명단에 표시하는 미판정 행. */
    public static CompletionView pending(Course course, User trainee, int progressRate, int attendanceRate) {
        return new CompletionView(
                null,
                trainee.getName(),
                trainee.getBirthDate(),
                courseLabel(course),
                progressRate,
                attendanceRate,
                null,
                null,
                CompletionResult.PENDING.name(),
                CompletionResult.PENDING.getLabel(),
                ConfirmStatus.PENDING.name(),
                ConfirmStatus.PENDING.getLabel(),
                null,
                false
        );
    }

    private static String courseLabel(Course course) {
        String cohort = course.getCohort();
        return course.getCourseName() + (cohort != null ? " / " + cohort : "");
    }
}
