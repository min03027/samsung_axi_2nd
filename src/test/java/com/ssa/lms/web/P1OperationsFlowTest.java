package com.ssa.lms.web;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.care.entity.LearnerCareRecord;
import com.ssa.lms.care.repository.LearnerCareRecordRepository;
import com.ssa.lms.notice.entity.Notification;
import com.ssa.lms.notice.entity.NotificationRecipient;
import com.ssa.lms.notice.entity.ReminderLog;
import com.ssa.lms.notice.repository.NotificationRecipientRepository;
import com.ssa.lms.notice.repository.NotificationRepository;
import com.ssa.lms.notice.repository.ReminderLogRepository;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class P1OperationsFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired LearnerCareRecordRepository careRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationRecipientRepository recipientRepository;
    @Autowired ReminderLogRepository reminderLogRepository;

    @Test
    void 학습일지_상담_후속조치가_훈련생과_관리자_화면에_이어진다() throws Exception {
        User trainee = userRepository.findByLoginId("trainee1").orElseThrow();
        User admin = userRepository.findByLoginId("admin").orElseThrow();

        mvc.perform(post("/trainee/journal").with(as(trainee)).with(csrf())
                        .param("subject", "데이터 전처리 회고")
                        .param("content", "결측치 처리에서 추가 설명이 필요합니다."))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/trainee/journal"));

        LearnerCareRecord journal = careRepository.findByTraineeId(trainee.getId()).get(0);
        assertThat(journal.getRecordType()).isEqualTo(LearnerCareRecord.RecordType.LEARNING_JOURNAL);

        mvc.perform(get("/admin/care/diary").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("데이터 전처리 회고")))
                .andExpect(content().string(containsString("새 학생 기록")));

        mvc.perform(post("/admin/care/follow-ups/update").with(as(admin)).with(csrf())
                        .param("recordId", journal.getId().toString())
                        .param("status", "COMPLETED")
                        .param("result", "보충 실습 자료를 안내하고 이해도를 재확인했습니다."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/care/follow-ups"));

        mvc.perform(get("/trainee/journal").with(as(trainee)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조치 완료")))
                .andExpect(content().string(containsString("보충 실습 자료를 안내")));
    }

    @Test
    void 성장리포트와_미응답_리마인더_발송결과를_관리자화면에서_확인한다() throws Exception {
        User trainee = userRepository.findByLoginId("trainee1").orElseThrow();
        User admin = userRepository.findByLoginId("admin").orElseThrow();
        LocalDateTime sentAt = LocalDateTime.of(2026, 8, 26, 9, 0);

        Notification report = notificationRepository.save(Notification.builder()
                .title("[주간 성장 리포트] 테스트 훈련생님의 학습 현황")
                .content("진도와 출석을 확인하세요.").priority(Notification.Priority.NORMAL)
                .targetType(Notification.TargetType.USER).targetRefId(trainee.getId())
                .sendAt(sentAt).sender(admin).status(Notification.NotificationStatus.SENT)
                .kind(Notification.NotificationKind.GROWTH_REPORT).sourceUrl("/trainee/growth").build());
        recipientRepository.save(NotificationRecipient.builder().notification(report).user(trainee).build());
        reminderLogRepository.save(ReminderLog.builder().user(trainee)
                .reminderType(ReminderLog.ReminderType.ASSIGNMENT).targetRefId(77L)
                .stage(ReminderLog.ReminderStage.OVERDUE).sentAt(sentAt).build());

        mvc.perform(get("/admin/settings/growth-report").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("최근 성장 리포트 발송 결과")))
                .andExpect(content().string(containsString("[주간 성장 리포트] 테스트 훈련생님의 학습 현황")))
                .andExpect(content().string(containsString("미확인")));

        mvc.perform(get("/admin/settings/reminder").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("최근 미응답 리마인더 발송 결과")))
                .andExpect(content().string(containsString("과제 미제출")))
                .andExpect(content().string(containsString("마감 후 독려")));
    }

    private RequestPostProcessor as(User user) {
        return SecurityMockMvcRequestPostProcessors.user(new LoginUser(user));
    }
}
