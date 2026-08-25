package com.ssa.lms.course.template;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CourseTemplateVersionRepository extends JpaRepository<CourseTemplateVersion,Long>{List<CourseTemplateVersion> findByTemplateIdOrderByVersionNoDesc(Long templateId);}
