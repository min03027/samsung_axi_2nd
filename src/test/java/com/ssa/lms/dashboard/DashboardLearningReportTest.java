package com.ssa.lms.dashboard;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.service.CourseQueryService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DashboardLearningReportTest {

    @Autowired MockMvc mvc;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseQueryService courseQueryService;
    @Autowired com.ssa.lms.user.repository.UserRepository userRepository;

    @Test
    @DisplayName("관리자는 전체 분반 실제 대시보드 지표를 xlsx로 내려받는다")
    @WithUserDetails("admin")
    void adminReport() throws Exception {
        byte[] body = mvc.perform(get("/admin/dashboard/learning-report.xlsx"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).startsWith((byte) 'P', (byte) 'K');
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            var sheet = workbook.getSheet("분반별 학습 현황");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("과정·분반");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("평균 진도율(%)");
        }
    }

    @Test
    @DisplayName("강사는 담당하지 않는 분반을 보고서 파라미터로 조회할 수 없다")
    @WithUserDetails("instructor1")
    void instructorScope() throws Exception {
        Long instructorId = userRepository.findByLoginId("instructor1").orElseThrow().getId();
        Course foreign = courseRepository.findAll().stream()
                .filter(course -> !courseQueryService.isInstructorOf(instructorId, course.getId()))
                .findFirst().orElseThrow();

        mvc.perform(get("/instructor/dashboard/learning-report.xlsx")
                        .param("courseId", foreign.getId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("훈련생은 관리자·강사 대시보드 보고서를 내려받을 수 없다")
    @WithUserDetails("trainee1")
    void traineeForbidden() throws Exception {
        mvc.perform(get("/admin/dashboard/learning-report.xlsx"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/instructor/dashboard/learning-report.xlsx"))
                .andExpect(status().isForbidden());
    }
}
