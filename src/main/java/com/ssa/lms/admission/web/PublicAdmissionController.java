package com.ssa.lms.admission.web;

import com.ssa.lms.admission.entity.ConsultationRequest;
import com.ssa.lms.admission.entity.CourseApplication;
import com.ssa.lms.admission.service.AdmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v2/api/public")
@RequiredArgsConstructor
public class PublicAdmissionController {

    private final AdmissionService admissionService;

    @GetMapping("/consultations/courses/{courseId}")
    public PublicConsultationCourseView consultationCourse(@PathVariable Long courseId) {
        return admissionService.consultableCourse(courseId);
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicSubmissionResponse application(@Valid @RequestBody PublicApplicationRequest request) {
        CourseApplication application = admissionService.submitApplication(request);
        return new PublicSubmissionResponse(application.getReceiptNumber(), application.getStatus().name(),
                "과정 신청이 접수되었습니다.");
    }

    @PostMapping("/consultations")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicSubmissionResponse consultation(@Valid @RequestBody PublicConsultationRequest request) {
        ConsultationRequest consultation = admissionService.submitConsultation(request);
        return new PublicSubmissionResponse(consultation.getReceiptNumber(), consultation.getStatus().name(),
                "상담 신청이 접수되었습니다.");
    }
}
