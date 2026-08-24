package com.ssa.lms.course.entity;

/** 기존 공개 과정 화면의 필터 분류. 내부 과정 카테고리와 별도로 공개 표현만 담당한다. */
public enum PublicCourseCategory {
    KDT("kdt", "KDT 신기술"),
    CREATIVE("creative", "AI·디자인 실무"),
    GLOBAL("global", "해외취업"),
    HIGHSCHOOL("highschool", "일반고 위탁"),
    LICENSE("license", "자격증"),
    SHORT("short", "재직자·단기");

    private final String key;
    private final String label;

    PublicCourseCategory(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }
}
