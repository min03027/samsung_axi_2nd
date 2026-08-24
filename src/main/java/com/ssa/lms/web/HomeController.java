package com.ssa.lms.web;

import com.ssa.lms.auth.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 루트 진입점 — 비로그인 사용자는 통합 랜딩으로, 로그인 사용자는 기존 안내 화면으로 분기한다.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return "redirect:/v2/index.html";
        }
        return "home";
    }
}
