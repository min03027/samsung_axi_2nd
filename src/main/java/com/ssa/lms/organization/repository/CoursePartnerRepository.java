package com.ssa.lms.organization.repository;

import com.ssa.lms.organization.entity.CoursePartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CoursePartnerRepository extends JpaRepository<CoursePartner, Long> {

    @Query("select cp from CoursePartner cp join fetch cp.organization o "
            + "where cp.course.id = :courseId order by o.displayOrder asc, o.name asc")
    List<CoursePartner> findDetailedByCourseId(@Param("courseId") Long courseId);

    @Query("select cp from CoursePartner cp join fetch cp.course c "
            + "where cp.organization.id = :organizationId order by c.startDate desc, c.id desc")
    List<CoursePartner> findDetailedByOrganizationId(@Param("organizationId") Long organizationId);
}
