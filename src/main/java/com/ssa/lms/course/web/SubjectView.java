package com.ssa.lms.course.web;

import com.ssa.lms.course.entity.Session;
import com.ssa.lms.course.entity.Subject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 상세 화면용 과목/차시 뷰 모델 (OSIV 비활성 → 트랜잭션 내에서 미리 매핑).
 */
public record SubjectView(Long id, String name, String description, int orderNo,
                          List<SessionView> sessions) {

    public static SubjectView of(Subject subject, List<Session> sessions) {
        return new SubjectView(subject.getId(), subject.getName(), subject.getDescription(),
                subject.getOrderNo(),
                sessions.stream().map(SessionView::of).toList());
    }

    public record SessionView(Long id, int seq, String name, LocalDate lessonDate,
                              LocalTime lessonStartTime, Integer learningMinutes) {
        public static SessionView of(Session s) {
            return new SessionView(s.getId(), s.getSeq(), s.getName(), s.getLessonDate(),
                    s.getLessonStartTime(), s.getLearningMinutes());
        }
    }
}
