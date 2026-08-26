package com.ssa.lms.course.service;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.SessionRepository;
import com.ssa.lms.course.web.CourseScheduleView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 과정 일정 관리 — 차시(Session)의 lessonDate 기준 일정 목록을 산출한다 (관리자/강사 공용).
 * 날짜가 지정된 차시만 대상이며 날짜 오름차순으로 정렬한다.
 * OSIV 비활성 → 트랜잭션 내에서 뷰 모델로 매핑해 반환한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseScheduleService {

    private final SessionRepository sessionRepository;
    private final CourseRepository courseRepository;

    /** 특정 과정의 일정(날짜 지정 차시) — 날짜 오름차순. 과정이 없으면 404 로 매핑되는 예외. */
    public List<CourseScheduleView> scheduleOf(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        return sessionRepository.findBySubjectCourseIdOrderBySubjectOrderNoAscSeqAsc(courseId).stream()
                .filter(s -> s.getLessonDate() != null)
                .map(s -> new CourseScheduleView(
                        s.getLessonDate(), s.getLessonStartTime(), course.getId(), course.getCourseCode(), course.getCourseName(),
                        s.getSubject().getName(), s.getSeq(), s.getName(), s.getLearningMinutes()))
                .sorted(Comparator.comparing(CourseScheduleView::lessonDate))
                .toList();
    }
}
