package com.ssa.lms.course.template;
import java.time.LocalDateTime;
public record CourseTemplateLinkView(Long id,Long courseId,String courseName,int appliedVersion,boolean autoSyncSafe,boolean updateAvailable,LocalDateTime lastSyncedAt){public static CourseTemplateLinkView of(CourseTemplateLink l){return new CourseTemplateLinkView(l.getId(),l.getTargetCourse().getId(),l.getTargetCourse().getCourseName(),l.getAppliedVersion(),l.isAutoSyncSafe(),l.getAppliedVersion()<l.getTemplate().getCurrentVersion(),l.getLastSyncedAt());}}
