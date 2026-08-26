package com.ssa.lms.content;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.content.repository.ContentRepository;
import com.ssa.lms.content.service.ContentLibraryService;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class ContentLibraryRenderTest {

    @Autowired MockMvc mvc;
    @Autowired ContentLibraryService libraryService;
    @Autowired ContentRepository contentRepository;
    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("공용 라이브러리 목록·등록·버전 화면을 렌더링한다")
    @WithUserDetails("instructor1")
    void libraryPages() throws Exception {
        mvc.perform(get("/instructor/content-library"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("공용 라이브러리")))
                .andExpect(content().string(containsString("버전 이력")));
        mvc.perform(get("/instructor/content-library/new").param("type", "VIDEO"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("공용 원본 등록")));
        mvc.perform(get("/instructor/content-library/versions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("VERSION HISTORY")));
    }

    @Test
    @DisplayName("공용 원본 상세·과정 배치 화면을 렌더링한다")
    @WithUserDetails("admin")
    void detailAndDeployPages() throws Exception {
        LoginUser admin = new LoginUser(userRepository.findByLoginId("admin").orElseThrow());
        Long id = libraryService.promoteExisting(contentRepository.findAll().get(0).getId(), admin);

        mvc.perform(get("/instructor/content-library/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("연결된 과정 콘텐츠")))
                .andExpect(content().string(containsString("버전 히스토리")));
        mvc.perform(get("/instructor/content-library/{id}/deploy", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("원본의 새 버전을 자동 반영")));
    }
}
