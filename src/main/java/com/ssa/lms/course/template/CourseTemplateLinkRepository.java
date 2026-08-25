package com.ssa.lms.course.template;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CourseTemplateLinkRepository extends JpaRepository<CourseTemplateLink,Long>{List<CourseTemplateLink> findByTemplateIdOrderByIdAsc(Long templateId);List<CourseTemplateLink> findByTemplateIdAndAutoSyncSafeTrueOrderByIdAsc(Long templateId);Optional<CourseTemplateLink> findByTemplateIdAndTargetCourseId(Long templateId,Long targetCourseId);long countByTemplateId(Long templateId);}
