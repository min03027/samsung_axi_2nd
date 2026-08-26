package com.ssa.lms.notice;

import com.ssa.lms.notice.service.GrowthReportService;
import com.ssa.lms.notice.entity.GrowthReportSetting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class GrowthReportSettingTest {
    @Autowired GrowthReportService growthReportService;

    @Test
    void 관리자가_발송주기와_시각을_자유롭게_저장한다() {
        var setting = growthReportService.saveSetting(true, 5, 14, 12);
        assertThat(setting.getIntervalDays()).isEqualTo(5);
        assertThat(setting.getSendHour()).isEqualTo(14);
        assertThat(setting.getLowProgressGap()).isEqualTo(12);
        GrowthReportSetting fresh = GrowthReportSetting.createDefault();
        fresh.update(true, 5, 14, 12);
        assertThat(fresh.isDue(LocalDateTime.of(2026, 8, 26, 14, 0))).isTrue();
    }

    @Test
    void 관리자는_자동발송_시각을_기다리지_않고_현재_리포트를_보낼_수_있다() {
        growthReportService.saveSetting(false, 7, 23, 10);

        int sent = growthReportService.sendNow(LocalDateTime.of(2026, 8, 26, 9, 0));

        assertThat(sent).isPositive();
    }
}
