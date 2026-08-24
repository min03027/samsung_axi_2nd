package com.ssa.lms.identity.repository;

import com.ssa.lms.identity.entity.ExamIdentityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamIdentityAuditLogRepository extends JpaRepository<ExamIdentityAuditLog, Long> {

    /** 세션별 감사 이력 — 최신순. LXP-016 의 "감사 이력 조회" 가 이걸 쓴다. */
    List<ExamIdentityAuditLog> findBySessionIdOrderByIdDesc(Long sessionId);

    long countBySessionIdAndAction(Long sessionId, ExamIdentityAuditLog.Action action);
}
