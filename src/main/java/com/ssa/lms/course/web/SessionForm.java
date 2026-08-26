package com.ssa.lms.course.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/** 차시 추가/수정 폼. seq(차시 번호)는 서비스가 자동 부여한다. */
@Getter
@Setter
public class SessionForm {

    @NotBlank(message = "차시명을 입력하세요.")
    @Size(max = 200)
    private String name;

    /** 진행 예정일 (선택) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate lessonDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime lessonStartTime;

    /** 인정 학습시간(분, 선택) */
    @PositiveOrZero(message = "학습시간은 0 이상이어야 합니다.")
    private Integer learningMinutes;
}
