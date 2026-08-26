package com.ssa.lms.dashboard.web;

import com.ssa.lms.dashboard.service.DropoutPredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 관리자 학습 분석 — 이탈(중도탈락) 예측 대시보드.
 * 경로 {@code /admin/analytics/**} 는 SecurityConfig 의 {@code /admin/**}(ADMIN) 규칙으로 커버된다.
 */
@Controller
@RequestMapping("/admin/analytics")
@RequiredArgsConstructor
public class DropoutAdminController {

    private static final String VIEW = "admin/admin-08-analytics/admin-dropout";

    private final DropoutPredictionService dropoutPredictionService;

    @GetMapping("/dropout")
    public String dropout(Model model) {
        model.addAttribute("prediction", dropoutPredictionService.forAdmin());
        return VIEW;
    }

    @GetMapping("/dropout/trainees/{loginId}")
    public String traineeDetail(@PathVariable String loginId, Model model) {
        var prediction = dropoutPredictionService.forAdmin();
        var trainee = prediction.riskTrainees().stream()
                .filter(candidate -> candidate.loginId().equals(loginId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "위험 학습자를 찾을 수 없습니다."));
        model.addAttribute("trainee", trainee);
        model.addAttribute("learningMetrics", List.of(
                new Metric("출석률", trainee.riskScore() >= 70 ? "64%" : "78%", "목표 80%"),
                new Metric("테스트 평균", trainee.riskScore() >= 70 ? "58점" : "72점", "최근 3회"),
                new Metric("과제 제출", trainee.riskScore() >= 70 ? "5 / 8" : "7 / 8", "누적 제출"),
                new Metric("코드 실행", trainee.riskScore() >= 70 ? "21회" : "46회", "최근 4주")
        ));
        model.addAttribute("interventions", List.of(
                new Intervention("08.25", "학습 독려", "담당 강사", "미접속 사유 확인 메시지 발송", "응답 대기"),
                new Intervention("08.19", "과제 피드백", "멘토 김서현", "미제출 과제 우선순위 안내", "완료")
        ));
        return "admin/admin-08-analytics/admin-dropout-trainee";
    }

    @GetMapping("/interventions")
    public String interventions(Model model) {
        model.addAttribute("prediction", dropoutPredictionService.forAdmin());
        return "admin/admin-08-analytics/admin-interventions";
    }

    @GetMapping("/learning")
    public String learningAnalytics(Model model) {
        model.addAttribute("prediction", dropoutPredictionService.forAdmin());
        model.addAttribute("classes", List.of(
                new ClassMetric("백엔드 심화 · A반", 16, 71, 74, 68, 3),
                new ClassMetric("데이터 분석 실무 · B반", 14, 66, 79, 72, 2),
                new ClassMetric("프론트엔드 실무 · A반", 18, 83, 88, 81, 0)
        ));
        return "admin/admin-08-analytics/admin-learning-analytics";
    }

    public record Metric(String label, String value, String note) {}
    public record Intervention(String date, String type, String owner, String note, String status) {}
    public record ClassMetric(String name, int trainees, int progress, int attendance, int testAverage, int highRisk) {}
}
