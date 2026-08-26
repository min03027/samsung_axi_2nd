package com.ssa.lms.web.management;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.care.entity.LearnerCareRecord;
import com.ssa.lms.care.service.LearnerCareService;
import com.ssa.lms.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 관리자와 강사가 같은 화면 구조를 쓰면서 역할별 담당 범위 안에서
 * 학습일지·상담·후속조치 기록을 조회하고 저장하는 진입점.
 */
@Controller
@RequiredArgsConstructor
public class ManagementPlatformController {

    private final LearnerCareService careService;

    @GetMapping({"/admin/care", "/instructor/care"})
    public String care(@AuthenticationPrincipal LoginUser user, Model model) {
        addScope(user, model);
        return "management/care";
    }

    @GetMapping({"/admin/care/diary", "/instructor/care/diary"})
    public String diary(@AuthenticationPrincipal LoginUser user, Model model) {
        addScope(user, model);
        model.addAttribute("trainees", careService.traineeOptions(user));
        model.addAttribute("records", careService.managementRecords(user));
        model.addAttribute("recordTypes", LearnerCareRecord.RecordType.values());
        model.addAttribute("careStatuses", LearnerCareRecord.CareStatus.values());
        return "management/diary";
    }

    @PostMapping({"/admin/care/diary", "/instructor/care/diary"})
    public String createRecord(@AuthenticationPrincipal LoginUser user,
                               @RequestParam Long traineeId,
                               @RequestParam LearnerCareRecord.RecordType recordType,
                               @RequestParam LearnerCareRecord.CareStatus status,
                               @RequestParam String subject,
                               @RequestParam String content,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime followUpAt,
                               RedirectAttributes ra) {
        careService.createByManager(user, traineeId, recordType, status, subject, content, followUpAt);
        ra.addFlashAttribute("message", "학생 기록을 저장하고 후속조치 흐름에 연결했습니다.");
        return "redirect:" + base(user) + "/care/diary";
    }

    @GetMapping({"/admin/care/follow-ups", "/instructor/care/follow-ups"})
    public String followUps(@AuthenticationPrincipal LoginUser user, Model model) {
        addScope(user, model);
        var records = careService.managementRecords(user);
        model.addAttribute("records", records);
        model.addAttribute("careStatuses", LearnerCareRecord.CareStatus.values());
        model.addAttribute("statusCounts", Arrays.stream(LearnerCareRecord.CareStatus.values())
                .collect(java.util.stream.Collectors.toMap(status -> status,
                        status -> records.stream().filter(record -> record.status() == status).count())));
        return "management/follow-ups";
    }

    @PostMapping({"/admin/care/follow-ups/update", "/instructor/care/follow-ups/update"})
    public String updateFollowUp(@AuthenticationPrincipal LoginUser user,
                                 @RequestParam Long recordId,
                                 @RequestParam LearnerCareRecord.CareStatus status,
                                 @RequestParam(required = false) String result,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime followUpAt,
                                 RedirectAttributes ra) {
        careService.updateFollowUp(user, recordId, status, result, followUpAt);
        ra.addFlashAttribute("message", "상담·후속조치 상태를 저장했습니다.");
        return "redirect:" + base(user) + "/care/follow-ups";
    }

    @GetMapping({"/admin/quality", "/instructor/quality"})
    public String quality(@AuthenticationPrincipal LoginUser user, Model model) {
        addScope(user, model);
        return "management/quality";
    }

    @GetMapping("/admin/career")
    public String careerManagement(@AuthenticationPrincipal LoginUser user, Model model) {
        addScope(user, model);
        return "management/career";
    }

    @GetMapping({"/admin/quality/improvements", "/instructor/quality/improvements"})
    public String qualityImprovements(@AuthenticationPrincipal LoginUser user, Model model) {
        addScope(user, model);
        return "management/quality-improvements";
    }

    private void addScope(LoginUser user, Model model) {
        boolean admin = user != null && user.getRole() == Role.ADMIN;
        model.addAttribute("managementScope", admin ? "전체 과정·수강생" : "내 담당 과정·수강생");
        model.addAttribute("managementBase", base(user));
    }

    private String base(LoginUser user) {
        return user != null && user.getRole() == Role.ADMIN ? "/admin" : "/instructor";
    }
}
