package com.ssa.lms.web.trainee;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TraineeCoreDataAccuracyTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("실제 데이터가 없는 훈련생 핵심 화면은 샘플 대신 빈 상태를 보여준다")
    void noSampleFallback() throws Exception {
        User fresh = userRepository.save(User.builder()
                .loginId("core-empty-" + System.nanoTime())
                .password("{noop}test1234")
                .name("빈데이터검증")
                .role(Role.TRAINEE)
                .status(UserStatus.ACTIVE)
                .build());
        var principal = SecurityMockMvcRequestPostProcessors.user(new LoginUser(fresh));

        mvc.perform(get("/trainee/learning").with(principal))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("학습 중인(승인된) 과정이 없습니다.")))
                .andExpect(content().string(not(containsString("화면 예시"))));
        mvc.perform(get("/trainee/attendance").with(principal))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("화면 예시"))));
        mvc.perform(get("/trainee/completion-management").with(principal))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("화면 예시"))));
    }
}
