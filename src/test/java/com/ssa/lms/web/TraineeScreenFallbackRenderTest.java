package com.ssa.lms.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 훈련생 화면의 "빈 화면 / 흰 화면" 재발 방지 테스트.
 *
 * <p>실제로 났던 사고 두 가지를 고정한다:
 * <ol>
 *   <li><b>Whitelabel Error Page</b> — 예전 정적 화면 경로({@code /templates/...})가 링크에
 *       남아 있어 누르면 404 가 났고, {@code /error} 매핑이 없어 흰 화면이 떴다.</li>
 *   <li><b>가짜 데이터 노출</b> — 배정된 항목이 없을 때 실제 기록처럼 보이는 샘플이
 *       핵심 학습·출결·이수 화면에 노출되면 안 된다.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TraineeScreenFallbackRenderTest {

    @Autowired MockMvc mvc;

    private static final String[] TRAINEE_PAGES = {
            "/trainee/assignment", "/trainee/exam", "/trainee/attendance",
            "/trainee/completion-management", "/trainee/survey",
            "/trainee/contents", "/trainee/learning"
    };

    @Test
    @DisplayName("훈련생 화면 어디에도 옛 정적 경로(/templates/...) 링크가 남아 있지 않다")
    @WithUserDetails("trainee1")
    void noStaleStaticLinks() throws Exception {
        for (String page : TRAINEE_PAGES) {
            mvc.perform(get(page)).andExpect(status().isOk())
                    // 눌렀을 때 404 Whitelabel 로 가던 링크
                    .andExpect(content().string(not(containsString("href=\"/templates/"))))
                    // 응답이 잘리지 않았는지 (200 만으로는 부족 — CLAUDE.md 규칙 3)
                    .andExpect(content().string(containsString("</html>")));
        }
        mvc.perform(get("/trainee/alarm")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/templates/"))))
                .andExpect(content().string(containsString("</html>")));
    }

    @Test
    @DisplayName("배정된 항목이 없는 계정의 핵심 화면은 샘플 대신 실제 빈 상태를 본다")
    @WithUserDetails("admin")   // 시드 관리자에게는 수강/출결/이수 데이터가 없다
    void emptyAccountSeesSampleData() throws Exception {
        for (String page : new String[]{"/trainee/attendance", "/trainee/completion-management",
                "/trainee/contents", "/trainee/learning"}) {
            mvc.perform(get(page)).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("화면 예시용 샘플 데이터"))))
                    .andExpect(content().string(containsString("</html>")));
        }
        // 과제·시험·설문 샘플은 이번 핵심 LXP 감사 범위 밖의 기존 동작이다.
        for (String page : new String[]{"/trainee/assignment", "/trainee/exam", "/trainee/survey"}) {
            mvc.perform(get(page)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("화면 예시용 샘플 데이터")));
        }
        // 과제/시험/설문은 인라인 JS(JSON)로 내려간다 — Thymeleaf 가 한글을 \\uXXXX 로 이스케이프하므로
        // 한글로 단언하면 항상 실패한다. 이스케이프되지 않는 id 로 확인한다.
        mvc.perform(get("/trainee/assignment"))
                .andExpect(content().string(containsString("\"id\":\"900001\"")));
        mvc.perform(get("/trainee/exam"))
                .andExpect(content().string(containsString("\"id\":\"900101\"")));
        mvc.perform(get("/trainee/survey"))
                .andExpect(content().string(containsString("\"id\":\"900301\"")));
        // 핵심 화면은 실제 데이터가 없으므로 샘플 행·발급 버튼이 없어야 한다.
        mvc.perform(get("/trainee/attendance"))
                .andExpect(content().string(not(containsString("오리엔테이션 및 개발환경 구축"))));
        mvc.perform(get("/trainee/completion-management"))
                .andExpect(content().string(not(containsString("이수증 다운로드"))));
    }

    @Test
    @DisplayName("본인 데이터가 있으면 예시가 아니라 실제 데이터가 나온다")
    @WithUserDetails("trainee1")
    void realDataWinsOverSample() throws Exception {
        mvc.perform(get("/trainee/assignment")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("화면 예시용 샘플 데이터"))))
                .andExpect(content().string(not(containsString("\"id\":\"900001\""))));
        // 수강 중인 과정에 콘텐츠가 있는 계정은 학습 콘텐츠 목록도 실제 데이터로 나온다
        mvc.perform(get("/trainee/contents")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("화면 예시용 샘플 데이터"))));
    }

    /**
     * 학습 콘텐츠 재생 화면(TR-10)은 목록에서만 도달할 수 있어서, 목록이 예시일 때
     * 재생 화면도 함께 열려야 캡처가 된다. 예전에는 예시 id 로 들어가면 콘텐츠를 못 찾아
     * 오류 화면으로 떨어졌다.
     */
    @Test
    @DisplayName("학습 콘텐츠가 없으면 빈 상태이며 존재하지 않는 샘플 id는 열리지 않는다")
    @WithUserDetails("admin")   // 시드 관리자에게는 수강 과정이 없다
    void sampleContentPlaysRender() throws Exception {
        mvc.perform(get("/trainee/contents")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/trainee/contents/900703/play"))))
                .andExpect(content().string(containsString("</html>")));

        mvc.perform(get("/trainee/learning")).andExpect(status().isOk())
                .andExpect(content().string(containsString("학습 중인(승인된) 과정이 없습니다.")))
                .andExpect(content().string(containsString("</html>")));

        mvc.perform(get("/trainee/contents/900703/play")).andExpect(status().isNotFound());
        mvc.perform(get("/trainee/contents/900704/play")).andExpect(status().isNotFound());
    }

    /** 존재하지 않는 콘텐츠 id로 고정 진도를 만들어 주지 않는다. */
    @Test
    @DisplayName("존재하지 않는 콘텐츠의 진도 저장 API는 404를 돌려준다")
    @WithUserDetails("trainee1")
    void sampleProgressApiIsGuarded() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/trainee/contents/900703/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionSeconds\":10,\"durationSeconds\":1440}")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("오류 화면은 Whitelabel 대신 안내 페이지 — 스택트레이스를 노출하지 않는다")
    @WithUserDetails("trainee1")
    void errorPageReplacesWhitelabel() throws Exception {
        mvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr("jakarta.servlet.error.status_code", 404))
                .andExpect(content().string(containsString("페이지를 찾을 수 없습니다")))
                .andExpect(content().string(containsString("내 홈으로")))
                .andExpect(content().string(not(containsString("Whitelabel"))))
                .andExpect(content().string(not(containsString("org.springframework.web"))));
    }

    @Test
    @DisplayName("존재하지 않는 이수 항목은 실제 항목처럼 안내하지 않고 404를 돌려준다")
    @WithUserDetails("trainee1")
    void sampleRowsAreGuarded() throws Exception {
        mvc.perform(get("/trainee/completion-management/900201/certificate"))
                .andExpect(status().isNotFound());
    }

    /**
     * 예전에는 예시 설문 상세를 목록으로 되돌려서 응답 화면을 캡처할 수 없었다.
     * 지금은 예시 상세를 그대로 렌더하고 <b>제출만</b> 막는다.
     */
    @Test
    @DisplayName("예시 설문은 응답 화면이 열리고, 제출만 안내로 막힌다")
    @WithUserDetails("trainee1")
    void sampleSurveyDetailRenders() throws Exception {
        mvc.perform(get("/trainee/survey/900301"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("화면 예시용 샘플 데이터")));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/trainee/survey/900301/submit").with(
                                org.springframework.security.test.web.servlet.request
                                        .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("error", containsString("제출되지 않습니다")));
    }

    /**
     * 설문 상세의 문항 유형 매핑 계약.
     *
     * <p>서버는 {@code SurveyQuestionType.name()} 이라 항상 대문자를 내려주는데 화면의 매핑
     * 테이블 키가 소문자여서 전부 'TEXT' 폴백으로 떨어졌다 — 별점·객관식이 통째로 주관식
     * 입력창으로 렌더됐다. 렌더는 브라우저 JS 가 하므로 MockMvc 로는 <b>양쪽 표기가 맞물리는지</b>
     * 까지만 고정한다 (서버가 대문자를 보내고, 페이지가 대문자 키로 받는지).</p>
     */
    @Test
    @DisplayName("설문 상세: 서버가 보내는 문항 유형과 화면 매핑 키의 대소문자가 맞다")
    @WithUserDetails("trainee1")
    void surveyQuestionTypesAreMappedInUpperCase() throws Exception {
        mvc.perform(get("/trainee/survey/900301"))
                .andExpect(status().isOk())
                // 서버가 내려주는 값
                .andExpect(content().string(containsString("\"type\":\"SCALE\"")))
                .andExpect(content().string(containsString("\"type\":\"SINGLE\"")))
                .andExpect(content().string(containsString("\"type\":\"MULTI\"")))
                // 화면이 받는 키
                .andExpect(content().string(containsString("SCALE: 'STAR'")))
                .andExpect(content().string(containsString("SINGLE: 'CHOICE'")))
                .andExpect(content().string(containsString("MULTI: 'CHOICE'")));
    }

    @Test
    @DisplayName("관리자는 훈련생 화면을 열람할 수 있지만 제출은 못 한다")
    @WithUserDetails("admin")
    void adminCanViewButNotSubmit() throws Exception {
        mvc.perform(get("/trainee/assignment")).andExpect(status().isOk());
        mvc.perform(get("/trainee/exam")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/trainee/assignment/1/submit").with(
                                org.springframework.security.test.web.servlet.request
                                        .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }
}
