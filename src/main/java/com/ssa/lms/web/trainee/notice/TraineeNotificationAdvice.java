package com.ssa.lms.web.trainee.notice;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.notice.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 모든 훈련생 화면에서 미확인 중요 알림을 진입 즉시 팝업으로 제공한다. */
@ControllerAdvice
@RequiredArgsConstructor
public class TraineeNotificationAdvice {

    private final NotificationService notificationService;

    @ModelAttribute
    public void addTraineeAlertPopup(@AuthenticationPrincipal LoginUser loginUser,
                                     HttpServletRequest request,
                                     Model model) {
        if (loginUser == null || request.getRequestURI() == null
                || !request.getRequestURI().startsWith("/trainee")) {
            return;
        }
        model.addAttribute("loginNoticePopup", notificationService.findLoginPopup(loginUser.getId()));
    }
}
