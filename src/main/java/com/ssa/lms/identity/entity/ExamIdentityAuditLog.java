package com.ssa.lms.identity.entity;

import com.ssa.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 신분확인 감사 로그 (LXP-016).
 *
 * <p>"누가 언제 남의 신분증을 열어 봤는가" 에 답할 수 있어야 한다. 개인정보 요건의 핵심이라
 * <b>조회(VIEW)까지</b> 남긴다. 판정 기록은 세션 엔티티에도 있지만, 세션은 최신 상태만
 * 들고 있어서 이력이 덮인다 — 그래서 append-only 로 따로 쌓는다.</p>
 *
 * <p>이미지 파일이 보존기간으로 파기돼도 이 로그는 남는다(파기 사실 자체가 증빙이다).</p>
 */
@Entity
@Table(
        name = "exam_identity_audit_log",
        indexes = {
                @Index(name = "idx_identity_audit_session", columnList = "session_id"),
                @Index(name = "idx_identity_audit_actor", columnList = "actor_user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamIdentityAuditLog extends BaseEntity {

    public enum Action {
        ISSUE_QR,
        SUBMIT_ID_CARD,
        SUBMIT_FACE_CHECK,
        /** 운영진이 이미지를 실제로 열어 봤다. */
        VIEW_IMAGE,
        OPEN_REVIEW,
        APPROVE,
        REJECT,
        REQUEST_RESUBMIT,
        /** 시험 시작이 성공해 이 응시의 증거 스냅샷이 만들어졌다 (P0-1). */
        LINK_ATTEMPT,
        PURGE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "document_id")
    private Long documentId;

    /** 행위자. 비로그인 모바일 업로드는 null 이고 토큰이 신원을 보장한다. */
    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 30, nullable = false)
    private Action action;

    /** 반려·재제출 사유. 판정 계열 action 에서는 필수로 채운다. */
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "ip", length = 45)
    private String ip;

    @Builder
    private ExamIdentityAuditLog(Long sessionId, Long documentId, Long actorUserId, String actorRole,
                                 Action action, String reason, LocalDateTime occurredAt, String ip) {
        this.sessionId = sessionId;
        this.documentId = documentId;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.action = action;
        this.reason = reason;
        this.occurredAt = occurredAt;
        this.ip = ip;
    }
}
