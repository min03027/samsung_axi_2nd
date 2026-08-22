package com.ssa.lms.auth;

import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 로그인/가입 세로 슬라이스 통합 테스트: 가입(PENDING) → 역할별 로그인 리다이렉트.
 * local 프로필 시드 계정(admin/instructor1/trainee1, pw 1234)을 사용한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void 로그인_화면이_렌더링된다() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("01-login/login"));
    }

    @Test
    void 훈련생_가입시_PENDING_상태와_동의시각이_저장되고_비밀번호는_해시된다() throws Exception {
        mvc.perform(post("/signup/trainee").with(csrf())
                        .param("loginId", "newtrainee")
                        .param("password", "password123")
                        .param("passwordConfirm", "password123")
                        .param("name", "홍길동")
                        .param("email", "newtrainee@example.com")
                        .param("phone", "010-1234-5678")
                        .param("birthDate", "2000-01-01")
                        .param("gender", "male")
                        .param("privacyConsent", "true")
                        .param("thirdPartyConsent", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signup/complete"));

        User user = userRepository.findByLoginId("newtrainee").orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user.getPrivacyConsentAt()).isNotNull();
        assertThat(user.getThirdPartyConsentAt()).isNotNull();
        assertThat(user.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", user.getPassword())).isTrue();
    }

    @Test
    void 필수_동의_미체크시_가입이_반려된다() throws Exception {
        mvc.perform(post("/signup/trainee").with(csrf())
                        .param("loginId", "noconsent")
                        .param("password", "password123")
                        .param("passwordConfirm", "password123")
                        .param("name", "미동의")
                        .param("email", "noconsent@example.com")
                        .param("phone", "010-0000-0000")
                        .param("birthDate", "2000-01-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("01-login/signup-trainee"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "privacyConsent", "thirdPartyConsent"));

        assertThat(userRepository.findByLoginId("noconsent")).isEmpty();
    }

    @Test
    void 비밀번호_불일치시_가입이_반려된다() throws Exception {
        mvc.perform(post("/signup/instructor").with(csrf())
                        .param("loginId", "mismatch")
                        .param("password", "password123")
                        .param("passwordConfirm", "different999")
                        .param("name", "불일치")
                        .param("email", "mismatch@example.com")
                        .param("phone", "010-1111-2222")
                        .param("birthDate", "1990-05-05")
                        .param("privacyConsent", "true")
                        .param("thirdPartyConsent", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupForm", "passwordMatched"));

        assertThat(userRepository.findByLoginId("mismatch")).isEmpty();
    }

    @Test
    void 중복_아이디_가입시_반려된다() throws Exception {
        mvc.perform(post("/signup/trainee").with(csrf())
                        .param("loginId", "trainee1")   // 시드 계정과 중복
                        .param("password", "password123")
                        .param("passwordConfirm", "password123")
                        .param("name", "중복")
                        .param("email", "dup@example.com")
                        .param("phone", "010-3333-4444")
                        .param("birthDate", "1995-03-03")
                        .param("privacyConsent", "true")
                        .param("thirdPartyConsent", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupForm", "loginId"));
    }

    @Test
    void 관리자_로그인시_admin으로_리다이렉트된다() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "admin").param("password", "1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void 강사_로그인시_instructor로_리다이렉트된다() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "instructor1").param("password", "1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/instructor"));
    }

    @Test
    void 훈련생_로그인시_trainee로_리다이렉트된다() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "trainee1").param("password", "1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/trainee"));
    }

    @Test
    void 비밀번호_오류시_error_파라미터로_리다이렉트된다() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "trainee1").param("password", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void 승인대기_계정_로그인시_pending_파라미터로_리다이렉트된다() throws Exception {
        // 가입 직후 PENDING 계정 생성
        mvc.perform(post("/signup/trainee").with(csrf())
                .param("loginId", "pendinguser")
                .param("password", "password123")
                .param("passwordConfirm", "password123")
                .param("name", "대기")
                .param("email", "pending@example.com")
                .param("phone", "010-5555-6666")
                .param("birthDate", "2001-02-02")
                .param("privacyConsent", "true")
                .param("thirdPartyConsent", "true"));

        mvc.perform(post("/login").with(csrf())
                        .param("username", "pendinguser").param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?pending"));
    }
}
