package com.ssa.lms.course;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.CourseStatus;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.SubjectRepository;
import com.ssa.lms.course.template.CourseTemplateDeployForm;
import com.ssa.lms.course.template.CourseTemplateForm;
import com.ssa.lms.course.template.CourseTemplateService;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CourseTemplateServiceTest {

    @Autowired CourseTemplateService service;
    @Autowired CourseRepository courseRepository;
    @Autowired SubjectRepository subjectRepository;
    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("과정 전체 구조를 템플릿으로 저장하고 작성중 과정에 안전 적용한다")
    void createAndDeployToDraftCourse() {
        Course source = courseRepository.findAllByOrderByStartDateDesc().stream()
                .filter(course -> !subjectRepository.findByCourseIdOrderByOrderNo(course.getId()).isEmpty())
                .findFirst().orElseThrow();
        Course target = courseRepository.save(Course.builder()
                .courseCode("TEST-TEMPLATE-" + System.nanoTime()).courseName("템플릿 적용 대상")
                .cohort("테스트").category("AI").description("작성중 과정")
                .startDate(LocalDate.now().plusMonths(1)).endDate(LocalDate.now().plusMonths(3))
                .capacity(20).status(CourseStatus.DRAFT).build());
        LoginUser admin = new LoginUser(userRepository.findByLoginId("admin").orElseThrow());

        CourseTemplateForm form = new CourseTemplateForm();
        form.setSourceCourseId(source.getId());
        form.setName("표준 과정 구조");
        form.setDescription("과목·차시·콘텐츠 재사용");
        form.setChangeSummary("최초 저장");
        Long templateId = service.create(form, admin);

        CourseTemplateDeployForm deploy = new CourseTemplateDeployForm();
        deploy.setTargetCourseId(target.getId());
        deploy.setAutoSyncSafe(true);
        service.deploy(templateId, deploy, admin);

        assertThat(subjectRepository.findByCourseIdOrderByOrderNo(target.getId()))
                .hasSameSizeAs(subjectRepository.findByCourseIdOrderByOrderNo(source.getId()));
        assertThat(service.links(templateId, admin)).singleElement().satisfies(link -> {
            assertThat(link.courseId()).isEqualTo(target.getId());
            assertThat(link.autoSyncSafe()).isTrue();
            assertThat(link.appliedVersion()).isEqualTo(1);
        });
    }
}
