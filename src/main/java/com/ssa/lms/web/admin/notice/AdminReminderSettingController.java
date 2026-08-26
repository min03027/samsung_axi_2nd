package com.ssa.lms.web.admin.notice;

import com.ssa.lms.notice.service.ReminderSettingService;
import com.ssa.lms.notice.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 리마인드 알림 설정 화면 (관리자).
 *
 * <p>마감 임박 알림을 언제 보낼지 관리자가 직접 정한다. 예전에는 코드에 박혀 있어
 * 기관 운영 방식이 달라도 개발자가 고쳐 재배포해야 했다.</p>
 *
 * <p>접근 권한은 SecurityConfig 가 담당한다 — {@code /admin/notice/**} 는
 * ADMIN·INSTRUCTOR 이지만, <b>설정 변경은 관리자만</b> 해야 하므로
 * 경로를 {@code /admin/settings/} 아래에 둔다({@code /admin/**} → ADMIN 전용).</p>
 */
@Controller
@RequestMapping("/admin/settings/reminder")
@RequiredArgsConstructor
public class AdminReminderSettingController {

    private final ReminderSettingService settingService;
    private final ReminderService reminderService;

    @GetMapping
    public String view(Model model) {
        model.addAttribute("setting", settingService.current());
        model.addAttribute("recentHistory", reminderService.recentHistory());
        model.addAttribute("active", "reminder");
        return "admin/admin-07-notice/admin-reminder-setting";
    }

    /**
     * 저장.
     *
     * <p>범위를 벗어나거나 1·2차 순서가 뒤집힌 값은 저장 과정에서 바로잡힌다.
     * 그래서 <b>저장된 실제 값을 안내 문구에 담아</b> 돌려준다 — 넣은 값과 다르게
     * 저장됐는데 "저장되었습니다"만 뜨면 관리자가 눈치채지 못한다.</p>
     */
    @PostMapping
    public String save(@RequestParam int firstNoticeHours,
                       @RequestParam int secondNoticeHours,
                       @RequestParam int overdueDays,
                       @RequestParam(defaultValue = "false") boolean assignmentEnabled,
                       @RequestParam(defaultValue = "false") boolean examEnabled,
                       @RequestParam(defaultValue = "false") boolean surveyEnabled,
                       @RequestParam(defaultValue = "false") boolean lessonEnabled,
                       @RequestParam(defaultValue = "false") boolean enabled,
                       RedirectAttributes ra) {

        var saved = settingService.save(firstNoticeHours, secondNoticeHours, overdueDays,
                assignmentEnabled, examEnabled, surveyEnabled, lessonEnabled, enabled);

        ra.addFlashAttribute("message", saved.isEnabled()
                ? "저장했습니다. 마감 %d시간 전, %d시간 전, 마감 후 %d일까지 알립니다."
                        .formatted(saved.getFirstNoticeHours(), saved.getSecondNoticeHours(),
                                saved.getOverdueDays())
                : "저장했습니다. 리마인드 알림이 꺼져 있어 발송되지 않습니다.");
        return "redirect:/admin/settings/reminder";
    }
}
