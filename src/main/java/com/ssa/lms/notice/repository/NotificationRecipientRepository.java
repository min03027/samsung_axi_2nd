package com.ssa.lms.notice.repository;

import com.ssa.lms.notice.entity.NotificationRecipient;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    @Query("select r from NotificationRecipient r where r.notification.id in :ids and r.user.id = :userId")
    List<NotificationRecipient> findByNotificationIdsAndUserId(@Param("ids") Collection<Long> ids,
                                                               @Param("userId") Long userId);

    @Query("select r from NotificationRecipient r where r.notification.id in :ids")
    List<NotificationRecipient> findByNotificationIds(@Param("ids") Collection<Long> ids);

    void deleteByNotificationIdIn(Collection<Long> ids);

    /** 내가 받은 알림 — 최신순. 훈련생 알림함이 쓴다. */
    @Query("""
            select r from NotificationRecipient r
              join fetch r.notification n
            where r.user.id = :userId
            order by n.sendAt desc, n.id desc
            """)
    List<NotificationRecipient> findMine(@Param("userId") Long userId, Pageable pageable);

    /**
     * 훈련생 화면 진입 즉시 보여줄 미확인 팝업.
     * 관리자가 팝업으로 지정한 공지뿐 아니라 HIGH·URGENT 중요 알림도 바로 확인시킨다.
     */
    @Query("""
            select r from NotificationRecipient r
              join fetch r.notification n
            where r.user.id = :userId
              and r.readAt is null
              and (n.popupOnLogin = true
                   or n.priority = com.ssa.lms.notice.entity.Notification.Priority.HIGH
                   or n.priority = com.ssa.lms.notice.entity.Notification.Priority.URGENT)
              and n.status = com.ssa.lms.notice.entity.Notification.NotificationStatus.SENT
            order by n.sendAt desc, n.id desc
            """)
    List<NotificationRecipient> findUnreadLoginPopups(@Param("userId") Long userId, Pageable pageable);

    /** 성장 리포트 등 종류별 최근 발송·읽음 결과를 운영 화면에 표시한다. */
    @Query("""
            select r from NotificationRecipient r
              join fetch r.notification n
              join fetch r.user u
            where n.kind = :kind
            order by n.sendAt desc, n.id desc, r.id desc
            """)
    List<NotificationRecipient> findRecentByKind(
            @Param("kind") com.ssa.lms.notice.entity.Notification.NotificationKind kind,
            Pageable pageable);
}
