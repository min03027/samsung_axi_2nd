package com.ssa.lms.review.service;

public class ReviewNotFoundException extends RuntimeException {
    public ReviewNotFoundException(Long id) { super("후기를 찾을 수 없습니다. id=" + id); }
}
