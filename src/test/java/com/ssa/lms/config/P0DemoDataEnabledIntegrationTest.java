package com.ssa.lms.config;

import com.ssa.lms.attendance.repository.AttendanceRepository;
import com.ssa.lms.content.repository.ContentRepository;
import com.ssa.lms.content.repository.ProgressRepository;
import com.ssa.lms.course.entity.EnrollmentStatus;
import com.ssa.lms.course.repository.CourseInstructorRepository;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.EnrollmentRepository;
import com.ssa.lms.course.repository.SessionRepository;
import com.ssa.lms.course.service.CourseQueryService;
import com.ssa.lms.notice.repository.NoticeRepository;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "lms.demo.seed-data=true",
        "lms.demo.password=test-only-password"
})
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class P0DemoDataEnabledIntegrationTest {

    @Autowired P0DemoDataInitializer initializer;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseInstructorRepository instructors;
    @Autowired EnrollmentRepository enrollments;
    @Autowired SessionRepository sessionRepo;
    @Autowired ContentRepository contents;
    @Autowired ProgressRepository progress;
    @Autowired AttendanceRepository attendance;
    @Autowired NoticeRepository notices;
    @Autowired CourseQueryService courseQuery;
    @Autowired MockMvc mvc;

    @Test
    void createsOneConnectedRoleDatasetAndSecondRunIsIdempotent() throws Exception {
        var admin = users.findByLoginId(P0DemoDataInitializer.ADMIN_LOGIN_ID).orElseThrow();
        var instructor = users.findByLoginId(P0DemoDataInitializer.INSTRUCTOR_LOGIN_ID).orElseThrow();
        var trainee = users.findByLoginId(P0DemoDataInitializer.TRAINEE_LOGIN_ID).orElseThrow();
        var course = courses.findByCourseCode(P0DemoDataInitializer.COURSE_CODE).orElseThrow();

        assertThat(users.count()).isEqualTo(3);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(instructor.getRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(trainee.getRole()).isEqualTo(Role.TRAINEE);
        assertThat(courses.count()).isEqualTo(1);
        assertThat(instructors.existsByCourseIdAndInstructorId(course.getId(), instructor.getId())).isTrue();
        assertThat(enrollments.findByTraineeIdAndCourseId(trainee.getId(), course.getId()))
                .get().extracting(e -> e.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);

        assertThat(courseQuery.findCourseIdsByInstructorId(instructor.getId())).containsExactly(course.getId());
        assertThat(courseQuery.findCourseIdsByUserId(trainee.getId())).containsExactly(course.getId());
        assertThat(courseQuery.findAllCourseOptions()).extracting(CourseQueryService.CourseOption::id)
                .containsExactly(course.getId());
        assertThat(sessionRepo.findBySubjectCourseIdOrderBySubjectOrderNoAscSeqAsc(course.getId())).hasSize(3);
        assertThat(contents.findByCourseIdOrderByOrderNoAscIdAsc(course.getId())).hasSize(2);
        assertThat(progress.findByUserIdAndCourseId(trainee.getId(), course.getId())).hasSize(1);
        assertThat(attendance.findByCourseIdAndTraineeId(course.getId(), trainee.getId())).hasSize(1);
        assertThat(notices.count()).isEqualTo(1);

        long userCount = users.count();
        long courseCount = courses.count();
        long contentCount = contents.count();
        long noticeCount = notices.count();
        initializer.run();

        assertThat(users.count()).isEqualTo(userCount);
        assertThat(courses.count()).isEqualTo(courseCount);
        assertThat(contents.count()).isEqualTo(contentCount);
        assertThat(notices.count()).isEqualTo(noticeCount);
    }

    @Test
    void allDemoAccountsLoginAndOpenTheirRoleHome() throws Exception {
        assertRoleHome(P0DemoDataInitializer.ADMIN_LOGIN_ID, "/admin");
        assertRoleHome(P0DemoDataInitializer.INSTRUCTOR_LOGIN_ID, "/instructor");
        assertRoleHome(P0DemoDataInitializer.TRAINEE_LOGIN_ID, "/trainee");
    }

    private void assertRoleHome(String loginId, String home) throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", loginId).param("password", "test-only-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(home))
                .andReturn().getRequest().getSession(false);
        mvc.perform(get(home).session(session)).andExpect(status().isOk());
    }
}
