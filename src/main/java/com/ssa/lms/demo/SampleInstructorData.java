package com.ssa.lms.demo;

import com.ssa.lms.attendance.web.AttendanceMatrixView;
import com.ssa.lms.completion.web.CompletionView;
import com.ssa.lms.content.entity.ContentStatus;
import com.ssa.lms.content.entity.ContentType;
import com.ssa.lms.content.web.ContentView;
import com.ssa.lms.course.entity.CourseStatus;
import com.ssa.lms.course.service.CourseQueryService;
import com.ssa.lms.course.service.CourseQueryService.CourseOption;
import com.ssa.lms.course.web.CourseScheduleView;
import com.ssa.lms.course.web.InstructorCourseView;
import com.ssa.lms.course.web.SubjectView;
import com.ssa.lms.grading.dto.AttemptGradingDetail;
import com.ssa.lms.notice.dto.NoticeListRow;
import com.ssa.lms.proctor.dto.LiveAttemptRow;
import com.ssa.lms.proctor.dto.LiveMonitorView;
import com.ssa.lms.proctor.dto.MonitoringRow;
import com.ssa.lms.proctor.dto.RecordingRow;
import com.ssa.lms.grading.dto.AttemptGradingRow;
import com.ssa.lms.grading.dto.ExamGradingRow;
import com.ssa.lms.grading.dto.ExamGradingSummary;
import com.ssa.lms.grading.dto.GradingQuestionRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 강사 화면 <b>예시(샘플) 데이터</b> — 화면정의서 캡처 및 기능 시연용.
 *
 * <p>{@link SampleScreenData} 와 같은 원칙이다: <b>DB 에는 아무것도 쓰지 않고</b>, 실제 데이터가
 * 0건일 때만 요청마다 메모리에서 조립한다. 실제 데이터가 1건이라도 생기면 그 순간부터
 * 예시는 나오지 않는다. 끄는 방법도 같다 — {@code LMS_DEMO_SAMPLE_DATA=false}.</p>
 *
 * <p>훈련생 화면과 파일을 나눈 이유는 단순히 분량이다. id 구간
 * ({@link SampleScreenData#SAMPLE_ID_MIN}~{@link SampleScreenData#SAMPLE_ID_MAX})과
 * {@link SampleScreenData#isSampleId} 판별은 그대로 공유한다.</p>
 *
 * <p><b>id 배분</b> — 과제 채점은 {@code SampleScreenData} 가 900401~900599 를 쓰고,
 * 이 클래스는 900700 이후를 쓴다. 겹치면 서로 다른 화면이 같은 id 를 예시로 주장하게 된다.</p>
 */
@Component
@RequiredArgsConstructor
public class SampleInstructorData {

    static final String COURSE = "K-디지털 트레이닝 풀스택 개발자 양성과정";
    static final String COHORT = "3기";
    static final String COURSE_CODE = "KDT-2026-001";

    static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    static final DateTimeFormatter DOT_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 예시 화면 전반에서 쓰는 수강생 명단. 이름과 식별자만 — 개인정보는 담지 않는다. */
    static final String[][] STUDENTS = {
            {"강민준", "trainee01"}, {"김서윤", "trainee02"}, {"박지호", "trainee03"},
            {"이하은", "trainee04"}, {"정우진", "trainee05"}, {"최유나", "trainee06"},
            {"한도윤", "trainee07"}, {"오세아", "trainee08"}, {"윤재원", "trainee09"},
            {"임채린", "trainee10"},
    };

    private static final long EXAM_ID_BASE = 900_701L;
    private static final long ATTEMPT_ID_BASE = 900_801L;

    private final SampleScreenData base;

    public boolean isEnabled() {
        return base.isEnabled();
    }

    /** 실제 데이터가 있으면 그대로, 0건이고 기능이 켜져 있으면 예시로 채운다. */
    public <T> SampleScreenData.Filled<T> fill(List<T> real, java.util.function.Supplier<List<T>> sample) {
        return base.fill(real, sample);
    }

    /**
     * 예시 담당 과정 1건.
     *
     * <p>담당 과정이 아예 없는 강사도 화면을 볼 수 있어야 해서 과정 선택 드롭다운에 넣는다.
     * <b>JPA 엔티티를 가짜로 만들지 않는다</b> — {@code Course} 는 id 가 {@code @GeneratedValue}
     * 라 빌더로 채울 수 없고, 영속 컨텍스트 밖의 엔티티를 화면에 흘리면 지연 로딩에서 터진다.
     * 화면이 실제로 읽는 네 필드만 담은 조회용 record 를 쓴다.</p>
     */
    public CourseOption sampleCourse() {
        return new CourseOption(SAMPLE_COURSE_ID, COURSE_CODE, COURSE, COHORT);
    }

    public List<CourseOption> sampleCourseOptions() {
        return List.of(sampleCourse());
    }

    /** 예시 과정 id. 실제 PK 와 겹치지 않게 예시 구간을 쓴다. */
    public static final long SAMPLE_COURSE_ID = 901_001L;

    /* ===================== 이수 관리 ===================== */

    /**
     * 담당 과정 수강생의 이수 현황 (instructor/graduate.html).
     * 이수/미이수/판정대기와 확정/이수예정이 섞이도록 만든다.
     */
    public List<CompletionView> completions() {
        if (!isEnabled()) {
            return List.of();
        }
        String courseLabel = COURSE + " / " + COHORT;
        LocalDateTime confirmedAt = LocalDateTime.now().minusDays(9).withHour(16).withMinute(0);
        int[] progress = {100, 98, 100, 96, 92, 74, 100, 88, 95, 61};
        int[] attendance = {96, 100, 92, 88, 90, 71, 98, 84, 93, 58};

        List<CompletionView> rows = new ArrayList<>(STUDENTS.length);
        for (int i = 0; i < STUDENTS.length; i++) {
            // 진도·출석이 모두 기준(80%) 이상이면 이수, 하나라도 미달이면 미이수,
            // 마지막 두 명은 아직 판정 전으로 둔다 — 세 상태가 한 화면에 다 보이게.
            boolean pending = i >= STUDENTS.length - 2;
            boolean passed = progress[i] >= 80 && attendance[i] >= 80;
            String result = pending ? "PENDING" : passed ? "PASS" : "FAIL";
            String resultLabel = pending ? "판정대기" : passed ? "이수" : "미이수";
            boolean confirmed = !pending && passed;

            rows.add(new CompletionView(
                    901_101L + i,
                    STUDENTS[i][0],
                    BIRTH_DATES[i],
                    courseLabel,
                    progress[i], attendance[i],
                    pending ? null : 84.0 + i,
                    !pending,
                    result, resultLabel,
                    confirmed ? "CONFIRMED" : "EXPECTED",
                    confirmed ? "확정" : "이수예정",
                    confirmed ? confirmedAt : null,
                    confirmed));
        }
        return rows;
    }

    /** 예시 생년월일. 실제 컬럼은 AES 암호문이라 여기 값과 무관하다. */
    private static final String[] BIRTH_DATES = {
            "1998-03-11", "1999-07-24", "1997-11-02", "2000-01-19", "1996-05-30",
            "1999-09-08", "1998-12-15", "2001-04-03", "1997-06-21", "2000-10-27",
    };

    /* ===================== 훈련생 관리 ===================== */

    /** 담당 훈련생 목록 (instructor/trainees.html). */
    public List<CourseQueryService.UserDisplay> trainees() {
        if (!isEnabled()) {
            return List.of();
        }
        List<CourseQueryService.UserDisplay> rows = new ArrayList<>(STUDENTS.length);
        for (int i = 0; i < STUDENTS.length; i++) {
            rows.add(new CourseQueryService.UserDisplay(
                    901_101L + i, STUDENTS[i][1], STUDENTS[i][0]));
        }
        return rows;
    }

    /* ===================== 출결현황 ===================== */

    /** 차시별 출결 매트릭스 (instructor/attendance.html). 12차시 × 10명. */
    public AttendanceMatrixView attendanceMatrix() {
        if (!isEnabled()) {
            return AttendanceMatrixView.of(List.of(), List.of());
        }
        String[] sessionNames = {
                "오리엔테이션 및 개발환경 구축", "HTML/CSS 기본 구조", "JavaScript 기초 문법",
                "JavaScript DOM 제어", "Java 객체지향 기초", "Java 컬렉션과 스트림",
                "데이터베이스와 SQL", "JDBC 와 커넥션 풀", "Spring Boot 시작하기",
                "Spring Data JPA", "REST API 설계", "React 컴포넌트 기초",
        };
        LocalDateTime base = LocalDateTime.now().minusDays(sessionNames.length * 2L);

        List<AttendanceMatrixView.Col> columns = new ArrayList<>(sessionNames.length);
        for (int s = 0; s < sessionNames.length; s++) {
            columns.add(new AttendanceMatrixView.Col(
                    901_201L + s, s + 1, sessionNames[s], base.plusDays(s * 2L).toLocalDate()));
        }

        List<AttendanceMatrixView.Row> rows = new ArrayList<>(STUDENTS.length);
        for (int i = 0; i < STUDENTS.length; i++) {
            List<AttendanceMatrixView.Cell> cells = new ArrayList<>(sessionNames.length);
            int credited = 0;
            for (int s = 0; s < sessionNames.length; s++) {
                // 결정적으로 흩뿌린다 — 요청마다 값이 바뀌면 캡처본과 화면이 달라진다.
                String status = attendanceStatus(i, s);
                boolean isCredited = !"ABSENT".equals(status);
                if (isCredited) {
                    credited++;
                }
                cells.add(new AttendanceMatrixView.Cell(
                        901_301L + i * sessionNames.length + s,
                        status, attendanceLabel(status), isCredited, true));
            }
            rows.add(new AttendanceMatrixView.Row(
                    901_101L + i, STUDENTS[i][0], cells,
                    (int) Math.round(credited * 100.0 / sessionNames.length)));
        }
        return AttendanceMatrixView.of(columns, rows);
    }

    private static String attendanceStatus(int student, int session) {
        int k = (student * 7 + session * 3) % 17;
        if (k == 4) {
            return "ABSENT";
        }
        if (k == 9) {
            return "LATE";
        }
        if (k == 13) {
            return "EXCUSED";
        }
        return "PRESENT";
    }

    private static String attendanceLabel(String status) {
        return switch (status) {
            case "PRESENT" -> "출석";
            case "LATE" -> "지각";
            case "EXCUSED" -> "공결";
            default -> "결석";
        };
    }

    /* ===================== 담당 과정 / 일정 / 콘텐츠 / 공지 ===================== */

    /** 담당 과정 목록 (instructor/courses.html). */
    public List<InstructorCourseView> myCourses() {
        if (!isEnabled()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        return List.of(
                new InstructorCourseView(SAMPLE_COURSE_ID, COURSE_CODE, COURSE, COHORT,
                        "KDT", CourseStatus.IN_PROGRESS, CourseStatus.IN_PROGRESS.getLabel(),
                        today.minusDays(60), today.plusDays(120), 25, 6, STUDENTS.length),
                new InstructorCourseView(SAMPLE_COURSE_ID + 1, "KDT-2026-002",
                        "K-디지털 트레이닝 데이터 분석가 양성과정", "1기",
                        "KDT", CourseStatus.RECRUITING, CourseStatus.RECRUITING.getLabel(),
                        today.plusDays(21), today.plusDays(201), 20, 5, 0),
                new InstructorCourseView(SAMPLE_COURSE_ID + 2, "KDT-2025-004",
                        "K-디지털 트레이닝 백엔드 개발자 양성과정", "2기",
                        "KDT", CourseStatus.COMPLETED, CourseStatus.COMPLETED.getLabel(),
                        today.minusDays(280), today.minusDays(40), 25, 6, 23));
    }

    /** 과정 상세 화면(instructor/courses-detail.html)이 읽는 필드만 담은 조회용 record. */
    public record CourseDetail(String courseCode, String courseName, String cohort, String category,
                               CourseStatus status, LocalDate startDate, LocalDate endDate,
                               int capacity, String description) {
    }

    /**
     * 예시 담당 과정 상세. 목록({@link #myCourses})의 예시 행을 클릭했을 때 렌더한다 —
     * 예시 id 는 실제 PK 가 아니어서 DB 조회로 가면 404 가 되기 때문. 목록과 같은 데이터에서
     * 만들어 목록↔상세가 어긋나지 않게 한다. 모르는 id 면 null (→ 컨트롤러가 실제 조회 경로로).
     */
    public CourseDetail courseDetail(long id) {
        return myCourses().stream()
                .filter(c -> c.id() == id)
                .findFirst()
                .map(c -> new CourseDetail(c.courseCode(), c.courseName(), c.cohort(), c.category(),
                        c.status(), c.startDate(), c.endDate(), c.capacity(),
                        "실무 프로젝트 중심으로 기획부터 배포까지 서비스 개발 전 과정을 경험하는 K-디지털 트레이닝 과정입니다."))
                .orElse(null);
    }

    /**
     * 예시 과정의 과목·차시 구성. {@link SubjectView} 는 순수 조회용 record 라 예시로 만들어도
     * 안전하다(JPA 엔티티 아님). 차시 일정은 과정 시작일부터 3일 간격으로 배치해 과정 상태
     * (모집중/진행중/종료)와 날짜가 자연스럽게 맞도록 한다.
     */
    public List<SubjectView> curriculum(long id) {
        return myCourses().stream()
                .filter(c -> c.id() == id)
                .findFirst()
                .map(this::sampleCurriculum)
                .orElse(List.of());
    }

    private List<SubjectView> sampleCurriculum(InstructorCourseView course) {
        // 각 행의 첫 칸이 과목명, 나머지가 차시명
        String[][] subjects = {
                {"웹 프론트엔드 기초", "HTML/CSS 기본 구조", "JavaScript 기초 문법", "JavaScript DOM 제어"},
                {"Java 백엔드", "Java 객체지향 기초", "Java 컬렉션과 스트림", "예외 처리와 테스트"},
                {"데이터베이스", "데이터베이스와 SQL", "JPA 엔티티 매핑"},
                {"Spring 프레임워크", "Spring Boot 시작하기", "Spring MVC와 Thymeleaf", "Spring Data JPA", "Spring Security"},
        };
        LocalDate lessonDate = course.startDate();
        long subjectId = SAMPLE_COURSE_ID + 100;
        long sessionId = SAMPLE_COURSE_ID + 200;
        List<SubjectView> rows = new ArrayList<>(subjects.length);
        for (int i = 0; i < subjects.length; i++) {
            List<SubjectView.SessionView> sessions = new ArrayList<>(subjects[i].length - 1);
            for (int j = 1; j < subjects[i].length; j++) {
                sessions.add(new SubjectView.SessionView(sessionId++, j, subjects[i][j], lessonDate, 180));
                lessonDate = lessonDate.plusDays(3);
            }
            rows.add(new SubjectView(subjectId++, subjects[i][0], null, i + 1, sessions));
        }
        return rows;
    }

    /** 일정 관리 (instructor/scheduler.html). 차시별 수업 일정. */
    public List<CourseScheduleView> schedule() {
        if (!isEnabled()) {
            return List.of();
        }
        String[][] plan = {
                {"웹 프론트엔드 기초", "HTML/CSS 기본 구조"},
                {"웹 프론트엔드 기초", "JavaScript 기초 문법"},
                {"웹 프론트엔드 기초", "JavaScript DOM 제어"},
                {"Java 백엔드", "Java 객체지향 기초"},
                {"Java 백엔드", "Java 컬렉션과 스트림"},
                {"데이터베이스", "데이터베이스와 SQL"},
                {"Spring 프레임워크", "Spring Boot 시작하기"},
                {"Spring 프레임워크", "Spring Data JPA"},
        };
        LocalDate start = LocalDate.now().minusDays(6);
        List<CourseScheduleView> rows = new ArrayList<>(plan.length);
        for (int i = 0; i < plan.length; i++) {
            rows.add(new CourseScheduleView(
                    start.plusDays(i * 2L), SAMPLE_COURSE_ID, COURSE_CODE, COURSE,
                    plan[i][0], i + 1, plan[i][1], 240));
        }
        return rows;
    }

    /** 학습 콘텐츠 (instructor/contents.html). */
    public List<ContentView> contents() {
        if (!isEnabled()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                sampleContent(901_401L, ContentType.VIDEO, "1차시 — 개발환경 구축 실습",
                        "JDK·IDE 설치부터 첫 프로젝트 생성까지 따라 하는 영상입니다.",
                        901_201L, "오리엔테이션 및 개발환경 구축", 1, 2_940, null, "49분", true, now.minusDays(24)),
                sampleContent(901_402L, ContentType.DOCUMENT, "HTML/CSS 실습 교안",
                        "레이아웃 실습에 사용하는 교안과 예제 코드입니다.",
                        901_202L, "HTML/CSS 기본 구조", 2, null, 38, "38쪽", true, now.minusDays(22)),
                sampleContent(901_403L, ContentType.VIDEO, "JavaScript DOM 제어 심화",
                        "이벤트 위임과 동적 렌더링을 다룹니다.",
                        901_204L, "JavaScript DOM 제어", 3, 3_600, null, "60분", false, now.minusDays(18)),
                sampleContent(901_404L, ContentType.DOCUMENT, "Spring Data JPA 정리 노트",
                        "영속성 컨텍스트와 연관관계 매핑 요약본입니다.",
                        901_210L, "Spring Data JPA", 4, null, 24, "24쪽", true, now.minusDays(6)));
    }

    private ContentView sampleContent(long id, ContentType type, String title, String description,
                                      Long sessionId, String sessionName, int orderNo,
                                      Integer durationSeconds, Integer pageCount, String durationLabel,
                                      boolean required, LocalDateTime createdAt) {
        return new ContentView(
                id, type, type.getLabel(), title, description,
                SAMPLE_COURSE_ID, COURSE, sessionId, sessionName,
                durationSeconds, pageCount, durationLabel,
                orderNo, required,
                ContentStatus.ACTIVE, ContentStatus.ACTIVE.getLabel(),
                // 예시 콘텐츠에는 실제 파일이 없다. 재생·다운로드 링크를 만들어 두면 눌렀을 때 오류가 난다.
                null, null, createdAt);
    }

    /** 공지사항 (instructor/notices.html). */
    public List<NoticeListRow> notices() {
        if (!isEnabled()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        return List.of(
                new NoticeListRow(901_501L, "필독", "[공지] 2026년 1차 중간평가 일정 안내",
                        "운영매니저", today.minusDays(2).format(DATE), today.minusDays(1).format(DATE),
                        142, true, COURSE),
                new NoticeListRow(901_502L, "학사", "출결 정정 요청은 해당 주 금요일까지 접수합니다",
                        "운영매니저", today.minusDays(9).format(DATE), today.minusDays(9).format(DATE),
                        87, true, COURSE),
                new NoticeListRow(901_503L, "취업", "상반기 채용 연계 기업 설명회 참가 신청",
                        "취업지원팀", today.minusDays(15).format(DATE), today.minusDays(15).format(DATE),
                        203, false, "전체"),
                new NoticeListRow(901_504L, "학사", "실습실 이용 시간 변경 안내 (야간 연장)",
                        "운영매니저", today.minusDays(28).format(DATE), today.minusDays(28).format(DATE),
                        64, false, COURSE));
    }

    /* ===================== 평가 모니터링 (감독) ===================== */

    /** 응시 감독 목록 (instructor/proctor/exams.html). */
    public List<MonitoringRow> monitoringList(String detailUrlPrefix) {
        if (!isEnabled()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<MonitoringRow> rows = new ArrayList<>();
        int no = 0;
        for (SampleExam e : EXAMS) {
            LocalDateTime start = now.plusDays(e.startOffsetDays()).withHour(10).withMinute(0);
            LocalDateTime end = start.plusHours(2);
            boolean upcoming = e.startOffsetDays() > 0;
            int completed = e.submitted();
            int inProgress = upcoming ? 0 : Math.max(0, STUDENTS.length - completed - 1);
            int notStarted = STUDENTS.length - completed - inProgress;

            rows.add(new MonitoringRow(
                    ++no, e.id(), COURSE, COURSE_CODE, e.title(), e.instructor(),
                    start.format(DATE),
                    start.format(TIME) + " ~ " + end.format(TIME),
                    upcoming ? "-" : "종료", upcoming ? "-" : "120분",
                    inProgress, completed, notStarted, 0, STUDENTS.length,
                    start.format(DATETIME), end.format(DATETIME),
                    detailUrlPrefix + e.id()));
        }
        return rows;
    }

    /** 시험 한 건의 감독 상세 (instructor/proctor/recordings.html 의 좌측 패널). */
    public LiveMonitorView liveMonitor(Long examId, String attemptUrlPrefix, String listUrl) {
        SampleExam e = isEnabled() ? exam(examId) : null;
        if (e == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.plusDays(e.startOffsetDays()).withHour(10).withMinute(0);
        int completed = e.submitted();
        int inProgress = Math.max(0, STUDENTS.length - completed - 1);

        return new LiveMonitorView(
                e.id(), e.title(), COURSE, COURSE_CODE, e.instructor(),
                start.format(DATETIME), start.plusHours(2).format(DATETIME),
                120, true, true,
                inProgress, completed, STUDENTS.length - completed - inProgress, 0, STUDENTS.length,
                false,
                attemptUrlPrefix, listUrl,
                liveAttempts(e, start));
    }

    private List<LiveAttemptRow> liveAttempts(SampleExam e, LocalDateTime start) {
        List<LiveAttemptRow> rows = new ArrayList<>();
        for (int i = 0; i < STUDENTS.length; i++) {
            boolean submitted = i < e.submitted();
            boolean live = !submitted && i < STUDENTS.length - 1;
            long warn = (i % 4 == 1) ? 2 : 0;
            long critical = (i == 3) ? 1 : 0;

            rows.add(new LiveAttemptRow(
                    900_801L + i, 901_101L + i, STUDENTS[i][0], STUDENTS[i][1], 1,
                    submitted ? "SUBMITTED" : live ? "IN_PROGRESS" : "NOT_STARTED",
                    submitted ? "제출완료" : live ? "응시중" : "미응시",
                    submitted || live ? start.format(DATETIME) : "-",
                    start.plusHours(2).format(DATETIME),
                    submitted ? start.plusMinutes(55 + i * 3L).format(DATETIME) : "-",
                    live ? 1_500 - i * 60L : 0,
                    live ? (25 - i) + "분 남음" : "-",
                    submitted || live ? 40 + i * 3L : 0,
                    warn, critical, (critical > 0 ? 1 : 0),
                    submitted || live ? "10.20.1." + (30 + i) : "-",
                    live));
        }
        return rows;
    }

    /**
     * 녹화 목록 (instructor/proctor/recordings.html).
     *
     * <p><b>재생은 막아 둔다</b>({@code playable=false}, 빈 스트림 URL) — 예시 행에는 실제 녹화
     * 파일이 없어서 재생을 걸면 스트리밍 엔드포인트가 없는 파일을 찾으러 간다.</p>
     */
    public List<RecordingRow> recordings() {
        if (!isEnabled()) {
            return List.of();
        }
        SampleExam e = EXAMS.get(0);
        LocalDateTime recordedAt = LocalDateTime.now().plusDays(e.startOffsetDays()).withHour(10).withMinute(0);
        List<RecordingRow> rows = new ArrayList<>();
        for (int i = 0; i < e.submitted(); i++) {
            long warn = (i % 4 == 1) ? 2 : 0;
            long critical = (i == 3) ? 1 : 0;
            rows.add(new RecordingRow(
                    901_601L + i, 900_801L + i, 901_101L + i,
                    STUDENTS[i][0], e.title(), COURSE, COURSE_CODE,
                    recordedAt.plusMinutes(i * 2L).format(DATETIME),
                    (55 + i * 3) + "분",
                    (180 + i * 12) + "MB",
                    "READY", "보관중",
                    false, warn, critical,
                    ""));
        }
        return rows;
    }

    /* ===================== 시험 채점 ===================== */

    /**
     * 예시 시험 1건.
     *
     * @param submitted 응시(제출) 인원 — 명단 앞에서부터
     * @param graded    채점 완료 인원 — 응시자 중 앞에서부터
     * @param confirmed 확정 인원 — 채점 완료자 중 앞에서부터
     */
    private record SampleExam(
            long id, String title, String instructor,
            int startOffsetDays, int endOffsetDays,
            int submitted, int graded, int confirmed,
            int manualQuestionCount, String status) {
    }

    private static final List<SampleExam> EXAMS = List.of(
            new SampleExam(EXAM_ID_BASE, "중간평가 — Java/Spring 기초", "김도현",
                    -6, -6, 9, 5, 2, 2, "채점중"),
            new SampleExam(EXAM_ID_BASE + 1, "쪽지시험 — HTML/CSS 레이아웃", "김도현",
                    -20, -20, 10, 10, 10, 0, "채점완료"),
            new SampleExam(EXAM_ID_BASE + 2, "최종평가 — 프로젝트 이해도", "박서연",
                    10, 10, 0, 0, 0, 3, "예정"));

    private static SampleExam exam(Long id) {
        if (id == null) {
            return null;
        }
        return EXAMS.stream().filter(e -> e.id() == id).findFirst().orElse(null);
    }

    /** 시험 채점 목록 (instructor/result.html). */
    public List<ExamGradingRow> examList(String urlPrefix) {
        if (!isEnabled()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<ExamGradingRow> rows = new ArrayList<>(EXAMS.size());
        int no = 0;
        for (SampleExam e : EXAMS) {
            LocalDateTime start = now.plusDays(e.startOffsetDays()).withHour(10).withMinute(0);
            LocalDateTime end = start.plusHours(2);
            rows.add(new ExamGradingRow(
                    ++no, String.valueOf(e.id()),
                    COURSE, COURSE_CODE,
                    e.title(), e.instructor(), "exam",
                    start.format(DOT_DATE), start.format(TIME) + "부터",
                    end.format(DOT_DATE), end.format(TIME) + "까지",
                    e.submitted(), STUDENTS.length - e.submitted(),
                    e.status(),
                    urlPrefix + "/exams/" + e.id(),
                    e.graded(), e.submitted() - e.graded()));
        }
        return rows;
    }

    /** 시험별 채점 현황 상단 요약 (instructor/result-grading.html). */
    public ExamGradingSummary examSummary(Long examId, String urlPrefix) {
        SampleExam e = isEnabled() ? exam(examId) : null;
        if (e == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.plusDays(e.startOffsetDays()).withHour(10).withMinute(0);
        boolean scheduled = "예정".equals(e.status());
        return new ExamGradingSummary(
                String.valueOf(e.id()),
                COURSE + " / " + COHORT,
                e.title(),
                e.instructor(),
                start.format(DATE),
                start.plusHours(2).format(DATE),
                e.status(),
                scheduled ? "status-scheduled" : "status-ongoing",
                e.submitted(), e.graded(), e.submitted() - e.graded(), e.confirmed(),
                STUDENTS.length,
                60, 100,
                e.manualQuestionCount(),
                // 예시 시험은 실제로 채점할 대상이 없다. 저장 버튼은 컨트롤러도 막는다.
                false,
                urlPrefix + "/exams/" + e.id(),
                urlPrefix + "/exams/" + e.id() + "/grades.xlsx",
                urlPrefix + "/exams/" + e.id() + "/grades.csv");
    }

    /** 시험별 응시자 목록. 미응시자도 행으로 나온다. */
    public List<AttemptGradingRow> attemptList(Long examId, String urlPrefix) {
        SampleExam e = isEnabled() ? exam(examId) : null;
        if (e == null) {
            return List.of();
        }
        List<AttemptGradingRow> rows = new ArrayList<>(STUDENTS.length);
        for (int i = 0; i < STUDENTS.length; i++) {
            boolean attempted = i < e.submitted();
            boolean graded = i < e.graded();
            boolean confirmed = i < e.confirmed();
            long attemptId = ATTEMPT_ID_BASE + i;

            rows.add(new AttemptGradingRow(
                    i + 1,
                    attempted ? String.valueOf(attemptId) : "",
                    String.valueOf(900_901L + i),
                    STUDENTS[i][0],
                    STUDENTS[i][1],
                    attempted ? (55 + i * 3) + "분" : "-",
                    graded ? String.valueOf(SCORES[i]) : "-",
                    attempted ? (confirmed ? "확정" : graded ? "채점완료" : "채점대기") : "미응시",
                    confirmed ? "confirmed" : graded ? "graded" : attempted ? "pending" : "none",
                    !attempted,
                    attempted ? urlPrefix + "/attempts/" + attemptId : ""));
        }
        return rows;
    }

    /** 채점 완료 응시자의 점수. 명단 순서대로 쓴다. */
    private static final int[] SCORES = {88, 92, 74, 96, 81, 69, 85, 78, 90, 83};

    /**
     * 채점 팝업 (instructor/grading-modal-result.html).
     *
     * <p>예시 판별은 <b>응시 id</b> 로 한다 — 이 화면 URL 에는 시험 id 가 없다.
     * 어느 시험의 응시인지는 첫 번째 예시 시험(채점 진행 중인 것)으로 고정한다.</p>
     */
    public AttemptGradingDetail attemptDetail(Long attemptId, String urlPrefix) {
        if (!isEnabled() || attemptId == null) {
            return null;
        }
        int i = (int) (attemptId - ATTEMPT_ID_BASE);
        SampleExam e = EXAMS.get(0);
        if (i < 0 || i >= e.submitted()) {
            return null;
        }
        boolean graded = i < e.graded();
        boolean confirmed = i < e.confirmed();
        int autoScore = graded ? SCORES[i] - 12 : SCORES[i] - 12;
        int manualScore = graded ? 12 : 0;

        return new AttemptGradingDetail(
                String.valueOf(attemptId),
                String.valueOf(e.id()),
                COURSE + " / " + COHORT,
                e.title(),
                new AttemptGradingDetail.Student(STUDENTS[i][0], STUDENTS[i][1]),
                (55 + i * 3) + "분",
                confirmed ? "확정" : graded ? "채점완료" : "채점대기",
                confirmed ? "confirmed" : graded ? "graded" : "pending",
                autoScore, manualScore, autoScore + manualScore,
                100, 60,
                graded ? (autoScore + manualScore) >= 60 : null,
                confirmed,
                // 예시 응시라 저장할 대상이 없다 — 화면에서도 컨트롤러에서도 막는다.
                false,
                !graded,
                1,
                sampleQuestions(graded),
                urlPrefix + "/attempts/" + attemptId + "/scores",
                urlPrefix + "/attempts/" + attemptId + "/confirm",
                urlPrefix + "/exams/" + e.id());
    }

    /** 자동채점 문항 3 + 수동채점(서술형) 문항 2 — 두 유형이 한 화면에 다 보이게 한다. */
    private List<GradingQuestionRow> sampleQuestions(boolean graded) {
        List<GradingQuestionRow> rows = new ArrayList<>();
        rows.add(new GradingQuestionRow("900951", 1, "객관식", "자동채점", "auto",
                20, 20,
                "다음 중 Spring Bean 의 기본 스코프로 옳은 것은?",
                "singleton", "singleton", null, false, false));
        rows.add(new GradingQuestionRow("900952", 2, "객관식", "자동채점", "auto",
                20, 0,
                "JPA 에서 지연 로딩(LAZY)이 적용되지 않는 연관관계는?",
                "@ManyToOne", "@OneToMany", null, false, false));
        rows.add(new GradingQuestionRow("900953", 3, "단답형", "자동채점", "auto",
                20, 20,
                "HTTP 상태코드 404 가 의미하는 것을 한 단어로 쓰시오.",
                "Not Found", "Not Found", null, false, false));
        rows.add(new GradingQuestionRow("900954", 4, "서술형",
                graded ? "채점완료" : "채점대기", graded ? "graded" : "pending",
                20, graded ? 14 : null,
                "트랜잭션의 ACID 특성을 각각 한 문장으로 설명하시오.",
                "원자성·일관성·격리성·지속성을 각각 정확히 설명하면 만점.",
                "원자성은 전부 성공하거나 전부 실패하는 것이고, 일관성은 트랜잭션 전후로 데이터 "
                        + "제약이 유지되는 것입니다. 격리성은 동시에 실행되는 트랜잭션이 서로 영향을 "
                        + "주지 않는 것이고, 지속성은 커밋된 결과가 유지되는 것입니다.",
                graded ? "네 가지 모두 언급했으나 격리 수준에 대한 설명이 부족합니다." : null,
                true, true));
        rows.add(new GradingQuestionRow("900955", 5, "서술형",
                graded ? "채점완료" : "채점대기", graded ? "graded" : "pending",
                20, graded ? 16 : null,
                "N+1 문제가 발생하는 상황과 해결 방법을 설명하시오.",
                "발생 원인(지연 로딩 + 반복 조회)과 해결책(fetch join, @EntityGraph, batch size) 언급 시 만점.",
                "연관 엔티티를 지연 로딩으로 두고 목록을 순회하면 건마다 추가 쿼리가 나갑니다. "
                        + "fetch join 이나 @EntityGraph 로 한 번에 가져오면 해결됩니다.",
                graded ? "원인과 해결책을 정확히 짚었습니다. batch size 옵션까지 언급하면 완벽합니다." : null,
                true, true));
        return rows;
    }
}
