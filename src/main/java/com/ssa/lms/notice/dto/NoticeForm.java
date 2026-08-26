package com.ssa.lms.notice.dto;

import com.ssa.lms.notice.entity.Notice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 공지 등록/수정 폼 (notice-add.html).
 *
 * 화면의 id 속성(noticeTitle/noticeContent 등)은 기존 JS 가 getElementById 로 잡고 있어
 * 건드리지 않고, name 만 camelCase 로 맞췄다 (CLAUDE.md 전환 규칙).
 */
@Getter
@Setter
public class NoticeForm {

    private Long id;

    private Long categoryId;

    /** 비우면 전체 공지. 값이 있으면 해당 과정 수강생 한정. */
    private Long courseId;

    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 200, message = "제목은 200자 이내로 입력하세요.")
    private String title;

    @NotBlank(message = "본문을 입력하세요.")
    private String content;

    private boolean pinned;

    /** 로그인 직후 팝업으로 노출. 읽음 처리는 기존 알림 수신자 상태를 사용한다. */
    private boolean popupOnLogin;

    /** 최초 게시 시 대상 훈련생에게 이메일도 함께 발송. */
    private boolean emailNotify;

    /** 체크 해제 시 임시저장(미게시) — publishedAt = null. */
    private boolean published = true;

    public static NoticeForm from(Notice n) {
        NoticeForm form = new NoticeForm();
        form.id = n.getId();
        form.categoryId = n.getCategory() == null ? null : n.getCategory().getId();
        form.courseId = n.getCourse() == null ? null : n.getCourse().getId();
        form.title = n.getTitle();
        form.content = n.getContent();
        form.pinned = n.isPinned();
        form.popupOnLogin = n.isPopupOnLogin();
        form.emailNotify = n.isEmailNotify();
        form.published = n.getPublishedAt() != null;
        return form;
    }
}
