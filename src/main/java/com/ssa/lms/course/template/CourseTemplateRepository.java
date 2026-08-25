package com.ssa.lms.course.template;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CourseTemplateRepository extends JpaRepository<CourseTemplate,Long>{List<CourseTemplate> findAllByOrderByUpdatedAtDescIdDesc();}
