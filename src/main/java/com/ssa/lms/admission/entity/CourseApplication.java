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
@Table(name = "course_application", indexes = {
        @Index(name = "ix_application_course_submitted", columnList = "course_id,submitted_at"),
        @Index(name = "ix_application_email_fp", columnList = "email_fingerprint"),
        @Index(name = "ix_application_phone_fp", columnList = "phone_fingerprint")
}, uniqueConstraints = @UniqueConstraint(name = "uk_application_receipt", columnNames = "receipt_number"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "receipt_number", nullable = false, length = 40)
    private String receiptNumber;

    @Column(name = "applicant_name", nullable = false, length = 80)
    private String applicantName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

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

    @Column(length = 80)
    private String employment;

    @Column(name = "desired_job", length = 120)
    private String desiredJob;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String motivation;

    @Column(columnDefinition = "TEXT")
    private String career;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(name = "training_card", length = 40)
    private String trainingCard;

    @Column(name = "dormitory_need", length = 40)
    private String dormitoryNeed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status;

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

    @Column(name = "truth_confirmed_at", nullable = false)
    private LocalDateTime truthConfirmedAt;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "status_changed_at", nullable = false)
    private LocalDateTime statusChangedAt;

    @Builder
    private CourseApplication(Course course, String receiptNumber, String applicantName, LocalDate birthDate,
                              String email, String phone, String emailFingerprint, String phoneFingerprint,
                              String employment, String desiredJob, String motivation, String career, String skills,
                              String trainingCard, String dormitoryNeed, User matchedUser,
                              boolean duplicateCandidate, LocalDateTime consentAt, String consentVersion) {
        this.course = course;
        this.receiptNumber = receiptNumber;
        this.applicantName = applicantName;
        this.birthDate = birthDate;
        this.email = email;
        this.phone = phone;
        this.emailFingerprint = emailFingerprint;
        this.phoneFingerprint = phoneFingerprint;
        this.employment = employment;
        this.desiredJob = desiredJob;
        this.motivation = motivation;
        this.career = career;
        this.skills = skills;
        this.trainingCard = trainingCard;
        this.dormitoryNeed = dormitoryNeed;
        this.status = ApplicationStatus.RECEIVED;
        this.matchedUser = matchedUser;
        this.duplicateCandidate = duplicateCandidate;
        this.privacyConsentAt = consentAt;
        this.privacyConsentVersion = consentVersion;
        this.truthConfirmedAt = consentAt;
        this.submittedAt = consentAt;
        this.statusChangedAt = consentAt;
    }

    public void updateProcessing(ApplicationStatus status, User assignedTo, String processingNote,
                                 LocalDate followUpDate, String finalResult, LocalDateTime changedAt) {
        this.status = status;
        this.assignedTo = assignedTo;
        this.processingNote = processingNote;
        this.followUpDate = followUpDate;
        this.finalResult = finalResult;
        this.statusChangedAt = changedAt;
    }
}
