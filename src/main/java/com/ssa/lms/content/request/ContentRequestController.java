package com.ssa.lms.content.request;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.content.entity.ContentType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ContentRequestController {
    private final ContentRequestService service;

    @PreAuthorize("hasRole('TRAINEE')")
    @GetMapping("/trainee/content-requests")
    public String mine(@AuthenticationPrincipal LoginUser user, Model model) {
        model.addAttribute("rows", service.mine(user.getId()));
        model.addAttribute("courses", service.myCourses(user.getId()));
        model.addAttribute("types", ContentType.values());
        if (!model.containsAttribute("form")) model.addAttribute("form", new ContentRequestForm());
        return "trainee/content-requests";
    }

    @PreAuthorize("hasRole('TRAINEE')")
    @PostMapping("/trainee/content-requests")
    public String create(@AuthenticationPrincipal LoginUser user,
                         @Valid @ModelAttribute("form") ContentRequestForm form,
                         BindingResult errors, RedirectAttributes ra, Model model) {
        if (errors.hasErrors()) {
            model.addAttribute("rows", service.mine(user.getId())); model.addAttribute("courses", service.myCourses(user.getId()));
            model.addAttribute("types", ContentType.values()); return "trainee/content-requests";
        }
        service.create(user.getId(), form); ra.addFlashAttribute("message", "콘텐츠 요청을 접수했습니다.");
        return "redirect:/trainee/content-requests";
    }

    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @GetMapping("/instructor/content-requests")
    public String staff(@AuthenticationPrincipal LoginUser user, Model model) {
        model.addAttribute("rows", service.staffRows(user));
        return "instructor/content-request-list";
    }

    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @GetMapping("/instructor/content-requests/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal LoginUser user, Model model) {
        var row = service.view(id, user); model.addAttribute("row", row);
        model.addAttribute("libraryItems", service.libraryOptions()); model.addAttribute("sessions", service.sessionOptions(row.courseId(), user));
        model.addAttribute("form", new ContentRequestDecisionForm()); return "instructor/content-request-detail";
    }

    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @PostMapping("/instructor/content-requests/{id}/review")
    public String review(@PathVariable Long id, @AuthenticationPrincipal LoginUser user) { service.startReview(id, user); return redirect(id); }

    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @PostMapping("/instructor/content-requests/{id}/fulfill")
    public String fulfill(@PathVariable Long id, @Valid @ModelAttribute("form") ContentRequestDecisionForm form,
                          BindingResult errors, @AuthenticationPrincipal LoginUser user, RedirectAttributes ra, Model model) {
        if (errors.hasErrors()) { var row=service.view(id,user); model.addAttribute("row",row); model.addAttribute("libraryItems",service.libraryOptions()); model.addAttribute("sessions",service.sessionOptions(row.courseId(), user)); return "instructor/content-request-detail"; }
        service.fulfill(id, form, user); ra.addFlashAttribute("message", "요청 콘텐츠를 과정에 배치했습니다."); return redirect(id);
    }

    @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
    @PostMapping("/instructor/content-requests/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam String note, @AuthenticationPrincipal LoginUser user, RedirectAttributes ra) { service.reject(id,note,user); ra.addFlashAttribute("message","요청을 반려했습니다."); return redirect(id); }
    private String redirect(Long id) { return "redirect:/instructor/content-requests/" + id; }
}
