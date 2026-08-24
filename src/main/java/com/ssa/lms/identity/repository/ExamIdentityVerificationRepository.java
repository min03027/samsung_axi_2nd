package com.ssa.lms.identity.repository;

import com.ssa.lms.identity.entity.ExamIdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ExamIdentityVerificationRepository extends JpaRepository<ExamIdentityVerification, Long> {

    /** 시험 중·시험 후 감사 조회 — 이 응시가 무엇을 근거로 입장했는가 (LXP-016). */
    Optional<ExamIdentityVerification> findByAttemptId(Long attemptId);

    /** 한 세션이 지금까지 만든 모든 증거. 재응시 이력을 시간순으로 되짚는다. */
    List<ExamIdentityVerification> findBySessionIdOrderByIdAsc(Long sessionId);

    /** 같은 (시험, 사용자) 의 증거 전체 — 세션이 재사용돼도 이력은 남는다. */
    @Query("""
            select v from ExamIdentityVerification v
            where v.session.exam.id = :examId and v.session.user.id = :userId
            order by v.id asc
            """)
    List<ExamIdentityVerification> findHistory(Long examId, Long userId);
}
