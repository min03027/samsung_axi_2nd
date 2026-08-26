package com.ssa.lms.notice.dto;

/** 훈련생 홈 로그인 팝업용 최소 모델. */
public record LoginNoticePopup(Long notificationId, String title, String content, String confirmUrl) {
}
