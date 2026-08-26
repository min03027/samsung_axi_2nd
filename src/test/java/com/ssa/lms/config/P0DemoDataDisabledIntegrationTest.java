package com.ssa.lms.config;

import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "lms.demo.seed-data=false")
@ActiveProfiles("demo")
class P0DemoDataDisabledIntegrationTest {

    @Autowired ApplicationContext context;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;

    @Test
    void explicitFalseCreatesNoDemoData() {
        assertThat(context.getBeansOfType(P0DemoDataInitializer.class)).isEmpty();
        assertThat(users.findByLoginId(P0DemoDataInitializer.ADMIN_LOGIN_ID)).isEmpty();
        assertThat(courses.findByCourseCode(P0DemoDataInitializer.COURSE_CODE)).isEmpty();
    }
}
