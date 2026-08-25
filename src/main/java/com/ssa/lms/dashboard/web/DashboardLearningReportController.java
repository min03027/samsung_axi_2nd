package com.ssa.lms.dashboard.web;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.dashboard.service.DashboardLearningReportService;
import com.ssa.lms.export.ExcelDownload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 역할별 조회 범위를 유지하는 분반별 학습 현황 엑셀 다운로드. */
@RestController
@RequiredArgsConstructor
public class DashboardLearningReportController {

    private final DashboardLearningReportService reportService;

    @GetMapping("/admin/dashboard/learning-report.xlsx")
    public ResponseEntity<byte[]> adminReport(@RequestParam(required = false) Long courseId) {
        return ExcelDownload.attachment(
                "분반별_학습현황_" + LocalDate.now(), reportService.forAdmin(courseId));
    }

    @GetMapping("/instructor/dashboard/learning-report.xlsx")
    public ResponseEntity<byte[]> instructorReport(
            @RequestParam(required = false) Long courseId,
            @AuthenticationPrincipal LoginUser loginUser) {
        return ExcelDownload.attachment(
                "담당분반_학습현황_" + LocalDate.now(),
                reportService.forInstructor(loginUser.getId(), courseId));
    }
}
