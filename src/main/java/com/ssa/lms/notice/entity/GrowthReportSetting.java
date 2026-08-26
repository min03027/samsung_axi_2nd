package com.ssa.lms.notice.entity;

import com.ssa.lms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 기관 공통 성장 리포트 발송 설정. */
@Entity
@Table(name = "growth_report_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrowthReportSetting extends BaseEntity {
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays = 7;

    @Column(name = "send_hour", nullable = false)
    private int sendHour = 9;

    @Column(name = "low_progress_gap", nullable = false)
    private int lowProgressGap = 10;

    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    public static GrowthReportSetting createDefault() { return new GrowthReportSetting(); }

    public void update(boolean enabled, int intervalDays, int sendHour, int lowProgressGap) {
        this.enabled = enabled;
        this.intervalDays = Math.max(1, Math.min(30, intervalDays));
        this.sendHour = Math.max(0, Math.min(23, sendHour));
        this.lowProgressGap = Math.max(0, Math.min(50, lowProgressGap));
    }

    public boolean isDue(LocalDateTime now) {
        return enabled && now.getHour() >= sendHour
                && (lastSentAt == null || !lastSentAt.plusDays(intervalDays).isAfter(now));
    }

    public void markSent(LocalDateTime now) { this.lastSentAt = now; }
}
