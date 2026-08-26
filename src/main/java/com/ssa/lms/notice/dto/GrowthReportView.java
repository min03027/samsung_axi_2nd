package com.ssa.lms.notice.dto;

/** 실제 대시보드 집계를 한 화면에 모은 성장 리포트. */
public record GrowthReportView(String courseName, int progressRate, Integer recommendedProgress,
                               Integer attendanceRate, int pendingCount, int remainingAssignments,
                               int remainingExams, int progressGap, String tone, String summary,
                               String actionLabel, String actionHref) {
}
