package com.ssa.lms.organization;

import com.ssa.lms.course.entity.*;
import com.ssa.lms.course.repository.CoursePublicationRepository;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.organization.entity.*;
import com.ssa.lms.organization.repository.CoursePartnerRepository;
import com.ssa.lms.organization.repository.PartnerOrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
class OrganizationCmsFlowTest {

    @Autowired MockMvc mvc;
    @Autowired PartnerOrganizationRepository organizationRepository;
    @Autowired CoursePartnerRepository coursePartnerRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CoursePublicationRepository publicationRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_관계유형과_과정연계를_포함해_기업기관을_등록한다() throws Exception {
        Course course = saveCourse("ORG-CREATE", CourseStatus.DRAFT);

        mvc.perform(post("/admin/organizations").with(csrf())
                        .param("name", "삼성 데이터 파트너")
                        .param("type", "COMPANY")
                        .param("oneLineDescription", "AI 프로젝트·채용 협력사")
                        .param("websiteUrl", "https://data-partner.example.com/about")
                        .param("relationshipTypes", "AGREEMENT", "EDUCATION", "RECRUITMENT")
                        .param("projectCourseIds", course.getId().toString())
                        .param("recruitmentCourseIds", course.getId().toString())
                        .param("homepageExposure", "true")
                        .param("exposureSites", "CAMPUS", "BIZ")
                        .param("exposurePositions", "PARTNER_ROLLING", "CLIENT_ROLLING")
                        .param("displayOrder", "2")
                        .param("internalNote", "계약 갱신일은 내부에서만 확인")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/organizations/*"));

        PartnerOrganization saved = organizationRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .filter(item -> item.getName().equals("삼성 데이터 파트너")).findFirst().orElseThrow();
        assertThat(saved.getRelationshipTypes()).containsExactlyInAnyOrder(
                OrganizationRelationshipType.AGREEMENT,
                OrganizationRelationshipType.EDUCATION,
                OrganizationRelationshipType.RECRUITMENT);
        assertThat(saved.getExposureSites()).containsExactlyInAnyOrder(
                OrganizationExposureSite.CAMPUS, OrganizationExposureSite.BIZ);
        CoursePartner link = coursePartnerRepository.findDetailedByOrganizationId(saved.getId()).get(0);
        assertThat(link.isProjectParticipant()).isTrue();
        assertThat(link.isRecruitmentLinked()).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_기관정보와_공개상태_정렬을_수정한다() throws Exception {
        PartnerOrganization organization = saveOrganization("수정 전 기관", "before.example.com",
                true, OrganizationStatus.ACTIVE, 9, OrganizationExposureSite.CAMPUS,
                OrganizationExposurePosition.PARTNER_ROLLING);

        mvc.perform(post("/admin/organizations/" + organization.getId()).with(csrf())
                        .param("name", "수정 후 기관")
                        .param("type", "PUBLIC_INSTITUTION")
                        .param("oneLineDescription", "수정된 설명")
                        .param("websiteUrl", "https://after.example.com")
                        .param("relationshipTypes", "PUBLIC_INSTITUTION", "AGREEMENT")
                        .param("displayOrder", "1")
                        .param("status", "INACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/organizations/" + organization.getId()));

        PartnerOrganization updated = organizationRepository.findOneById(organization.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("수정 후 기관");
        assertThat(updated.getStatus()).isEqualTo(OrganizationStatus.INACTIVE);
        assertThat(updated.isHomepageExposure()).isFalse();
        assertThat(updated.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void 공개_API는_공개조건과_사이트_위치를_적용하고_관리자순서로_반환한다() throws Exception {
        saveOrganization("두 번째 기관", "second.example.com", true, OrganizationStatus.ACTIVE, 2,
                OrganizationExposureSite.CAMPUS, OrganizationExposurePosition.PARTNER_ROLLING);
        saveOrganization("첫 번째 기관", "first.example.com", true, OrganizationStatus.ACTIVE, 1,
                OrganizationExposureSite.CAMPUS, OrganizationExposurePosition.PARTNER_ROLLING);
        saveOrganization("비공개 기관", "hidden.example.com", false, OrganizationStatus.ACTIVE, 0,
                OrganizationExposureSite.CAMPUS, OrganizationExposurePosition.PARTNER_ROLLING);
        saveOrganization("중지 기관", "inactive.example.com", true, OrganizationStatus.INACTIVE, 0,
                OrganizationExposureSite.CAMPUS, OrganizationExposurePosition.PARTNER_ROLLING);
        saveOrganization("기업교육 기관", "biz-only.example.com", true, OrganizationStatus.ACTIVE, 0,
                OrganizationExposureSite.BIZ, OrganizationExposurePosition.CLIENT_ROLLING);

        mvc.perform(get("/v2/api/organizations")
                        .param("site", "CAMPUS").param("position", "PARTNER_ROLLING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("첫 번째 기관"))
                .andExpect(jsonPath("$[1].name").value("두 번째 기관"))
                .andExpect(content().string(not(containsString("비공개 기관"))))
                .andExpect(content().string(not(containsString("중지 기관"))))
                .andExpect(content().string(not(containsString("기업교육 기관"))))
                .andExpect(content().string(not(containsString("internalNote"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 이름과_홈페이지도메인_중복등록을_차단한다() throws Exception {
        saveOrganization("중복 확인 기관", "duplicate.example.com", false,
                OrganizationStatus.ACTIVE, 0, OrganizationExposureSite.CAMPUS,
                OrganizationExposurePosition.PARTNER_ROLLING);

        mvc.perform(post("/admin/organizations").with(csrf())
                        .param("name", "  중복   확인 기관 ")
                        .param("type", "COMPANY").param("displayOrder", "0").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("같은 이름의 기업·기관이 이미 등록되어 있습니다.")));

        mvc.perform(post("/admin/organizations").with(csrf())
                        .param("name", "다른 이름")
                        .param("type", "COMPANY")
                        .param("websiteUrl", "https://www.duplicate.example.com/hello")
                        .param("displayOrder", "0").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("같은 홈페이지 도메인의 기업·기관이 이미 등록되어 있습니다.")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 과정_CMS에서_등록기관을_선택하면_공개과정에_기관명이_표시된다() throws Exception {
        Course course = saveCourse("ORG-COURSE", CourseStatus.RECRUITING);
        publicationRepository.save(CoursePublication.builder()
                .course(course).oneLineIntroduction("공개 과정").audience("교육 대상")
                .recruitmentStatus(RecruitmentStatus.RECRUITING)
                .recruitmentStartDate(LocalDate.of(2026, 8, 1))
                .applicationDeadline(LocalDate.of(2026, 9, 20))
                .educationTime("평일 주간").educationMethod("오프라인")
                .tuitionFee(0L).selfPayment(0L).governmentSupport(0L).additionalCost(0L)
                .publicVisible(true).publicationSite(PublicationSite.CLASS)
                .publicCategory(PublicCourseCategory.KDT).requiredDocuments("지원서")
                .projectPartners("기존 자유입력 회사").build());
        PartnerOrganization organization = saveOrganization("구조화 프로젝트사", "project.example.com",
                false, OrganizationStatus.ACTIVE, 1, OrganizationExposureSite.CLASS,
                OrganizationExposurePosition.COURSE_PROJECT);

        mvc.perform(post("/admin/courses/" + course.getId()).with(csrf())
                        .param("courseCode", course.getCourseCode())
                        .param("courseName", course.getCourseName())
                        .param("cohort", "1기").param("category", "AI")
                        .param("startDate", "2026-10-01").param("endDate", "2027-02-28")
                        .param("capacity", "20").param("status", "RECRUITING")
                        .param("completionProgressRate", "80")
                        .param("oneLineIntroduction", "공개 과정").param("audience", "교육 대상")
                        .param("recruitmentStartDate", "2026-08-01")
                        .param("applicationDeadline", "2026-09-20")
                        .param("educationTime", "평일 주간").param("educationMethod", "오프라인")
                        .param("tuitionFee", "0").param("selfPayment", "0")
                        .param("governmentSupport", "0").param("additionalCost", "0")
                        .param("requiredDocuments", "지원서")
                        .param("projectPartners", "기존 자유입력 회사")
                        .param("projectPartnerOrganizationIds", organization.getId().toString())
                        .param("publicVisible", "true").param("publicationSite", "CLASS")
                        .param("publicCategory", "KDT").param("displayOrder", "0"))
                .andExpect(status().is3xxRedirection());

        CoursePartner link = coursePartnerRepository.findDetailedByCourseId(course.getId()).get(0);
        assertThat(link.getOrganization().getId()).isEqualTo(organization.getId());
        assertThat(link.isProjectParticipant()).isTrue();
        mvc.perform(get("/v2/api/courses/" + course.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectPartners").value("구조화 프로젝트사"));
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void 강사는_기업기관_관리와_공개설정을_변경할_수_없다() throws Exception {
        mvc.perform(get("/admin/organizations")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/organizations").with(csrf())
                        .param("name", "강사 등록 시도").param("type", "COMPANY")
                        .param("displayOrder", "0").param("status", "ACTIVE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 홈페이지는_정적기업목록대신_공개_CMS_영역을_사용한다() throws Exception {
        mvc.perform(get("/v2/site/campus/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-public-organizations")))
                .andExpect(content().string(not(containsString("<span class=\"logorow__item\">고용노동부</span>"))));
        mvc.perform(get("/v2/site/biz/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-organization-site=\"BIZ\"")));
    }

    private Course saveCourse(String code, CourseStatus status) {
        return courseRepository.save(Course.builder()
                .courseCode(code).courseName(code + " 과정").cohort("1기").category("AI")
                .description("과정 설명").startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2027, 2, 28)).capacity(20).status(status)
                .completionProgressRate(80).build());
    }

    private PartnerOrganization saveOrganization(String name, String domain,
                                                   boolean exposed, OrganizationStatus status,
                                                   int displayOrder, OrganizationExposureSite site,
                                                   OrganizationExposurePosition position) {
        return organizationRepository.save(PartnerOrganization.builder()
                .name(name).normalizedName(name.trim().replaceAll("\\s+", " ").toLowerCase())
                .type(OrganizationType.COMPANY).oneLineDescription(name + " 설명")
                .websiteUrl("https://" + domain).websiteDomain(domain)
                .relationshipTypes(Set.of(OrganizationRelationshipType.AGREEMENT))
                .homepageExposure(exposed).exposureSites(Set.of(site))
                .exposurePositions(Set.of(position)).displayOrder(displayOrder)
                .internalNote("공개되면 안 되는 메모").status(status).build());
    }
}
