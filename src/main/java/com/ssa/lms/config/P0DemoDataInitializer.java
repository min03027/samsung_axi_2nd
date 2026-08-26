package com.ssa.lms.config;

import com.ssa.lms.attendance.entity.Attendance;
import com.ssa.lms.attendance.entity.AttendanceStatus;
import com.ssa.lms.attendance.repository.AttendanceRepository;
import com.ssa.lms.content.entity.Content;
import com.ssa.lms.content.entity.ContentStatus;
import com.ssa.lms.content.entity.ContentType;
import com.ssa.lms.content.entity.Progress;
import com.ssa.lms.content.repository.ContentRepository;
import com.ssa.lms.content.repository.ProgressRepository;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.CourseInstructor;
import com.ssa.lms.course.entity.CourseStatus;
import com.ssa.lms.course.entity.Enrollment;
import com.ssa.lms.course.entity.EnrollmentStatus;
import com.ssa.lms.course.entity.Session;
import com.ssa.lms.course.entity.Subject;
import com.ssa.lms.course.repository.CourseInstructorRepository;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.EnrollmentRepository;
import com.ssa.lms.notice.entity.Notice;
import com.ssa.lms.notice.repository.NoticeRepository;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * P0 역할별 데모 계정이 하나의 과정 데이터를 함께 조회하도록 만드는 비운영 전용 시드.
 * demo 프로필과 lms.demo.seed-data=true가 동시에 있어야 실행된다.
 */
@Slf4j
@Component
@Profile("demo")
@Order(10)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lms.demo.seed-data", havingValue = "true")
public class P0DemoDataInitializer implements CommandLineRunner {

    public static final String ADMIN_LOGIN_ID = "demo_admin";
    public static final String INSTRUCTOR_LOGIN_ID = "demo_instructor";
    public static final String TRAINEE_LOGIN_ID = "demo_trainee";
    public static final String COURSE_CODE = "DEMO-AI-DATA-001";
    public static final String COURSE_NAME = "[DEMO] AI 기반 데이터 분석 과정";

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ContentRepository contentRepository;
    private final ProgressRepository progressRepository;
    private final AttendanceRepository attendanceRepository;
    private final NoticeRepository noticeRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${lms.demo.password:}")
    private String demoPassword;

    @Override
    @Transactional
    public void run(String... args) {
        Optional<User> admin = userRepository.findByLoginId(ADMIN_LOGIN_ID);
        Optional<User> instructor = userRepository.findByLoginId(INSTRUCTOR_LOGIN_ID);
        Optional<User> trainee = userRepository.findByLoginId(TRAINEE_LOGIN_ID);
        Optional<Course> course = courseRepository.findByCourseCode(COURSE_CODE);

        if (admin.isPresent() || instructor.isPresent() || trainee.isPresent() || course.isPresent()) {
            if (isCompleteDemoSet(admin, instructor, trainee, course)) {
                log.info("[demo] P0 역할별 데모 데이터가 이미 있어 생성을 건너뜁니다. courseCode={}", COURSE_CODE);
            } else {
                log.warn("[demo] 데모 고유 키 일부가 기존 데이터와 충돌하여 아무 데이터도 변경하지 않습니다. "
                        + "loginIds=[{}, {}, {}], courseCode={}",
                        ADMIN_LOGIN_ID, INSTRUCTOR_LOGIN_ID, TRAINEE_LOGIN_ID, COURSE_CODE);
            }
            return;
        }

        if (demoPassword == null || demoPassword.isBlank()) {
            throw new IllegalStateException("lms.demo.seed-data=true 사용 시 LMS_DEMO_PASSWORD를 반드시 설정해야 합니다.");
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        String encodedPassword = passwordEncoder.encode(demoPassword);

        User savedAdmin = userRepository.save(demoUser(
                ADMIN_LOGIN_ID, "[DEMO] 운영관리자", Role.ADMIN, "demo-admin@example.invalid", encodedPassword));
        User savedInstructor = userRepository.save(demoUser(
                INSTRUCTOR_LOGIN_ID, "[DEMO] 담당강사", Role.INSTRUCTOR,
                "demo-instructor@example.invalid", encodedPassword));
        User savedTrainee = userRepository.save(demoUser(
                TRAINEE_LOGIN_ID, "[DEMO] 수강생", Role.TRAINEE,
                "demo-trainee@example.invalid", encodedPassword));

        Course savedCourse = courseRepository.save(buildCourse(today));
        Session yesterdaySession = savedCourse.getSubjects().get(0).getSessions().get(0);
        Session todaySession = savedCourse.getSubjects().get(0).getSessions().get(1);

        courseInstructorRepository.save(CourseInstructor.builder()
                .course(savedCourse).instructor(savedInstructor).primaryInstructor(true).build());

        Enrollment enrollment = Enrollment.builder()
                .trainee(savedTrainee).course(savedCourse)
                .status(EnrollmentStatus.APPROVED).appliedAt(now.minusDays(15)).build();
        enrollment.approve(now.minusDays(14));
        enrollment.updateProgressRate(25.0);
        enrollmentRepository.save(enrollment);

        Content first = contentRepository.save(document(
                savedCourse, yesterdaySession, 1, "[DEMO] 데이터 분석 시작하기", 4));
        contentRepository.save(document(
                savedCourse, todaySession, 2, "[DEMO] 데이터 전처리 실습", 6));

        Progress progress = Progress.builder().user(savedTrainee).content(first).build();
        progress.updateDocumentProgress(2, 4, 90);
        progressRepository.save(progress);

        attendanceRepository.save(Attendance.builder()
                .course(savedCourse).session(yesterdaySession).trainee(savedTrainee)
                .attendanceDate(today.minusDays(1)).status(AttendanceStatus.PRESENT)
                .accessCount(1).manual(false).note("[DEMO] 학습 접속 기반 출석").build());

        noticeRepository.save(Notice.builder()
                .course(savedCourse).title("[DEMO] 이번 주 학습 안내")
                .content("데모 과정의 오늘 일정과 학습 콘텐츠를 확인해 주세요.")
                .author(savedAdmin).pinned(true).publishedAt(now.minusHours(1))
                .popupOnLogin(false).emailNotify(false).build());

        log.info("[demo] P0 역할별 데모 데이터 생성 완료: accounts=3, courseCode={}, courseId={}",
                COURSE_CODE, savedCourse.getId());
    }

    private boolean isCompleteDemoSet(Optional<User> admin, Optional<User> instructor,
                                      Optional<User> trainee, Optional<Course> course) {
        if (admin.isEmpty() || instructor.isEmpty() || trainee.isEmpty() || course.isEmpty()) {
            return false;
        }
        User adminUser = admin.get();
        User instructorUser = instructor.get();
        User traineeUser = trainee.get();
        Course demoCourse = course.get();
        return adminUser.getRole() == Role.ADMIN
                && instructorUser.getRole() == Role.INSTRUCTOR
                && traineeUser.getRole() == Role.TRAINEE
                && COURSE_NAME.equals(demoCourse.getCourseName())
                && courseInstructorRepository.existsByCourseIdAndInstructorId(
                        demoCourse.getId(), instructorUser.getId())
                && enrollmentRepository.findByTraineeIdAndCourseId(
                        traineeUser.getId(), demoCourse.getId()).isPresent()
                && contentRepository.countByCourseId(demoCourse.getId()) >= 2;
    }

    private User demoUser(String loginId, String name, Role role, String email, String encodedPassword) {
        return User.builder()
                .loginId(loginId).password(encodedPassword).name(name)
                .role(role).status(UserStatus.ACTIVE).email(email)
                .privacyConsentAt(null).thirdPartyConsentAt(null)
                .build();
    }

    private Course buildCourse(LocalDate today) {
        Course course = Course.builder()
                .courseCode(COURSE_CODE).courseName(COURSE_NAME).cohort("DEMO-1기")
                .category("AI·데이터").description("[DEMO] 역할별 화면 연동을 검증하는 비운영 과정")
                .startDate(today.minusDays(14)).endDate(today.plusDays(90))
                .capacity(20).status(CourseStatus.IN_PROGRESS).completionProgressRate(80)
                .build();
        Subject subject = Subject.builder()
                .name("[DEMO] 데이터 분석 기초").description("비운영 데모 과목").orderNo(1).build();
        subject.addSession(Session.builder().seq(1).name("데이터 분석 환경 이해")
                .lessonDate(today.minusDays(1)).lessonStartTime(LocalTime.of(10, 0))
                .learningMinutes(60).build());
        subject.addSession(Session.builder().seq(2).name("데이터 전처리 실습")
                .lessonDate(today).lessonStartTime(LocalTime.of(14, 0))
                .learningMinutes(90).build());
        subject.addSession(Session.builder().seq(3).name("시각화 미니 프로젝트")
                .lessonDate(today.plusDays(1)).lessonStartTime(LocalTime.of(10, 0))
                .learningMinutes(120).build());
        course.addSubject(subject);
        return course;
    }

    private Content document(Course course, Session session, int order, String title, int pages) {
        return Content.builder()
                .course(course).session(session).type(ContentType.DOCUMENT)
                .title(title).description("[DEMO] 내부 기능 검증용 문서 콘텐츠")
                .fileUrl(null).originalFileName(null).fileSize(null).mimeType(null)
                .pageCount(pages).orderNo(order).required(true).status(ContentStatus.ACTIVE)
                .build();
    }
}
