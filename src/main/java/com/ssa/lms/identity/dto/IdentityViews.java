package com.ssa.lms.identity.dto;

import java.time.LocalDateTime;

/**
 * 화면에 내려보내는 값 모음.
 *
 * <p>엔티티를 그대로 컨트롤러까지 올리면 지연 로딩 프록시가 트랜잭션 밖에서 터진다
 * (LazyInitializationException). 서비스가 트랜잭션 안에서 필요한 값만 뽑아 담는다.
 * 저장 키(storageKey)처럼 밖으로 나가면 안 되는 값은 여기에 넣지 않는다.</p>
 */
public final class IdentityViews {

    private IdentityViews() {
    }

    /** PC 사전점검 화면 상단. */
    public record Precheck(Long sessionId, Long examId, String examTitle,
                           String traineeName, String status) {
    }

    /** QR 로 열린 모바일 화면. */
    public record Mobile(boolean blocked, String message, String reason,
                         String examTitle, String traineeName,
                         String status, String statusLabel, String decisionReason,
                         long remainingSeconds,
                         boolean hasIdCard, boolean hasFaceCheck) {

        public static Mobile blocked(String message, String reason) {
            return new Mobile(true, message, reason, null, null, null, null, null, 0, false, false);
        }
    }

    /**
     * PC·모바일 공용 상태 응답.
     *
     * <p><b>{@code hasIdCard} 와 {@code hasFaceCheck} 를 모두 내려보낸다</b> (P1-2).
     * 예전에는 얼굴 여부만 있어서, 화면이 "신분증만 제출" 과 "아무것도 제출 안 함" 을
     * 구별하지 못하고 둘 다 "미제출" 로 찍었다.</p>
     *
     * @param submissionComplete 두 자료 + 동의 증거가 모두 갖춰져 검토 대기로 올라갔는가
     */
    public record Status(String status, String statusLabel, boolean canEnter,
                         String reason, int resubmitCount,
                         boolean hasIdCard, boolean hasFaceCheck, boolean submissionComplete,
                         LocalDateTime approvalExpiresAt) {
    }

    /** 운영진 대기열 한 줄 + 상세 공용. */
    public record Row(Long sessionId, String examTitle, String traineeName, String loginId,
                      String submittedAt, String waited, String status, String statusLabel,
                      String tone, String reason, int resubmitCount,
                      Long idDocumentId, Long faceDocumentId, Long attemptId) {
    }
}
