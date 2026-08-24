package com.ssa.lms.admission.web;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record PublicConsultationRequest(
        Long courseId,
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Pattern(regexp = "^[0-9+() -]{9,20}$") String phone,
        @NotBlank @Size(max = 100) String type,
        @NotNull @FutureOrPresent LocalDate date,
        @NotBlank @Size(max = 50) String time,
        @NotBlank @Size(max = 30) String contact,
        @NotBlank @Size(max = 40) String dorm,
        @Size(max = 4000) String message,
        @AssertTrue(message = "개인정보 수집·이용 동의가 필요합니다.") boolean privacy
) {
}
