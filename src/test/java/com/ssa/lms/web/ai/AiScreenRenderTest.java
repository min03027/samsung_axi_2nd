package com.ssa.lms.web.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 화면(프론트엔드)이 끝까지 렌더되는지 고정한다.
 *
 * <p><b>status 200 만으로는 부족하다.</b> Thymeleaf 가 렌더 도중 예외를 만나면 응답 헤더는
 * 이미 나간 뒤라 200 인 채로 HTML 이 잘린다. 그래서 {@code </html>} 까지 왔는지 확인한다
 * (CLAUDE.md 규칙 3).</p>
 *
 * <p>대시보드 3종은 컨트롤러가 {@code @AuthenticationPrincipal LoginUser} 를 받으므로
 * {@code @WithMockUser} 로는 principal 이 null 이 된다 — 시드 계정을 쓰는
 * {@code @WithUserDetails} 가 필요하다.</p>
 *
 * <p>지금 이 화면들은 서버 데이터 없이 각자 JS 더미로 그리는 단계다. 그래도 fragment 호출
 * (사이드바·헤더)과 active 키가 틀리면 여기서 잡힌다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AiScreenRenderTest {

    @Autowired MockMvc mvc;

    private void assertFullyRendered(String url, String mustContain) throws Exception {
        MvcResult res = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn();
        String html = res.getResponse().getContentAsString();

        assertThat(html)
                .as("%s 렌더가 도중에 끊겼다 — 200 이어도 HTML 이 잘리면 화면이 깨진다", url)
                .contains("</html>");
        assertThat(html)
                .as("%s 에 화면 고유 요소가 없다", url)
                .contains(mustContain);
    }

    @Test
    @WithMockUser(username = "trainee1", roles = "TRAINEE")
    @DisplayName("훈련생 AI 화면 3종이 끝까지 렌더된다")
    void 훈련생_AI화면() throws Exception {
        assertFullyRendered("/trainee/ai/qna", "chatLog");
        assertFullyRendered("/trainee/ai/curriculum", "recommendList");
        assertFullyRendered("/trainee/ai/roadmap", "roadmapList");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @DisplayName("강사 AI 학습진단 화면이 끝까지 렌더된다")
    void 강사_AI진단화면() throws Exception {
        assertFullyRendered("/instructor/ai/diagnosis", "diagBody");
    }

    @Test
    @WithUserDetails("trainee1")
    @DisplayName("훈련생 대시보드에 AI 위젯 자리가 있다")
    void 훈련생_대시보드_위젯() throws Exception {
        // 수강생 홈 1차 UX 개편에서 예전 hpAiCards 묶음은 제거됐고,
        // 공통 플로팅 AI 학습 챗봇이 같은 진입 역할을 맡는다.
        assertFullyRendered("/trainee", "floatingChatbotWindow");
    }

    @Test
    @WithUserDetails("instructor1")
    @DisplayName("강사 대시보드에 AI 위젯 자리가 있다")
    void 강사_대시보드_위젯() throws Exception {
        assertFullyRendered("/instructor", "instAiCards");
    }

    @Test
    @WithUserDetails("admin")
    @DisplayName("관리자 대시보드에 AI 위젯 자리가 있다")
    void 관리자_대시보드_위젯() throws Exception {
        assertFullyRendered("/admin", "adminAiCards");
    }

    @Test
    @WithMockUser(username = "trainee1", roles = "TRAINEE")
    @DisplayName("훈련생은 강사 AI 진단 화면에 들어갈 수 없다")
    void 권한_경계() throws Exception {
        mvc.perform(get("/instructor/ai/diagnosis")).andExpect(status().isForbidden());
    }

    /**
     * AI 도우미 대화는 브라우저(localStorage)에 보관하고, 보관 키를 사용자 id 로 나눈다.
     * 그 id 는 이 화면이 심어주는 {@code window._meId} 하나에서 온다.
     *
     * <p><b>이 값이 빠지면 조용히 망가진다.</b> JS 는 키를 'anon' 으로 되돌리고, 그러면
     * 같은 PC 를 쓰는 모든 계정이 다시 같은 칸을 공유한다 — 화면은 멀쩡히 그려지고
     * 에러도 안 나므로 다음 사람이 남의 대화를 볼 때까지 아무도 모른다.
     * 실제로 그렇게 새고 있었다.</p>
     */
    @Test
    @WithUserDetails("trainee1")
    @DisplayName("AI 도우미 화면이 대화 보관 키용 사용자 id 를 내려준다")
    void 대화보관키_사용자별_분리() throws Exception {
        String html = mvc.perform(get("/trainee/ai/qna"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("window._meId 가 실제 id 로 렌더돼야 한다 — null/누락이면 계정끼리 대화가 섞인다")
                .containsPattern("window\\._meId\\s*=\\s*\\d+");
    }
}
