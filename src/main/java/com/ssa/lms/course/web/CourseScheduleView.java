package com.ssa.lms.course.web;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 일정 관리 화면용 차시(Session) 일정 항목 — lessonDate 가 지정된 차시만 산출한다.
 * 관리자/강사 공용 (강사 뷰는 담당 과정으로 이미 필터된 courseId 만 넘어온다).
 */
public record CourseScheduleView(LocalDate lessonDate, LocalTime lessonStartTime,
                                 Long courseId, String courseCode, String courseName,
                                 String subjectName, int seq, String sessionName, Integer learningMinutes) {
}
