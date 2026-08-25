package com.ssa.lms.content.service;

public class ContentLibraryNotFoundException extends RuntimeException {
    public ContentLibraryNotFoundException(Long id) {
        super("공용 콘텐츠 원본을 찾을 수 없습니다: " + id);
    }
}
