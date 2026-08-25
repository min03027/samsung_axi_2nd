package com.ssa.lms.web;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.content.request.ContentRequestForm;
import com.ssa.lms.content.request.ContentRequestService;
import com.ssa.lms.course.entity.EnrollmentStatus;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.EnrollmentRepository;
import com.ssa.lms.course.repository.SubjectRepository;
import com.ssa.lms.course.template.CourseTemplateForm;
import com.ssa.lms.course.template.CourseTemplateService;
import com.ssa.lms.demand.DemandSignalForm;
import com.ssa.lms.demand.DemandSignalService;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class LxpContentOperationsRenderTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired SubjectRepository subjectRepository;
    @Autowired ContentRequestService requestService;
    @Autowired CourseTemplateService templateService;
    @Autowired DemandSignalService demandService;

    @Test
    @WithUserDetails("trainee1")
    @DisplayName("훈련생 콘텐츠 요청 화면을 렌더링한다")
    void traineeRequestPage() throws Exception {
        mvc.perform(get("/trainee/content-requests"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("학습에 필요한 콘텐츠를 요청하세요")));
    }

    @Test
    @WithUserDetails("instructor1")
    @DisplayName("담당자는 요청 목록과 요청 상세를 렌더링한다")
    void staffRequestPages() throws Exception {
        var trainee = userRepository.findByLoginId("trainee1").orElseThrow();
        var enrollment = enrollmentRepository.findByTraineeIdOrderByAppliedAtDesc(trainee.getId()).stream()
                .filter(row -> row.getStatus() == EnrollmentStatus.APPROVED || row.getStatus() == EnrollmentStatus.COMPLETED)
                .findFirst().orElseThrow();
        ContentRequestForm form = new ContentRequestForm();
        form.setCourseId(enrollment.getCourse().getId()); form.setTitle("추가 자료"); form.setReason("보충 학습이 필요합니다.");
        Long id = requestService.create(trainee.getId(), form);

        mvc.perform(get("/instructor/content-requests")).andExpect(status().isOk()).andExpect(content().string(containsString("추가 콘텐츠 요청")));
        mvc.perform(get("/instructor/content-requests/{id}", id)).andExpect(status().isOk()).andExpect(content().string(containsString("라이브러리에서 제공")));
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("과정 템플릿과 산업수요 추천 화면을 렌더링한다")
    void templateAndDemandPages() throws Exception {
        var admin = new LoginUser(userRepository.findByLoginId("admin").orElseThrow());
        var source = courseRepository.findAllByOrderByStartDateDesc().stream()
                .filter(course -> !subjectRepository.findByCourseIdOrderByOrderNo(course.getId()).isEmpty()).findFirst().orElseThrow();
        CourseTemplateForm template = new CourseTemplateForm();
        template.setSourceCourseId(source.getId()); template.setName("렌더링 템플릿"); template.setChangeSummary("최초 저장");
        Long templateId = templateService.create(template, admin);
        DemandSignalForm demand = new DemandSignalForm();
        demand.setTitle("AI 실무 수요"); demand.setIndustry("IT"); demand.setJobRole("개발"); demand.setSkills("Spring, AWS");
        demand.setDemandScore(80); demand.setObservedOn(LocalDate.now());
        Long signalId = demandService.create(demand);

        mvc.perform(get("/instructor/course-templates")).andExpect(status().isOk()).andExpect(content().string(containsString("과정 템플릿")));
        mvc.perform(get("/instructor/course-templates/{id}", templateId)).andExpect(status().isOk()).andExpect(content().string(containsString("버전 이력")));
        mvc.perform(get("/instructor/demand-signals")).andExpect(status().isOk()).andExpect(content().string(containsString("산업수요와 과정 최신화")));
        mvc.perform(get("/instructor/demand-signals/{id}", signalId)).andExpect(status().isOk()).andExpect(content().string(containsString("과정 최신화 추천")));
    }
}
