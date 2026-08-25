package com.ssa.lms.organization.web;

import com.ssa.lms.course.service.CourseService;
import com.ssa.lms.organization.entity.*;
import com.ssa.lms.organization.service.DuplicateOrganizationException;
import com.ssa.lms.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/organizations")
@RequiredArgsConstructor
public class OrganizationAdminController {

    private static final String VIEW_DIR = "admin/organizations/";

    private final OrganizationService organizationService;
    private final CourseService courseService;

    @GetMapping
    public String list(@RequestParam(required = false) String query,
                       @RequestParam(required = false) OrganizationType type,
                       @RequestParam(required = false) OrganizationRelationshipType relationship,
                       @RequestParam(required = false) OrganizationStatus status,
                       @RequestParam(required = false) Boolean homepageExposure,
                       Model model) {
        var organizations = organizationService.findAll(query, type, relationship, status, homepageExposure);
        model.addAttribute("organizations", organizations);
        model.addAttribute("query", query);
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedRelationship", relationship);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedExposure", homepageExposure);
        model.addAttribute("types", OrganizationType.values());
        model.addAttribute("relationships", OrganizationRelationshipType.values());
        model.addAttribute("statuses", OrganizationStatus.values());
        return VIEW_DIR + "list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("organizationForm", new OrganizationForm());
        prepareFormModel(model, false, null);
        return VIEW_DIR + "form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute OrganizationForm organizationForm,
                         BindingResult bindingResult, Model model,
                         RedirectAttributes redirectAttributes) {
        validateExposure(organizationForm, bindingResult);
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, false, null);
            return VIEW_DIR + "form";
        }
        try {
            Long id = organizationService.create(organizationForm);
            redirectAttributes.addFlashAttribute("message", "기업·기관을 등록했습니다.");
            return "redirect:/admin/organizations/" + id;
        } catch (DuplicateOrganizationException e) {
            bindingResult.reject("duplicate", e.getMessage());
            prepareFormModel(model, false, null);
            return VIEW_DIR + "form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("organization", organizationService.get(id));
        model.addAttribute("courseLinks", organizationService.courseLinks(id));
        return VIEW_DIR + "detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("organizationForm", organizationService.formForEdit(id));
        prepareFormModel(model, true, id);
        return VIEW_DIR + "form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute OrganizationForm organizationForm,
                         BindingResult bindingResult, Model model,
                         RedirectAttributes redirectAttributes) {
        validateExposure(organizationForm, bindingResult);
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, true, id);
            return VIEW_DIR + "form";
        }
        try {
            organizationService.update(id, organizationForm);
            redirectAttributes.addFlashAttribute("message", "기업·기관 정보를 수정했습니다.");
            return "redirect:/admin/organizations/" + id;
        } catch (DuplicateOrganizationException e) {
            bindingResult.reject("duplicate", e.getMessage());
            prepareFormModel(model, true, id);
            return VIEW_DIR + "form";
        }
    }

    private void prepareFormModel(Model model, boolean editing, Long id) {
        model.addAttribute("editing", editing);
        model.addAttribute("organizationId", id);
        model.addAttribute("types", OrganizationType.values());
        model.addAttribute("relationships", OrganizationRelationshipType.values());
        model.addAttribute("sites", OrganizationExposureSite.values());
        model.addAttribute("positions", OrganizationExposurePosition.values());
        model.addAttribute("statuses", OrganizationStatus.values());
        model.addAttribute("courses", courseService.findAll());
    }

    private void validateExposure(OrganizationForm form, BindingResult bindingResult) {
        if (form.isHomepageExposure() && form.getExposureSites().isEmpty()) {
            bindingResult.rejectValue("exposureSites", "required", "홈페이지 노출 사이트를 하나 이상 선택하세요.");
        }
        if (form.isHomepageExposure() && form.getExposurePositions().isEmpty()) {
            bindingResult.rejectValue("exposurePositions", "required", "홈페이지 노출 위치를 하나 이상 선택하세요.");
        }
    }
}
