package com.ssa.lms.course.template;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter public class CourseTemplateForm {@NotNull private Long sourceCourseId;@NotBlank @Size(max=200) private String name;@Size(max=2000) private String description;@Size(max=2000) private String changeSummary;}
