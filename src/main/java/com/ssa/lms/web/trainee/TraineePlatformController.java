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
}
