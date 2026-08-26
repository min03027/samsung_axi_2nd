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

    /** 로그인 직후 보여줄 미확인 팝업 공지. 한 번에 하나만 꺼내 순서대로 확인하게 한다. */
    @Query("""
            select r from NotificationRecipient r
              join fetch r.notification n
            where r.user.id = :userId
              and r.readAt is null
              and n.popupOnLogin = true
              and n.status = com.ssa.lms.notice.entity.Notification.NotificationStatus.SENT
            order by n.sendAt asc, n.id asc
            """)
    List<NotificationRecipient> findUnreadLoginPopups(@Param("userId") Long userId, Pageable pageable);
}
