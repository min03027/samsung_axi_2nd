package com.ssa.lms.content.request;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.content.entity.Content;
import com.ssa.lms.content.entity.ContentLibraryItem;
import com.ssa.lms.content.service.ContentLibraryService;
import com.ssa.lms.content.web.ContentLibraryDeployForm;
import com.ssa.lms.content.web.ContentLibraryItemView;
import com.ssa.lms.content.web.SessionOption;
import com.ssa.lms.course.entity.EnrollmentStatus;
import com.ssa.lms.course.repository.CourseInstructorRepository;
import com.ssa.lms.course.repository.EnrollmentRepository;
import com.ssa.lms.content.repository.ContentRepository;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentRequestService {
    private final ContentRequestRepository requestRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final UserRepository userRepository;
    private final ContentRepository contentRepository;
    private final ContentLibraryService libraryService;

    public List<ContentRequestView> mine(Long traineeId) {
        return requestRepository.findByTraineeIdOrderByCreatedAtDesc(traineeId).stream().map(ContentRequestView::of).toList();
    }

    public List<com.ssa.lms.course.entity.Course> myCourses(Long traineeId) {
        return enrollmentRepository.findByTraineeIdOrderByAppliedAtDesc(traineeId).stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.APPROVED || e.getStatus() == EnrollmentStatus.COMPLETED)
                .map(e -> e.getCourse()).toList();
    }

    public List<ContentRequestView> staffRows(LoginUser actor) {
        List<ContentRequest> rows = actor.getRole() == Role.ADMIN
                ? requestRepository.findAllByOrderByCreatedAtDesc()
                : requestRepository.findByCourseIdInOrderByCreatedAtDesc(
                        courseInstructorRepository.findCourseIdsByInstructorId(actor.getId()));
        return rows.stream().map(ContentRequestView::of).toList();
    }

    public ContentRequestView view(Long id, LoginUser actor) {
        ContentRequest request = get(id);
        assertCanManage(request, actor);
        return ContentRequestView.of(request);
    }

    public List<ContentLibraryItemView> libraryOptions() {
        return libraryService.list(null, null, com.ssa.lms.content.entity.ContentLibraryStatus.PUBLISHED);
    }

    public List<SessionOption> sessionOptions(Long courseId) {
        return libraryService.sessionOptions().stream().filter(s -> s.courseId().equals(courseId)).toList();
    }

    @Transactional
    public Long create(Long traineeId, ContentRequestForm form) {
        var enrollment = enrollmentRepository.findByTraineeIdAndCourseId(traineeId, form.getCourseId())
                .orElseThrow(() -> new AccessDeniedException("수강 중인 과정에만 콘텐츠를 요청할 수 있습니다."));
        if (enrollment.getStatus() != EnrollmentStatus.APPROVED && enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            throw new AccessDeniedException("승인된 과정에만 콘텐츠를 요청할 수 있습니다.");
        }
        User trainee = userRepository.findById(traineeId).orElseThrow();
        return requestRepository.save(ContentRequest.builder().trainee(trainee).course(enrollment.getCourse())
                .preferredType(form.getPreferredType()).title(form.getTitle().trim())
                .reason(form.getReason().trim()).build()).getId();
    }

    @Transactional
    public void startReview(Long id, LoginUser actor) {
        ContentRequest request = get(id); assertCanManage(request, actor);
        request.startReview(userRepository.findById(actor.getId()).orElseThrow());
    }

    @Transactional
    public void fulfill(Long id, ContentRequestDecisionForm form, LoginUser actor) {
        ContentRequest request = get(id); assertCanManage(request, actor);
        ContentLibraryDeployForm deploy = new ContentLibraryDeployForm();
        deploy.setCourseId(request.getCourse().getId()); deploy.setSessionId(form.getSessionId());
        deploy.setOrderNo(form.getOrderNo()); deploy.setRequired(form.getRequired()); deploy.setAutoSync(form.getAutoSync());
        Long contentId = libraryService.deploy(form.getLibraryItemId(), deploy);
        Content content = contentRepository.findById(contentId).orElseThrow();
        ContentLibraryItem item = libraryService.get(form.getLibraryItemId());
        request.fulfill(userRepository.findById(actor.getId()).orElseThrow(), item, content, trim(form.getNote()));
    }

    @Transactional
    public void reject(Long id, String note, LoginUser actor) {
        ContentRequest request = get(id); assertCanManage(request, actor);
        if (note == null || note.isBlank()) throw new IllegalArgumentException("반려 사유를 입력하세요.");
        request.reject(userRepository.findById(actor.getId()).orElseThrow(), note.trim());
    }

    private ContentRequest get(Long id) { return requestRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("콘텐츠 요청을 찾을 수 없습니다: " + id)); }
    private void assertCanManage(ContentRequest r, LoginUser actor) {
        if (actor.getRole() == Role.ADMIN) return;
        if (actor.getRole() != Role.INSTRUCTOR || !courseInstructorRepository.existsByCourseIdAndInstructorId(r.getCourse().getId(), actor.getId()))
            throw new AccessDeniedException("담당 과정의 요청만 처리할 수 있습니다.");
    }
    private String trim(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
