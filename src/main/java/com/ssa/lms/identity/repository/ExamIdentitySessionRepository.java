package com.ssa.lms.identity.repository;

import com.ssa.lms.identity.entity.ExamIdentitySession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ExamIdentitySessionRepository extends JpaRepository<ExamIdentitySession, Long> {

    /** 해당 (시험, 사용자) 의 가장 최근 세션. 활성 세션은 하나만 쓰는 것이 원칙이다. */
    Optional<ExamIdentitySession> findTopByExamIdAndUserIdOrderByIdDesc(Long examId, Long userId);

    /**
     * 세션 생성 경쟁을 막기 위한 <b>행 잠금</b> 조회 (지적 10).
     *
     * <p>Java {@code synchronized} 는 인스턴스가 둘 이상이면 무의미하다. DB 행 잠금을 써야
     * 여러 인스턴스에서도 "활성 세션 하나" 가 지켜진다. 잠글 행이 없는 최초 생성 경쟁은
     * 이 잠금만으로 못 막으므로, 호출부가 생성 후 재조회로 승자를 확인한다.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from ExamIdentitySession s
            where s.exam.id = :examId and s.user.id = :userId
            order by s.id desc
            limit 1
            """)
    Optional<ExamIdentitySession> lockLatest(Long examId, Long userId);

    /**
     * 세션 변경 경로 공통 <b>행 잠금</b> 조회 (P1-3).
     *
     * <p>제출·판정이 같은 세션을 동시에 건드리면 문서 포인터나 상태가 유실된다.
     * 신분증 업로드는 토큰 행을 먼저 잠그므로, 두 자원을 함께 잡는 경로의 순서는
     * <b>항상 토큰 → 세션</b> 이다. 나머지 경로는 세션 하나만 잠그므로 교착이 생기지 않는다.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExamIdentitySession s where s.id = :id")
    Optional<ExamIdentitySession> lockById(Long id);

    /** 운영진 대기열 — 판정이 끝나지 않은 것부터 오래 기다린 순으로. */
    @Query("""
            select s from ExamIdentitySession s
              join fetch s.exam e
              join fetch s.user u
            where s.status in :statuses
            order by s.id asc
            """)
    List<ExamIdentitySession> findQueue(List<ExamIdentitySession.Status> statuses);
}
