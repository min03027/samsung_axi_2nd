package com.ssa.lms.admission.service;

import com.ssa.lms.admission.entity.*;
import com.ssa.lms.admission.repository.ConsultationRequestRepository;
import com.ssa.lms.admission.repository.CourseApplicationRepository;
import com.ssa.lms.admission.web.PublicApplicationRequest;
import com.ssa.lms.admission.web.PublicConsultationRequest;
import com.ssa.lms.admission.web.PublicConsultationCourseView;
import com.ssa.lms.course.entity.*;
import com.ssa.lms.course.repository.CoursePublicationRepository;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionService {

    public static final String PRIVACY_CONSENT_VERSION = "PRIVACY-2026-08-V1";

    private final CourseApplicationRepository courseApplicationRepository;
    private final ConsultationRequestRepository consultationRequestRepository;
    private final CoursePublicationRepository coursePublicationRepository;
    private final UserRepository userRepository;
    private final ContactFingerprint contactFingerprint;

    @Transactional
    public CourseApplication submitApplication(PublicApplicationRequest request) {
        CoursePublication publication = coursePublicationRepository.findPublishedByCourseId(
                        request.courseId(), RecruitmentStatus.RECRUITING,
                        PublicationSite.CLASS, PublicationSite.ALL)
                .orElseThrow(() -> new AdmissionException("현재 신청 가능한 공개 과정이 아닙니다."));

        String email = ContactFingerprint.normalizeEmail(request.email());
        String phone = ContactFingerprint.normalizePhone(request.phone());
        requirePhone(phone);
        String emailFingerprint = contactFingerprint.hash(email);
        String phoneFingerprint = contactFingerprint.hash(phone);
        UserMatch userMatch = matchExistingUser(email, phone);
        boolean duplicate = courseApplicationRepository.existsByCourseIdAndEmailFingerprint(
                request.courseId(), emailFingerprint)
                || courseApplicationRepository.existsByCourseIdAndPhoneFingerprint(
                request.courseId(), phoneFingerprint)
                || userMatch.possibleMatch();
        LocalDateTime now = LocalDateTime.now();

        return courseApplicationRepository.save(CourseApplication.builder()
                .course(publication.getCourse())
                .receiptNumber(receipt("APP"))
                .applicantName(clean(request.name()))
                .birthDate(request.birth())
                .email(email)
                .phone(phone)
                .emailFingerprint(emailFingerprint)
                .phoneFingerprint(phoneFingerprint)
                .employment(clean(request.employment()))
                .desiredJob(clean(request.job()))
                .motivation(clean(request.motivation()))
                .career(cleanNullable(request.career()))
                .skills(cleanNullable(request.skills()))
                .trainingCard(clean(request.card()))
                .dormitoryNeed(clean(request.dorm()))
                .matchedUser(userMatch.exactMatch())
                .duplicateCandidate(duplicate)
                .consentAt(now)
                .consentVersion(PRIVACY_CONSENT_VERSION)
                .build());
    }

    @Transactional
    public ConsultationRequest submitConsultation(PublicConsultationRequest request) {
        Course course = request.courseId() == null ? null : consultationPublication(request.courseId()).getCourse();
        String email = ContactFingerprint.normalizeEmail(request.email());
        String phone = ContactFingerprint.normalizePhone(request.phone());
        requirePhone(phone);
        String emailFingerprint = contactFingerprint.hash(email);
        String phoneFingerprint = contactFingerprint.hash(phone);
        UserMatch userMatch = matchExistingUser(email, phone);
        boolean duplicate = consultationRequestRepository.existsByEmailFingerprint(emailFingerprint)
                || consultationRequestRepository.existsByPhoneFingerprint(phoneFingerprint)
                || userMatch.possibleMatch();
        LocalDateTime now = LocalDateTime.now();

        return consultationRequestRepository.save(ConsultationRequest.builder()
                .course(course)
                .receiptNumber(receipt("CNS"))
                .requesterName(clean(request.name()))
                .email(email)
                .phone(phone)
                .emailFingerprint(emailFingerprint)
                .phoneFingerprint(phoneFingerprint)
                .consultationType(clean(request.type()))
                .preferredDate(request.date())
                .preferredTime(clean(request.time()))
                .contactMethod(clean(request.contact()))
                .dormitoryInterest(clean(request.dorm()))
                .message(cleanNullable(request.message()))
                .matchedUser(userMatch.exactMatch())
                .duplicateCandidate(duplicate)
                .consentAt(now)
                .consentVersion(PRIVACY_CONSENT_VERSION)
                .build());
    }

    public List<CourseApplication> applications() {
        return courseApplicationRepository.findAllByOrderBySubmittedAtDesc();
    }

    public List<ConsultationRequest> consultations() {
        return consultationRequestRepository.findAllByOrderBySubmittedAtDesc();
    }

    public PublicConsultationCourseView consultableCourse(Long courseId) {
        CoursePublication publication = consultationPublication(courseId);
        Course course = publication.getCourse();
        return new PublicConsultationCourseView(course.getId(), course.getCourseName(),
                publication.getPublicCategory() == null ? "과정" : publication.getPublicCategory().getLabel(),
                course.getStartDate(), course.getEndDate(), publication.getEducationTime(),
                course.getCapacity(), publication.getRequiredDocuments());
    }

    public CourseApplication application(Long id) {
        return courseApplicationRepository.findById(id)
                .orElseThrow(() -> new AdmissionException("지원서를 찾을 수 없습니다."));
    }

    public ConsultationRequest consultation(Long id) {
        return consultationRequestRepository.findById(id)
                .orElseThrow(() -> new AdmissionException("상담 신청을 찾을 수 없습니다."));
    }

    public List<User> assignableAdmins() {
        return userRepository.findByRoleOrderByNameAsc(Role.ADMIN).stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .toList();
    }

    @Transactional
    public void updateApplication(Long id, ApplicationStatus status, Long assigneeId,
                                  String processingNote, LocalDate followUpDate, String finalResult) {
        CourseApplication application = application(id);
        if (status == ApplicationStatus.REGISTERED
                && (application.getStatus() != ApplicationStatus.APPROVED || application.getMatchedUser() == null)) {
            throw new AdmissionException("등록 완료는 승인 상태이며 연결된 기존 계정이 있을 때만 선택할 수 있습니다.");
        }
        application.updateProcessing(status, assignee(assigneeId), cleanNullable(processingNote),
                followUpDate, cleanNullable(finalResult), LocalDateTime.now());
    }

    @Transactional
    public void updateConsultation(Long id, ConsultationStatus status, Long assigneeId,
                                   String processingNote, LocalDate followUpDate, String finalResult) {
        ConsultationRequest consultation = consultation(id);
        consultation.updateProcessing(status, assignee(assigneeId), cleanNullable(processingNote),
                followUpDate, cleanNullable(finalResult), LocalDateTime.now());
    }

    private CoursePublication consultationPublication(Long courseId) {
        CoursePublication publication = coursePublicationRepository.findByCourseId(courseId)
                .orElseThrow(() -> new AdmissionException("상담 가능한 공개 과정이 아닙니다."));
        boolean publicSite = publication.getPublicationSite() == PublicationSite.CLASS
                || publication.getPublicationSite() == PublicationSite.ALL;
        boolean consultableStatus = publication.getRecruitmentStatus() == RecruitmentStatus.PRE_CONSULTATION
                || publication.getRecruitmentStatus() == RecruitmentStatus.RECRUITING;
        if (!publication.isPublicVisible() || !publicSite || !consultableStatus) {
            throw new AdmissionException("상담 가능한 공개 과정이 아닙니다.");
        }
        return publication;
    }

    private User assignee(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        User user = userRepository.findById(assigneeId)
                .orElseThrow(() -> new AdmissionException("담당자를 찾을 수 없습니다."));
        if (user.getRole() != Role.ADMIN || user.getStatus() != UserStatus.ACTIVE) {
            throw new AdmissionException("활성 관리자 계정만 담당자로 지정할 수 있습니다.");
        }
        return user;
    }

    private UserMatch matchExistingUser(String email, String phone) {
        User exact = null;
        boolean possible = false;
        for (User user : userRepository.findAll()) {
            boolean emailMatch = !email.isBlank()
                    && email.equals(ContactFingerprint.normalizeEmail(user.getEmail()));
            boolean phoneMatch = !phone.isBlank()
                    && phone.equals(ContactFingerprint.normalizePhone(user.getPhone()));
            possible |= emailMatch || phoneMatch;
            if (emailMatch && phoneMatch) {
                exact = user;
                break;
            }
        }
        return new UserMatch(exact, possible);
    }

    private void requirePhone(String phone) {
        if (phone.length() < 10 || phone.length() > 11) {
            throw new AdmissionException("휴대전화 번호 형식을 확인해 주세요.");
        }
    }

    private String receipt(String prefix) {
        return "AXI-" + prefix + "-"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String clean(String value) {
        return value.strip();
    }

    private String cleanNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private record UserMatch(User exactMatch, boolean possibleMatch) {
    }
}
