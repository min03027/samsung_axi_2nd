package com.ssa.lms.config;

import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "lms.demo.seed-data=true",
        "lms.demo.password=test-only-password"
})
@ActiveProfiles("demo")
@Import(P0DemoDataConflictIntegrationTest.ConflictConfig.class)
class P0DemoDataConflictIntegrationTest {

    @Autowired UserRepository users;
    @Autowired CourseRepository courses;

    @Test
    void existingKeyIsNeverOverwrittenAndWholeSeedIsSkipped() {
        var existing = users.findByLoginId(P0DemoDataInitializer.ADMIN_LOGIN_ID).orElseThrow();
        assertThat(existing.getName()).isEqualTo("기존 계정");
        assertThat(existing.getRole()).isEqualTo(Role.TRAINEE);
        assertThat(users.findByLoginId(P0DemoDataInitializer.INSTRUCTOR_LOGIN_ID)).isEmpty();
        assertThat(users.findByLoginId(P0DemoDataInitializer.TRAINEE_LOGIN_ID)).isEmpty();
        assertThat(courses.findByCourseCode(P0DemoDataInitializer.COURSE_CODE)).isEmpty();
    }

    @TestConfiguration
    static class ConflictConfig {
        @Bean
        @Order(0)
        CommandLineRunner existingDemoKey(UserRepository users, PasswordEncoder encoder) {
            return args -> users.save(User.builder()
                    .loginId(P0DemoDataInitializer.ADMIN_LOGIN_ID)
                    .password(encoder.encode("pre-existing"))
                    .name("기존 계정").role(Role.TRAINEE).status(UserStatus.ACTIVE)
                    .email("existing@example.invalid").build());
        }
    }
}
