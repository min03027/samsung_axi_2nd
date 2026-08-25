package com.ssa.lms.course.template;
public enum CourseTemplateStatus { PUBLISHED("사용 중"), ARCHIVED("보관"); private final String label; CourseTemplateStatus(String label){this.label=label;} public String getLabel(){return label;} }
