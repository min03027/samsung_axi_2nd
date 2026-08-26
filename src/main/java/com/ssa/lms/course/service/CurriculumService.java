package com.ssa.lms.course.service;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.Session;
import com.ssa.lms.course.entity.Subject;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.SessionRepository;
import com.ssa.lms.course.repository.SubjectRepository;
import com.ssa.lms.course.web.SessionForm;
import com.ssa.lms.course.web.SubjectForm;
import com.ssa.lms.course.web.SubjectView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 과정 구성(과목/차시) 관리. 과목은 과정 내 정렬순서(orderNo), 차시는 과목 내 번호(seq)를
 * 서비스가 자동으로 이어 붙인다. OSIV 비활성이므로 조회는 트랜잭션 내에서 뷰 모델로 매핑해 반환한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumService {

    private final CourseRepository courseRepository;
    private final SubjectRepository subjectRepository;
    private final SessionRepository sessionRepository;

    /** 과정의 과목+차시 전체 구성 (정렬 순서대로). */
    public List<SubjectView> curriculum(Long courseId) {
        return subjectRepository.findByCourseIdOrderByOrderNo(courseId).stream()
                .map(s -> SubjectView.of(s, sessionRepository.findBySubjectIdOrderBySeq(s.getId())))
                .toList();
    }

    @Transactional
    public Long addSubject(Long courseId, SubjectForm form) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        int nextOrder = (int) subjectRepository.countByCourseId(courseId) + 1;
        Subject subject = Subject.builder()
                .name(form.getName()).description(form.getDescription()).orderNo(nextOrder).build();
        course.addSubject(subject);           // 연관관계 + 정렬순서 설정
        return subjectRepository.save(subject).getId();
    }

    @Transactional
    public void updateSubject(Long subjectId, SubjectForm form) {
        Subject subject = getSubject(subjectId);
        subject.update(form.getName(), form.getDescription(), subject.getOrderNo());
    }

    @Transactional
    public void deleteSubject(Long subjectId) {
        subjectRepository.delete(getSubject(subjectId));   // 차시는 orphanRemoval 로 함께 삭제
    }

    @Transactional
    public Long addSession(Long subjectId, SessionForm form) {
        Subject subject = getSubject(subjectId);
        int nextSeq = (int) sessionRepository.countBySubjectId(subjectId) + 1;
        Session session = Session.builder()
                .seq(nextSeq).name(form.getName())
                .lessonDate(form.getLessonDate()).lessonStartTime(form.getLessonStartTime())
                .learningMinutes(form.getLearningMinutes()).build();
        subject.addSession(session);          // 연관관계 설정
        return sessionRepository.save(session).getId();
    }

    @Transactional
    public void updateSession(Long sessionId, SessionForm form) {
        Session session = getSession(sessionId);
        session.update(session.getSeq(), form.getName(), form.getLessonDate(), form.getLessonStartTime(),
                form.getLearningMinutes());
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        sessionRepository.delete(getSession(sessionId));
    }

    private Subject getSubject(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("과목을 찾을 수 없습니다: " + id));
    }

    private Session getSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("차시를 찾을 수 없습니다: " + id));
    }
}
