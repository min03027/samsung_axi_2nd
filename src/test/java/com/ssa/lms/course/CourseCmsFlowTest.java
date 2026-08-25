package com.ssa.lms.course;

import com.ssa.lms.course.entity.*;
import com.ssa.lms.course.repository.CourseInstructorRepository;
import com.ssa.lms.course.repository.CoursePublicationRepository;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class CourseCmsFlowTest {

    @Autowired MockMvc mvc;
    @Autowired CourseRepository courseRepository;
    @Autowired CoursePublicationRepository publicationRepository;
    @Autowired CourseInstructorRepository instructorRepository;
    @Autowired UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void 과정_등록과_수정에서_공개정보를_같은_과정에_저장한다() throws Exception {
        mvc.perform(post("/admin/courses").with(csrf())
                        .param("courseCode", "CMS-CREATE-01")
                        .param("courseName", "AI 실무 과정")
                        .param("cohort", "1기")
                        .param("category", "AI")
                        .param("description", "내부 운영 메모")
                        .param("oneLineIntroduction", "AI로 실제 업무 결과물을 만듭니다.")
                        .param("audience", "AI 실무를 시작하려는 수강생")
                        .param("prerequisites", "선수지식 없음")
                        .param("recruitmentStartDate", "2026-09-01")
                        .param("applicationDeadline", "2026-09-20")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2027-02-28")
                        .param("educationTime", "월-금 09:00-17:40")
                        .param("educationMethod", "오프라인 실습")
                        .param("capacity", "20")
                        .param("tuitionFee", "5000000")
                        .param("selfPayment", "300000")
                        .param("governmentSupport", "4700000")
                        .param("additionalCost", "0")
                        .param("publicationSite", "CLASS")
                        .param("publicCategory", "KDT")
                        .param("publicVisible", "true")
                        .param("status", "DRAFT")
                        .param("completionProgressRate", "80"))
                .andExpect(status().is3xxRedirection());

        Course course = courseRepository.findByCourseCode("CMS-CREATE-01").orElseThrow();
        CoursePublication publication = publicationRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(publication.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.PRE_CONSULTATION);
        assertThat(publication.getOneLineIntroduction()).contains("업무 결과물");
        assertThat(publication.isPublicVisible()).isTrue();

        mvc.perform(post("/admin/courses/" + course.getId()).with(csrf())
                        .param("courseCode", "CMS-CREATE-01")
                        .param("courseName", "AI 실무 과정 개정")
                        .param("cohort", "1기")
                        .param("category", "AI")
                        .param("oneLineIntroduction", "수정된 공개 한줄소개")
                        .param("audience", "수정된 교육 대상")
                        .param("recruitmentStartDate", "2026-09-01")
                        .param("applicationDeadline", "2026-09-20")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2027-02-28")
                        .param("educationTime", "월-금 09:00-17:40")
                        .param("educationMethod", "오프라인 실습")
                        .param("capacity", "22")
                        .param("tuitionFee", "5000000")
                        .param("selfPayment", "300000")
                        .param("governmentSupport", "4700000")
                        .param("additionalCost", "0")
                        .param("publicationSite", "CLASS")
                        .param("publicCategory", "KDT")
                        .param("status", "DRAFT")
                        .param("completionProgressRate", "80"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/courses/" + course.getId()));

        CoursePublication updated = publicationRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(updated.getOneLineIntroduction()).isEqualTo("수정된 공개 한줄소개");
        assertThat(courseRepository.findById(course.getId()).orElseThrow().getCapacity()).isEqualTo(22);
        assertThat(updated.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.PRE_CONSULTATION);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 필수정보가_부족하면_모집중_전환을_거절한다() throws Exception {
        Course course = saveCourse("CMS-NOT-READY", CourseStatus.DRAFT);
        publicationRepository.save(CoursePublication.builder()
                .course(course).publicationSite(PublicationSite.CLASS).build());

        mvc.perform(post("/admin/courses/" + course.getId() + "/publication/status").with(csrf())
                        .param("status", "RECRUITING"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("publicationError", "publicationMissing"));

        assertThat(publicationRepository.findByCourseId(course.getId()).orElseThrow().getRecruitmentStatus())
                .isEqualTo(RecruitmentStatus.PRE_CONSULTATION);
        assertThat(courseRepository.findById(course.getId()).orElseThrow().getStatus())
                .isEqualTo(CourseStatus.DRAFT);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 준비된_과정은_모집중으로_전환되고_홈페이지에_노출된다() throws Exception {
        Course course = saveCourse("CMS-READY", CourseStatus.DRAFT);
        savePublication(course, RecruitmentStatus.PRE_CONSULTATION, true, PublicationSite.CLASS);
        instructorRepository.save(CourseInstructor.builder()
                .course(course)
                .instructor(userRepository.findByLoginId("instructor1").orElseThrow())
                .primaryInstructor(true)
                .build());

        mvc.perform(post("/admin/courses/" + course.getId() + "/publication/status").with(csrf())
                        .param("status", "RECRUITING"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("publicationMessage", "모집중 상태로 변경했습니다."));

        assertThat(publicationRepository.findByCourseId(course.getId()).orElseThrow().getRecruitmentStatus())
                .isEqualTo(RecruitmentStatus.RECRUITING);
        assertThat(courseRepository.findById(course.getId()).orElseThrow().getStatus())
                .isEqualTo(CourseStatus.RECRUITING);

        mvc.perform(get("/v2/api/courses"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CMS-READY")));
        mvc.perform(get("/v2/api/courses/" + course.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseName").value("CMS-READY 과정"));
    }

    @Test
    void 비노출_모집중과_모집마감_과정은_공개_API에서_제외된다() throws Exception {
        Course visible = saveCourse("CMS-PUBLIC", CourseStatus.RECRUITING);
        savePublication(visible, RecruitmentStatus.RECRUITING, true, PublicationSite.CLASS);
        Course hidden = saveCourse("CMS-HIDDEN", CourseStatus.RECRUITING);
        savePublication(hidden, RecruitmentStatus.RECRUITING, false, PublicationSite.CLASS);
        Course closed = saveCourse("CMS-CLOSED", CourseStatus.RECRUITMENT_CLOSED);
        savePublication(closed, RecruitmentStatus.CLOSED, true, PublicationSite.CLASS);

        mvc.perform(get("/v2/api/courses"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CMS-PUBLIC")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("CMS-HIDDEN"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("CMS-CLOSED"))));
        mvc.perform(get("/v2/api/courses/" + hidden.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/v2/api/courses/" + closed.getId())).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 모집중_과정은_모집마감으로_순차_전환되고_신청대상에서_제외된다() throws Exception {
        Course course = saveCourse("CMS-CLOSE-FLOW", CourseStatus.RECRUITING);
        savePublication(course, RecruitmentStatus.RECRUITING, true, PublicationSite.CLASS);

        mvc.perform(post("/admin/courses/" + course.getId() + "/publication/status").with(csrf())
                        .param("status", "CLOSED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("publicationMessage", "모집마감 상태로 변경했습니다."));

        assertThat(publicationRepository.findByCourseId(course.getId()).orElseThrow().getRecruitmentStatus())
                .isEqualTo(RecruitmentStatus.CLOSED);
        assertThat(courseRepository.findById(course.getId()).orElseThrow().getStatus())
                .isEqualTo(CourseStatus.RECRUITMENT_CLOSED);
        mvc.perform(get("/v2/api/courses/" + course.getId())).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void 강사는_홈페이지_모집상태를_변경할_수_없다() throws Exception {
        Course course = saveCourse("CMS-INSTRUCTOR-DENIED", CourseStatus.DRAFT);
        savePublication(course, RecruitmentStatus.PRE_CONSULTATION, true, PublicationSite.CLASS);
        mvc.perform(post("/admin/courses/" + course.getId() + "/publication/status").with(csrf())
                        .param("status", "RECRUITING"))
                .andExpect(status().isForbidden());
    }

    private Course saveCourse(String code, CourseStatus status) {
        return courseRepository.save(Course.builder()
                .courseCode(code).courseName(code + " 과정").cohort("1기").category("AI")
                .description("내부 과정 설명")
                .startDate(LocalDate.of(2026, 10, 1)).endDate(LocalDate.of(2027, 2, 28))
                .capacity(20).status(status).completionProgressRate(80).build());
    }

    private CoursePublication savePublication(Course course, RecruitmentStatus status,
                                               boolean visible, PublicationSite site) {
        return publicationRepository.save(CoursePublication.builder()
                .course(course)
                .oneLineIntroduction("공개 한줄소개")
                .audience("교육 대상")
                .prerequisites("선수지식 없음")
                .recruitmentStatus(status)
                .recruitmentStartDate(LocalDate.of(2026, 9, 1))
                .applicationDeadline(LocalDate.of(2026, 9, 20))
                .selectionProcess("지원서 → 상담 → 발표")
                .requiredDocuments("지원서")
                .educationTime("월-금 09:00-17:40")
                .educationMethod("오프라인 실습")
                .tuitionFee(5_000_000L).selfPayment(300_000L)
                .governmentSupport(4_700_000L).additionalCost(0L)
                .publicVisible(visible).publicationSite(site)
                .publicCategory(PublicCourseCategory.KDT)
                .displayOrder(1).featured(false)
                .build());
    }
}
