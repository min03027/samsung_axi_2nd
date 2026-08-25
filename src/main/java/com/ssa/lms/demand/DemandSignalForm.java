package com.ssa.lms.demand;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
@Getter @Setter public class DemandSignalForm{@NotBlank @Size(max=200)private String title;@NotBlank @Size(max=100)private String industry;@NotBlank @Size(max=100)private String jobRole;@NotBlank @Size(max=1000)private String skills;@Min(0)@Max(100)private int demandScore=70;@NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)private LocalDate observedOn=LocalDate.now();@Size(max=200)private String sourceName;@Size(max=500)private String sourceUrl;@Size(max=3000)private String notes;}
