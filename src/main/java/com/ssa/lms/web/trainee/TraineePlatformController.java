package com.ssa.lms.web.trainee;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.care.service.LearnerCareService;
import com.ssa.lms.notice.service.GrowthReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 수강생 플랫폼에서 데이터 연동 전 정보 구조를 확인하는 페이지의 진입점.
 *
 * <p>기존 도메인 서비스와 DB를 건드리지 않고 화면 자리만 제공한다. 과제·시험·출결처럼
 * 이미 구현된 기능은 이 컨트롤러를 거치지 않고 기존 URL과 컨트롤러를 그대로 사용한다.</p>
 */
@Controller
@RequestMapping("/trainee")
@RequiredArgsConstructor
public class TraineePlatformController {

    private final GrowthReportService growthReportService;
    private final LearnerCareService careService;

    @GetMapping("/evaluations")
    public String evaluations() {
        return "trainee/evaluations";
    }

    @GetMapping("/growth")
    public String growth(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("report", growthReportService.current(loginUser.getId(), loginUser.getName()));
        return "trainee/growth";
    }

    @GetMapping("/journal")
    public String journal(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("records", careService.myRecords(loginUser));
        return "trainee/journal";
    }

    @PostMapping("/journal")
    public String createJournal(@AuthenticationPrincipal LoginUser loginUser,
                                @RequestParam String subject, @RequestParam String content,
                                RedirectAttributes ra) {
        careService.createJournal(loginUser, subject, content);
        ra.addFlashAttribute("message", "학습일지를 저장했습니다. 담당자가 같은 흐름에서 확인할 수 있습니다.");
        return "redirect:/trainee/journal";
    }

    @GetMapping("/career")
    public String career() {
        return "trainee/career";
    }

    @GetMapping("/career/portfolio")
    public String careerPortfolio(Model model) {
        return careerScreen(model, "portfolio", "직무 포트폴리오", "학습 결과물을 직무 역량 기준으로 정리하고 공개 준비 상태를 확인합니다.");
    }

    @GetMapping("/career/applications")
    public String careerApplications(Model model) {
        return careerScreen(model, "applications", "지원·면접 관리", "지원 기업, 전형 단계, 면접 일정과 후속 행동을 한 흐름으로 관리합니다.");
    }

    @GetMapping("/career/recommendations")
    public String careerRecommendations(Model model) {
        return careerScreen(model, "recommendations", "기업·직무 추천", "희망 직무와 보유 역량을 기준으로 확인할 채용 기회를 모아봅니다.");
    }

    @GetMapping("/career/follow-up")
    public String careerFollowUp(Model model) {
        return careerScreen(model, "follow-up", "취업 후 관리", "입사 이후 적응 상태와 추가 학습, 후속 상담 계획을 이어서 확인합니다.");
    }

    private String careerScreen(Model model, String mode, String title, String description) {
        model.addAttribute("careerMode", mode);
        model.addAttribute("careerTitle", title);
        model.addAttribute("careerDescription", description);
        return "trainee/career-workspace";
    }
}
