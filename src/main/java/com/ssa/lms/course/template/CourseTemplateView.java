package com.ssa.lms.course.template;
import java.time.LocalDateTime;
public record CourseTemplateView(Long id,String name,String description,Long sourceCourseId,String sourceCourseName,int currentVersion,CourseTemplateStatus status,long linkedCourses,LocalDateTime updatedAt){public static CourseTemplateView of(CourseTemplate t,long links){return new CourseTemplateView(t.getId(),t.getName(),t.getDescription(),t.getSourceCourse().getId(),t.getSourceCourse().getCourseName(),t.getCurrentVersion(),t.getStatus(),links,t.getUpdatedAt()!=null?t.getUpdatedAt():t.getCreatedAt());}}
