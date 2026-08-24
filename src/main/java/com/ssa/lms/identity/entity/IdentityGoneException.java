package com.ssa.lms.identity.entity;

/** 보존기간 경과 등으로 자료가 사라진 경우 — 410 Gone 으로 내린다. */
public class IdentityGoneException extends RuntimeException {
    public IdentityGoneException(String message) {
        super(message);
    }
}
