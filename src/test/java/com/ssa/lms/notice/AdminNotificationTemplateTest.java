package com.ssa.lms.notice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminNotificationTemplateTest {

    @Test
    void 수신_읽음_열은_수신자수_다음_읽은수를_표시한다() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/templates/admin/admin-07-notice/admin-alarm.html")) {
            assertThat(input).isNotNull();
            String template = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(template)
                    .contains("${row.recipientCount} + ' / ' + ${row.readCount}")
                    .doesNotContain("${row.readCount} + ' / ' + ${row.recipientCount}");
        }
    }

    @Test
    void 관리자_상태는_개인_읽음이_아닌_전체_수신자_집계로_표시한다() {
        assertThat(row(10, 0).aggregateReadStatus()).isEqualTo("읽지 않음");
        assertThat(row(10, 1).aggregateReadStatus()).isEqualTo("일부 읽음");
        assertThat(row(10, 10).aggregateReadStatus()).isEqualTo("전체 읽음");
        assertThat(row(0, 0).aggregateReadStatus()).isEqualTo("수신자 없음");
    }

    private static com.ssa.lms.notice.dto.NotificationListRow row(long recipients, long reads) {
        return new com.ssa.lms.notice.dto.NotificationListRow(
                1L, "발송완료", "-", "제목", "내용", "2026-08-26",
                "높음", "관리자", "/trainee/alarm", recipients, reads);
    }
}
