package com.ssa.lms.notice;

import com.ssa.lms.notice.dto.LoginNoticePopup;
import com.ssa.lms.notice.dto.NoticeForm;
import com.ssa.lms.notice.service.NoticeService;
import com.ssa.lms.notice.service.NotificationService;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NoticeAutomationTest {
    @Autowired NoticeService noticeService;
    @Autowired NotificationService notificationService;
    @Autowired UserRepository userRepository;

    @Test
    void 게시한_팝업_공지는_훈련생에게_미확인_팝업으로_전달된다() {
        var admin = userRepository.findByRoleOrderByNameAsc(Role.ADMIN).get(0);
        var trainee = userRepository.findByRoleOrderByNameAsc(Role.TRAINEE).get(0);
        NoticeForm form = new NoticeForm();
        form.setTitle("필수 확인 공지");
        form.setContent("로그인 후 확인해야 할 안내입니다.");
        form.setPublished(true);
        form.setPopupOnLogin(true);

        Long noticeId = noticeService.create(form, admin.getId(), Role.ADMIN);
        LoginNoticePopup popup = notificationService.findLoginPopup(trainee.getId());

        assertThat(popup).isNotNull();
        assertThat(popup.title()).contains("필수 확인 공지");
        assertThat(popup.confirmUrl()).isEqualTo("/trainee/notice/" + noticeId);
        assertThat(notificationService.markRead(java.util.List.of(popup.notificationId()), trainee.getId()))
                .isEqualTo(1);
    }

    @Test
    void 이미_게시된_공지도_수정화면에서_팝업을_켜면_다시_노출된다() {
        var admin = userRepository.findByRoleOrderByNameAsc(Role.ADMIN).get(0);
        var trainee = userRepository.findByRoleOrderByNameAsc(Role.TRAINEE).get(0);
        NoticeForm form = new NoticeForm();
        form.setTitle("기존 게시 공지");
        form.setContent("처음에는 일반 공지로 게시합니다.");
        form.setPublished(true);

        Long noticeId = noticeService.create(form, admin.getId(), Role.ADMIN);
        var notification = notificationService.findForRecipient(trainee.getId(),
                new com.ssa.lms.notice.dto.NotificationSearchCond(null, null, null, null, null),
                org.springframework.data.domain.PageRequest.of(0, 50)).stream()
                .filter(row -> row.actionUrl().equals("/trainee/notice/" + noticeId))
                .findFirst().orElseThrow();
        notificationService.markRead(java.util.List.of(notification.id()), trainee.getId());

        NoticeForm edit = noticeService.loadForm(noticeId);
        edit.setPopupOnLogin(true);
        noticeService.update(noticeId, edit, admin.getId(), Role.ADMIN);

        LoginNoticePopup popup = notificationService.findLoginPopup(trainee.getId());
        assertThat(popup).isNotNull();
        assertThat(popup.confirmUrl()).isEqualTo("/trainee/notice/" + noticeId);
    }
}
