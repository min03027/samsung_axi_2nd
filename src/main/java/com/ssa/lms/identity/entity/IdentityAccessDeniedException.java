package com.ssa.lms.identity.entity;

/**
 * 남의 신분확인 세션·문서에 접근하려 한 경우.
 *
 * <p>{@link IdentitySessionStateException}(상태 위반, 400)과 구분한다 —
 * 권한 문제는 403 으로 내려야 하고, 없는 것과 못 보는 것을 화면에서 구별시키지 않는다.</p>
 */
public class IdentityAccessDeniedException extends RuntimeException {
    public IdentityAccessDeniedException(String message) {
        super(message);
    }
}
