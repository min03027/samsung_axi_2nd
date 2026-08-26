package com.ssa.lms.care.service;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.care.entity.LearnerCareRecord;
import com.ssa.lms.care.repository.LearnerCareRecordRepository;
import com.ssa.lms.course.service.CourseQueryService;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LearnerCareService {
    private final LearnerCareRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final CourseQueryService courseQueryService;

    public record TraineeOption(Long id, String name, String loginId) {}
    public record CareRecordView(Long id, Long traineeId, String traineeName, String authorName,
                                 LearnerCareRecord.RecordType type, LearnerCareRecord.CareStatus status,
                                 String subject, String content, String result,
                                 LocalDateTime followUpAt, LocalDateTime createdAt) {}

    @Transactional(readOnly = true)
    public List<TraineeOption> traineeOptions(LoginUser actor) {
        return allowedTrainees(actor).stream()
                .map(u -> new TraineeOption(u.getId(), u.getName(), u.getLoginId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CareRecordView> managementRecords(LoginUser actor) {
        List<Long> ids = allowedTrainees(actor).stream().map(User::getId).toList();
        if (ids.isEmpty()) return List.of();
        return recordRepository.findByTraineeIds(ids).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<CareRecordView> myRecords(LoginUser trainee) {
        return recordRepository.findByTraineeId(trainee.getId()).stream().map(this::toView).toList();
    }

    @Transactional
    public void createByManager(LoginUser actor, Long traineeId, LearnerCareRecord.RecordType type,
                                LearnerCareRecord.CareStatus status, String subject, String content,
                                LocalDateTime followUpAt) {
        assertAllowed(actor, traineeId);
        User author = requireUser(actor.getId());
        User trainee = requireUser(traineeId);
        recordRepository.save(LearnerCareRecord.builder().trainee(trainee).author(author)
                .recordType(type).status(status).subject(required(subject, "제목"))
                .content(required(content, "내용")).followUpAt(followUpAt).build());
    }

    @Transactional
    public void createJournal(LoginUser trainee, String subject, String content) {
        User user = requireUser(trainee.getId());
        if (user.getRole() != Role.TRAINEE) throw new AccessDeniedException("훈련생만 작성할 수 있습니다.");
        recordRepository.save(LearnerCareRecord.builder().trainee(user).author(user)
                .recordType(LearnerCareRecord.RecordType.LEARNING_JOURNAL)
                .status(LearnerCareRecord.CareStatus.OBSERVATION)
                .subject(required(subject, "제목")).content(required(content, "내용")).build());
    }

    @Transactional
    public void updateFollowUp(LoginUser actor, Long recordId, LearnerCareRecord.CareStatus status,
                               String result, LocalDateTime followUpAt) {
        LearnerCareRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("기록을 찾을 수 없습니다."));
        assertAllowed(actor, record.getTrainee().getId());
        record.updateFollowUp(status, result == null ? "" : result.trim(), followUpAt);
    }

    private List<User> allowedTrainees(LoginUser actor) {
        if (actor.getRole() == Role.ADMIN) {
            return userRepository.findByRoleAndStatusOrderByNameAsc(Role.TRAINEE, UserStatus.ACTIVE);
        }
        if (actor.getRole() != Role.INSTRUCTOR) return List.of();
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long courseId : courseQueryService.findCourseIdsByInstructorId(actor.getId())) {
            ids.addAll(courseQueryService.findUserIdsByCourseId(courseId));
        }
        if (ids.isEmpty()) return List.of();
        return userRepository.findAllById(ids).stream()
                .filter(u -> u.getRole() == Role.TRAINEE && u.getStatus() == UserStatus.ACTIVE)
                .sorted(java.util.Comparator.comparing(User::getName)).toList();
    }

    private void assertAllowed(LoginUser actor, Long traineeId) {
        boolean allowed = allowedTrainees(actor).stream().anyMatch(u -> u.getId().equals(traineeId));
        if (!allowed) throw new AccessDeniedException("담당 범위의 훈련생 기록만 처리할 수 있습니다.");
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "을 입력해 주세요.");
        return value.trim();
    }

    private CareRecordView toView(LearnerCareRecord r) {
        return new CareRecordView(r.getId(), r.getTrainee().getId(), r.getTrainee().getName(),
                r.getAuthor().getName(), r.getRecordType(), r.getStatus(), r.getSubject(), r.getContent(),
                r.getResult(), r.getFollowUpAt(), r.getCreatedAt());
    }
}
