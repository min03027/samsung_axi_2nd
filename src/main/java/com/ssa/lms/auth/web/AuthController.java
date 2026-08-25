package com.ssa.lms.auth.web;

import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.service.DuplicateLoginIdException;
import com.ssa.lms.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 로그인 화면 + 회원가입 플로우.
 * 흐름: /login ↔ /signup(유형 선택) → /signup/{trainee|instructor} → /signup/complete
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** 커스텀 로그인 화면 (인증 처리는 Spring Security 가 POST /login 으로 수행) */
    @GetMapping("/login")
    public String loginPage() {
        return "01-login/login";
    }

    /*
     * v2 화면(몰입클라쓰)의 '수강생 로그인' 진입점.
     * v2 에서 /login 으로 주소를 걸지 않고 v2 경로 안에서 같은 화면을 연다.
     * 정적 복사본을 두지 않고 같은 템플릿을 렌더하는 이유 — 로그인 폼은 CSRF 토큰과
     * 실패 사유(?error/?pending) 표시를 서버가 심어 줘야 동작한다. 복사본은 곧 원본과 갈린다.
     * 인증 처리는 그대로 POST /login (Spring Security) 이다.
     */
    @GetMapping({"/v2/login", "/v2/login/index.html"})
    public String v2LoginPage() {
        return "01-login/login";
    }

    /** 가입 유형 선택 */
    @GetMapping("/signup")
    public String signupType() {
        return "01-login/signup";
    }

    @GetMapping("/signup/trainee")
    public String signupTraineeForm(Model model) {
        model.addAttribute("signupForm", newForm(Role.TRAINEE));
        return "01-login/signup-trainee";
    }

    @PostMapping("/signup/trainee")
    public String signupTrainee(@Valid @ModelAttribute SignupForm signupForm,
                                BindingResult bindingResult) {
        signupForm.setRole(Role.TRAINEE.name());
        return processSignup(signupForm, bindingResult, "01-login/signup-trainee");
    }

    @GetMapping("/signup/instructor")
    public String signupInstructorForm(Model model) {
        model.addAttribute("signupForm", newForm(Role.INSTRUCTOR));
        return "01-login/signup-instructor";
    }

    @PostMapping("/signup/instructor")
    public String signupInstructor(@Valid @ModelAttribute SignupForm signupForm,
                                   BindingResult bindingResult) {
        signupForm.setRole(Role.INSTRUCTOR.name());
        return processSignup(signupForm, bindingResult, "01-login/signup-instructor");
    }

    @GetMapping("/signup/complete")
    public String signupComplete() {
        return "01-login/signup-complete";
    }

    private String processSignup(SignupForm form, BindingResult bindingResult, String formView) {
        if (bindingResult.hasErrors()) {
            return formView;
        }
        try {
            userService.signup(form);
        } catch (DuplicateLoginIdException e) {
            bindingResult.rejectValue("loginId", "duplicate", "이미 사용 중인 아이디입니다.");
            return formView;
        }
        return "redirect:/signup/complete";
    }

    private SignupForm newForm(Role role) {
        SignupForm form = new SignupForm();
        form.setRole(role.name());
        return form;
    }
}
