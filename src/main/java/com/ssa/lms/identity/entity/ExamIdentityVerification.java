package com.ssa.lms.identity.entity;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.exam.entity.ExamAttempt;
import com.ssa.lms.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 한 <b>응시(ExamAttempt)</b>가 통과한 신분확인 증거의 <b>불변 스냅샷</b> (P0-1).
 *
 * <p><b>왜 세션만으로는 부족했나</b><br>
 * {@link ExamIdentitySession} 은 {@code (exam, user)} 당 한 행이고, 재제출·만료 후
 * {@code reopen()} 하면 승인 시각·문서 포인터가 <b>덮어써진다</b>. 그래서 첫 응시가 무엇을
 * 근거로 입장했는지가 두 번째 검증에 지워졌다. 세션에 {@code attempt} 를 하나만 두던 이전
 * 구조는 두 번째 attempt 를 조용히 무시해, LXP-016 의 사후 감사가 거짓이 됐다.</p>
 *
 * <p><b>모델</b><br>
 * 세션 = "지금 진행 중인 검증 작업대"(가변).
 * 이 엔티티 = "attempt N 이 입장할 때 실제로 통과했던 증거"(불변, attempt 당 정확히 한 행).
 * 시험 시작이 성공한 순간 세션의 현재 승인 증거를 복사해 만든다. 이후 세션이 재제출·만료로
 * 초기화돼도 이 행은 그대로 남는다.</p>
 *
 * <p>문서 자체({@link ExamIdentityDocument})는 원래부터 append-only 이므로 참조만 붙잡아 두면
 * 파일·해시·업로드 시각이 함께 보존된다. 별도 파일 복사는 하지 않는다.</p>
 *
 * <p>같은 승인(유효시간 30분) 안에서 두 번 입장하면 두 행이 같은 {@code cycleNo} 와 같은
 * 문서를 가리킨다. 그것이 사실이기 때문이다 — 두 응시가 같은 검증 주기를 근거로 입장했다.</p>
 */
@Entity
@Table(
        name = "exam_identity_verification",
        /* attempt 당 증거는 정확히 하나. 중복 삽입은 DB 가 막는다. */
        uniqueConstraints = @UniqueConstraint(
                name = "uk_identity_verification_attempt", columnNames = "attempt_id"),
        indexes = {
                @Index(name = "idx_identity_verification_session", columnList = "session_id"),
                @Index(name = "idx_identity_verification_attempt", columnList = "attempt_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamIdentityVerification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 이 증거를 만든 검증 세션. 세션은 이후 재사용되므로 현재 상태와 다를 수 있다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamIdentitySession session;

    /** 이 증거로 입장한 응시 회차. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    /**
     * 몇 번째 검증 주기인가. 세션의 승인 횟수를 그대로 복사한다.
     * 재응시를 위해 다시 검증받았으면 값이 올라간다.
     */
    @Column(name = "cycle_no", nullable = false)
    private int cycleNo;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approval_expires_at")
    private LocalDateTime approvalExpiresAt;

    /** 승인 근거가 된 신분증. 세션 포인터가 바뀌어도 이 참조는 그대로다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_document_id")
    private ExamIdentityDocument idDocument;

    /** 승인 근거가 된 입장 직전 얼굴 사진. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "face_document_id")
    private ExamIdentityDocument faceDocument;

    @Column(name = "face_consent_at")
    private LocalDateTime faceConsentAt;

    @Column(name = "face_consent_version", length = 40)
    private String faceConsentVersion;

    /**
     * 입장 시점의 웹캠 점검 기록 시각.
     *
     * <p><b>보안 증명이 아니다.</b> 서버는 영상 프레임을 받은 적이 없다 — 브라우저가 수행한
     * 점검 결과를 서버가 받아 적은 시각일 뿐이다. 감사에서 이 값을 "카메라가 켜져 있었다" 는
     * 증거로 쓰면 안 된다.</p>
     */
    @Column(name = "webcam_checked_at")
    private LocalDateTime webcamCheckedAt;

    /** 이 증거가 attempt 에 연결된 시각(= 시험 시작 시각). */
    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @Builder
    private ExamIdentityVerification(ExamIdentitySession session, ExamAttempt attempt, int cycleNo,
                                     LocalDateTime approvedAt, User approvedBy,
                                     LocalDateTime approvalExpiresAt,
                                     ExamIdentityDocument idDocument, ExamIdentityDocument faceDocument,
                                     LocalDateTime faceConsentAt, String faceConsentVersion,
                                     LocalDateTime webcamCheckedAt, LocalDateTime linkedAt) {
        this.session = session;
        this.attempt = attempt;
        this.cycleNo = cycleNo;
        this.approvedAt = approvedAt;
        this.approvedBy = approvedBy;
        this.approvalExpiresAt = approvalExpiresAt;
        this.idDocument = idDocument;
        this.faceDocument = faceDocument;
        this.faceConsentAt = faceConsentAt;
        this.faceConsentVersion = faceConsentVersion;
        this.webcamCheckedAt = webcamCheckedAt;
        this.linkedAt = linkedAt;
    }

    /** 세션의 <b>현재</b> 승인 증거를 그대로 복사한다. 호출 시점에 세션은 APPROVED 여야 한다. */
    public static ExamIdentityVerification snapshotOf(ExamIdentitySession s, ExamAttempt attempt,
                                                      LocalDateTime now) {
        return ExamIdentityVerification.builder()
                .session(s)
                .attempt(attempt)
                .cycleNo(s.getApprovalCount())
                .approvedAt(s.getApprovedAt())
                .approvedBy(s.getApprovedBy())
                .approvalExpiresAt(s.getApprovalExpiresAt())
                .idDocument(s.getCurrentDocument())
                .faceDocument(s.getFaceCheckDocument())
                .faceConsentAt(s.getFaceConsentAt())
                .faceConsentVersion(s.getFaceConsentVersion())
                .webcamCheckedAt(s.getWebcamCheckedAt())
                .linkedAt(now)
                .build();
    }
}
