package com.ssa.lms.config;

import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class LocalDataInitializerRegressionTest {

    @Autowired UserRepository users;
    @Autowired CourseRepository courses;

    @Test
    void existingLocalCoreAccountsAndCourseRemainAvailable() {
        assertThat(users.findByLoginId("admin")).isPresent();
        assertThat(users.findByLoginId("instructor1")).isPresent();
        assertThat(users.findByLoginId("trainee1")).isPresent();
        assertThat(courses.findByCourseCode("COURSE-2026-001")).isPresent();
    }
}
