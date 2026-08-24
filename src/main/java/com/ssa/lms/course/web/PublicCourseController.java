package com.ssa.lms.course.web;

import com.ssa.lms.course.entity.PublicationSite;
import com.ssa.lms.course.service.PublicCourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** `/v2/**` 공개 영역에서 사용하는 읽기 전용 과정 API. */
@RestController
@RequestMapping("/v2/api/courses")
@RequiredArgsConstructor
public class PublicCourseController {

    private final PublicCourseService publicCourseService;

    @GetMapping
    public List<PublicCourseView> list() {
        return publicCourseService.publishedCourses(PublicationSite.CLASS);
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<PublicCourseView> detail(@PathVariable Long courseId) {
        return publicCourseService.publishedCourse(courseId, PublicationSite.CLASS)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
