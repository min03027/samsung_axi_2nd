package com.ssa.lms.organization.service;

public class DuplicateOrganizationException extends RuntimeException {
    public DuplicateOrganizationException(String message) {
        super(message);
    }
}
