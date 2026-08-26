package com.ssa.lms.web.trainee.notice;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.notice.dto.NotificationListRow;
import com.ssa.lms.notice.dto.LoginNoticePopup;
import com.ssa.lms.notice.dto.NotificationSearchCond;
import com.ssa.lms.notice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 훈련생 알림함.
 *
 * <p><b>왜 새로 만드나:</b> 알림(Notification)은 관리자가 보내는 쪽만 화면이 있었고,
 * <b>훈련생이 받은 알림을 볼 화면이 없었다.</b> 리마인드 스케줄러가 알림을 만들어도
 * 볼 곳이 없으면 의미가 없다. 기존 정적 화면 {@code trainee/alram.html} 을 연결한다.</p>
 *
 * <p>파일명 {@code alram} 은 오타지만(PLAN.md Phase 0 에 정리 대상으로 적혀 있다),
 * 지금 바꾸면 A 쪽 링크와 어긋나므로 그대로 둔다. URL 은 정상 철자를 쓴다.</p>
 */
@Controller
@RequestMapping("/trainee/alarm")
@RequiredArgsConstructor
public class TraineeAlarmController {

    private static final int PAGE_SIZE = 50;

    private final NotificationService notificationService;

    /**
     * 내가 받은 알림 목록.
     *
     * <p>{@code NotificationService.search} 는 관리자용이라 전체를 돌려준다.
     * 훈련생에게는 <b>본인이 수신자인 것만</b> 보여야 하므로 별도 조회를 쓴다.</p>
     */
    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser loginUser,
                       @ModelAttribute("cond") NotificationSearchCond cond,
                       Model model) {
        List<NotificationListRow> rows = notificationService.findForRecipient(
                loginUser.getId(), cond, PageRequest.of(0, PAGE_SIZE));
        model.addAttribute("rows", rows);
        model.addAttribute("unreadCount",
                rows.stream().filter(r -> "읽지않음".equals(r.readStatus())).count());
        return "trainee/alram";
    }

    /**
     * 화면을 열어 둔 상태에서도 새 중요 알림을 확인하기 위한 경량 조회 API.
     * 읽지 않은 팝업이 없으면 204를 반환해 빈 모달을 만들지 않는다.
     */
    @GetMapping("/popup")
    public ResponseEntity<LoginNoticePopup> popup(@AuthenticationPrincipal LoginUser loginUser) {
        LoginNoticePopup popup = notificationService.findLoginPopup(loginUser.getId());
        return popup == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(popup);
    }

    /** 읽음 처리 — 본인 수신 건만 바뀐다(서비스에서 보장). */
    @PostMapping("/read")
    public String markRead(@AuthenticationPrincipal LoginUser loginUser,
                           @RequestParam("ids") List<Long> ids,
                           @RequestParam(required = false) String returnTo,
                           RedirectAttributes redirectAttributes) {
        int changed = notificationService.markRead(ids, loginUser.getId());
        redirectAttributes.addFlashAttribute("message", changed + "건을 읽음 처리했습니다.");
        String safeReturn = returnTo != null && returnTo.startsWith("/trainee/")
                ? returnTo : "/trainee/alarm";
        return "redirect:" + safeReturn;
    }
}
