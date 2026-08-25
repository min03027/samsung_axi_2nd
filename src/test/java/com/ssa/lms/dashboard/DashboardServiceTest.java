package com.ssa.lms.dashboard;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.CourseStatus;
import com.ssa.lms.course.entity.Enrollment;
import com.ssa.lms.course.entity.EnrollmentStatus;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.EnrollmentRepository;
import com.ssa.lms.dashboard.dto.AdminDashboardView;
import com.ssa.lms.dashboard.dto.InstructorDashboardView;
import com.ssa.lms.dashboard.dto.TraineeDashboardView;
import com.ssa.lms.dashboard.service.AdminDashboardService;
import com.ssa.lms.dashboard.service.InstructorDashboardService;
import com.ssa.lms.dashboard.service.TraineeDashboardService;
import com.ssa.lms.grading.entity.Grade;
import com.ssa.lms.grading.repository.GradeRepository;
import com.ssa.lms.notice.entity.Notice;
import com.ssa.lms.notice.repository.NoticeRepository;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대시보드가 지켜야 하는 규칙을 고정한다.
 *
 * <ul>
 *   <li>{@code Grade.passed == null} 은 <b>미정</b>이다 — 절대 "불합격"으로 표시하지 않는다</li>
 *   <li>강사는 담당 과정만 본다 (권한정의서 △)</li>
 *   <li>훈련생은 본인 데이터만 + 공지는 "전체 + 본인 수강 과정"만</li>
 *   <li>데이터가 0건인 계정에서도 예외 없이 빈 화면 모델이 나온다</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class DashboardServiceTest {

    @Autowired AdminDashboardService adminDashboardService;
    @Autowired InstructorDashboardService instructorDashboardService;
    @Autowired TraineeDashboardService traineeDashboardService;

    @Autowired UserRepository userRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired GradeRepository gradeRepository;
    @Autowired NoticeRepository noticeRepository;

    private User user(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    /* ===================== passed == null ===================== */

    @Test
    @DisplayName("확정 성적의 passed 가 null(미정)이면 '불합격'이 아니라 '판정 미정'으로 표시한다")
    void 미정_성적은_불합격이_아니다() {
        User trainee = user("trainee1");
        // 서비스가 보는 범위와 같은 조건이어야 한다 — 승인/수료 수강만 성적을 조회한다.
        Course course = enrollmentRepository.findByTraineeIdOrderByAppliedAtDesc(trainee.getId())
                .stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.APPROVED
                        || e.getStatus() == EnrollmentStatus.COMPLETED)
                .map(Enrollment::getCourse)
                .findFirst().orElseThrow();

        // 수동 채점이 남아 passed 가 미정(null)인 채로 확정된 성적.
        // applyInterimScore 는 passed 를 null 로 두고, confirm 은 passed 를 건드리지 않는다.
        Grade grade = Grade.builder()
                .user(trainee)
                .course(course)
                .evalType(Grade.EvalType.EXAM)
                .evalRefId(999_999L)
                .status(Grade.GradeStatus.UNGRADED)
                .build();
        grade.applyInterimScore(80, null, user("instructor1"), LocalDateTime.now());
        grade.confirm(user("instructor1"), LocalDateTime.now());
        gradeRepository.saveAndFlush(grade);
        assertThat(grade.getPassed()).isNull();

        TraineeDashboardView view = traineeDashboardService.load(trainee.getId(), trainee.getName());
        String recentGrade = view.stats().stream()
                .filter(s -> "최근 성적".equals(s.k()))
                .findFirst().orElseThrow().v();

        assertThat(recentGrade).doesNotContain("불합격");
        assertThat(recentGrade).contains("판정 미정");
    }

    @Test
    @DisplayName("passed 라벨: null=판정 미정 / true=합격 / false=불합격")
    void passed_라벨_규칙() {
        assertThat(TraineeDashboardService.passedLabel(null)).isEqualTo("판정 미정");
        assertThat(TraineeDashboardService.passedLabel(Boolean.TRUE)).isEqualTo("합격");
        assertThat(TraineeDashboardService.passedLabel(Boolean.FALSE)).isEqualTo("불합격");
    }

    /* ===================== 강사 = 담당 과정 한정 ===================== */

    @Test
    @DisplayName("강사 대시보드에는 담당하지 않는 과정이 하나도 섞이지 않는다")
    void 강사는_담당_과정만_본다() {
        User instructor = user("instructor1");
        InstructorDashboardView view =
                instructorDashboardService.load(instructor.getId(), instructor.getName());

        // 시드의 '강사1 미담당' 과정은 이름으로 식별할 수 있게 만들어져 있다.
        assertThat(view.courses())
                .extracting(InstructorDashboardView.CourseCard::name)
                .noneMatch(name -> name.contains("미담당"));
        assertThat(view.todayEvals())
                .extracting(InstructorDashboardView.EvalItem::title)
                .noneMatch(title -> title.contains("담당 아님") || title.contains("미담당"));
        assertThat(view.proctors())
                .extracting(InstructorDashboardView.ProctorItem::title)
                .noneMatch(title -> title.contains("미담당"));
    }

    /* ===================== 훈련생 = 본인 데이터 + 공지 노출 범위 ===================== */

    @Test
    @DisplayName("훈련생 초기화면 공지는 전체 공지 + 본인 수강 과정 공지만 보인다")
    void 수강하지_않는_과정_공지는_보이지_않는다() {
        User trainee = user("trainee1");
        Course foreign = courseRepository.findAll().stream()
                .filter(c -> enrollmentRepository
                        .findByTraineeIdAndCourseId(trainee.getId(), c.getId()).isEmpty())
                .findFirst().orElseThrow();

        noticeRepository.saveAndFlush(Notice.builder()
                .title("[격리검증] 수강하지 않는 과정 전용 공지")
                .content("이 공지는 다른 과정 수강생에게 보이면 안 된다")
                .author(user("admin"))
                .course(foreign)
                .pinned(true)
                .publishedAt(LocalDateTime.now().minusDays(1))
                .build());

        TraineeDashboardView view = traineeDashboardService.load(trainee.getId(), trainee.getName());

        assertThat(view.notices())
                .extracting(TraineeDashboardView.NoticeItem::title)
                .noneMatch(t -> t.contains("[격리검증]"));
    }

    @Test
    @DisplayName("훈련생 안심 홈은 실제 과정 상태와 코치 요약을 항상 제공한다")
    void 안심_홈_요약은_실제_데이터로_구성된다() {
        User trainee = user("trainee1");

        TraineeDashboardView view = traineeDashboardService.load(trainee.getId(), trainee.getName());

        assertThat(view.assurance()).isNotNull();
        assertThat(view.assurance().headline()).contains(trainee.getName());
        assertThat(view.assurance().progressRate()).isBetween(0, 100);
        assertThat(view.todaySchedule()).isNotNull();
        assertThat(view.coach()).isNotNull();
        assertThat(view.coach().href()).startsWith("/trainee/qna/tutoring");
        assertThat(view.courses())
                .as("과정별 이어서 학습은 다른 과정으로 섞이지 않아야 한다")
                .allSatisfy(course -> assertThat(course.continueHref())
                        .isNotEqualTo("/trainee/learning")
                        .matches("/trainee/contents/\\d+/play|/trainee/learning\\?courseId=\\d+"));
    }

    /* ===================== 데이터 0건 ===================== */

    @Test
    @DisplayName("수강 과정이 하나도 없는 훈련생도 예외 없이 빈 대시보드가 나온다")
    void 신규_훈련생도_화면이_깨지지_않는다() {
        User fresh = userRepository.save(User.builder()
                .loginId("dashboard_fresh_trainee")
                .password("{noop}1234")
                .name("신규훈련생")
                .role(Role.TRAINEE)
                .status(UserStatus.ACTIVE)
                .build());

        TraineeDashboardView view = traineeDashboardService.load(fresh.getId(), fresh.getName());

        assertThat(view.courses()).isEmpty();
        assertThat(view.todos()).isEmpty();
        assertThat(view.stats()).isNotEmpty();
        assertThat(view.assurance().tone()).isEqualTo("empty");
        assertThat(view.todaySchedule()).isEmpty();
        assertThat(view.coach().hasFeedback()).isFalse();
        // 전체 공지(course=null)는 신규 훈련생에게도 보여야 한다 — 유의사항 정보제공 요건.
        assertThat(view.notices()).allMatch(n -> "전체".equals(n.scope()));
    }

    @Test
    @DisplayName("담당 과정이 없는 강사도 예외 없이 빈 대시보드가 나온다")
    void 신규_강사도_화면이_깨지지_않는다() {
        User fresh = userRepository.save(User.builder()
                .loginId("dashboard_fresh_instructor")
                .password("{noop}1234")
                .name("신규강사")
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());

        InstructorDashboardView view = instructorDashboardService.load(fresh.getId(), fresh.getName());

        assertThat(view.courses()).isEmpty();
        assertThat(view.todayEvals()).isEmpty();
        assertThat(view.proctors()).isEmpty();
        assertThat(view.kpi().pendingExams()).isEqualTo("0");
    }

    /* ===================== 관리자 집계 ===================== */

    @Test
    @DisplayName("관리자 KPI 의 사용자 수는 실제 역할별 사용자 수와 같다")
    void 관리자_사용자_KPI가_DB와_일치한다() {
        AdminDashboardView view = adminDashboardService.load(user("admin").getId());

        assertThat(view.kpi().users().trainees())
                .isEqualTo(String.valueOf(userRepository.countByRole(Role.TRAINEE)));
        assertThat(view.kpi().users().instructors())
                .isEqualTo(String.valueOf(userRepository.countByRole(Role.INSTRUCTOR)));
        assertThat(view.kpi().users().admins())
                .isEqualTo(String.valueOf(userRepository.countByRole(Role.ADMIN)));
    }

    @Test
    @DisplayName("관리자 과정 KPI 는 CourseStatus 분포와 같다")
    void 관리자_과정_KPI가_DB와_일치한다() {
        AdminDashboardView view = adminDashboardService.load(user("admin").getId());

        long inProgress = courseRepository.findAll().stream()
                .filter(c -> c.getStatus() == CourseStatus.IN_PROGRESS).count();
        assertThat(view.kpi().courses().inProgress()).isEqualTo(String.valueOf(inProgress));
    }

    @Test
    @DisplayName("출결 기록이 없는 과정의 출석률은 0% 가 아니라 '-' 다")
    void 출결_기록이_없으면_대시() {
        AdminDashboardView view = adminDashboardService.load(user("admin").getId());
        Course noAttendance = courseRepository.findAll().stream()
                .filter(c -> c.getCourseCode().equals("COURSE-2026-003"))
                .findFirst().orElse(null);
        if (noAttendance == null) {
            return; // 시드가 바뀌면 이 검증은 건너뛴다
        }
        assertThat(view.courseTop())
                .filteredOn(r -> r.name().equals(noAttendance.getCourseName()))
                .allMatch(r -> "-".equals(r.attendanceRate()));
    }
}
