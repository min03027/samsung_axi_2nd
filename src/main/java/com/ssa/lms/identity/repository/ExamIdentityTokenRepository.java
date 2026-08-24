package com.ssa.lms.identity.repository;

import com.ssa.lms.identity.entity.ExamIdentityToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ExamIdentityTokenRepository extends JpaRepository<ExamIdentityToken, Long> {

    /** 원문이 아니라 해시로만 찾는다. */
    Optional<ExamIdentityToken> findByTokenHash(String tokenHash);

    /**
     * 업로드 확정 직전 <b>행을 잠그고</b> 다시 읽는다 (지적 10).
     *
     * <p>{@code reject()} 검사와 {@code consume()} 사이에 다른 요청이 끼어들면
     * max-use 를 넘겨 여러 제출이 확정될 수 있다. 잠근 뒤 다시 판정해야 안전하다.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ExamIdentityToken t where t.tokenHash = :tokenHash")
    Optional<ExamIdentityToken> lockByTokenHash(String tokenHash);

    List<ExamIdentityToken> findBySessionIdAndRevokedAtIsNull(Long sessionId);

    List<ExamIdentityToken> findAllBySessionId(Long sessionId);
}
