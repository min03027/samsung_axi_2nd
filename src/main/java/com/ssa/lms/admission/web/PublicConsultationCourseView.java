package com.ssa.lms.admission.web;

import java.time.LocalDate;

public record PublicConsultationCourseView(
        Long id,
        String courseName,
        String categoryLabel,
        LocalDate educationStartDate,
        LocalDate educationEndDate,
        String educationTime,
        int capacity,
        String requiredDocuments
) {
}
