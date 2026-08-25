package com.ssa.lms.content.entity;

/** 공용 콘텐츠 원본의 운영 상태. */
public enum ContentLibraryStatus {

    DRAFT("작성 중"),
    PUBLISHED("사용 가능"),
    ARCHIVED("보관됨");

    private final String label;

    ContentLibraryStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
