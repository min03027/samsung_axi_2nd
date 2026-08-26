package com.ssa.lms.notice.dto;

import com.ssa.lms.notice.entity.Notification;

import java.time.format.DateTimeFormatter;

/**
 * 알림 내역 한 행 (admin-alarm.html).
 *
 * 테이블 헤더와 1:1 대응: 상태 / 번호 / 제목 / 내용 / 날짜 / 중요도 / 작성자
 * 원본 더미: admin-alarm.html 인라인 스크립트의 alarmHistory 배열.
 *
 * @param readStatus 화면 표기 "읽음" / "읽지않음" — 로그인 사용자 본인의 수신 상태.
 *                   수신자가 아니면(관리자가 남의 알림을 보는 경우) "-".
 */
public record NotificationListRow(
        Long id,
        String status,
        String readStatus,
        String title,
        String content,
        String date,
        String importance,
        String author,
        String actionUrl,
        long recipientCount,
        long readCount
) {

    /** 관리자 알림 목록에서 수신자 전체의 읽음 진행 상태를 표시한다. */
    public String aggregateReadStatus() {
        if (recipientCount == 0) return "수신자 없음";
        if (readCount == 0) return "읽지 않음";
        if (readCount >= recipientCount) return "전체 읽음";
        return "일부 읽음";
    }

    public static NotificationListRow of(Notification n, String readStatus,
                                         long recipientCount, long readCount) {
        return new NotificationListRow(
                n.getId(),
                statusLabel(n.getStatus()),
                readStatus,
                n.getTitle(),
                n.getContent(),
                n.getSendAt() == null ? "-" : n.getSendAt().format(DateTimeFormatter.ISO_LOCAL_DATE),
                importanceLabel(n.getPriority()),
                n.getSender().getName(),
                n.getSourceUrl() == null || !n.getSourceUrl().startsWith("/trainee/")
                        ? "/trainee/alarm" : n.getSourceUrl(),
                recipientCount,
                readCount
        );
    }

    public static String statusLabel(Notification.NotificationStatus s) {
        return switch (s) {
            case DRAFT -> "임시저장";
            case SCHEDULED -> "예약";
            case SENT -> "발송완료";
            case CANCELED -> "취소";
        };
    }

    /** 화면은 높음/중간/낮음 3단계만 쓴다. URGENT 는 "긴급"으로 따로 보여준다. */
    public static String importanceLabel(Notification.Priority p) {
        return switch (p) {
            case URGENT -> "긴급";
            case HIGH -> "높음";
            case NORMAL -> "중간";
            case LOW -> "낮음";
        };
    }
}
