package com.ssa.lms.config;

import com.ssa.lms.auth.LoginFailureHandler;
import com.ssa.lms.auth.RoleBasedAuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * 공통 보안 설정 — 권한 3종(ADMIN/INSTRUCTOR/TRAINEE) URL 경계.
 * 공동 소유 파일: 수정 전 반드시 상대 개발자와 공유 (CLAUDE.md).
 *
 * <p>커스텀 로그인 화면(01-login/login.html) 연동 완료 (A 로그인 슬라이스, feat/a-auth-login).
 * 로그인 성공 시 {@link RoleBasedAuthenticationSuccessHandler} 로 역할별 화면(/admin·/instructor·/trainee)
 * 리다이렉트, 실패 시 {@link LoginFailureHandler} 로 사유별 분기(?error / ?pending).</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RoleBasedAuthenticationSuccessHandler successHandler;
    private final LoginFailureHandler failureHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 정적 리소스, 로그인/가입, H2 콘솔(local)
                        .requestMatchers("/static/**", "/css/**", "/js/**", "/img/**",
                                "/icons/**", "/font/**", "/favicon.ico", "/v2/**").permitAll()
                        .requestMatchers("/", "/login", "/signup/**", "/error").permitAll()
                        // 약관·방침 — 가입 동의 대상 문서라 로그인 전에도 열려야 한다
                        .requestMatchers("/terms", "/privacy").permitAll()
                        /* QR 로 열리는 모바일 신분증 제출 (LXP-015).
                           훈련생 휴대폰은 LXP 에 로그인돼 있지 않으므로 로그인 세션으로 인가할 수 없다.
                           대신 URL 안의 일회용 토큰이 신원을 보장한다 — MobileIdentityController 가
                           매 요청 ExamIdentityService.lookup() 으로 해시 대조·만료·폐기·사용횟수·
                           세션 상태를 검증한다. permitAll 이지만 토큰이 없으면 아무것도 열리지 않는다.
                           토큰 TTL 10분, 새 QR 발급 시 이전 토큰 즉시 폐기. */
                        .requestMatchers("/m/id/**").permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")).permitAll()
                        // 역할별 URL 경계 — 구체 경로가 먼저 와야 한다 (권한정의서 기준, a-requests.md P1-6)
                        // B 도메인: 관리자 모듈 중 강사도 접근 가능한 영역
                        .requestMatchers("/admin/evaluation/**", "/admin/support/**",
                                "/admin/notice/**", "/admin/survey/**").hasAnyRole("ADMIN", "INSTRUCTOR")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/instructor/proctor/**").hasAnyRole("ADMIN", "INSTRUCTOR")
                        .requestMatchers("/instructor/**").hasAnyRole("INSTRUCTOR", "ADMIN")
                        // 훈련생 영역의 <b>쓰기</b>(응시 시작·답안 저장·과제 제출·설문 응답·진도 기록)는
                        // 본인만 할 수 있다 — ADMIN 도 대신 제출할 수 없다. 모든 변경은 POST 로 들어온다.
                        .requestMatchers(HttpMethod.POST, "/trainee/**").hasRole("TRAINEE")
                        .requestMatchers(HttpMethod.PUT, "/trainee/**").hasRole("TRAINEE")
                        .requestMatchers(HttpMethod.PATCH, "/trainee/**").hasRole("TRAINEE")
                        .requestMatchers(HttpMethod.DELETE, "/trainee/**").hasRole("TRAINEE")
                        // 조회(GET)는 ADMIN 도 연다 — 운영자가 훈련생 화면을 그대로 확인해야
                        // 문의 대응과 화면 점검이 된다. 데이터는 각 컨트롤러가 인증 주체(user.getId())
                        // 기준으로만 조회하므로 관리자에게 남의 제출물이 보이지는 않는다.
                        .requestMatchers("/trainee/**").hasAnyRole("TRAINEE", "ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        // 커스텀 로그인 화면(01-login/login.html) 연동
                        .loginPage("/login")
                        .loginProcessingUrl("/login")   // POST /login 으로 인증 처리
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(successHandler)  // 역할별 리다이렉트
                        .failureHandler(failureHandler)  // 실패 사유별 분기(?error / ?pending)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/v2/index.html")   // 로그아웃하면 통합 홈으로
                        .permitAll()
                )
                // H2 콘솔용 (local 프로필에서만 콘솔이 열림)
                .csrf(csrf -> csrf.ignoringRequestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
