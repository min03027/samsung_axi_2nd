package com.ssa.lms.course;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.CourseStatus;
import com.ssa.lms.course.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 관리자 과정 CRUD 세로 슬라이스: 등록(코드 중복/기간 검증) → 목록/상세 렌더링 → 수정 → 상태변경 → 삭제.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CourseAdminFlowTest {

    @Autowired MockMvc mvc;
    @Autowired CourseRepository courseRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void 과정_목록과_등록폼이_렌더링된다() throws Exception {
        mvc.perform(get("/admin/courses")).andExpect(status().isOk())
                .andExpect(view().name("admin/admin-03-courses/admin-courses-edu"))
                .andExpect(content().string(containsString("/static/icons/add.svg")))
                .andExpect(content().string(not(containsString("/static/icons//add.svg"))));
        mvc.perform(get("/admin/courses/new")).andExpect(status().isOk())
                .andExpect(view().name("admin/admin-03-courses/admin-courses-edu-add"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 과정을_등록하면_상세로_리다이렉트되고_저장된다() throws Exception {
        mvc.perform(post("/admin/courses").with(csrf())
                        .param("courseCode", "COURSE-2026-TEST")
                        .param("courseName", "테스트 과정")
                        .param("cohort", "2기")
                        .param("category", "AI")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-08-31")
                        .param("capacity", "25")
                        .param("status", "DRAFT")
                        .param("completionProgressRate", "80"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/courses/*"));

        Course c = courseRepository.findByCourseCode("COURSE-2026-TEST").orElseThrow();
        assertThat(c.getCourseName()).isEqualTo("테스트 과정");
        assertThat(c.getStatus()).isEqualTo(CourseStatus.DRAFT);

        // 상세 렌더링
        mvc.perform(get("/admin/courses/" + c.getId())).andExpect(status().isOk())
                .andExpect(view().name("admin/admin-03-courses/courses-detail"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 종료일이_시작일보다_앞서면_검증오류로_폼이_다시_렌더링된다() throws Exception {
        mvc.perform(post("/admin/courses").with(csrf())
                        .param("courseCode", "COURSE-2026-BAD")
                        .param("courseName", "기간오류 과정")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-03-01")
                        .param("capacity", "10")
                        .param("status", "DRAFT")
                        .param("completionProgressRate", "80"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/admin-03-courses/admin-courses-edu-add"))
                .andExpect(model().attributeHasFieldErrors("courseForm", "periodValid"));

        assertThat(courseRepository.findByCourseCode("COURSE-2026-BAD")).isEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 중복_과정코드로_등록시_반려된다() throws Exception {
        // 시드 과정 COURSE-2026-001 과 중복
        mvc.perform(post("/admin/courses").with(csrf())
                        .param("courseCode", "COURSE-2026-001")
                        .param("courseName", "중복 과정")
                        .param("startDate", "2026-03-01")
                        .param("endDate", "2026-08-31")
                        .param("capacity", "10")
                        .param("status", "DRAFT")
                        .param("completionProgressRate", "80"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("courseForm", "courseCode"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 과정을_수정하고_상태를_변경한다() throws Exception {
        Long id = courseRepository.save(Course.builder()
                .courseCode("COURSE-2026-EDIT").courseName("수정전").cohort("1기")
                .startDate(java.time.LocalDate.of(2026, 1, 1)).endDate(java.time.LocalDate.of(2026, 6, 1))
                .capacity(20).status(CourseStatus.DRAFT).completionProgressRate(80).build()).getId();

        mvc.perform(post("/admin/courses/" + id).with(csrf())
                        .param("courseCode", "COURSE-2026-EDIT")
                        .param("courseName", "수정후")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-06-01")
                        .param("capacity", "40")
                        .param("status", "RECRUITING")
                        .param("completionProgressRate", "70"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/courses/" + id));

        Course updated = courseRepository.findById(id).orElseThrow();
        assertThat(updated.getCourseName()).isEqualTo("수정후");
        assertThat(updated.getCapacity()).isEqualTo(40);

        mvc.perform(post("/admin/courses/" + id + "/status").with(csrf())
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection());
        assertThat(courseRepository.findById(id).orElseThrow().getStatus()).isEqualTo(CourseStatus.IN_PROGRESS);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 과정을_삭제하면_soft_delete_되어_목록에서_사라진다() throws Exception {
        Long id = courseRepository.save(Course.builder()
                .courseCode("COURSE-2026-DEL").courseName("삭제대상")
                .startDate(java.time.LocalDate.of(2026, 1, 1)).endDate(java.time.LocalDate.of(2026, 6, 1))
                .capacity(20).status(CourseStatus.DRAFT).completionProgressRate(80).build()).getId();

        mvc.perform(post("/admin/courses/" + id + "/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/courses"));

        assertThat(courseRepository.findById(id)).isEmpty();  // @SQLRestriction 로 조회 제외
    }
}
