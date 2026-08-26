package com.ssa.lms.course.service;

import com.ssa.lms.course.entity.*;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.EnrollmentRepository;
import com.ssa.lms.course.web.EnrollmentView;
import com.ssa.lms.course.web.MyEnrollmentView;
import com.ssa.lms.course.web.PendingEnrollmentView;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 수강신청. 훈련생이 모집중(RECRUITING) 과정에 신청(APPLIED)하고, 관리자가 승인(APPROVED)/반려(REJECTED)한다.
 * 취소·반려는 삭제하지 않고 상태로 남긴다 (3년 보존). 승인 시 정원(capacity)을 초과할 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    /** 재신청 불가 — 이미 진행 중인 신청 상태들. */
    private static final Set<EnrollmentStatus> ACTIVE =
            Set.of(EnrollmentStatus.APPLIED, EnrollmentStatus.APPROVED, EnrollmentStatus.COMPLETED);

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    /* ===== 훈련생 ===== */

    @Transactional
    public void apply(Long traineeId, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        if (course.getStatus() != CourseStatus.RECRUITING) {
            throw new EnrollmentException("모집중인 과정만 신청할 수 있습니다.");
        }
        User trainee = userRepository.findById(traineeId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + traineeId));

        enrollmentRepository.findByTraineeIdAndCourseId(traineeId, courseId).ifPresentOrElse(existing -> {
            if (ACTIVE.contains(existing.getStatus())) {
                throw new EnrollmentException("이미 신청한 과정입니다.");
            }
            existing.reapply(LocalDateTime.now());   // 반려/취소 건 재신청
        }, () -> enrollmentRepository.save(Enrollment.builder()
                .trainee(trainee).course(course)
                .status(EnrollmentStatus.APPLIED).appliedAt(LocalDateTime.now()).build()));
    }

    @Transactional
    public void cancel(Long enrollmentId, Long traineeId) {
        Enrollment e = get(enrollmentId);
        if (!e.getTrainee().getId().equals(traineeId)) {
            throw new EnrollmentException("본인 신청만 취소할 수 있습니다.");
        }
        if (e.getStatus() != EnrollmentStatus.APPLIED && e.getStatus() != EnrollmentStatus.APPROVED) {
            throw new EnrollmentException("신청/승인 상태에서만 취소할 수 있습니다.");
        }
        e.cancel();
    }

    public List<MyEnrollmentView> myEnrollments(Long traineeId) {
        return enrollmentRepository.findByTraineeIdOrderByAppliedAtDesc(traineeId).stream()
                .map(MyEnrollmentView::of).toList();
    }

    /** 훈련생이 신청 가능한 모집중 과정 (이미 신청/승인/수료한 과정 제외). */
    public List<Course> openCoursesFor(Long traineeId) {
        Set<Long> activeCourseIds = enrollmentRepository.findByTraineeIdOrderByAppliedAtDesc(traineeId).stream()
                .filter(e -> ACTIVE.contains(e.getStatus()))
                .map(e -> e.getCourse().getId())
                .collect(Collectors.toSet());
        return courseRepository.findByStatusOrderByStartDateDesc(CourseStatus.RECRUITING).stream()
                .filter(c -> !activeCourseIds.contains(c.getId()))
                .toList();
    }

    /* ===== 관리자 ===== */

    /** 전체 과정의 승인 대기(APPLIED) 신청 목록 — 통합 승인 화면 (신청 오래된 순). */
    public List<PendingEnrollmentView> pendingEnrollments() {
        return enrollmentRepository.findAllByStatusWithTraineeAndCourse(EnrollmentStatus.APPLIED).stream()
                .map(PendingEnrollmentView::of).toList();
    }

    public List<EnrollmentView> enrollmentsOf(Long courseId) {
        return enrollmentRepository.findByCourseIdOrderByAppliedAtDesc(courseId).stream()
                .map(EnrollmentView::of).toList();
    }

    @Transactional
    public void approve(Long enrollmentId) {
        Enrollment e = get(enrollmentId);
        if (e.getStatus() != EnrollmentStatus.APPLIED) {
            throw new EnrollmentException("신청(APPLIED) 상태만 승인할 수 있습니다.");
        }
        long approved = enrollmentRepository.countByCourseIdAndStatus(
                e.getCourse().getId(), EnrollmentStatus.APPROVED);
        if (approved >= e.getCourse().getCapacity()) {
            throw new EnrollmentException("정원이 초과되어 승인할 수 없습니다.");
        }
        e.approve(LocalDateTime.now());
    }

    @Transactional
    public void reject(Long enrollmentId) {
        Enrollment e = get(enrollmentId);
        if (e.getStatus() != EnrollmentStatus.APPLIED) {
            throw new EnrollmentException("신청(APPLIED) 상태만 반려할 수 있습니다.");
        }
        e.reject(LocalDateTime.now());
    }

    /**
     * 관리자: 취소·반려 건을 다시 신청(APPLIED) 상태로 복구한다.
     * 훈련생 재신청은 모집중(RECRUITING) 과정에만 허용되므로, 모집이 끝난 과정에서
     * 실수로 취소한 훈련생은 이 경로로만 되살릴 수 있다. 정원 검증은 승인 시점에 이뤄진다.
     */
    @Transactional
    public void restore(Long enrollmentId) {
        Enrollment e = get(enrollmentId);
        if (e.getStatus() != EnrollmentStatus.CANCELLED && e.getStatus() != EnrollmentStatus.REJECTED) {
            throw new EnrollmentException("취소/반려 상태만 신청 상태로 복구할 수 있습니다.");
        }
        e.reapply(LocalDateTime.now());
    }

    /** 이수 기준을 통과한 승인 수강생을 수료 상태로 전환한다. 이미 수료 상태면 그대로 둔다. */
    @Transactional
    public void completeByCriteria(Long traineeId, Long courseId, LocalDateTime completedAt) {
        Enrollment enrollment = enrollmentRepository.findByTraineeIdAndCourseId(traineeId, courseId)
                .orElseThrow(() -> new EnrollmentException("승인된 수강 정보를 찾을 수 없습니다."));
        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
            return;
        }
        if (enrollment.getStatus() != EnrollmentStatus.APPROVED) {
            throw new EnrollmentException("승인된 수강생만 자동 이수 처리할 수 있습니다.");
        }
        enrollment.complete(completedAt);
    }

    private Enrollment get(Long id) {
        return enrollmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("수강신청을 찾을 수 없습니다: " + id));
    }
}
