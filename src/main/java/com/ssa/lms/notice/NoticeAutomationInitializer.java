package com.ssa.lms.notice;

import com.ssa.lms.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 기존 게시 공지의 팝업 설정을 배포 직후 실제 알림 수신 데이터로 보정한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeAutomationInitializer {
    private final NoticeService noticeService;

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizePublishedPopups() {
        int count = noticeService.synchronizePublishedPopups();
        if (count > 0) log.info("게시 공지 로그인 팝업 {}건 동기화", count);
    }
}
