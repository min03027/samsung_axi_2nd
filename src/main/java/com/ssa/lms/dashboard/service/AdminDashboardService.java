package com.ssa.lms.dashboard.service;

import com.ssa.lms.assignment.dto.AssignmentSearchCond;
import com.ssa.lms.assignment.dto.CourseAssignmentRow;
import com.ssa.lms.assignment.service.CourseAssignmentService;
import com.ssa.lms.attendance.service.AttendanceService;
import com.ssa.lms.completion.service.CompletionService;
import com.ssa.lms.completion.web.CompletionView;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.CourseStatus;
import com.ssa.lms.course.service.CourseQueryService;
import com.ssa.lms.course.service.CourseService;
import com.ssa.lms.dashboard.dto.AdminDashboardView;
import com.ssa.lms.dashboard.dto.DashboardFormat;
import com.ssa.lms.exam.dto.ExamListRow;
import com.ssa.lms.exam.dto.ExamSearchCond;
import com.ssa.lms.exam.service.ExamService;
import com.ssa.lms.grading.dto.ExamGradingRow;
import com.ssa.lms.grading.service.ExamGradingService;
import com.ssa.lms.notice.dto.NoticeListRow;
import com.ssa.lms.notice.dto.NoticeSearchCond;
import com.ssa.lms.notice.service.NoticeService;
import com.ssa.lms.proctor.dto.MonitoringRow;
import com.ssa.lms.proctor.service.ProctorMonitorService;
import com.ssa.lms.proctor.service.ProctorViewer;
import com.ssa.lms.support.dto.ResponseListRow;
import com.ssa.lms.support.dto.SupportStats;
import com.ssa.lms.support.service.QnaService;
import com.ssa.lms.support.service.TutoringService;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 관리자 대시보드 집계.
 *
 * <p><b>집계를 다시 구현하지 않는다.</b> 성적/채점/응시/부정행위/Q&A/공지 수치는 각 슬라이스가
 * 이미 만들어 둔 서비스에서 가져온다. 같은 숫자를 두 곳에서 세면 화면마다 값이 갈린다.</p>
 *
 * <ul>
 *   <li>시험 채점 현황 → {@link ExamGradingService#examList}</li>
 *   <li>응시 현황 → {@link ProctorMonitorService#monitoringList} (완료 = SUBMITTED + AUTO_SUBMITTED, VOIDED 제외)</li>
 *   <li>Q&A/튜터링 → {@link QnaService#stats()} / {@code responseRows()}</li>
 *   <li>수강생 명단 → {@link CourseQueryService#findUserIdsByCourseId} (A의 리포지토리를 직접 쓰지 않는다)</li>
 *   <li>출결/이수 → A 도메인의 {@link AttendanceService} / {@link CompletionService} <b>읽기 전용</b> 호출</li>
 * </ul>
 *
 * <p>성적({@code Grade})은 이 화면에 노출되는 항목이 없어 조회하지 않는다. 필요해지면
 * {@code GradeQueryService} 만 쓸 것 — {@code GradeRepository} 직접 조회 금지.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    /** TOP 패널이 보여주는 행 수. 화면 레이아웃(패널 높이)에 맞춘 값이다. */
    private static final int TOP_ROWS = 5;
    private static final int NOTICE_ROWS = 5;

    private static final String GRADING_PREFIX = "/admin/evaluation/grading";
    private static final String ASSIGNMENT_PREFIX = "/admin/evaluation/assignments/";
    private static final String MONITOR_PREFIX = "/admin/evaluation/monitoring/";
    private static final String NOTICE_PREFIX = "/admin/notice/";
    private static final String SUPPORT_RESPONSE_URL = "/admin/support/response";
    private static final String COMPLETION_URL = "/admin/completion";

    private final UserRepository userRepository;              // A 소유 — count 조회만 (읽기 전용)
    private final CourseService courseService;                // A
    private final CourseQueryService courseQueryService;      // A
    private final AttendanceService attendanceService;        // A
    private final CompletionService completionService;        // A
    private final ExamService examService;
    private final ExamGradingService examGradingService;
    private final CourseAssignmentService courseAssignmentService;
    private final ProctorMonitorService proctorMonitorService;
    private final QnaService qnaService;
    private final TutoringService tutoringService;
    private final NoticeService noticeService;

    public AdminDashboardView load(Long adminId) {
        LocalDate today = LocalDate.now();

        List<Course> courses = courseService.findAll();
        List<ExamListRow> exams = examService.search(new ExamSearchCond());
        List<ExamGradingRow> gradingRows =
                examGradingService.examList(null, null, null, adminId, true, GRADING_PREFIX);
        List<CourseAssignmentRow> assignmentRows =
                courseAssignmentService.search(AssignmentSearchCond.empty(), ASSIGNMENT_PREFIX);
        List<MonitoringRow> monitoringRows = proctorMonitorService.monitoringList(
                new ProctorViewer(adminId, Role.ADMIN), MONITOR_PREFIX);
        SupportStats supportStats = qnaService.stats();
        List<ResponseListRow> supportRows = supportRows();
        List<CompletionView> completions = completions(courses);

        return new AdminDashboardView(
                DashboardFormat.today(today),
                kpi(courses, exams, assignmentRows, completions, supportStats),
                risks(gradingRows, assignmentRows, monitoringRows, supportStats),
                courseTop(courses),
                evalTop(gradingRows, assignmentRows),
                completionTop(completions),
                supportTop(supportRows),
                notices());
    }

    /* ===================== KPI ===================== */

    private AdminDashboardView.Kpi kpi(List<Course> courses, List<ExamListRow> exams,
                                       List<CourseAssignmentRow> assignments,
                                       List<CompletionView> completions, SupportStats support) {
        return new AdminDashboardView.Kpi(
                new AdminDashboardView.Users(
                        String.valueOf(userRepository.countByRole(Role.TRAINEE)),
                        String.valueOf(userRepository.countByRole(Role.INSTRUCTOR)),
                        String.valueOf(userRepository.countByRole(Role.ADMIN))),
                courseKpi(courses),
                evalKpi(exams, assignments),
                attendanceKpi(completions),
                new AdminDashboardView.Support(
                        String.valueOf(support.waitingCount()),
                        String.valueOf(support.inProgressCount()),
                        String.valueOf(support.noResponseCount())));
    }

    private AdminDashboardView.Courses courseKpi(List<Course> courses) {
        long inProgress = courses.stream().filter(c -> c.getStatus() == CourseStatus.IN_PROGRESS).count();
        long upcoming = courses.stream()
                .filter(c -> c.getStatus() == CourseStatus.DRAFT
                        || c.getStatus() == CourseStatus.RECRUITING
                        || c.getStatus() == CourseStatus.RECRUITMENT_CLOSED)
                .count();
        long ended = courses.stream()
                .filter(c -> c.getStatus() == CourseStatus.COMPLETED || c.getStatus() == CourseStatus.CLOSED)
                .count();
        return new AdminDashboardView.Courses(
                String.valueOf(inProgress), String.valueOf(upcoming), String.valueOf(ended));
    }

    /**
     * 평가 진행 = 시험 + 과제.
     *
     * <p>시험은 {@link ExamListRow#status()} 가 이미 "임시저장/예정/진행중/종료/보관" 라벨이라 그대로 쓴다.
     * 임시저장은 아직 평가가 아니므로 셋 중 어디에도 넣지 않는다.</p>
     *
     * <p>과제는 {@link CourseAssignmentRow#status()} 가 채점 진행률에서 파생된 값(waiting/pending/completed)
     * 이라 기간 판정에 쓸 수 없다. 그래서 표시용 시작/마감 날짜 문자열로 판정한다 — 파싱이 실패하면
     * 그 행은 세지 않는다(포맷이 바뀌어도 화면이 죽지 않게).</p>
     */
    private AdminDashboardView.Evals evalKpi(List<ExamListRow> exams, List<CourseAssignmentRow> assignments) {
        long inProgress = exams.stream().filter(e -> "진행중".equals(e.status())).count();
        long upcoming = exams.stream().filter(e -> "예정".equals(e.status())).count();
        long ended = exams.stream()
                .filter(e -> "종료".equals(e.status()) || "보관".equals(e.status()))
                .count();

        LocalDate today = LocalDate.now();
        for (CourseAssignmentRow row : assignments) {
            LocalDate start = parseDate(row.startDate());
            LocalDate end = parseDate(row.endDate());
            if (start == null || end == null) {
                continue;
            }
            if (today.isBefore(start)) {
                upcoming++;
            } else if (today.isAfter(end)) {
                ended++;
            } else {
                inProgress++;
            }
        }
        return new AdminDashboardView.Evals(
                String.valueOf(inProgress), String.valueOf(upcoming), String.valueOf(ended));
    }

    /** 출결/이수 — A 도메인. {@code Completion} 이 한 건도 없으면 전부 "-" (0 이 아니다). */
    private AdminDashboardView.Attendance attendanceKpi(List<CompletionView> completions) {
        if (completions.isEmpty()) {
            return new AdminDashboardView.Attendance(
                    DashboardFormat.NONE, DashboardFormat.NONE, DashboardFormat.NONE);
        }
        long pending = completions.stream().filter(c -> !"CONFIRMED".equals(c.confirmStatus())).count();
        long confirmed = completions.stream().filter(c -> "CONFIRMED".equals(c.confirmStatus())).count();
        long certificates = completions.stream().filter(CompletionView::certificateIssuable).count();
        return new AdminDashboardView.Attendance(
                String.valueOf(pending), String.valueOf(confirmed), String.valueOf(certificates));
    }

    /* ===================== 운영 알림 / 리스크 ===================== */

    /**
     * 리스크는 새 테이블이 아니라 위에서 이미 구한 집계에서 파생한다.
     * 건수가 0인 항목은 아예 넣지 않는다 — "0건" 알림은 노이즈다.
     */
    private List<AdminDashboardView.Risk> risks(List<ExamGradingRow> gradingRows,
                                                List<CourseAssignmentRow> assignments,
                                                List<MonitoringRow> monitoringRows,
                                                SupportStats support) {
        List<AdminDashboardView.Risk> risks = new ArrayList<>();

        int ungradedExams = gradingRows.stream().mapToInt(ExamGradingRow::ungraded).sum();
        if (ungradedExams > 0) {
            risks.add(new AdminDashboardView.Risk(
                    "[평가] 시험 채점 대기 " + ungradedExams + "건",
                    "채점 대상 시험 " + gradingRows.stream().filter(r -> r.ungraded() > 0).count() + "개",
                    GRADING_PREFIX));
        }

        long pendingAssignments = assignments.stream().filter(a -> "pending".equals(a.status())).count();
        if (pendingAssignments > 0) {
            risks.add(new AdminDashboardView.Risk(
                    "[평가] 과제 채점 대기 " + pendingAssignments + "건",
                    "제출은 있는데 채점이 끝나지 않았습니다",
                    "/admin/evaluation/assignments"));
        }

        int live = (int) monitoringRows.stream().filter(r -> r.inProgress() > 0).count();
        if (live > 0) {
            risks.add(new AdminDashboardView.Risk(
                    "[감독] 응시 진행 중 시험 " + live + "건",
                    "현재 응시자 "
                            + monitoringRows.stream().mapToInt(MonitoringRow::inProgress).sum() + "명",
                    "/admin/evaluation/monitoring"));
        }

        int voided = monitoringRows.stream().mapToInt(MonitoringRow::voided).sum();
        if (voided > 0) {
            risks.add(new AdminDashboardView.Risk(
                    "[감독] 무효 처리된 응시 " + voided + "건",
                    "영상/이벤트 로그 확인 필요",
                    "/admin/evaluation/monitoring"));
        }

        if (support.waitingCount() > 0) {
            risks.add(new AdminDashboardView.Risk(
                    "[응답] 미답변 Q&A " + support.waitingCount() + "건",
                    "평균 첫 응답 " + support.avgFirstResponse(),
                    SUPPORT_RESPONSE_URL));
        }
        if (support.noResponseCount() > 0) {
            risks.add(new AdminDashboardView.Risk(
                    "[응답] SLA 24h 초과 " + support.noResponseCount() + "건",
                    "긴급 응대 필요",
                    SUPPORT_RESPONSE_URL));
        }
        return risks;
    }

    /* ===================== TOP 표 ===================== */

    private List<AdminDashboardView.CourseTop> courseTop(List<Course> courses) {
        return courses.stream()
                .sorted(Comparator.comparing(Course::getStartDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TOP_ROWS)
                .map(c -> new AdminDashboardView.CourseTop(
                        c.getCourseName(),
                        DashboardFormat.nullToDash(c.getCohort()),
                        DashboardFormat.date(c.getStartDate()) + " ~ " + DashboardFormat.date(c.getEndDate()),
                        averageAttendanceRate(c.getId()),
                        courseQueryService.findUserIdsByCourseId(c.getId()).size() + "명",
                        c.getStatus().getLabel()))
                .toList();
    }

    /** 출결 기록이 없으면 "-" — 0% 로 찍으면 전원 결석으로 읽힌다. */
    private String averageAttendanceRate(Long courseId) {
        Map<Long, Integer> rates = attendanceService.attendanceRates(courseId);
        if (rates.isEmpty()) {
            return DashboardFormat.NONE;
        }
        return DashboardFormat.percent(
                (int) Math.round(rates.values().stream().mapToInt(Integer::intValue).average().orElse(0)));
    }

    private List<AdminDashboardView.EvalTop> evalTop(List<ExamGradingRow> gradingRows,
                                                     List<CourseAssignmentRow> assignments) {
        List<AdminDashboardView.EvalTop> rows = new ArrayList<>();
        for (ExamGradingRow r : gradingRows) {
            rows.add(new AdminDashboardView.EvalTop(
                    "[시험] " + r.title(),
                    r.courseName(),
                    gradingStatusLabel(r.status()),
                    r.submitted() + "/" + (r.submitted() + r.notSubmitted()),
                    gradingLabel(r.submitted(), r.ungraded()),
                    r.address()));
        }
        for (CourseAssignmentRow a : assignments) {
            long total = a.submitted() + a.notSubmitted();
            rows.add(new AdminDashboardView.EvalTop(
                    "[과제] " + a.title(),
                    a.courseName(),
                    gradingStatusLabel(a.status()),
                    a.submitted() + "/" + total,
                    a.submitted() == 0 ? DashboardFormat.NONE
                            : ("pending".equals(a.status()) ? "채점대기" : "완료"),
                    a.address()));
        }
        // 채점이 남은 것부터 보여준다 — 관리자가 먼저 손대야 하는 순서다.
        rows.sort(Comparator.comparing((AdminDashboardView.EvalTop e) -> !"채점중".equals(e.status())));
        return rows.stream().limit(TOP_ROWS).toList();
    }

    /**
     * 평가 목록의 "상태" 컬럼.
     *
     * <p>여기 들어오는 값은 시험/과제 <b>기간</b> 상태가 아니라 <b>채점 진행률에서 파생된</b>
     * 값(waiting/pending/completed)이다. "진행중/종료" 로 옮기면 응시 기간과 헷갈리므로
     * 채점 관점의 문구를 그대로 쓴다.</p>
     */
    private String gradingStatusLabel(String derivedStatus) {
        return switch (derivedStatus == null ? "" : derivedStatus) {
            case "pending" -> "채점중";
            case "completed" -> "채점완료";
            default -> "제출없음";
        };
    }

    /** 제출이 0건이면 "완료"가 아니라 "-" 다. 채점할 게 없는 것과 다 채점한 것은 다르다. */
    private String gradingLabel(int submitted, int ungraded) {
        if (submitted == 0) {
            return DashboardFormat.NONE;
        }
        return ungraded > 0 ? ungraded + "대기" : "완료";
    }

    private List<AdminDashboardView.CompletionTop> completionTop(List<CompletionView> completions) {
        return completions.stream()
                .filter(c -> !"CONFIRMED".equals(c.confirmStatus()))
                .limit(TOP_ROWS)
                .map(c -> new AdminDashboardView.CompletionTop(
                        c.courseLabel(),
                        c.traineeName(),
                        DashboardFormat.percent(c.attendanceRate()),
                        c.resultLabel(),
                        c.confirmStatusLabel(),
                        COMPLETION_URL))
                .toList();
    }

    private List<AdminDashboardView.SupportTop> supportTop(List<ResponseListRow> rows) {
        return rows.stream()
                .sorted(Comparator.comparingLong(ResponseListRow::elapsedMinutes).reversed())
                .limit(TOP_ROWS)
                .map(r -> new AdminDashboardView.SupportTop(
                        r.type(), r.status(), r.course(), r.requester(), r.elapsed(), r.detailUrl()))
                .toList();
    }

    private List<AdminDashboardView.NoticeItem> notices() {
        List<NoticeListRow> rows = noticeService.search(
                new NoticeSearchCond(null, null, null, null, null),
                PageRequest.of(0, NOTICE_ROWS,
                        Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("id")))).getContent();
        return rows.stream()
                .map(n -> new AdminDashboardView.NoticeItem(
                        (n.pinned() ? "[고정] " : "") + n.title(), NOTICE_PREFIX + n.id()))
                .toList();
    }

    /* ===================== 내부 ===================== */

    /** 미응답이 오래된 순으로 정렬해 쓰려고 Q&A + 튜터링을 합쳐 둔다 (AdminSupportController 와 같은 조합). */
    private List<ResponseListRow> supportRows() {
        // 관리자 대시보드는 SecurityConfig 상 ADMIN 전용이라 스코프 없이(null) 전체를 본다.
        List<ResponseListRow> rows = new ArrayList<>(qnaService.responseRows(null));
        rows.addAll(tutoringService.responseRows(null));
        return rows;
    }

    private List<CompletionView> completions(List<Course> courses) {
        List<CompletionView> all = new ArrayList<>();
        for (Course c : courses) {
            all.addAll(completionService.viewsByCourse(c.getId()));
        }
        return all;
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank() || DashboardFormat.NONE.equals(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text, DashboardFormat.DATE);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
