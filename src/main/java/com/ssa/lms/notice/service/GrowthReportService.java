package com.ssa.lms.notice.service;

import com.ssa.lms.dashboard.dto.TraineeDashboardView;
import com.ssa.lms.dashboard.service.TraineeDashboardService;
import com.ssa.lms.notice.dto.GrowthReportView;
import com.ssa.lms.notice.entity.GrowthReportSetting;
import com.ssa.lms.notice.entity.Notification;
import com.ssa.lms.notice.repository.GrowthReportSettingRepository;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 진도·권장 진도·출석·미완료 항목을 묶어 화면과 이메일에 같은 리포트를 제공한다. */
@Service
@RequiredArgsConstructor
public class GrowthReportService {
    private final GrowthReportSettingRepository settingRepository;
    private final UserRepository userRepository;
    private final TraineeDashboardService dashboardService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public GrowthReportSetting currentSetting() {
        return settingRepository.findById(GrowthReportSetting.SINGLETON_ID)
                .orElseGet(GrowthReportSetting::createDefault);
    }

    @Transactional
    public GrowthReportSetting saveSetting(boolean enabled, int intervalDays, int sendHour,
                                           int lowProgressGap) {
        GrowthReportSetting setting = settingRepository.findById(GrowthReportSetting.SINGLETON_ID)
                .orElseGet(GrowthReportSetting::createDefault);
        setting.update(enabled, intervalDays, sendHour, lowProgressGap);
        return settingRepository.save(setting);
    }

    @Transactional(readOnly = true)
    public GrowthReportView current(Long userId, String userName) {
        return toView(dashboardService.load(userId, userName), currentSetting().getLowProgressGap());
    }

    @Transactional
    public int sendDue(LocalDateTime now) {
        return sendReports(now, false);
    }

    /** 관리자 시연·운영 확인용 즉시 발송. 자동 발송 주기와 무관하게 현재 값을 집계한다. */
    @Transactional
    public int sendNow(LocalDateTime now) {
        return sendReports(now, true);
    }

    private int sendReports(LocalDateTime now, boolean force) {
        GrowthReportSetting setting = settingRepository.findById(GrowthReportSetting.SINGLETON_ID)
                .orElseGet(() -> settingRepository.save(GrowthReportSetting.createDefault()));
        if (!force && !setting.isDue(now)) return 0;

        int sent = 0;
        for (User user : userRepository.findByRoleAndStatusOrderByNameAsc(Role.TRAINEE, UserStatus.ACTIVE)) {
            GrowthReportView report = toView(dashboardService.load(user.getId(), user.getName()),
                    setting.getLowProgressGap());
            if (report.courseName() == null || "배정된 과정 없음".equals(report.courseName())) continue;
            String title = "[주간 성장 리포트] " + user.getName() + "님의 학습 현황";
            String content = """
                    과정: %s
                    현재 진도: %d%% / 권장 진도: %s
                    출석률: %s
                    남은 과제·시험: %d건

                    %s
                    """.formatted(report.courseName(), report.progressRate(), percent(report.recommendedProgress()),
                    percent(report.attendanceRate()), report.pendingCount(), report.summary());
            notificationService.dispatchPersonal(user, title, content,
                    Notification.NotificationKind.GROWTH_REPORT, "/trainee/growth", true);
            sent++;
        }
        setting.markSent(now);
        return sent;
    }

    private GrowthReportView toView(TraineeDashboardView dashboard, int lowGap) {
        TraineeDashboardView.Assurance a = dashboard.assurance();
        int gap = a.recommendedProgress() == null ? 0 : a.progressRate() - a.recommendedProgress();
        String tone = gap <= -lowGap || (a.attendanceRate() != null && a.attendanceRate() < 80)
                ? "support" : (gap < 0 ? "care" : "steady");
        String summary = switch (tone) {
            case "support" -> "학습 속도가 계획보다 늦어요. 이번 주 학습 계획을 담당 강사와 함께 확인해 보세요.";
            case "care" -> "권장 진도까지 조금 남았어요. 가장 가까운 과제와 학습부터 이어가면 됩니다.";
            default -> "현재 학습 흐름이 계획에 맞게 유지되고 있어요. 지금의 페이스를 이어가세요.";
        };
        return new GrowthReportView(a.courseName(), a.progressRate(), a.recommendedProgress(),
                a.attendanceRate(), a.totalTodoCount(), a.remainingAssignments(), a.remainingExams(),
                gap, tone, summary, a.primaryActionLabel(), a.primaryActionHref());
    }

    private static String percent(Integer value) { return value == null ? "집계 전" : value + "%"; }
}
