package com.ssa.lms.identity.entity;

import com.ssa.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * QR 일회용 토큰 (LXP-015).
 *
 * <p>QR 에는 이 토큰의 <b>원문</b>과 모바일 URL 만 들어간다. userId·examId 같은 식별자는
 * 넣지 않는다 — QR 사진 한 장이 유출되면 그대로 개인정보가 되기 때문이다.
 * DB 에는 원문 대신 SHA-256 <b>해시</b>만 저장한다. 저장소가 털려도 토큰을 재현할 수 없다.</p>
 *
 * <p>새 QR 을 발급하면 이전 토큰은 즉시 {@link #revoke} 된다. 화면에 QR 이 두 개 떠 있는데
 * 둘 다 살아 있으면, 먼저 찍어둔 QR 로 나중에 아무나 올릴 수 있다.</p>
 */
@Entity
@Table(
        name = "exam_identity_token",
        indexes = {
                @Index(name = "idx_identity_token_hash", columnList = "token_hash", unique = true),
                @Index(name = "idx_identity_token_session", columnList = "session_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamIdentityToken extends BaseEntity {

    /** 토큰 수명(분). 짧게 잡는다 — QR 은 촬영되어 남을 수 있다. */
    public static final int TTL_MINUTES = 10;

    /** 한 토큰으로 올릴 수 있는 횟수. 업로드 실패 재시도를 감안해 1보다 크게 둔다. */
    public static final int DEFAULT_MAX_USE = 3;

    /** 토큰 검증 실패 사유 — 화면 안내를 사유별로 다르게 하려고 구분한다. */
    public enum Rejection {
        NOT_FOUND,
        EXPIRED,
        REVOKED,
        USED_UP,
        SESSION_CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamIdentitySession session;

    /** SHA-256 hex. 원문은 저장하지 않는다. */
    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "first_used_at")
    private LocalDateTime firstUsedAt;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "max_use", nullable = false)
    private int maxUse;

    @Column(name = "issued_to_ip", length = 45)
    private String issuedToIp;

    @Builder
    private ExamIdentityToken(ExamIdentitySession session, String tokenHash,
                              LocalDateTime issuedAt, String issuedToIp, Integer maxUse) {
        this.session = session;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = issuedAt.plusMinutes(TTL_MINUTES);
        this.issuedToIp = issuedToIp;
        this.maxUse = (maxUse == null || maxUse < 1) ? DEFAULT_MAX_USE : maxUse;
        this.useCount = 0;
    }

    /**
     * 지금 이 토큰을 쓸 수 있는지. 쓸 수 없으면 사유를 돌려준다.
     * null 이면 사용 가능.
     */
    public Rejection reject(LocalDateTime now) {
        if (revokedAt != null) return Rejection.REVOKED;
        if (!now.isBefore(expiresAt)) return Rejection.EXPIRED;
        if (useCount >= maxUse) return Rejection.USED_UP;
        if (!session.acceptsSubmission()) return Rejection.SESSION_CLOSED;
        return null;
    }

    public boolean isUsable(LocalDateTime now) {
        return reject(now) == null;
    }

    /** 업로드가 실제로 성공했을 때만 소모한다. 화면만 열어본 것은 세지 않는다. */
    public void consume(LocalDateTime now) {
        if (firstUsedAt == null) {
            this.firstUsedAt = now;
        }
        this.useCount++;
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public long remainingSeconds(LocalDateTime now) {
        if (now.isAfter(expiresAt)) return 0;
        return java.time.Duration.between(now, expiresAt).getSeconds();
    }
}
