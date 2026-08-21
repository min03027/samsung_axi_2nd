package com.ssa.lms.web;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.web.landing.LandingPageData;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 루트 진입점 — 비로그인 사용자는 랜딩, 로그인 사용자는 기존 역할별 모듈로 분기한다. */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final LandingPageData landingPageData;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        if (loginUser == null) {
            landingPageData.addTo(model);
            return "landing/index";
        }
        return switch (loginUser.getRole()) {
            case ADMIN -> "redirect:/admin";
            case INSTRUCTOR -> "redirect:/instructor";
            case TRAINEE -> "redirect:/trainee";
        };
    }
}
