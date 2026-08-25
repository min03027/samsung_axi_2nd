package com.ssa.lms.course.repository;

import com.ssa.lms.course.entity.CourseInstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseInstructorRepository extends JpaRepository<CourseInstructor, Long> {

    List<CourseInstructor> findByCourseId(Long courseId);

    List<CourseInstructor> findByInstructorId(Long instructorId);

    boolean existsByCourseIdAndInstructorId(Long courseId, Long instructorId);

    boolean existsByCourseId(Long courseId);

    /** 강사 담당 과정 id 목록 — CourseQueryService 전용 (soft delete 된 과정은 @SQLRestriction 으로 제외) */
    @Query("select ci.course.id from CourseInstructor ci where ci.instructor.id = :instructorId")
    List<Long> findCourseIdsByInstructorId(@Param("instructorId") Long instructorId);

    /** 과정 담당 강사 목록 — 강사 User 를 fetch join 으로 함께 로딩 (N+1 방지) */
    @Query("select ci from CourseInstructor ci join fetch ci.instructor where ci.course.id = :courseId "
            + "order by ci.primaryInstructor desc, ci.instructor.name asc")
    List<CourseInstructor> findWithInstructorByCourseId(@Param("courseId") Long courseId);
}
