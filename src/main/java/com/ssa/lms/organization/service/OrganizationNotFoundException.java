package com.ssa.lms.organization.service;

public class OrganizationNotFoundException extends RuntimeException {
    public OrganizationNotFoundException(Long id) {
        super("기업·기관을 찾을 수 없습니다. id=" + id);
    }
}
