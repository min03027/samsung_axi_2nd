package com.ssa.lms.content.request;

import com.ssa.lms.course.entity.Course;

public record ContentRequestCourseOption(Long id, String courseName) {
    public static ContentRequestCourseOption of(Course course) {
        return new ContentRequestCourseOption(course.getId(), course.getCourseName());
    }
}
