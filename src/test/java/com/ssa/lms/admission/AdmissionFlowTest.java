package com.ssa.lms.admission;

import com.ssa.lms.admission.entity.*;
import com.ssa.lms.admission.repository.ConsultationRequestRepository;
import com.ssa.lms.admission.repository.CourseApplicationRepository;
import com.ssa.lms.course.entity.*;
import com.ssa.lms.course.repository.CoursePublicationRepository;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class AdmissionFlowTest {

    @Autowired MockMvc mvc;
    @Autowired CourseRepository courseRepository;
    @Autowired CoursePublicationRepository publicationRepository;
    @Autowired CourseApplicationRepository applicationRepository;
    @Autowired ConsultationRequestRepository consultationRepository;
    @Autowired UserRepository userRepository;

    @Test
    void 공개_과정_신청을_서버에_접수한다() throws Exception {
        Course course = publishedCourse("ADMISSION-APP", RecruitmentStatus.RECRUITING, true);

        mvc.perform(post("/v2/api/public/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(course.getId(), true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNumber").value(org.hamcrest.Matchers.startsWith("AXI-APP-")))
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        CourseApplication saved = applicationRepository.findAllByOrderBySubmittedAtDesc().get(0);
        assertThat(saved.getCourse().getId()).isEqualTo(course.getId());
        assertThat(saved.getApplicantName()).isEqualTo("홍길동");
        assertThat(saved.getPrivacyConsentVersion()).isEqualTo("PRIVACY-2026-08-V1");
        assertThat(saved.getEmail()).isEqualTo("applicant@example.com");
    }

    @Test
    void 공개_사전상담을_일반상담과_과정연결상담으로_접수한다() throws Exception {
        Course course = publishedCourse("ADMISSION-CNS", RecruitmentStatus.PRE_CONSULTATION, true);

        mvc.perform(get("/v2/api/public/consultations/courses/" + course.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseName").value("ADMISSION-CNS 과정"));

        mvc.perform(post("/v2/api/public/consultations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultationJson(course.getId(), true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNumber").value(org.hamcrest.Matchers.startsWith("AXI-CNS-")));
        mvc.perform(post("/v2/api/public/consultations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultationJson(null, true)))
                .andExpect(status().isCreated());

        assertThat(consultationRepository.findAllByOrderBySubmittedAtDesc()).hasSize(2);
    }

    @Test
    void 개인정보_미동의와_잘못된_과정은_거절한다() throws Exception {
        Course course = publishedCourse("ADMISSION-CONSENT", RecruitmentStatus.RECRUITING, true);

        mvc.perform(post("/v2/api/public/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(course.getId(), false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/v2/api/public/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(999999L, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUBMISSION_REJECTED"));
    }

    @Test
    void 모집마감과_미공개_과정은_지원을_거절한다() throws Exception {
        Course closed = publishedCourse("ADMISSION-CLOSED", RecruitmentStatus.CLOSED, true);
        Course hidden = publishedCourse("ADMISSION-HIDDEN", RecruitmentStatus.RECRUITING, false);

        mvc.perform(post("/v2/api/public/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(closed.getId(), true)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v2/api/public/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(hidden.getId(), true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_목록과_상세를_보고_신청_상태를_변경한다() throws Exception {
        Course course = publishedCourse("ADMISSION-ADMIN-APP", RecruitmentStatus.RECRUITING, true);
        mvc.perform(post("/v2/api/public/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(course.getId(), true)))
                .andExpect(status().isCreated());
        CourseApplication application = applicationRepository.findAllByOrderBySubmittedAtDesc().get(0);

        mvc.perform(get("/admin/admissions/applications"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("과정 지원자")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("홍길동")));
        mvc.perform(get("/admin/admissions/applications/" + application.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(application.getReceiptNumber())));
        mvc.perform(post("/admin/admissions/applications/" + application.getId()).with(csrf())
                        .param("status", "REVIEWING")
                        .param("processingNote", "서류 검토 시작")
                        .param("followUpDate", "2026-09-10"))
                .andExpect(status().is3xxRedirection());

        assertThat(applicationRepository.findById(application.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.REVIEWING);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_상담_상태와_담당자를_변경한다() throws Exception {
        Course course = publishedCourse("ADMISSION-ADMIN-CNS", RecruitmentStatus.RECRUITING, true);
        mvc.perform(post("/v2/api/public/consultations").contentType(MediaType.APPLICATION_JSON)
                        .content(consultationJson(course.getId(), true)))
                .andExpect(status().isCreated());
        ConsultationRequest consultation = consultationRepository.findAllByOrderBySubmittedAtDesc().get(0);
        Long adminId = userRepository.findByLoginId("admin").orElseThrow().getId();

        mvc.perform(get("/admin/admissions/consultations"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("사전상담")));
        mvc.perform(get("/admin/admissions/consultations/" + consultation.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(consultation.getReceiptNumber())));
        mvc.perform(post("/admin/admissions/consultations/" + consultation.getId()).with(csrf())
                        .param("status", "ASSIGNED")
                        .param("assigneeId", adminId.toString())
                        .param("processingNote", "전화 상담 배정"))
                .andExpect(status().is3xxRedirection());

        ConsultationRequest updated = consultationRepository.findById(consultation.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ConsultationStatus.ASSIGNED);
        assertThat(updated.getAssignedTo().getId()).isEqualTo(adminId);
    }

    @Test
    void 동일_과정의_중복_연락처를_관리자_확인_대상으로_표시한다() throws Exception {
        Course course = publishedCourse("ADMISSION-DUP", RecruitmentStatus.RECRUITING, true);
        mvc.perform(post("/v2/api/public/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(course.getId(), true))).andExpect(status().isCreated());
        mvc.perform(post("/v2/api/public/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(course.getId(), true))).andExpect(status().isCreated());

        assertThat(applicationRepository.findAllByOrderBySubmittedAtDesc().get(0).isDuplicateCandidate()).isTrue();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void 강사는_지원자_개인정보_관리화면에_접근할_수_없다() throws Exception {
        mvc.perform(get("/admin/admissions/applications")).andExpect(status().isForbidden());
    }

    private Course publishedCourse(String code, RecruitmentStatus status, boolean visible) {
        Course course = courseRepository.save(Course.builder()
                .courseCode(code).courseName(code + " 과정").cohort("1기").category("AI")
                .description("과정 설명").startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2027, 2, 28)).capacity(20)
                .status(status == RecruitmentStatus.RECRUITING ? CourseStatus.RECRUITING : CourseStatus.DRAFT)
                .completionProgressRate(80).build());
        publicationRepository.save(CoursePublication.builder()
                .course(course).oneLineIntroduction("공개 과정")
                .audience("교육 대상").recruitmentStatus(status)
                .recruitmentStartDate(LocalDate.of(2026, 8, 1))
                .applicationDeadline(LocalDate.of(2026, 9, 20))
                .educationTime("평일 주간").educationMethod("오프라인")
                .tuitionFee(0L).selfPayment(0L).governmentSupport(0L).additionalCost(0L)
                .publicVisible(visible).publicationSite(PublicationSite.CLASS)
                .publicCategory(PublicCourseCategory.KDT).requiredDocuments("지원서")
                .build());
        return course;
    }

    private String applicationJson(Long courseId, boolean privacy) {
        return """
                {"courseId":%d,"name":"홍길동","birth":"1995-01-01",
                 "email":"applicant@example.com","phone":"010-1234-5678",
                 "employment":"구직 중","job":"데이터 분석가","motivation":"AI 실무 역량을 쌓고 싶습니다.",
                 "career":"관련 프로젝트 경험","skills":"Python, SQL","card":"보유","dorm":"필요",
                 "privacy":%s,"truth":true}
                """.formatted(courseId, privacy);
    }

    private String consultationJson(Long courseId, boolean privacy) {
        String id = courseId == null ? "null" : courseId.toString();
        return """
                {"courseId":%s,"name":"김상담","email":"counsel@example.com","phone":"010-9876-5432",
                 "type":"과정 선택","date":"2026-09-15","time":"14:00–16:00","contact":"전화",
                 "dorm":"미정","message":"과정이 궁금합니다.","privacy":%s}
                """.formatted(id, privacy);
    }
}
