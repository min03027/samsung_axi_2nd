package com.ssa.lms.identity.entity;

import com.ssa.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 제출된 이미지 한 장 (신분증 또는 입장 직전 얼굴 확인용 사진).
 *
 * <p>파일 자체는 웹 루트 밖 비공개 저장소에 있고, 여기에는 <b>메타데이터만</b> 남는다.
 * {@code storageKey} 는 클라이언트에 절대 내려보내지 않는다 — 키를 알면 권한 검사를
 * 우회할 여지가 생긴다. 화면에는 document id 만 주고, 스트리밍 엔드포인트가
 * 매 요청마다 권한을 다시 본다.</p>
 */
@Entity
@Table(
        name = "exam_identity_document",
        indexes = @Index(name = "idx_identity_doc_session", columnList = "session_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamIdentityDocument extends BaseEntity {

    public enum Kind {
        /** 모바일에서 올린 신분증 앞면. */
        ID_CARD,
        /** 사전점검 통과 후 훈련생이 직접 촬영·제출한 얼굴 확인용 정지 이미지. */
        FACE_CHECK
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamIdentitySession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, nullable = false)
    private Kind kind;

    /** 비공개 저장소 내부 키. 외부 노출 금지. */
    @Column(name = "storage_key", length = 300, nullable = false)
    private String storageKey;

    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_from_ip", length = 45)
    private String uploadedFromIp;

    /** 보존기간 만료 시각. 배치가 이 시각을 지난 파일을 지운다. */
    @Column(name = "purge_after")
    private LocalDateTime purgeAfter;

    @Column(name = "purged_at")
    private LocalDateTime purgedAt;

    @Builder
    private ExamIdentityDocument(ExamIdentitySession session, Kind kind, String storageKey,
                                 String contentType, long sizeBytes, String sha256,
                                 Integer width, Integer height, LocalDateTime uploadedAt,
                                 String uploadedFromIp, LocalDateTime purgeAfter) {
        this.session = session;
        this.kind = kind;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.width = width;
        this.height = height;
        this.uploadedAt = uploadedAt;
        this.uploadedFromIp = uploadedFromIp;
        this.purgeAfter = purgeAfter;
    }

    public boolean isPurged() {
        return purgedAt != null;
    }

    public void markPurged(LocalDateTime now) {
        this.purgedAt = now;
    }
}
