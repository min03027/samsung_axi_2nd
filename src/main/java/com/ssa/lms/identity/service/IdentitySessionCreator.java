package com.ssa.lms.identity.service;

import com.ssa.lms.exam.entity.Exam;
import com.ssa.lms.identity.entity.ExamIdentitySession;
import com.ssa.lms.identity.repository.ExamIdentitySessionRepository;
import com.ssa.lms.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 생성만 <b>별도 트랜잭션</b>에서 시도한다 (지적 10).
 *
 * <p>(exam,user) 유니크 제약에 걸리면 Hibernate 의 영속성 컨텍스트가 오염되어
 * 같은 트랜잭션 안에서는 재조회조차 못 한다. 생성 시도를 REQUIRES_NEW 로 떼어 내면
 * 충돌한 트랜잭션만 롤백되고, 바깥에서 승자 행을 정상적으로 읽을 수 있다.</p>
 *
 * <p>자기 호출(self-invocation)로는 REQUIRES_NEW 가 걸리지 않으므로 별도 빈으로 둔다.</p>
 */
@Component
@RequiredArgsConstructor
public class IdentitySessionCreator {

    private final ExamIdentitySessionRepository sessionRepository;

    /**
     * 세션을 만든다. 유니크 충돌이면 {@link DataIntegrityViolationException} 을 <b>그대로 던진다</b>.
     *
     * <p>여기서 catch 하면 트랜잭션이 이미 rollback-only 로 표시된 뒤라
     * 커밋 시점에 {@code UnexpectedRollbackException} 이 난다. 예외는 트랜잭션 경계를
     * 넘어간 뒤(=호출부)에서 잡아야 한다.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExamIdentitySession create(Exam exam, User user, String ip) {
        return sessionRepository.saveAndFlush(
                ExamIdentitySession.builder().exam(exam).user(user).createdIp(ip).build());
    }
}
