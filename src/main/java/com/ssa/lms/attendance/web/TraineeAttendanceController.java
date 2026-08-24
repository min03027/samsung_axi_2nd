package com.ssa.lms.attendance.web;

import com.ssa.lms.attendance.service.AttendanceService;
import com.ssa.lms.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 훈련생 출결현황 — <b>본인</b> 출결 기록만 과정별로 조회한다.
 *
 * <p>권한 경계: 조회 대상은 인증 주체({@code user.getId()})로 고정하므로 URL 로 남의 출결을
 * 볼 수 없다. 경로 {@code /trainee/**} 는 SecurityConfig 의 (TRAINEE,ADMIN) 규칙으로 커버된다.</p>
 */
@Controller
@RequestMapping("/trainee/attendance")
@RequiredArgsConstructor
public class TraineeAttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping
    public String attendance(@AuthenticationPrincipal LoginUser user, Model model) {
        model.addAttribute("courses", attendanceService.traineeAttendance(user.getId()));
        return "trainee/attendance";
    }
}
