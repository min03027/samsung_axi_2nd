package com.ssa.lms.notice.repository;

import com.ssa.lms.notice.entity.ReminderLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {

    /**
     * 이미 보낸 (사용자, 단계) 조합 조회 — 재발송을 막는다.
     *
     * <p>대상마다 조회하면 N+1 이라 한 대상(예: 과제 1건)에 대해 한 번에 가져온다.
     * 반환값은 user id 목록이며, 여기 있는 사용자는 건너뛴다.</p>
     */
    @Query("""
            select r.user.id from ReminderLog r
            where r.reminderType = :type
              and r.targetRefId = :targetRefId
              and r.stage = :stage
            """)
    List<Long> findSentUserIds(@Param("type") ReminderLog.ReminderType type,
                               @Param("targetRefId") Long targetRefId,
                               @Param("stage") ReminderLog.ReminderStage stage);

    /** 여러 대상을 한 번에 — [targetRefId, userId] 쌍으로 돌려준다. */
    @Query("""
            select r.targetRefId, r.user.id from ReminderLog r
            where r.reminderType = :type
              and r.targetRefId in :targetRefIds
              and r.stage = :stage
            """)
    List<Object[]> findSentPairs(@Param("type") ReminderLog.ReminderType type,
                                 @Param("targetRefIds") Collection<Long> targetRefIds,
                                 @Param("stage") ReminderLog.ReminderStage stage);

    /** 운영자가 최근 실제 발송 결과를 확인하는 화면용. */
    @Query("""
            select r from ReminderLog r
              join fetch r.user u
            order by r.sentAt desc, r.id desc
            """)
    List<ReminderLog> findRecent(Pageable pageable);
}
