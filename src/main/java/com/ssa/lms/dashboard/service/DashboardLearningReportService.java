package com.ssa.lms.dashboard.service;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.service.CourseQueryService;
import com.ssa.lms.course.service.CourseService;
import com.ssa.lms.dashboard.dto.DashboardMetrics;
import com.ssa.lms.export.ExcelWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 관리자·강사 대시보드에 이미 표시되는 실제 과정 진도 지표를 엑셀로 내보낸다.
 * 화면과 보고서가 다른 계산식을 갖지 않도록 {@link DashboardMetricsService} 결과를 그대로 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardLearningReportService {

    private final CourseService courseService;
    private final CourseQueryService courseQueryService;
    private final DashboardMetricsService dashboardMetricsService;

    public List<CourseOption> adminCourseOptions() {
        return options(courseService.findAll());
    }

    public List<CourseOption> instructorCourseOptions(Long instructorId) {
        return options(instructorCourses(instructorId));
    }

    public byte[] forAdmin(Long courseId) {
        return workbook(select(courseService.findAll(), courseId));
    }

    public byte[] forInstructor(Long instructorId, Long courseId) {
        return workbook(select(instructorCourses(instructorId), courseId));
    }

    private List<Course> instructorCourses(Long instructorId) {
        return courseService.findAll().stream()
                .filter(course -> courseQueryService.isInstructorOf(instructorId, course.getId()))
                .toList();
    }

    private List<Course> select(List<Course> scopedCourses, Long courseId) {
        if (courseId == null) {
            return scopedCourses;
        }
        return scopedCourses.stream()
                .filter(course -> course.getId().equals(courseId))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "조회 가능한 분반을 찾을 수 없습니다."));
    }

    private List<CourseOption> options(List<Course> courses) {
        return courses.stream()
                .map(course -> new CourseOption(course.getId(), label(course)))
                .toList();
    }

    private byte[] workbook(List<Course> courses) {
        try (ExcelWriter writer = ExcelWriter.create()) {
            writer.sheet("분반별 학습 현황",
                    "과정·분반", "수강 인원", "평균 진도율(%)", "기간 경과율(%)", "진도 격차(%p)");

            int written = 0;
            for (Course course : courses) {
                DashboardMetrics metrics = dashboardMetricsService.of(List.of(course));
                if (metrics.courses().isEmpty()) {
                    continue;
                }
                DashboardMetrics.CourseProgress progress = metrics.courses().get(0);
                Integer elapsed = progress.elapsed() < 0 ? null : progress.elapsed();
                Integer gap = elapsed == null ? null : progress.progress() - elapsed;
                writer.row(label(course), progress.trainees(), progress.progress(), elapsed, gap);
                written++;
            }
            if (written == 0) {
                writer.emptyNote("조회 범위에 집계 가능한 실제 수강 데이터가 없습니다.");
            }
            return writer.toBytes();
        }
    }

    private String label(Course course) {
        return course.getCohort() == null || course.getCohort().isBlank()
                ? course.getCourseName()
                : course.getCourseName() + " · " + course.getCohort();
    }

    public record CourseOption(Long id, String label) {
    }
}
