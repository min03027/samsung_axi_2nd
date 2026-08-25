package com.ssa.lms.course.template;
import jakarta.validation.constraints.NotNull;
import lombok.*;
@Getter @Setter public class CourseTemplateDeployForm {@NotNull private Long targetCourseId;private Boolean autoSyncSafe=Boolean.TRUE;}
