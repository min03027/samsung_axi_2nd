package com.ssa.lms.identity.entity;

import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.exam.entity.Exam;
import com.ssa.lms.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 응시 전 신분확인 세션 (LXP-015 / LXP-016).
 *
 * <p><b>왜 ExamAttempt 가 아니라 exam + user 에 묶는가</b><br>
 * QR 발급·신분증 제출은 시험 <i>시작 전</i>에 일어난다. 그런데 {@code ExamAttemptService.start()}
 * 는 attempt 를 만든 <i>뒤</i> 서버가 ENTER 를 기록하는 순서라, attempt 가 아직 없다.
 * 토큰을 attempt 에 묶으면 "attempt 를 만들려면 신분확인이 필요하고, 신분확인을 하려면
 * attempt 가 필요한" 순환이 된다. 그래서 (examId, userId) 에 바인딩하고,
 * start() 가 성공한 뒤에 {@link ExamIdentityVerification} 스냅샷으로 attempt 를 <b>사후 연결</b>한다.</p>
 *
 * <p><b>이 엔티티는 "지금 진행 중인 검증 작업대" 다.</b> 재제출·만료로 초기화되며 값이 덮어써진다.
 * 응시별 감사 증거는 {@link ExamIdentityVerification} 이 불변으로 보관한다 (P0-1).</p>
 *
 * <p>상태 전이는 {@link Status} 참고. <b>신분증과 얼굴 사진이 둘 다</b> 들어와야 {@code SUBMITTED}
 * 가 되고(한쪽만이면 {@code PENDING} 유지), 운영진 판정 없이 {@code APPROVED} 로 가는 경로는
 * 존재하지 않는다(자동 승인 금지).</p>
 */
@Entity
@Table(
        name = "exam_identity_session",
        /* (시험, 사용자) 당 세션은 <b>하나</b>. 동시 요청이 들어와도 DB 가 중복을 막는다 (지적 10).
           Java synchronized 는 인스턴스가 둘 이상이면 무의미하다.
           만료된 세션도 지우지 않고 같은 행을 reopen() 해서 이 제약을 유지한다. */
        uniqueConstraints = @UniqueConstraint(
                name = "uk_identity_session_exam_user", columnNames = {"exam_id", "user_id"}),
        indexes = {
                @Index(name = "idx_identity_session_exam_user", columnList = "exam_id, user_id"),
                @Index(name = "idx_identity_session_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamIdentitySession extends BaseEntity {

    /** 승인이 유효한 시간(분). 이 시간이 지나면 다시 신분확인을 받아야 입장할 수 있다. */
    public static final int APPROVAL_VALID_MINUTES = 30;

    /** 재제출 허용 횟수 상한. 넘으면 운영진이 세션을 새로 열어야 한다. */
    public static final int MAX_RESUBMIT = 3;

    /** 웹캠 점검 통과가 유효한 시간(분). 지나면 다시 점검해야 입장할 수 있다. */
    public static final int WEBCAM_VALID_MINUTES = 15;

    public enum Status {
        /** 아직 완전 제출이 아니다 — 미제출이거나 신분증·얼굴 중 <b>한쪽만</b> 들어온 상태. */
        PENDING,
        /** 신분증과 얼굴 사진이 <b>둘 다</b> 제출됐다. 운영진 판정 대기. */
        SUBMITTED,
        /** 운영진이 열어 검토 중임을 표시. */
        UNDER_REVIEW,
        /** 운영진 승인. 이 상태에서만 시험 입장이 열린다. */
        APPROVED,
        /** 운영진 <b>최종 반려</b>. 사유 필수. 더 이상 제출할 수 없다. */
        REJECTED,
        /** 운영진이 <b>재제출을 명시적으로 요청</b>한 상태. 이 상태에서만 다시 올릴 수 있다. */
        RESUBMIT_REQUIRED,
        /** 만료. 새 세션이 필요하다. */
        EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private Status status;

    /** 세션을 연 IP. 감사용. */
    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    /**
     * 승인 유효 만료 시각. 승인 시점에 계산해 박는다.
     * 화면이 보내는 값이 아니라 서버가 정한 값만 입장 판정에 쓴다.
     */
    @Column(name = "approval_expires_at")
    private LocalDateTime approvalExpiresAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    /** 반려·재제출 요청 사유. 두 판정 모두 필수다. */
    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "resubmit_count", nullable = false)
    private int resubmitCount;

    /** 제출된 최신 신분증. 재제출하면 새 문서로 교체된다(이전 문서는 superseded 로 남는다). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_document_id")
    private ExamIdentityDocument currentDocument;

    /**
     * 입장 직전 얼굴 확인용 정지 이미지. 훈련생이 명시적으로 촬영·제출한 것만 들어온다.
     * 자동 촬영·백그라운드 저장은 하지 않는다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "face_check_document_id")
    private ExamIdentityDocument faceCheckDocument;

    /** 얼굴 사진 제출 동의 시각. 동의 없이 저장하지 않았다는 증거다. */
    @Column(name = "face_consent_at")
    private LocalDateTime faceConsentAt;

    /** 동의 당시 안내 문구 버전. 문구가 바뀌면 무엇에 동의했는지 되짚을 수 있어야 한다. */
    @Column(name = "face_consent_version", length = 40)
    private String faceConsentVersion;

    /**
     * 웹캠 연결 점검을 통과한 시각. 브라우저가 보낸 pass 값을 그대로 믿지 않고
     * 서버가 시각을 찍어 두었다가 입장 시 신선도를 본다.
     *
     * <p><b>한계 (중요)</b>: 이 값은 <b>클라이언트가 수행한 점검 결과</b>를 서버가 받아 적은
     * <b>시각</b>일 뿐이다. 서버는 실제 영상 프레임을 본 적이 없다 — 이번 범위에 영상 송출이 없기 때문이다.
     * /webcam-checked 를 직접 호출하면 카메라 없이도 이 값이 찍힌다. 따라서 이 필드로
     * "서버가 카메라 연결을 검증했다" 고 말해서는 안 된다. 서버 검증이 필요하면
     * WebRTC 송출 같은 별도 수단이 있어야 한다.</p>
     */
    @Column(name = "webcam_checked_at")
    private LocalDateTime webcamCheckedAt;

    /**
     * 지금까지 이 세션이 받은 <b>승인 횟수</b>. 승인할 때마다 1 씩 오른다.
     *
     * <p>재응시를 위해 다시 검증받으면 값이 올라가고, 그 값이 각 응시의 증거 스냅샷
     * ({@link ExamIdentityVerification#getCycleNo()}) 에 복사된다. 그래서 두 응시가 서로 다른
     * 검증 주기를 통과했는지 한눈에 구분된다.</p>
     */
    @Column(name = "approval_count", nullable = false)
    private int approvalCount;

    @Builder
    private ExamIdentitySession(Exam exam, User user, String createdIp) {
        this.exam = exam;
        this.user = user;
        this.createdIp = createdIp;
        this.status = Status.PENDING;
        this.resubmitCount = 0;
    }

    /* ===================== 상태 전이 ===================== */

    /**
     * 신분증이 올라왔다. 자동 승인은 하지 않는다 — 반드시 운영진 판정을 기다린다.
     *
     * <p>{@link #acceptsSubmission()} 이 true 인 상태에서만 받는다. 검토 중이거나 이미
     * 판정이 끝난 세션의 파일을 교체할 수 있으면, 운영진이 본 것과 저장된 것이 달라진다.</p>
     */
    public void attachIdCard(ExamIdentityDocument document, LocalDateTime now) {
        requireOpen();
        this.currentDocument = document;
        this.decisionReason = null;
        promoteIfComplete();
    }

    /**
     * 신분증과 얼굴 사진이 <b>둘 다</b> 갖춰졌을 때만 검토 대기로 올린다.
     *
     * <p>한쪽만 들어왔다고 SUBMITTED 로 바꾸면, 화면에 적힌 순서(신분증 → 얼굴)대로 한 사람이
     * 두 번째 제출에서 거부당한다. 실제로 그렇게 막혀 있었다.</p>
     */
    private void promoteIfComplete() {
        if (isSubmissionComplete()) {
            this.status = Status.SUBMITTED;
        }
    }

    /** 승인 가능한 완전 제출 상태인가 — 두 문서 + 동의 증거가 모두 있어야 한다. */
    public boolean isSubmissionComplete() {
        return currentDocument != null
                && faceCheckDocument != null
                && faceConsentAt != null
                && faceConsentVersion != null && !faceConsentVersion.isBlank();
    }

    /** 운영진이 열어 검토를 시작했다. 대기열에서 "검토 중" 으로 보인다. */
    public void markUnderReview() {
        if (!isReviewable()) {
            throw new IdentitySessionStateException(
                    "신분증과 얼굴 확인용 사진이 모두 제출되어야 검토할 수 있습니다. 현재 상태: " + status);
        }
        this.status = Status.UNDER_REVIEW;
    }

    public void approve(User reviewer, LocalDateTime now) {
        /* 신분증만 보고 승인하면 "신분증과 입장 직전 얼굴을 나란히 대조" 라는 흐름이 성립하지 않는다.
           일부 제출 상태는 승인 대상이 아니다. */
        if (!isReviewable()) {
            throw new IdentitySessionStateException(
                    "신분증과 얼굴 확인용 사진이 모두 제출되어야 승인할 수 있습니다. 현재 상태: " + status
                            + " (신분증=" + (currentDocument != null ? "있음" : "없음")
                            + ", 얼굴=" + (faceCheckDocument != null ? "있음" : "없음") + ")");
        }
        this.status = Status.APPROVED;
        /* 승인할 때마다 검증 주기가 하나 올라간다. 각 응시의 증거 스냅샷이 이 번호를 복사해
           "이 응시는 몇 번째 검증을 통과했는가" 를 남긴다 (P0-1). */
        this.approvalCount++;
        this.approvedAt = now;
        this.approvedBy = reviewer;
        this.approvalExpiresAt = now.plusMinutes(APPROVAL_VALID_MINUTES);
        this.decidedAt = now;
        this.decidedBy = reviewer;
        this.decisionReason = null;
    }

    /**
     * 반려. 사유가 없으면 거부한다 — 훈련생이 무엇을 고쳐야 할지 알 수 없기 때문이다.
     *
     * @param requestResubmit true 면 재제출을 요청한다(횟수 상한 검사).
     */
    public void reject(User reviewer, String reason, boolean requestResubmit, LocalDateTime now) {
        if (!isReviewable()) {
            throw new IdentitySessionStateException(
                    "신분증과 얼굴 확인용 사진이 모두 제출되어야 판정할 수 있습니다. 현재 상태: " + status);
        }
        if (reason == null || reason.isBlank()) {
            throw new IdentitySessionStateException("반려 사유는 필수입니다.");
        }
        if (requestResubmit) {
            if (resubmitCount >= MAX_RESUBMIT) {
                throw new IdentitySessionStateException(
                        "재제출 허용 횟수(" + MAX_RESUBMIT + "회)를 초과했습니다. 세션을 새로 열어야 합니다.");
            }
            this.resubmitCount++;
        }
        /* 최종 반려(REJECTED)와 재제출 요청(RESUBMIT_REQUIRED)을 상태로 구분한다.
           하나로 합치면 최종 반려한 세션에도 다시 올릴 수 있어 횟수 상한이 무의미해진다. */
        this.status = requestResubmit ? Status.RESUBMIT_REQUIRED : Status.REJECTED;
        if (requestResubmit) {
            /* 재제출을 요청했으면 <b>신분증과 얼굴 사진을 모두</b> 다시 받는다.
               한쪽만 비우면 과거 자료와 새 자료를 섞어 승인할 수 있다.
               ExamIdentityDocument 행과 파일은 감사·보존 목적으로 남기고,
               '현재 문서' 포인터만 끊는다. */
            resetSubmission();
        }
        this.decidedAt = now;
        this.decidedBy = reviewer;
        this.decisionReason = reason.strip();
        /* 승인 흔적을 남겨두면 만료 판정이 꼬인다 — 반려 시 확실히 지운다. */
        this.approvedAt = null;
        this.approvedBy = null;
        this.approvalExpiresAt = null;
    }

    public void expire() {
        this.status = Status.EXPIRED;
        this.approvalExpiresAt = null;
    }

    /**
     * 얼굴 확인용 사진. 훈련생이 사전점검에서 직접 촬영·동의·제출한 것만 들어온다.
     * 승인 뒤에는 바꿀 수 없다 — 운영진이 본 사진과 저장된 사진이 달라지면 대조가 무의미해진다.
     */
    public void attachFaceCheck(ExamIdentityDocument document, LocalDateTime consentAt, String consentVersion) {
        if (!acceptsFaceCheck()) {
            throw new IdentitySessionStateException(
                    "지금은 얼굴 확인용 사진을 제출·변경할 수 없습니다. 현재 상태: " + status);
        }
        this.faceCheckDocument = document;
        this.faceConsentAt = consentAt;
        this.faceConsentVersion = consentVersion;
        promoteIfComplete();
    }

    /**
     * 승인 뒤에 문제를 발견해 다시 받아야 할 때. 운영진의 명시적 동작으로만 호출한다.
     * 승인 흔적을 지우고 재제출 요청 상태로 되돌린다.
     */
    public void requestResubmitAfterApproval(User reviewer, String reason, LocalDateTime now) {
        if (reason == null || reason.isBlank()) {
            throw new IdentitySessionStateException("재제출 사유는 필수입니다.");
        }
        if (resubmitCount >= MAX_RESUBMIT) {
            throw new IdentitySessionStateException(
                    "재제출 허용 횟수(" + MAX_RESUBMIT + "회)를 초과했습니다.");
        }
        this.resubmitCount++;
        this.status = Status.RESUBMIT_REQUIRED;
        resetSubmission();
        this.decidedAt = now;
        this.decidedBy = reviewer;
        this.decisionReason = reason.strip();
        this.approvedAt = null;
        this.approvedBy = null;
        this.approvalExpiresAt = null;
    }

    /** 웹캠 점검 통과를 서버에 기록한다. */
    public void markWebcamChecked(LocalDateTime now) {
        this.webcamCheckedAt = now;
    }

    /** 웹캠 점검이 아직 신선한가. 오래된 통과 기록으로 입장하지 못하게 한다. */
    public boolean isWebcamFresh(LocalDateTime now, int validMinutes) {
        return webcamCheckedAt != null && webcamCheckedAt.isAfter(now.minusMinutes(validMinutes));
    }

    /* ===================== 판정 ===================== */

    /**
     * 지금 이 세션으로 시험에 입장할 수 있는가.
     * 화면 배지가 아니라 이 값만 서버 게이트에서 쓴다.
     */
    public boolean canEnter(LocalDateTime now) {
        return status == Status.APPROVED
                && approvalExpiresAt != null
                && now.isBefore(approvalExpiresAt);
    }

    /** 승인은 받았지만 유효시간이 지난 상태 — 안내 문구를 구분하기 위해 따로 판정한다. */
    public boolean isApprovalExpired(LocalDateTime now) {
        return status == Status.APPROVED
                && approvalExpiresAt != null
                && !now.isBefore(approvalExpiresAt);
    }

    /**
     * 새 제출(신분증 업로드)을 받을 수 있는 상태인가.
     *
     * <p>PENDING(아직 안 냄)과 RESUBMIT_REQUIRED(운영진이 다시 내라고 함) <b>둘뿐</b>이다.
     * SUBMITTED·UNDER_REVIEW 를 포함하면 운영진이 검토하는 도중에 파일이 바뀌고,
     * REJECTED 를 포함하면 최종 반려가 무력화된다.</p>
     */
    public boolean acceptsSubmission() {
        return status == Status.PENDING || status == Status.RESUBMIT_REQUIRED;
    }

    /** 검토 시작·승인이 가능한가. 완전 제출이 아니면 운영진이 손댈 수 없다. */
    public boolean isReviewable() {
        return isSubmissionComplete()
                && (status == Status.SUBMITTED || status == Status.UNDER_REVIEW);
    }

    /**
     * 얼굴 확인용 사진을 받을 수 있는 상태인가.
     *
     * <p>신분증과 같은 규칙이다 — 검토 대기·검토 중에 사진이 바뀌면 운영진이 본 것과
     * 저장된 것이 달라진다. 승인 뒤 교체는 대조 자체를 무의미하게 만든다.</p>
     */
    public boolean acceptsFaceCheck() {
        return status == Status.PENDING || status == Status.RESUBMIT_REQUIRED;
    }

    /** 종결된 세션인가. 종결이면 새로 열지 않고 그대로 둔다(최종 반려 우회 방지). */
    /** 현재 제출 포인터와 증거를 모두 끊는다. 과거 문서 행·파일은 남긴다. */
    private void resetSubmission() {
        this.currentDocument = null;
        this.faceCheckDocument = null;
        this.faceConsentAt = null;
        this.faceConsentVersion = null;
        this.webcamCheckedAt = null;
        this.approvedAt = null;
        this.approvedBy = null;
        this.approvalExpiresAt = null;
    }

    /**
     * 만료된 세션을 같은 행에서 다시 연다.
     *
     * <p>새 행을 만들면 (exam,user) 유니크 제약을 깰 수밖에 없다. 재제출 횟수는
     * <b>유지</b >한다 — 만료를 반복해 반려 횟수를 초기화하는 우회를 막는다.</p>
     */
    public void reopen() {
        if (status != Status.EXPIRED) {
            throw new IdentitySessionStateException("만료된 세션만 다시 열 수 있습니다. 현재 상태: " + status);
        }
        this.status = Status.PENDING;
        resetSubmission();
        this.decidedAt = null;
        this.decidedBy = null;
        this.decisionReason = null;
    }

    public boolean isTerminal() {
        return status == Status.EXPIRED;
    }

    private void requireOpen() {
        if (!acceptsSubmission()) {
            throw new IdentitySessionStateException(
                    "더 이상 신분증을 제출할 수 없는 상태입니다. 현재 상태: " + status);
        }
    }
}
