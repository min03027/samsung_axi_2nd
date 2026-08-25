package com.ssa.lms.web.trainee;

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
class TraineePlatformIaRenderTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("수강생 공통 헤더는 확정 IA와 실제 알림함 경로를 제공한다")
    @WithUserDetails("trainee1")
    void commonNavigationUsesFinalIa() throws Exception {
        String html = getHtml("/trainee");

        assertThat(html)
                .contains("홈", "내 학습", "과제·평가", "출결·이수", "AI 학습지원",
                        "성장·피드백", "취업·포트폴리오", "공지·알림")
                .contains("href=\"/trainee/alarm\"")
                .contains("class=\"trainee-nav-toggle\"")
                .doesNotContain("준비 중인 기능입니다.")
                .contains("</html>");
    }

    @Test
    @DisplayName("B등급 허브 세 화면은 가짜 수치 없이 완전한 HTML로 렌더된다")
    @WithUserDetails("trainee1")
    void uiOnlyHubsRenderWithoutFakeMetrics() throws Exception {
        String evaluations = getHtml("/trainee/evaluations");
        String growth = getHtml("/trainee/growth");
        String career = getHtml("/trainee/career");

        assertThat(evaluations)
                .contains("과제 확인하기", "시험 확인하기", "참여할 평가 없음", "</html>");
        assertThat(growth)
                .contains("나의 학습 기록", "도움과 피드백", "주간 성장 흐름", "</html>");
        assertThat(career)
                .contains("직무 로드맵", "포트폴리오", "나의 취업 여정", "</html>");

        assertThat(evaluations + growth + career)
                .doesNotContain("연결 준비 중", "시간 미정", "mock data", "준비 중입니다");
    }

    private String getHtml(String path) throws Exception {
        return mvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
