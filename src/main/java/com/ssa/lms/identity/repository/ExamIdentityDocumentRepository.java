package com.ssa.lms.identity.repository;

import com.ssa.lms.identity.entity.ExamIdentityDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

import java.util.List;

public interface ExamIdentityDocumentRepository extends JpaRepository<ExamIdentityDocument, Long> {
    List<ExamIdentityDocument> findBySessionIdOrderByIdDesc(Long sessionId);

    /** 보존기간이 지났고 아직 파기되지 않은 문서. */
    @Query("""
            select d from ExamIdentityDocument d
            where d.purgedAt is null and d.purgeAfter is not null and d.purgeAfter <= :now
            order by d.id asc
            """)
    List<ExamIdentityDocument> findPurgeTargets(LocalDateTime now);
}
