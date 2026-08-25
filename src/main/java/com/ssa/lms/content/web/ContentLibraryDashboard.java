package com.ssa.lms.content.web;

/** 콘텐츠 라이브러리 상단 운영 요약. */
public record ContentLibraryDashboard(
        long itemCount,
        long publishedCount,
        long linkedContentCount,
        long versionCount
) {
}
