package com.ssa.lms.web.admin.notice;

import com.ssa.lms.notice.service.GrowthReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/settings/growth-report")
@RequiredArgsConstructor
public class AdminGrowthReportSettingController {
    private final GrowthReportService growthReportService;

    @GetMapping
    public String view(Model model) {
        model.addAttribute("setting", growthReportService.currentSetting());
        model.addAttribute("active", "growth-report");
        return "admin/admin-07-notice/admin-growth-report-setting";
    }

    @PostMapping
    public String save(@RequestParam(defaultValue = "false") boolean enabled,
                       @RequestParam int intervalDays, @RequestParam int sendHour,
                       @RequestParam int lowProgressGap, RedirectAttributes ra) {
        var setting = growthReportService.saveSetting(enabled, intervalDays, sendHour, lowProgressGap);
        ra.addFlashAttribute("message", setting.isEnabled()
                ? "%d일마다 %02d시에 성장 리포트를 발송하도록 저장했습니다."
                    .formatted(setting.getIntervalDays(), setting.getSendHour())
                : "성장 리포트 자동 발송을 껐습니다.");
        return "redirect:/admin/settings/growth-report";
    }

    @PostMapping("/send-now")
    public String sendNow(RedirectAttributes ra) {
        int sent = growthReportService.sendNow(LocalDateTime.now());
        ra.addFlashAttribute("message", sent == 0
                ? "발송할 수강 과정이 있는 활성 훈련생이 없습니다."
                : "현재 학습 기록으로 성장 리포트 %d건을 즉시 발송했습니다.".formatted(sent));
        return "redirect:/admin/settings/growth-report";
    }
}
