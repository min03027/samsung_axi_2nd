package com.ssa.lms.admission.web;

import com.ssa.lms.admission.entity.ApplicationStatus;
import com.ssa.lms.admission.entity.ConsultationStatus;
import com.ssa.lms.admission.service.AdmissionException;
import com.ssa.lms.admission.service.AdmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/admissions")
@RequiredArgsConstructor
public class AdminAdmissionController {

    private final AdmissionService admissionService;

    @GetMapping
    public String home() {
        return "redirect:/admin/admissions/applications";
    }

    @GetMapping("/applications")
    public String applications(Model model) {
        model.addAttribute("applications", admissionService.applications());
        return "admin/admissions/applications";
    }

    @GetMapping("/applications/{id}")
    public String application(@PathVariable Long id, Model model) {
        model.addAttribute("courseApplication", admissionService.application(id));
        addOptions(model);
        return "admin/admissions/application-detail";
    }

    @PostMapping("/applications/{id}")
    public String updateApplication(@PathVariable Long id,
                                    @RequestParam ApplicationStatus status,
                                    @RequestParam(required = false) Long assigneeId,
                                    @RequestParam(required = false) String processingNote,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate followUpDate,
                                    @RequestParam(required = false) String finalResult,
                                    RedirectAttributes redirectAttributes) {
        try {
            admissionService.updateApplication(id, status, assigneeId,
                    processingNote, followUpDate, finalResult);
            redirectAttributes.addFlashAttribute("message", "지원자 처리 정보를 저장했습니다.");
        } catch (AdmissionException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/admissions/applications/" + id;
    }

    @GetMapping("/consultations")
    public String consultations(Model model) {
        model.addAttribute("consultations", admissionService.consultations());
        return "admin/admissions/consultations";
    }

    @GetMapping("/consultations/{id}")
    public String consultation(@PathVariable Long id, Model model) {
        model.addAttribute("consultation", admissionService.consultation(id));
        addOptions(model);
        return "admin/admissions/consultation-detail";
    }

    @PostMapping("/consultations/{id}")
    public String updateConsultation(@PathVariable Long id,
                                     @RequestParam ConsultationStatus status,
                                     @RequestParam(required = false) Long assigneeId,
                                     @RequestParam(required = false) String processingNote,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate followUpDate,
                                     @RequestParam(required = false) String finalResult,
                                     RedirectAttributes redirectAttributes) {
        try {
            admissionService.updateConsultation(id, status, assigneeId,
                    processingNote, followUpDate, finalResult);
            redirectAttributes.addFlashAttribute("message", "상담 처리 정보를 저장했습니다.");
        } catch (AdmissionException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/admissions/consultations/" + id;
    }

    private void addOptions(Model model) {
        model.addAttribute("applicationStatuses", ApplicationStatus.values());
        model.addAttribute("consultationStatuses", ConsultationStatus.values());
        model.addAttribute("assignees", admissionService.assignableAdmins());
    }
}
