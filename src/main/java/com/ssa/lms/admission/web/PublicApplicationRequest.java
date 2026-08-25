package com.ssa.lms.admission.web;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record PublicApplicationRequest(
        @NotNull Long courseId,
        @NotBlank @Size(max = 80) String name,
        @NotNull @Past LocalDate birth,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Pattern(regexp = "^[0-9+() -]{9,20}$") String phone,
        @NotBlank @Size(max = 80) String employment,
        @NotBlank @Size(max = 120) String job,
        @NotBlank @Size(max = 4000) String motivation,
        @Size(max = 4000) String career,
        @Size(max = 2000) String skills,
        @NotBlank @Size(max = 40) String card,
        @NotBlank @Size(max = 40) String dorm,
        @AssertTrue(message = "개인정보 수집·이용 동의가 필요합니다.") boolean privacy,
        @AssertTrue(message = "지원 내용 확인이 필요합니다.") boolean truth
) {
}
