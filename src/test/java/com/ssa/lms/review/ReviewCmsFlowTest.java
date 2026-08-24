package com.ssa.lms.review;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.CourseStatus;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.organization.entity.OrganizationRelationshipType;
import com.ssa.lms.organization.entity.OrganizationStatus;
import com.ssa.lms.organization.entity.OrganizationType;
import com.ssa.lms.organization.entity.PartnerOrganization;
import com.ssa.lms.organization.repository.PartnerOrganizationRepository;
import com.ssa.lms.review.entity.*;
import com.ssa.lms.review.repository.StudentReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class ReviewCmsFlowTest {

    @Autowired MockMvc mvc;
    @Autowired StudentReviewRepository reviewRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired PartnerOrganizationRepository organizationRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_과정과_취업기업_공개동의를_포함해_후기를_등록한다() throws Exception {
        Course course = saveCourse("REVIEW-CREATE");
        PartnerOrganization organization = saveOrganization("리뷰 채용 기업");

        mvc.perform(post("/admin/reviews").with(csrf())
                        .param("title", "비전공에서 데이터 분석가로")
                        .param("content", "프로젝트 경험이 면접의 중심이 됐습니다.")
                        .param("authorDisplayName", "김지윤")
                        .param("authorDisplayType", "MASKED")
                        .param("contentType", "EMPLOYMENT_SUCCESS")
                        .param("courseId", course.getId().toString())
                        .param("cohortSnapshot", "3기")
                        .param("completionYear", "2026")
                        .param("employmentOrganizationId", organization.getId().toString())
                        .param("jobTitle", "데이터 분석가")
                        .param("employed", "true")
                        .param("preTrainingSituation", "비전공 취업 준비생")
                        .param("courseExperience", "데이터 분석 실습")
                        .param("projectExperience", "고객 이탈 예측")
                        .param("employmentJourney", "포트폴리오와 면접")
                        .param("currentRoleDetail", "지표 분석과 대시보드 운영")
                        .param("imageUrl", "https://cdn.example.com/review.jpg")
                        .param("videoUrl", "https://video.example.com/interview")
                        .param("publicVisible", "true")
                        .param("exposureSites", "MAIN", "CAMPUS", "CLASS")
                        .param("exposurePositions", "HOMEPAGE_FEATURED", "CAMPUS_REVIEWS", "COURSE_DETAIL")
                        .param("displayOrder", "2")
                        .param("featured", "true")
                        .param("homepagePublicationConsent", "true")
                        .param("imagePublicationConsent", "true")
                        .param("employmentPublicationConsent", "true")
                        .param("videoPublicationConsent", "true")
                        .param("internalNote", "공개되면 안 되는 메모")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/reviews/*"));

        StudentReview saved = reviewRepository.findAllByOrderByFeaturedDescDisplayOrderAscIdDesc().stream()
                .filter(item -> item.getTitle().equals("비전공에서 데이터 분석가로")).findFirst().orElseThrow();
        assertThat(saved.getCourse().getId()).isEqualTo(course.getId());
        assertThat(saved.getEmploymentOrganization().getId()).isEqualTo(organization.getId());
        assertThat(saved.getPublicationConsentRecordedAt()).isNotNull();

        mvc.perform(get("/v2/api/reviews").param("site", "CLASS")
                        .param("position", "COURSE_DETAIL").param("courseId", course.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].authorDisplayName").value("김○윤"))
                .andExpect(jsonPath("$[0].employmentCompany").value("리뷰 채용 기업"))
                .andExpect(jsonPath("$[0].jobTitle").value("데이터 분석가"))
                .andExpect(jsonPath("$[0].imageUrl").value("https://cdn.example.com/review.jpg"))
                .andExpect(jsonPath("$[0].videoUrl").value("https://video.example.com/interview"))
                .andExpect(content().string(not(containsString("공개되면 안 되는 메모"))));

        mvc.perform(post("/admin/reviews/" + saved.getId()).with(csrf())
                        .param("title", "수정된 수료생 후기")
                        .param("content", "관리자 수정 내용")
                        .param("authorDisplayName", "김지윤")
                        .param("authorDisplayType", "ANONYMOUS")
                        .param("contentType", "INTERVIEW")
                        .param("courseId", course.getId().toString())
                        .param("completionYear", "2026")
                        .param("displayOrder", "1")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/reviews/" + saved.getId()));
        StudentReview updated = reviewRepository.findOneById(saved.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("수정된 수료생 후기");
        assertThat(updated.isPublicVisible()).isFalse();
    }

    @Test
    void 공개_API는_공개동의와_사이트_위치_정렬을_적용한다() throws Exception {
        saveReview("두 번째 공개 후기", 2, true, true, true,
                Set.of(ReviewExposureSite.CAMPUS), Set.of(ReviewExposurePosition.CAMPUS_REVIEWS));
        saveReview("첫 번째 공개 후기", 1, true, true, true,
                Set.of(ReviewExposureSite.CAMPUS), Set.of(ReviewExposurePosition.CAMPUS_REVIEWS));
        saveReview("홈페이지 동의 없는 후기", 0, true, false, true,
                Set.of(ReviewExposureSite.CAMPUS), Set.of(ReviewExposurePosition.CAMPUS_REVIEWS));
        saveReview("관리자 비공개 후기", 0, false, true, true,
                Set.of(ReviewExposureSite.CAMPUS), Set.of(ReviewExposurePosition.CAMPUS_REVIEWS));
        saveReview("다른 사이트 후기", 0, true, true, true,
                Set.of(ReviewExposureSite.MAIN), Set.of(ReviewExposurePosition.HOMEPAGE_FEATURED));

        mvc.perform(get("/v2/api/reviews").param("site", "CAMPUS").param("position", "CAMPUS_REVIEWS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("첫 번째 공개 후기"))
                .andExpect(jsonPath("$[1].title").value("두 번째 공개 후기"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 조건에_맞는_공개후기가_없으면_빈배열을_반환한다() throws Exception {
        mvc.perform(get("/v2/api/reviews").param("courseId", "999999"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void 사진_취업_영상_개별동의가_없으면_해당정보를_공개하지_않는다() throws Exception {
        Course course = saveCourse("REVIEW-CONSENT");
        PartnerOrganization organization = saveOrganization("비공개 취업기업");
        StudentReview review = reviewRepository.save(StudentReview.builder()
                .title("본문만 공개하는 후기").content("공개 동의된 본문")
                .authorDisplayName("홍길동").authorDisplayType(ReviewAuthorDisplayType.ANONYMOUS)
                .contentType(ReviewContentType.VIDEO).course(course).completionYear(2026)
                .employmentOrganization(organization).jobTitle("비공개 직무").employed(true)
                .employmentJourney("비공개 취업 과정").currentRoleDetail("비공개 현재 직무 내용")
                .imageUrl("https://secret.example.com/photo.jpg")
                .videoUrl("https://secret.example.com/video")
                .publicVisible(true).exposureSites(Set.of(ReviewExposureSite.CAMPUS))
                .exposurePositions(Set.of(ReviewExposurePosition.CAMPUS_REVIEWS))
                .displayOrder(0).featured(false).homepagePublicationConsent(true)
                .imagePublicationConsent(false).employmentPublicationConsent(false)
                .videoPublicationConsent(false).publicationConsentRecordedAt(LocalDateTime.now())
                .internalNote("민감한 내부 메모").status(ReviewStatus.ACTIVE).build());

        mvc.perform(get("/v2/api/reviews/" + review.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorDisplayName").value("익명"))
                .andExpect(jsonPath("$.employed").value(false))
                .andExpect(jsonPath("$.employmentCompany").doesNotExist())
                .andExpect(jsonPath("$.jobTitle").doesNotExist())
                .andExpect(jsonPath("$.employmentJourney").doesNotExist())
                .andExpect(jsonPath("$.currentRoleDetail").doesNotExist())
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.videoUrl").doesNotExist())
                .andExpect(content().string(not(containsString("민감한 내부 메모"))))
                .andExpect(content().string(not(containsString("비공개 취업기업"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자_목록_등록_상세_수정화면이_렌더링된다() throws Exception {
        StudentReview review = saveReview("관리 화면 후기", 0, false, false, true,
                Set.of(), Set.of());
        mvc.perform(get("/admin/reviews")).andExpect(status().isOk())
                .andExpect(content().string(containsString("후기 관리")));
        mvc.perform(get("/admin/reviews/new")).andExpect(status().isOk())
                .andExpect(content().string(containsString("후기 등록")));
        mvc.perform(get("/admin/reviews/" + review.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("관리 화면 후기")));
        mvc.perform(get("/admin/reviews/" + review.getId() + "/edit")).andExpect(status().isOk())
                .andExpect(content().string(containsString("후기 수정")));
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void 강사는_후기_CMS를_조회하거나_수정할_수_없다() throws Exception {
        mvc.perform(get("/admin/reviews")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/reviews").with(csrf())
                        .param("title", "강사 등록 시도").param("content", "차단 대상")
                        .param("authorDisplayName", "강사").param("authorDisplayType", "ANONYMOUS")
                        .param("contentType", "TEXT").param("displayOrder", "0").param("status", "ACTIVE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 공개화면은_고정후기대신_CMS_데이터영역을_사용한다() throws Exception {
        mvc.perform(get("/v2/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-review-site=\"MAIN\"")))
                .andExpect(content().string(not(containsString("김지윤 수료생"))));
        mvc.perform(get("/v2/site/campus/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-review-position=\"CAMPUS_REVIEWS\"")));
        mvc.perform(get("/v2/site/campus/reviews.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-review-filter-company")));
        mvc.perform(get("/v2/site/campus/review-detail.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-review-detail")));
        mvc.perform(get("/v2/site/class/course.html").param("courseId", "1")).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-review-course-from-query=\"true\"")));
    }

    private Course saveCourse(String code) {
        return courseRepository.save(Course.builder()
                .courseCode(code).courseName(code + " 과정").cohort("1기").category("AI")
                .description("과정 설명").startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2027, 2, 28)).capacity(20).status(CourseStatus.RECRUITING)
                .completionProgressRate(80).build());
    }

    private PartnerOrganization saveOrganization(String name) {
        return organizationRepository.save(PartnerOrganization.builder()
                .name(name).normalizedName(name.toLowerCase()).type(OrganizationType.COMPANY)
                .oneLineDescription("후기 연결 기업").relationshipTypes(Set.of(OrganizationRelationshipType.RECRUITMENT))
                .homepageExposure(false).exposureSites(Set.of()).exposurePositions(Set.of())
                .displayOrder(0).status(OrganizationStatus.ACTIVE).build());
    }

    private StudentReview saveReview(String title, int order, boolean visible, boolean consent,
                                     boolean active, Set<ReviewExposureSite> sites,
                                     Set<ReviewExposurePosition> positions) {
        return reviewRepository.save(StudentReview.builder()
                .title(title).content(title + " 본문").authorDisplayName("테스트수료생")
                .authorDisplayType(ReviewAuthorDisplayType.MASKED).contentType(ReviewContentType.TEXT)
                .publicVisible(visible).exposureSites(sites).exposurePositions(positions)
                .displayOrder(order).featured(false).homepagePublicationConsent(consent)
                .imagePublicationConsent(false).employmentPublicationConsent(false)
                .videoPublicationConsent(false).publicationConsentRecordedAt(consent ? LocalDateTime.now() : null)
                .internalNote("내부 메모").status(active ? ReviewStatus.ACTIVE : ReviewStatus.INACTIVE).build());
    }
}
