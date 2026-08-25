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

    /**
     * 공개 사이트의 폴더형 주소를 실제 정적 진입 화면으로 연결한다.
     *
     * <p>Cloudflare Pages/Workers는 디렉터리의 {@code index.html}을 자동으로 찾지만,
     * Spring Boot의 기본 정적 리소스 핸들러는 같은 주소를 자동 해석하지 않는다.
     * 삼성 LXP 도메인에서도 동일한 대표 URL을 사용할 수 있도록 서버 내부에서 전달한다.</p>
     */
    @GetMapping({"/v2", "/v2/"})
    public String publicHome() {
        return "forward:/v2/index.html";
    }

    @GetMapping({"/v2/site/campus", "/v2/site/campus/"})
    public String employmentCampus() {
        return "forward:/v2/site/campus/index.html";
    }

    @GetMapping({"/v2/site/class", "/v2/site/class/"})
    public String immersiveClass() {
        return "forward:/v2/site/class/index.html";
    }

    @GetMapping({"/v2/site/biz", "/v2/site/biz/"})
    public String businessCraft() {
        return "forward:/v2/site/biz/index.html";
    }

    @GetMapping({"/v2/site/lxp", "/v2/site/lxp/"})
    public String lxpIntroduction() {
        return "forward:/v2/site/lxp/index.html";
    }
}
