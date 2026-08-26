package com.ssa.lms.notice;

import com.ssa.lms.course.entity.*;
import com.ssa.lms.course.repository.*;
import com.ssa.lms.notice.entity.ReminderLog;
import com.ssa.lms.notice.repository.ReminderLogRepository;
import com.ssa.lms.notice.service.ReminderService;
import com.ssa.lms.notice.service.ReminderSettingService;
import com.ssa.lms.user.entity.*;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LessonReminderTest {
    @Autowired UserRepository userRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired SubjectRepository subjectRepository;
    @Autowired SessionRepository sessionRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired ReminderSettingService settingService;
    @Autowired ReminderService reminderService;
    @Autowired ReminderLogRepository reminderLogRepository;

    @Test
    void 시작시각이_있는_수업은_24시간_전에_한번만_알린다() {
        String suffix = String.valueOf(System.nanoTime());
        User trainee = userRepository.save(User.builder().loginId("lesson" + suffix).password("pw")
                .name("수업알림훈련생").role(Role.TRAINEE).status(UserStatus.ACTIVE).build());
        Course course = courseRepository.save(Course.builder().courseCode("LESSON-" + suffix)
                .courseName("수업 알림 과정").startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(30))
                .capacity(10).status(CourseStatus.IN_PROGRESS).build());
        Subject subject = Subject.builder().name("AI 기초").orderNo(1).build();
        course.addSubject(subject);
        subjectRepository.save(subject);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        Session lesson = Session.builder().seq(1).name("첫 수업")
                .lessonDate(now.plusHours(24).plusMinutes(10).toLocalDate())
                .lessonStartTime(now.plusHours(24).plusMinutes(10).toLocalTime())
                .learningMinutes(60).build();
        subject.addSession(lesson);
        sessionRepository.save(lesson);
        Enrollment enrollment = enrollmentRepository.save(Enrollment.builder()
                .trainee(trainee).course(course).status(EnrollmentStatus.APPLIED).build());
        enrollment.approve(now);
        settingService.save(24, 1, 3, false, false, false, true, true);

        int first = reminderService.remindDue(now, ReminderLog.ReminderStage.BEFORE_24H);
        int second = reminderService.remindDue(now, ReminderLog.ReminderStage.BEFORE_24H);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(reminderLogRepository.findAll()).anyMatch(log ->
                log.getReminderType() == ReminderLog.ReminderType.LESSON
                        && log.getUser().getId().equals(trainee.getId()));
    }
}
