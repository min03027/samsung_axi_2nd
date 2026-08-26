package com.ssa.lms.notice;

import com.ssa.lms.notice.entity.ReminderLog;
import com.ssa.lms.notice.service.NotificationService;
import com.ssa.lms.notice.service.ReminderService;
import com.ssa.lms.notice.service.GrowthReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 예약 알림 발송 스케줄러.
 *
 * <p>지금까지 {@code Notification.sendAt} 은 저장만 되고 시각이 도래해도 아무 일이 없었다.
 * 관리자가 화면에서 "예약"으로 저장한 알림이 영영 나가지 않는 상태였다.</p>
 *
 * <p><b>주기를 1분으로 잡은 이유:</b> 알림은 분 단위 정밀도면 충분하고, 더 촘촘히 돌면
 * 발송할 게 없어도 매번 조회 쿼리가 나간다. 반대로 더 길게 잡으면 "10:00 발송"으로
 * 예약한 알림이 10:05에 나가 사용자가 예약이 고장 났다고 느낀다.</p>
 *
 * <p><b>다중 인스턴스 주의:</b> 서버를 2대 이상 띄우면 양쪽에서 동시에 돌아 같은 알림을
 * 두 번 처리하려 한다. 지금은 {@code uk_notification_recipient} 유니크 제약과
 * {@code fanOut()} 의 중복 검사가 막아주지만, 이중화 시점에는 분산 락(ShedLock 등)이
 * 필요하다. 인프라 구성이 정해지면 재검토할 것.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationService notificationService;
    private final ReminderService reminderService;
    private final GrowthReportService growthReportService;

    /** 테스트·로컬에서 끌 수 있게 스위치를 둔다. 기본 활성. */
    @Value("${lms.scheduler.notification.enabled:true}")
    private boolean enabled;

    /** 리마인드는 별도로 끌 수 있게 한다 — 시드 데이터로 대량 발송되는 걸 막을 때 쓴다. */
    @Value("${lms.scheduler.reminder.enabled:true}")
    private boolean reminderEnabled;

    @Scheduled(fixedDelayString = "${lms.scheduler.notification.interval-ms:60000}")
    public void dispatchScheduledNotifications() {
        if (!enabled) {
            return;
        }
        try {
            int sent = notificationService.dispatchDue(LocalDateTime.now());
            if (sent > 0) {
                log.info("예약 알림 {}건 발송", sent);
            }
        } catch (RuntimeException e) {
            // 스케줄러 스레드에서 예외가 새면 이후 주기가 통째로 멈춘다. 반드시 삼킨다.
            log.error("예약 알림 발송 중 오류", e);
        }
    }

    /**
     * 미제출·미응시·미응답자 리마인드.
     *
     * <p>주기를 1시간으로 잡은 이유: 마감 24시간 전/1시간 전을 잡아내는 데 이 정도면 충분하고,
     * {@code ReminderService} 가 "마감이 [now+lead, now+lead+1시간)" 구간인 것만 고르도록
     * 되어 있어 주기와 구간 폭이 맞아야 빠짐없이 잡힌다. 주기를 늘리면 그 사이 마감분을 놓친다.</p>
     *
     * <p>중복 발송은 {@code ReminderLog} 가 막는다.</p>
     */
    @Scheduled(fixedDelayString = "${lms.scheduler.reminder.interval-ms:3600000}")
    public void sendReminders() {
        if (!reminderEnabled) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            int total = 0;
            for (ReminderLog.ReminderStage stage : ReminderLog.ReminderStage.values()) {
                total += reminderService.remindDue(now, stage);
            }
            total += growthReportService.sendDue(now);
            if (total > 0) {
                log.info("리마인드 알림 {}건 발송", total);
            }
        } catch (RuntimeException e) {
            log.error("리마인드 발송 중 오류", e);
        }
    }
}
