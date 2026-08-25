package com.ssa.lms.identity.entity;

/** 신분확인 세션의 상태 전이 규칙 위반. 화면에 그대로 보여줄 수 있는 문구를 담는다. */
public class IdentitySessionStateException extends RuntimeException {
    public IdentitySessionStateException(String message) {
        super(message);
    }
}
