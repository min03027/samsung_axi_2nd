package com.ssa.lms.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 신분증·얼굴 사진 보존기간 파기 배치 (지적 9.3).
 *
 * <p>주기는 프로퍼티 기본값으로 둔다 — 공용 {@code application*.yml} 은 이번 작업의
 * 수정 금지 대상이라 여기에 기본값을 박고, 운영에서 환경변수로 덮을 수 있게 한다.</p>
 *
 * <p>스케줄링 활성화는 기존 {@code NotificationSchedulingConfig} 의 {@code @EnableScheduling} 을
 * 그대로 쓴다 — 새 설정을 추가하지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityRetentionScheduler {

    private final ExamIdentityService identityService;

    /** 기본 1시간마다. {@code lms.identity.purge-interval-ms} 로 조정한다. */
    @Scheduled(fixedDelayString = "${lms.identity.purge-interval-ms:3600000}",
               initialDelayString = "${lms.identity.purge-initial-delay-ms:60000}")
    public void purgeExpired() {
        try {
            int purged = identityService.purgeExpiredDocuments();
            if (purged > 0) {
                log.info("신분확인 자료 보존기간 파기: {}건", purged);
            }
        } catch (Exception e) {
            /* 배치가 죽으면 다음 주기에 다시 시도한다 — 애플리케이션은 계속 떠 있어야 한다. */
            log.error("신분확인 자료 파기 배치 실패", e);
        }
    }
}
