package com.ssa.lms.review.web;

import com.ssa.lms.course.service.CourseService;
import com.ssa.lms.organization.service.OrganizationService;
import com.ssa.lms.review.entity.*;
import com.ssa.lms.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
public class ReviewAdminController {

    private static final String VIEW_DIR = "admin/reviews/";
    private final ReviewService reviewService;
    private final CourseService courseService;
    private final OrganizationService organizationService;

    @GetMapping
    public String list(@RequestParam(required = false) String query,
                       @RequestParam(required = false) Long courseId,
                       @RequestParam(required = false) ReviewContentType contentType,
                       @RequestParam(required = false) ReviewStatus status,
                       @RequestParam(required = false) Boolean publicVisible,
                       Model model) {
        model.addAttribute("reviews", reviewService.findAll(query, courseId, contentType, status, publicVisible));
        model.addAttribute("query", query); model.addAttribute("selectedCourseId", courseId);
        model.addAttribute("selectedContentType", contentType); model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedPublicVisible", publicVisible);
        prepareOptions(model);
        return VIEW_DIR + "list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("reviewForm", new ReviewForm());
        prepareForm(model, false, null);
        return VIEW_DIR + "form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute ReviewForm reviewForm, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        validateExposure(reviewForm, errors);
        if (errors.hasErrors()) { prepareForm(model, false, null); return VIEW_DIR + "form"; }
        Long id = reviewService.create(reviewForm);
        redirect.addFlashAttribute("message", "후기를 등록했습니다.");
        return "redirect:/admin/reviews/" + id;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("review", reviewService.get(id));
        return VIEW_DIR + "detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("reviewForm", reviewService.formForEdit(id));
        prepareForm(model, true, id);
        return VIEW_DIR + "form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute ReviewForm reviewForm,
                         BindingResult errors, Model model, RedirectAttributes redirect) {
        validateExposure(reviewForm, errors);
        if (errors.hasErrors()) { prepareForm(model, true, id); return VIEW_DIR + "form"; }
        reviewService.update(id, reviewForm);
        redirect.addFlashAttribute("message", "후기를 수정했습니다.");
        return "redirect:/admin/reviews/" + id;
    }

    private void prepareForm(Model model, boolean editing, Long id) {
        model.addAttribute("editing", editing); model.addAttribute("reviewId", id);
        prepareOptions(model);
        model.addAttribute("authorDisplayTypes", ReviewAuthorDisplayType.values());
        model.addAttribute("sites", ReviewExposureSite.values());
        model.addAttribute("positions", ReviewExposurePosition.values());
        model.addAttribute("organizations", organizationService.selectableOrganizations());
    }

    private void prepareOptions(Model model) {
        model.addAttribute("courses", courseService.findAll());
        model.addAttribute("contentTypes", ReviewContentType.values());
        model.addAttribute("statuses", ReviewStatus.values());
    }

    private void validateExposure(ReviewForm form, BindingResult errors) {
        if (form.isPublicVisible() && form.getExposureSites().isEmpty()) {
            errors.rejectValue("exposureSites", "required", "공개할 사이트를 하나 이상 선택하세요.");
        }
        if (form.isPublicVisible() && form.getExposurePositions().isEmpty()) {
            errors.rejectValue("exposurePositions", "required", "공개할 위치를 하나 이상 선택하세요.");
        }
    }
}
