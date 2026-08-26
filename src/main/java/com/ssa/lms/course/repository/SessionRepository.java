package com.ssa.lms.course.repository;

import com.ssa.lms.course.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findBySubjectIdOrderBySeq(Long subjectId);

    long countBySubjectId(Long subjectId);

    List<Session> findBySubjectCourseIdOrderBySubjectOrderNoAscSeqAsc(Long courseId);

    @Query("""
            select s from Session s
              join fetch s.subject sub
              join fetch sub.course c
            where s.lessonDate between :from and :to
              and s.lessonStartTime is not null
            order by s.lessonDate, s.lessonStartTime
            """)
    List<Session> findScheduledBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
