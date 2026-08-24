package com.ssa.lms.course.repository;

import com.ssa.lms.course.entity.CoursePublication;
import com.ssa.lms.course.entity.PublicationSite;
import com.ssa.lms.course.entity.RecruitmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CoursePublicationRepository extends JpaRepository<CoursePublication, Long> {

    Optional<CoursePublication> findByCourseId(Long courseId);

    @Query("select p from CoursePublication p join fetch p.course c "
            + "where p.publicVisible = true and p.recruitmentStatus = :status "
            + "and (p.publicationSite = :site or p.publicationSite = :allSite) "
            + "order by p.featured desc, p.displayOrder asc, c.startDate asc")
    List<CoursePublication> findPublished(@Param("status") RecruitmentStatus status,
                                          @Param("site") PublicationSite site,
                                          @Param("allSite") PublicationSite allSite);

    @Query("select p from CoursePublication p join fetch p.course c "
            + "where c.id = :courseId and p.publicVisible = true and p.recruitmentStatus = :status "
            + "and (p.publicationSite = :site or p.publicationSite = :allSite)")
    Optional<CoursePublication> findPublishedByCourseId(@Param("courseId") Long courseId,
                                                        @Param("status") RecruitmentStatus status,
                                                        @Param("site") PublicationSite site,
                                                        @Param("allSite") PublicationSite allSite);
}
