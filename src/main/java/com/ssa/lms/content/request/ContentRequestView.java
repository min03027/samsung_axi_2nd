package com.ssa.lms.content.request;

import java.time.LocalDateTime;

public record ContentRequestView(Long id, Long courseId, String courseName, String traineeName,
                                 String preferredType, String title, String reason,
                                 ContentRequestStatus status, String statusLabel,
                                 String assignedTo, String decisionNote, String fulfilledTitle,
                                 LocalDateTime createdAt, LocalDateTime decidedAt) {
    public static ContentRequestView of(ContentRequest r) {
        return new ContentRequestView(r.getId(), r.getCourse().getId(), r.getCourse().getCourseName(),
                r.getTrainee().getName(), r.getPreferredType() == null ? "무관" : r.getPreferredType().getLabel(),
                r.getTitle(), r.getReason(), r.getStatus(), r.getStatus().getLabel(),
                r.getAssignedTo() == null ? null : r.getAssignedTo().getName(), r.getDecisionNote(),
                r.getFulfilledContent() == null ? null : r.getFulfilledContent().getTitle(),
                r.getCreatedAt(), r.getDecidedAt());
    }
}
