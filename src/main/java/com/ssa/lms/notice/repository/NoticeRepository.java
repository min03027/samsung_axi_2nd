package com.ssa.lms.notice.repository;

import com.ssa.lms.notice.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * 관리자 공지 목록 검색.
     * 화면(admin-notice.html)의 필터: 카테고리 / 등록일 범위 / 검색어(제목·내용·작성자).
     * null 파라미터는 조건에서 제외된다.
     *
     * category·course·author 는 목록에서 전부 읽으므로 fetch join 으로 N+1 을 막는다.
     * (count 쿼리는 countQuery 로 따로 준다 — fetch join + Pageable 조합은 count 를 못 만든다)
     */
    @Query(value = """
            select n from Notice n
            left join fetch n.category c
            left join fetch n.course co
            join fetch n.author a
            where (:categoryId is null or c.id = :categoryId)
              and (cast(:from as timestamp) is null or n.createdAt >= :from)
              and (cast(:to as timestamp) is null or n.createdAt < :to)
              and (:keyword is null
                   or lower(n.title) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(n.content) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(a.name) like lower(concat('%', cast(:keyword as string), '%')))
            """,
            countQuery = """
            select count(n) from Notice n
            where (:categoryId is null or n.category.id = :categoryId)
              and (cast(:from as timestamp) is null or n.createdAt >= :from)
              and (cast(:to as timestamp) is null or n.createdAt < :to)
              and (:keyword is null
                   or lower(n.title) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(n.content) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(n.author.name) like lower(concat('%', cast(:keyword as string), '%')))
            """)
    Page<Notice> search(@Param("categoryId") Long categoryId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to,
                        @Param("keyword") String keyword,
                        Pageable pageable);

    /**
     * 훈련생/강사에게 노출되는 공지 목록.
     * 게시된 것(publishedAt <= now)만, 전체 공지(course is null) + 내가 속한 과정 공지.
     */
    @Query(value = """
            select n from Notice n
            left join fetch n.category c
            left join fetch n.course co
            join fetch n.author a
            where n.publishedAt is not null and n.publishedAt <= :now
              and (n.course is null or n.course.id in :courseIds)
              and (:keyword is null
                   or lower(n.title) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(n.content) like lower(concat('%', cast(:keyword as string), '%')))
            """,
            countQuery = """
            select count(n) from Notice n
            where n.publishedAt is not null and n.publishedAt <= :now
              and (n.course is null or n.course.id in :courseIds)
              and (:keyword is null
                   or lower(n.title) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(n.content) like lower(concat('%', cast(:keyword as string), '%')))
            """)
    Page<Notice> searchPublished(@Param("now") LocalDateTime now,
                                 @Param("courseIds") java.util.Collection<Long> courseIds,
                                 @Param("keyword") String keyword,
                                 Pageable pageable);

    /** 배포 전에 팝업으로 지정돼 있던 게시 공지를 알림 수신 행과 동기화할 때 사용한다. */
    @Query("""
            select n from Notice n
            left join fetch n.course
            join fetch n.author
            where n.popupOnLogin = true
              and n.publishedAt is not null
              and n.publishedAt <= :now
            order by n.id asc
            """)
    List<Notice> findPublishedLoginPopups(@Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = {"category", "course", "author", "attachments"})
    Optional<Notice> findWithDetailById(Long id);

    /** 상세 화면의 "이전 글" — 나보다 먼저 등록된 것 중 가장 최근. */
    @Query("""
            select n from Notice n
            where n.id < :id
            order by n.id desc
            limit 1
            """)
    Optional<Notice> findPrev(@Param("id") Long id);

    /** 상세 화면의 "다음 글". */
    @Query("""
            select n from Notice n
            where n.id > :id
            order by n.id asc
            limit 1
            """)
    Optional<Notice> findNext(@Param("id") Long id);

    /**
     * 조회수 증가.
     *
     * 엔티티 로드 → increaseViewCount() 로 하면 @LastModifiedDate 가 갱신돼
     * 목록의 "수정일"이 조회할 때마다 바뀐다. 그래서 UPDATE 를 직접 쏜다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Notice n set n.viewCount = n.viewCount + 1 where n.id = :id")
    void increaseViewCount(@Param("id") Long id);
}
