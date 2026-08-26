package com.ssa.lms.config;

import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:p0-prod-safety;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "lms.admin.init-password=test-admin-only",
        "lms.demo.seed-data=true",
        "lms.demo.password=test-only-password"
})
@ActiveProfiles("prod")
class P0DemoDataProductionSafetyIntegrationTest {

    @Autowired ApplicationContext context;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;

    @Test
    void productionProfileCannotRunDemoSeederEvenWhenFlagIsTrue() {
        assertThat(context.getBeansOfType(P0DemoDataInitializer.class)).isEmpty();
        assertThat(users.findByLoginId(P0DemoDataInitializer.ADMIN_LOGIN_ID)).isEmpty();
        assertThat(courses.findByCourseCode(P0DemoDataInitializer.COURSE_CODE)).isEmpty();
    }
}
