package com.ssa.lms.admission.entity;

import com.ssa.lms.common.converter.CryptoConverter;
import com.ssa.lms.common.entity.BaseEntity;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "consultation_request", indexes = {
        @Index(name = "ix_consultation_course_submitted", columnList = "course_id,submitted_at"),
        @Index(name = "ix_consultation_email_fp", columnList = "email_fingerprint"),
        @Index(name = "ix_consultation_phone_fp", columnList = "phone_fingerprint")
}, uniqueConstraints = @UniqueConstraint(name = "uk_consultation_receipt", columnNames = "receipt_number"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultationRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "receipt_number", nullable = false, length = 40)
    private String receiptNumber;

    @Column(name = "requester_name", nullable = false, length = 80)
    private String requesterName;

    @Convert(converter = CryptoConverter.class)
    @Column(nullable = false, length = 512)
    private String email;

    @Convert(converter = CryptoConverter.class)
    @Column(nullable = false, length = 512)
    private String phone;

    @Column(name = "email_fingerprint", nullable = false, length = 64)
    private String emailFingerprint;

    @Column(name = "phone_fingerprint", nullable = false, length = 64)
    private String phoneFingerprint;

    @Column(name = "consultation_type", nullable = false, length = 100)
    private String consultationType;

    @Column(name = "preferred_date", nullable = false)
    private LocalDate preferredDate;

    @Column(name = "preferred_time", nullable = false, length = 50)
    private String preferredTime;

    @Column(name = "contact_method", nullable = false, length = 30)
    private String contactMethod;

    @Column(name = "dormitory_interest", length = 40)
    private String dormitoryInterest;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConsultationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_user_id")
    private User matchedUser;

    @Column(name = "duplicate_candidate", nullable = false)
    private boolean duplicateCandidate;

    @Column(name = "processing_note", columnDefinition = "TEXT")
    private String processingNote;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "final_result", columnDefinition = "TEXT")
    private String finalResult;

    @Column(name = "privacy_consent_at", nullable = false)
    private LocalDateTime privacyConsentAt;

    @Column(name = "privacy_consent_version", nullable = false, length = 40)
    private String privacyConsentVersion;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "status_changed_at", nullable = false)
    private LocalDateTime statusChangedAt;

    @Builder
    private ConsultationRequest(Course course, String receiptNumber, String requesterName,
                                String email, String phone, String emailFingerprint, String phoneFingerprint,
                                String consultationType, LocalDate preferredDate, String preferredTime,
                                String contactMethod, String dormitoryInterest, String message,
                                User matchedUser, boolean duplicateCandidate,
                                LocalDateTime consentAt, String consentVersion) {
        this.course = course;
        this.receiptNumber = receiptNumber;
        this.requesterName = requesterName;
        this.email = email;
        this.phone = phone;
        this.emailFingerprint = emailFingerprint;
        this.phoneFingerprint = phoneFingerprint;
        this.consultationType = consultationType;
        this.preferredDate = preferredDate;
        this.preferredTime = preferredTime;
        this.contactMethod = contactMethod;
        this.dormitoryInterest = dormitoryInterest;
        this.message = message;
        this.status = ConsultationStatus.RECEIVED;
        this.matchedUser = matchedUser;
        this.duplicateCandidate = duplicateCandidate;
        this.privacyConsentAt = consentAt;
        this.privacyConsentVersion = consentVersion;
        this.submittedAt = consentAt;
        this.statusChangedAt = consentAt;
    }

    public void updateProcessing(ConsultationStatus status, User assignedTo, String processingNote,
                                 LocalDate followUpDate, String finalResult, LocalDateTime changedAt) {
        this.status = status;
        this.assignedTo = assignedTo;
        this.processingNote = processingNote;
        this.followUpDate = followUpDate;
        this.finalResult = finalResult;
        this.statusChangedAt = changedAt;
    }
}
