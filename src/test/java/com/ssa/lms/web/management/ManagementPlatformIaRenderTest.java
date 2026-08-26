package com.ssa.lms.web.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ManagementPlatformIaRenderTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("관리자 기존 대시보드는 통합 IA와 관리자 전용 영역을 끝까지 렌더한다")
    @WithUserDetails("admin")
    void adminDashboardUsesUnifiedNavigation() throws Exception {
        String html = getHtml("/admin");

        assertThat(html)
                .contains("통합 관리자 플랫폼", "대시보드", "교육 운영", "학습·평가", "수강생 케어",
                        "소통·교육품질", "성과관리", "시스템", "관리자")
                .contains("href=\"/admin/care\"", "href=\"/admin/quality\"")
                .contains("</html>");
    }

    @Test
    @DisplayName("강사 기존 대시보드는 같은 IA 골격과 강사 업무 범위를 끝까지 렌더한다")
    @WithUserDetails("instructor1")
    void instructorDashboardUsesUnifiedNavigation() throws Exception {
        String html = getHtml("/instructor");

        assertThat(html)
                .contains("통합 관리자 플랫폼", "대시보드", "교육 운영", "학습·평가", "수강생 케어",
                        "소통·교육품질", "강사")
                .contains("href=\"/instructor/care\"", "href=\"/instructor/quality\"")
                .doesNotContain("관리자·권한", "데이터·HRD", "설정·활동 로그")
                .contains("</html>");
    }

    @Test
    @DisplayName("기존 공용 튜터링 화면도 로그인 역할에 맞는 공통 내비게이션을 사용한다")
    @WithUserDetails("instructor1")
    void sharedSupportRouteUsesInstructorNavigation() throws Exception {
        String html = getHtml("/admin/support/tutoring");

        assertThat(html)
                .contains("통합 관리자 플랫폼", "강사", "수강생 케어")
                .doesNotContain("관리자·권한")
                .contains("</html>");
    }

    @Test
    @DisplayName("관리자 B단계 허브는 가짜 데이터나 저장 기능 없이 완전한 HTML로 렌더된다")
    @WithUserDetails("admin")
    void adminUiHubsRenderWithoutFakeRecords() throws Exception {
        String care = getHtml("/admin/care");
        String diary = getHtml("/admin/care/diary");
        String followUps = getHtml("/admin/care/follow-ups");
        String quality = getHtml("/admin/quality");

        assertThat(care).contains("케어 워크스페이스", "현재 연결된 관리 기능", "</html>");
        assertThat(diary).contains("학생 다이어리", "기록 타임라인", "</html>");
        assertThat(followUps).contains("상담·후속조치", "상담·조치 보드", "</html>");
        assertThat(quality).contains("교육품질", "현재 연결된 품질 관련 기능", "</html>");
        assertNoFakeData(care + diary + followUps + quality);
    }

    @Test
    @DisplayName("강사 B단계 허브는 담당 범위와 강사 내비게이션으로 렌더된다")
    @WithUserDetails("instructor1")
    void instructorUiHubsRenderWithinAssignedScope() throws Exception {
        String care = getHtml("/instructor/care");
        String diary = getHtml("/instructor/care/diary");
        String followUps = getHtml("/instructor/care/follow-ups");
        String quality = getHtml("/instructor/quality");

        assertThat(care + diary + followUps + quality)
                .contains("내 담당 과정·수강생", "강사", "</html>")
                .doesNotContain("관리자·권한");
        assertNoFakeData(care + diary + followUps + quality);
    }

    @Test
    @DisplayName("관리자 취업 현황과 과정 개선 보드가 렌더된다")
    @WithUserDetails("admin")
    void careerAndImprovementScreensRender() throws Exception {
        String career = getHtml("/admin/career");
        String improvements = getHtml("/admin/quality/improvements");

        assertThat(career).contains("취업 여정 현황", "후속조치 우선 목록", "</html>");
        assertThat(improvements).contains("개선 과제 등록", "과정 개선 진행판", "개선 전·후 성과 비교", "</html>");
    }

    @Test
    @DisplayName("강사도 담당 범위의 과정 개선 보드를 확인한다")
    @WithUserDetails("instructor1")
    void instructorImprovementBoardRenders() throws Exception {
        String html = getHtml("/instructor/quality/improvements");
        assertThat(html).contains("내 담당 과정·수강생", "과정 개선 진행판", "</html>");
    }

    @Test
    @DisplayName("수강생은 관리자 케어 허브에 접근할 수 없다")
    @WithUserDetails("trainee1")
    void traineeCannotAccessAdminCareHub() throws Exception {
        mvc.perform(get("/admin/care"))
                .andExpect(status().isForbidden());
    }

    private void assertNoFakeData(String html) {
        assertThat(html)
                .doesNotContain("mock data", "Mock Data", "시간 미정", "샘플 학생", "테스트 학생", "가짜");
    }

    private String getHtml(String path) throws Exception {
        return mvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
