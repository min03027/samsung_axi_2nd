package com.ssa.lms.demo;

import com.ssa.lms.assignment.dto.CourseAssignmentRow;
import com.ssa.lms.assignment.dto.GradeHistoryRow;
import com.ssa.lms.assignment.dto.GradingStudentRow;
import com.ssa.lms.assignment.dto.GradingSummaryView;
import com.ssa.lms.assignment.dto.SubmissionDetailView;
import com.ssa.lms.assignment.dto.TraineeAssignmentCard;
import com.ssa.lms.assignment.dto.UserOption;
import com.ssa.lms.attendance.web.TraineeAttendanceView;
import com.ssa.lms.completion.web.CompletionView;
import com.ssa.lms.content.entity.ContentStatus;
import com.ssa.lms.content.entity.ContentType;
import com.ssa.lms.content.service.ProgressService;
import com.ssa.lms.content.web.ContentView;
import com.ssa.lms.content.web.CourseOption;
import com.ssa.lms.content.web.LearningContentView;
import com.ssa.lms.content.web.LearningSessionGroup;
import com.ssa.lms.content.web.ProgressResponse;
import com.ssa.lms.exam.dto.ExamTakeRow;
import com.ssa.lms.survey.dto.SurveyDetailView;
import com.ssa.lms.survey.dto.TraineeSurveyRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 훈련생·강사 화면 <b>예시(샘플) 데이터</b> — 화면정의서 캡처용.
 *
 * <p><b>왜 필요한가:</b> 새로 만든 계정이나 아직 과제·시험이 배정되지 않은 과정에서는
 * 과제/시험/출결/이수/설문 화면이 전부 "데이터가 없습니다" 만 나온다. 화면정의서에는
 * 훈련생이 실제로 무엇을 할 수 있는지가 보여야 하므로, <b>본인 데이터가 0건일 때만</b>
 * 예시 행을 대신 보여준다. 강사 과제 채점 목록도 같은 이유로 포함한다.</p>
 *
 * <p><b>DB 에는 아무것도 쓰지 않는다.</b> 출결·이수는 3년 보존 대상 증빙 데이터라
 * 시연용 레코드를 섞으면 안 된다. 여기서 만드는 값은 요청마다 메모리에서 조립되고
 * 사라진다. 실제 데이터가 1건이라도 생기면 그 순간부터 예시는 나오지 않는다.</p>
 *
 * <p><b>끄는 방법:</b> {@code LMS_DEMO_SAMPLE_DATA=false} (서버 {@code .env})
 * 또는 {@code --lms.demo.sample-data=false}. 재배포 없이 값만 바꾸면 된다.</p>
 *
 * <p><b>id 규약:</b> 예시 행의 id 는 {@link #SAMPLE_ID_MIN}~{@link #SAMPLE_ID_MAX} 구간을 쓴다.
 * 실제 데이터와 겹치지 않게 하려는 것이고, 컨트롤러는 {@link #isSampleId}로 판별해
 * "예시 데이터에는 이 동작을 할 수 없다"고 안내한다 — 예시 id 를 그대로 조회하면
 * 500 이나 빈 화면이 나기 때문이다.</p>
 */
@Component
public class SampleScreenData {

    /** 예시 행 id 구간 (양끝 포함). 실제 PK 가 이 구간에 닿을 일은 없다. */
    public static final long SAMPLE_ID_MIN = 900_001L;
    public static final long SAMPLE_ID_MAX = 909_999L;

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    /** 과제 목록 화면의 날짜·시간 표기 (CourseAssignmentRow 와 같은 형식이어야 한다). */
    private static final DateTimeFormatter DOT_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final String COURSE = "K-디지털 트레이닝 풀스택 개발자 양성과정";
    private static final String COHORT = "3기";
    private static final String COURSE_LABEL = COURSE + " / " + COHORT;

    private final boolean enabled;

    public SampleScreenData(@Value("${lms.demo.sample-data:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static boolean isSampleId(Long id) {
        return id != null && id >= SAMPLE_ID_MIN && id <= SAMPLE_ID_MAX;
    }

    /**
     * 실제 데이터가 있으면 그대로, 0건이고 기능이 켜져 있으면 예시로 채운다.
     * 예시를 썼는지 여부는 화면 상단 안내 문구를 띄우는 데 쓴다.
     */
    public <T> Filled<T> fill(List<T> real, Supplier<List<T>> sample) {
        if (real != null && !real.isEmpty()) {
            return new Filled<>(real, false);
        }
        if (!enabled) {
            return new Filled<>(real == null ? List.of() : real, false);
        }
        return new Filled<>(sample.get(), true);
    }

    /** @param sample 예시 데이터로 채워졌는지 — true 면 화면이 "예시" 배지를 보여준다. */
    public record Filled<T>(List<T> rows, boolean sample) {
    }

    /* ===================== 과제 ===================== */

    public List<TraineeAssignmentCard> assignments() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                // 진행중 — 제출 모달까지 열어볼 수 있게 canSubmit=true 로 둔다
                new TraineeAssignmentCard(
                        "900001",
                        "REST API 게시판 구현",
                        COURSE, COHORT,
                        "ONGOING",
                        now.plusDays(5).withHour(23).withMinute(59).format(DATETIME),
                        "FILE_TEXT", true,
                        "Spring Boot 로 게시글 CRUD REST API 를 구현하고, 소스 압축본과 구현 설명을 함께 제출하세요. "
                                + "평가 기준: 요구사항 충족 50점 / 코드 품질 30점 / 문서화 20점.",
                        false, null, null, null,
                        true, 0, false),
                // 제출완료 + 채점완료 — 결과 모달(점수·피드백·내 답안)을 보여준다
                new TraineeAssignmentCard(
                        "900002",
                        "React 재사용 컴포넌트 3종 만들기",
                        COURSE, COHORT,
                        "SUBMITTED",
                        now.minusDays(4).withHour(23).withMinute(59).format(DATETIME),
                        "LINK", false,
                        "버튼·모달·테이블 컴포넌트를 props 기반으로 재사용 가능하게 만들고 배포 링크를 제출하세요.",
                        true,
                        "컴포넌트 분리 기준이 명확하고 props 설계가 좋았습니다. 모달의 접근성(포커스 트랩)만 보완하면 실무에서 그대로 쓸 수 있는 수준입니다.",
                        "https://sample-components.vercel.app\n첨부: 컴포넌트_설명서.pdf",
                        "92 / 100",
                        false, 1, false),
                // 제출완료 + 채점대기
                new TraineeAssignmentCard(
                        "900003",
                        "데이터 전처리 리포트",
                        COURSE, COHORT,
                        "SUBMITTED",
                        now.minusDays(1).withHour(23).withMinute(59).format(DATETIME),
                        "TEXT", true,
                        "결측치 처리와 정규화 과정을 실제 데이터셋 기준으로 정리해 제출하세요.",
                        false, null,
                        "결측치는 평균 대치 대신 중앙값으로 처리했고, 이상치는 IQR 기준으로 제거했습니다. "
                                + "정규화는 StandardScaler 를 사용했습니다.",
                        null,
                        false, 1, false),
                // 미제출(마감) — 지각 제출 불가 상태
                new TraineeAssignmentCard(
                        "900004",
                        "SQL 실행계획 분석 과제",
                        COURSE, COHORT,
                        "LATE",
                        now.minusDays(7).withHour(23).withMinute(59).format(DATETIME),
                        "FILE", false,
                        "느린 쿼리 3건의 실행계획을 캡처하고 인덱스 개선 전후를 비교해 제출하세요.",
                        false, null, null, null,
                        false, 0, false)
        );
    }

    /* ===================== 시험 ===================== */

    /**
     * 응시 목록 예시. 전부 {@code startable=false} 다 —
     * 예시 시험을 실제로 시작시키면 존재하지 않는 시험 id 로 응시 회차를 만들려다 실패한다.
     */
    public List<ExamTakeRow> exams() {
        LocalDateTime now = LocalDateTime.now();
        String block = "화면 예시용 샘플 시험입니다. 실제 시험이 배정되면 응시할 수 있어요.";
        return List.of(
                new ExamTakeRow(
                        "900101", "중간평가 — Java/Spring 기초",
                        "900001", COURSE, "8차시",
                        now.minusDays(1).format(DATETIME), now.plusDays(2).format(DATETIME),
                        60, 25, "객관식+주관식", "1회 응시", 0, 1,
                        "available", "응시 전 본인인증이 필요합니다.",
                        true, null, null,
                        true, false, block, false, 1, "채점 완료 후 공개"),
                new ExamTakeRow(
                        "900102", "쪽지시험 — HTML/CSS 레이아웃",
                        "900001", COURSE, "5차시",
                        now.minusDays(9).format(DATETIME), now.minusDays(8).format(DATETIME),
                        20, 10, "객관식", "재응시 불가", 1, 1,
                        "completed", "제출 완료 — 18/20 (90점)",
                        false, null, null,
                        true, false, block, false, 1, "즉시 공개"),
                new ExamTakeRow(
                        "900103", "최종평가 — 프로젝트 이해도",
                        "900001", COURSE, "20차시",
                        now.plusDays(10).format(DATETIME), now.plusDays(10).plusHours(2).format(DATETIME),
                        90, 40, "객관식+서술형", "1회 응시", 0, 1,
                        "scheduled", "응시 기간이 시작되면 열립니다.",
                        true, null, null,
                        true, false, block, false, 2, "채점 완료 후 공개")
        );
    }

    /* ===================== 출결현황 ===================== */

    public List<TraineeAttendanceView> attendance() {
        // 12차시: 출석 9 / 지각 1 / 공결 1 / 결석 1 → 인정 11건 = 92%
        String[][] plan = {
                {"오리엔테이션 및 개발환경 구축", "PRESENT"},
                {"HTML/CSS 기본 구조", "PRESENT"},
                {"JavaScript 기초 문법", "PRESENT"},
                {"JavaScript DOM 제어", "LATE"},
                {"Java 객체지향 기초", "PRESENT"},
                {"Java 컬렉션과 스트림", "PRESENT"},
                {"데이터베이스와 SQL", "PRESENT"},
                {"JDBC 와 커넥션 풀", "EXCUSED"},
                {"Spring Boot 시작하기", "PRESENT"},
                {"Spring Data JPA", "ABSENT"},
                {"REST API 설계", "PRESENT"},
                {"React 컴포넌트 기초", "PRESENT"},
        };

        LocalDate start = LocalDate.now().minusDays(plan.length * 2L);
        List<TraineeAttendanceView.Row> rows = new ArrayList<>();
        int credited = 0;
        for (int i = 0; i < plan.length; i++) {
            String status = plan[i][1];
            boolean isCredited = !"ABSENT".equals(status);
            if (isCredited) {
                credited++;
            }
            rows.add(new TraineeAttendanceView.Row(
                    i + 1, plan[i][0], start.plusDays(i * 2L), status, labelOf(status), isCredited));
        }
        int rate = (int) Math.round(credited * 100.0 / rows.size());
        return List.of(new TraineeAttendanceView(SAMPLE_ID_MIN, COURSE_LABEL, rate, rows));
    }

    private String labelOf(String status) {
        return switch (status) {
            case "PRESENT" -> "출석";
            case "LATE" -> "지각";
            case "EXCUSED" -> "공결";
            default -> "결석";
        };
    }

    /* ===================== 이수관리 ===================== */

    public List<CompletionView> completions() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                // 확정된 과정 — 이수증 다운로드 버튼이 보이는 상태
                new CompletionView(
                        900_201L, "홍길동", "1998-04-12",
                        "K-디지털 트레이닝 백엔드 개발자 양성과정 / 2기",
                        100, 96, 92.0, true,
                        "PASS", "이수", "CONFIRMED", "확정",
                        now.minusDays(20), true),
                // 진행 중인 과정 — 이수예정
                new CompletionView(
                        900_202L, "홍길동", "1998-04-12",
                        COURSE_LABEL,
                        68, 92, null, false,
                        "PENDING", "판정대기", "EXPECTED", "이수예정",
                        null, false)
        );
    }

    /* ===================== 설문조사 ===================== */

    public List<TraineeSurveyRow> surveys() {
        LocalDate today = LocalDate.now();
        return List.of(
                new TraineeSurveyRow("900301", "중간 만족도 조사", COURSE, COHORT,
                        today.minusDays(2).format(DATE), today.plusDays(5).format(DATE),
                        10, "ONGOING", today.minusDays(3).format(DATE)),
                new TraineeSurveyRow("900302", "강사 강의 평가 (8차시)", COURSE, COHORT,
                        today.minusDays(14).format(DATE), today.minusDays(7).format(DATE),
                        8, "SUBMITTED", today.minusDays(15).format(DATE)),
                new TraineeSurveyRow("900303", "수료 후 취업 지원 수요조사", COURSE, COHORT,
                        today.plusDays(10).format(DATE), today.plusDays(20).format(DATE),
                        12, "SCHEDULED", today.minusDays(1).format(DATE))
        );
    }

    /**
     * 예시 설문의 <b>응답 화면</b>. 목록에서 「응답하기」/「보기」를 눌렀을 때 쓴다.
     *
     * <p>예시 행만 있는 상태에서 상세를 열면 컨트롤러가 목록으로 되돌려서
     * 응답 화면 자체를 캡처할 수 없었다. 여기서 같은 id 의 상세를 만들어 준다 —
     * 목록과 마찬가지로 DB 는 건드리지 않고, 제출은 컨트롤러가 막는다.</p>
     *
     * <p>세 건이 각각 다른 상태를 보여준다: 900301 응답 가능 / 900302 제출완료(읽기전용)
     * / 900303 기간 전. 예시 id 가 아니거나 기능이 꺼져 있으면 {@code null}.</p>
     */
    public SurveyDetailView surveyDetail(Long id) {
        if (!enabled || id == null) {
            return null;
        }
        LocalDateTime today = LocalDate.now().atTime(23, 59);
        return switch (id.intValue()) {
            case 900301 -> new SurveyDetailView(
                    "900301", "중간 만족도 조사", COURSE_LABEL,
                    today.plusDays(5).format(DATETIME),
                    false, true, true,
                    List.of(
                            sampleScale("9003011", "과정 전반에 대한 만족도를 평가해 주세요.", 5),
                            sampleScale("9003012", "강의 진도와 난이도는 적절했나요?", 5),
                            sampleSingle("9003013", "가장 도움이 된 활동은 무엇인가요?",
                                    List.of("이론 강의", "실습 과제", "튜터링/Q&A", "동료 학습")),
                            sampleMulti("9003014", "개선이 필요하다고 느낀 영역을 모두 선택해 주세요.",
                                    List.of("강의 자료", "실습 환경", "과제 난이도", "학습 지원 응답 속도")),
                            sampleText("9003015", "과정 운영에 바라는 점을 자유롭게 적어 주세요.")));
            case 900302 -> new SurveyDetailView(
                    "900302", "강사 강의 평가 (8차시)", COURSE_LABEL,
                    today.minusDays(7).format(DATETIME),
                    true, false, true,
                    List.of(
                            sampleScale("9003021", "강사의 설명이 이해하기 쉬웠나요?", 5),
                            sampleSingle("9003022", "수업 진행 속도는 어땠나요?",
                                    List.of("너무 느림", "적절함", "너무 빠름")),
                            sampleText("9003023", "강의에 대해 남기고 싶은 의견을 적어 주세요.")));
            case 900303 -> new SurveyDetailView(
                    "900303", "수료 후 취업 지원 수요조사", COURSE_LABEL,
                    today.plusDays(20).format(DATETIME),
                    false, false, false,
                    List.of(
                            sampleScale("9003031", "취업 준비에 이 과정이 얼마나 도움이 되었나요?", 5),
                            sampleMulti("9003032", "필요한 취업 지원을 모두 선택해 주세요.",
                                    List.of("이력서 첨삭", "모의 면접", "채용 연계", "포트폴리오 리뷰")),
                            sampleText("9003033", "후속 과정에서 배우고 싶은 주제가 있다면 적어 주세요.")));
            default -> null;
        };
    }

    private static SurveyDetailView.Question sampleScale(String id, String title, int max) {
        return new SurveyDetailView.Question(id, "SCALE", true, title, max, List.of());
    }

    private static SurveyDetailView.Question sampleSingle(String id, String title, List<String> options) {
        return new SurveyDetailView.Question(id, "SINGLE", true, title, null, sampleChoices(id, options));
    }

    private static SurveyDetailView.Question sampleMulti(String id, String title, List<String> options) {
        return new SurveyDetailView.Question(id, "MULTI", false, title, null, sampleChoices(id, options));
    }

    private static SurveyDetailView.Question sampleText(String id, String title) {
        return new SurveyDetailView.Question(id, "TEXT", false, title, null, List.of());
    }

    private static List<SurveyDetailView.Choice> sampleChoices(String questionId, List<String> options) {
        List<SurveyDetailView.Choice> choices = new ArrayList<>(options.size());
        for (int i = 0; i < options.size(); i++) {
            choices.add(new SurveyDetailView.Choice(questionId + "-" + (i + 1), options.get(i)));
        }
        return choices;
    }

    /* ===================== 강사 과제 채점 ===================== */

    /**
     * 강사 과제 채점 목록 예시.
     *
     * <p>담당 과정에 아직 과제가 배정되지 않은 강사는 목록이 비어 화면정의서에 넣을 그림이
     * 나오지 않는다. 채점 대기/완료/평가예정 세 상태가 모두 보이도록 섞어 둔다.</p>
     *
     * <p>「채점하기」는 실제 채점 화면 URL 로 보낸다 — 그쪽도 예시 데이터를 렌더하므로
     * 열어서 캡처할 수 있다. 저장 동작만 컨트롤러가 막는다.</p>
     */
    public List<CourseAssignmentRow> courseAssignments(String gradingUrlPrefix) {
        LocalDateTime now = LocalDateTime.now();
        List<CourseAssignmentRow> rows = new ArrayList<>(SAMPLE_ASSIGNMENTS.size());
        int no = 0;
        for (SampleAssignment a : SAMPLE_ASSIGNMENTS) {
            LocalDateTime startAt = now.plusDays(a.startOffsetDays());
            LocalDateTime endAt = now.plusDays(a.endOffsetDays());
            rows.add(new CourseAssignmentRow(
                    String.valueOf(a.id()), ++no,
                    COURSE, "KDT-2026-001",
                    a.title(), a.instructor(), "assignment",
                    startAt.format(DOT_DATE), startAt.format(TIME) + "부터",
                    endAt.format(DOT_DATE), endAt.format(TIME) + "까지",
                    a.submitted(), SAMPLE_STUDENTS.length - a.submitted(), a.status(),
                    gradingUrlPrefix + a.id() + "/grading"));
        }
        return rows;
    }

    /* ===================== 강사 채점 화면 ===================== */

    /**
     * 예시 채점 화면의 수강생 명단. 이름과 식별자만 쓴다 — 화면이 "이름 (식별자)" 로 보여주고,
     * 생년월일 같은 개인정보는 채점 화면에 실릴 이유가 없다 ({@code GradingStudentRow} 주석 참고).
     */
    private static final String[][] SAMPLE_STUDENTS = {
            {"강민준", "trainee01"}, {"김서윤", "trainee02"}, {"박지호", "trainee03"},
            {"이하은", "trainee04"}, {"정우진", "trainee05"}, {"최유나", "trainee06"},
            {"한도윤", "trainee07"}, {"오세아", "trainee08"}, {"윤재원", "trainee09"},
            {"임채린", "trainee10"},
    };

    /** 채점 완료된 학생에게 붙는 점수. 명단 순서대로 쓴다. */
    private static final int[] SAMPLE_SCORES = {92, 88, 76, 95, 81, 90, 68, 84, 79, 87};

    private static final long SAMPLE_USER_ID_BASE = 900_601L;
    private static final long SAMPLE_SUBMISSION_ID_BASE = 900_501L;

    /**
     * 예시 과제 배정 1건.
     *
     * <p>목록 화면의 제출/미제출 수와 채점 화면의 학생 행이 <b>같은 정의에서 나온다</b>.
     * 두 군데에 숫자를 따로 적으면 목록은 "18명 제출" 인데 채점 화면에는 8행만 있는 식으로
     * 어긋나고, 화면정의서에 그대로 남는다.</p>
     *
     * @param startOffsetDays 오늘 기준 며칠 (음수 = 과거)
     * @param submitted       명단 앞에서부터 몇 명이 제출했는지
     * @param graded          제출자 중 앞에서부터 몇 명이 채점 완료인지
     */
    private record SampleAssignment(
            long id, String title, String instructor, String description,
            int startOffsetDays, int endOffsetDays,
            int submitted, int graded, String status) {
    }

    private static final List<SampleAssignment> SAMPLE_ASSIGNMENTS = List.of(
            new SampleAssignment(900_401L, "REST API 게시판 구현", "김도현",
                    "Spring Boot 로 게시글 CRUD REST API 를 구현하고, 소스 압축본과 구현 설명을 함께 제출하세요.",
                    -9, 5, 8, 5, "pending"),
            new SampleAssignment(900_402L, "React 재사용 컴포넌트 3종 만들기", "김도현",
                    "버튼·모달·테이블 컴포넌트를 props 기반으로 재사용 가능하게 만들고 배포 링크를 제출하세요.",
                    -16, -4, 10, 10, "completed"),
            new SampleAssignment(900_403L, "데이터 전처리 리포트", "박서연",
                    "결측치 처리와 정규화 과정을 실제 데이터셋 기준으로 정리해 제출하세요.",
                    -12, -1, 9, 3, "pending"),
            new SampleAssignment(900_404L, "SQL 실행계획 분석 과제", "김도현",
                    "느린 쿼리 3건의 실행계획을 캡처하고 인덱스 개선 전후를 비교해 제출하세요.",
                    -24, -7, 10, 10, "completed"),
            new SampleAssignment(900_405L, "최종 팀 프로젝트 기획서", "박서연",
                    "팀별 최종 프로젝트의 주제·기술스택·일정을 담은 기획서를 제출하세요.",
                    3, 17, 0, 0, "waiting"));

    private static SampleAssignment sampleAssignment(Long id) {
        if (id == null) {
            return null;
        }
        return SAMPLE_ASSIGNMENTS.stream().filter(a -> a.id() == id).findFirst().orElse(null);
    }

    /** 채점 화면 상단 요약. 예시 배정이 아니거나 기능이 꺼져 있으면 {@code null}. */
    public GradingSummaryView gradingSummary(Long courseAssignmentId) {
        SampleAssignment a = enabled ? sampleAssignment(courseAssignmentId) : null;
        if (a == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        return new GradingSummaryView(
                a.id(),
                COURSE + " (" + COHORT + ")",
                a.title(),
                now.plusDays(a.startOffsetDays()).format(DATETIME)
                        + " ~ " + now.plusDays(a.endOffsetDays()).format(DATETIME),
                "수동", "60점 이상", "파일 + 텍스트",
                100, 60,
                SAMPLE_STUDENTS.length, a.submitted(), a.graded());
    }

    /**
     * 채점 화면 학생 목록. 미제출자도 행으로 나온다 (실제 화면과 같은 규칙).
     *
     * <p>제출자에게는 채점 팝업 URL 을 준다 — 팝업도 예시 상세를 렌더하므로 열어서 캡처할 수 있다.
     * 저장만 컨트롤러가 막는다.</p>
     */
    public List<GradingStudentRow> gradingStudents(Long courseAssignmentId, String gradingUrlPrefix) {
        SampleAssignment a = enabled ? sampleAssignment(courseAssignmentId) : null;
        if (a == null) {
            return List.of();
        }
        LocalDateTime deadline = LocalDateTime.now().plusDays(a.endOffsetDays());
        List<GradingStudentRow> rows = new ArrayList<>(SAMPLE_STUDENTS.length);

        for (int i = 0; i < SAMPLE_STUDENTS.length; i++) {
            boolean submitted = i < a.submitted();
            boolean graded = i < a.graded();
            boolean late = submitted && i == 2;          // 지각 제출 1건 — 감점 근거 표시용
            long submissionId = SAMPLE_SUBMISSION_ID_BASE + i;

            rows.add(new GradingStudentRow(
                    i + 1,
                    SAMPLE_USER_ID_BASE + i,
                    submitted ? submissionId : null,
                    SAMPLE_STUDENTS[i][0],
                    SAMPLE_STUDENTS[i][1],
                    submitted ? "제출" : "미제출",
                    !submitted ? "-" : graded ? (i == 0 ? "확정" : "채점완료") : "미채점",
                    graded ? String.valueOf(SAMPLE_SCORES[i]) : "-",
                    (submitted && i == 1 ? 1 : 0) + "회",
                    !submitted ? "disabled" : graded ? "edit" : "grading",
                    COURSE,
                    a.title(),
                    submitted
                            ? deadline.minusDays(1).plusHours(i).format(DATETIME) : "-",
                    late,
                    submitted
                            ? gradingUrlPrefix + a.id() + "/submissions/" + submissionId + "/grading" : "",
                    graded && i == 0 ? sampleHistory(a.instructor()) : List.of()));
        }
        return rows;
    }

    /** 성적 정정 이력 1건 — 화면의 "변경 이력" 탭이 비어 보이지 않게 한다. */
    private List<GradeHistoryRow> sampleHistory(String instructor) {
        return List.of(new GradeHistoryRow(
                LocalDateTime.now().minusDays(2).withHour(14).withMinute(30).format(DATETIME),
                instructor, "85 → 92", "통과", "채점 기준 재검토 후 문서화 항목 재평가"));
    }

    /** 채점 화면의 미제출자 목록. */
    public List<UserOption> gradingNotSubmitted(Long courseAssignmentId) {
        SampleAssignment a = enabled ? sampleAssignment(courseAssignmentId) : null;
        if (a == null) {
            return List.of();
        }
        List<UserOption> users = new ArrayList<>();
        for (int i = a.submitted(); i < SAMPLE_STUDENTS.length; i++) {
            users.add(new UserOption(
                    SAMPLE_USER_ID_BASE + i, SAMPLE_STUDENTS[i][1], SAMPLE_STUDENTS[i][0]));
        }
        return users;
    }

    /**
     * 채점 팝업(grading-modal)이 보여주는 제출물 상세.
     *
     * <p>예시 판별은 <b>{@code courseAssignmentId}</b> 로 한다 — 제출물 id 만으로는 실제 제출물과
     * 구분할 근거가 없고, 팝업 URL 에 두 값이 모두 들어 있다.</p>
     *
     * <p>첨부파일은 넣지 않는다. 다운로드 URL 을 만들어 두면 눌렀을 때 실제로 없는 파일을
     * 찾으러 가서 오류가 난다 — 답안은 텍스트와 링크로만 보여준다.</p>
     */
    public SubmissionDetailView submissionDetail(Long courseAssignmentId, Long submissionId) {
        SampleAssignment a = enabled ? sampleAssignment(courseAssignmentId) : null;
        if (a == null || submissionId == null) {
            return null;
        }
        int i = (int) (submissionId - SAMPLE_SUBMISSION_ID_BASE);
        if (i < 0 || i >= a.submitted()) {
            return null;
        }
        boolean graded = i < a.graded();
        Integer currentScore = graded ? SAMPLE_SCORES[i] : null;
        LocalDateTime deadline = LocalDateTime.now().plusDays(a.endOffsetDays());

        return new SubmissionDetailView(
                submissionId,
                a.id(),
                SAMPLE_STUDENTS[i][0],
                a.title(),
                COURSE,
                a.description(),
                List.of(
                        new SubmissionDetailView.CriteriaRow(1, "요구사항 충족", 50),
                        new SubmissionDetailView.CriteriaRow(2, "코드 품질 및 구조", 30),
                        new SubmissionDetailView.CriteriaRow(3, "문서화", 20)),
                "파일 + 텍스트",
                "요구사항에 맞춰 CRUD 를 구현했고, 예외 처리는 @RestControllerAdvice 로 한 곳에 모았습니다. "
                        + "테스트는 서비스 계층 위주로 작성했습니다.",
                "https://github.com/sample/kdt-board-api",
                List.of(),
                (i == 1 ? 2 : 1),
                i == 2,
                deadline.minusDays(1).plusHours(i).format(DATETIME),
                100, 60,
                currentScore,
                graded ? "요구사항은 충실히 구현했습니다. 예외 처리 응답 형식을 통일하면 더 좋겠습니다." : null,
                graded ? "재제출 없이 1회차에 통과." : null,
                currentScore != null);
    }

    /* ===================== 학습 콘텐츠 (목록 · 차시별 · 재생) ===================== */

    /** 예시 콘텐츠가 걸려 있는 과정 id. 목록·차시별·재생 세 화면이 같은 값을 쓴다. */
    public static final long SAMPLE_COURSE_ID = 900_720L;
    private static final long SAMPLE_CONTENT_ID_BASE = 900_701L;
    private static final long SAMPLE_SESSION_ID_BASE = 900_711L;

    /**
     * 예시 VOD 의 정지 화면(poster).
     *
     * <p>재생 가능한 영상 파일은 두지 않는다 — 없는 파일 URL 을 내려주면 플레이어가 검은 상자로
     * 뜨고 브라우저가 404 를 받으러 간다. 대신 강의 화면 이미지를 poster 로 깔아 "일시정지된
     * 강의 영상"으로 보이게 한다. 실제 콘텐츠가 등록되면 그 파일이 그대로 재생된다.</p>
     */
    public static final String SAMPLE_VIDEO_POSTER = "/static/img/sample-video-poster.svg";

    /** 예시 문서의 지면 1장. 문서 뷰어가 이미지로 렌더하므로 SVG 로 만들어 뒀다(바이너리 미포함). */
    private static final String SAMPLE_DOC_PAGE = "/static/img/sample-doc-page.svg";

    private static final String[] SAMPLE_SESSION_NAMES = {
            "1차시 · 오리엔테이션 및 개발환경 구축",
            "5차시 · Spring Boot 시작하기",
            "8차시 · Spring Data JPA",
    };
    private static final int[] SAMPLE_SESSION_SEQ = {1, 5, 8};

    /**
     * 예시 콘텐츠 1건.
     *
     * @param sessionIndex   {@link #SAMPLE_SESSION_NAMES} 의 인덱스
     * @param durationSeconds VIDEO 만 사용 (DOCUMENT 는 null)
     * @param pageCount       DOCUMENT 만 사용 (VIDEO 는 null)
     * @param progressRate    내 진도율(%) — {@link ProgressService#COMPLETION_RATE} 이상이면 "완료"
     */
    private record SampleContent(
            long id, ContentType type, String title, String description,
            int sessionIndex, Integer durationSeconds, Integer pageCount, int progressRate) {
    }

    /**
     * 완료 2건 / 학습 중 2건 / 미학습 2건 — 목록 화면에 "완료·진행중" 배지와 진도율이 모두 보이도록
     * 섞어 둔다. 재생 화면 캡처의 주인공은 62% 로 멈춰 있는 900703(이어보기 상태)이다.
     */
    private static final List<SampleContent> SAMPLE_CONTENTS = List.of(
            new SampleContent(SAMPLE_CONTENT_ID_BASE, ContentType.VIDEO,
                    "개발환경 구축 — JDK · IDE 설치와 프로젝트 생성",
                    "Temurin JDK 17 과 IntelliJ 를 설치하고 첫 Spring Boot 프로젝트를 생성합니다.",
                    0, 1_080, null, 100),
            new SampleContent(SAMPLE_CONTENT_ID_BASE + 1, ContentType.DOCUMENT,
                    "학습 안내서 — 과정 운영 규정과 평가 기준",
                    "출결 인정 기준, 과제·시험 배점, 이수 판정 기준을 정리한 안내서입니다.",
                    0, null, 12, 100),
            new SampleContent(SAMPLE_CONTENT_ID_BASE + 2, ContentType.VIDEO,
                    "Spring Boot 프로젝트 구조와 자동설정",
                    "빌드 파일과 패키지 구조를 살펴보고 자동설정(Auto-configuration) 동작을 확인합니다.",
                    1, 1_440, null, 62),
            new SampleContent(SAMPLE_CONTENT_ID_BASE + 3, ContentType.DOCUMENT,
                    "실습 자료 — REST 컨트롤러 작성하기",
                    "요청 매핑과 응답 형식을 단계별로 따라 하는 실습 자료입니다.",
                    1, null, 8, 50),
            new SampleContent(SAMPLE_CONTENT_ID_BASE + 4, ContentType.VIDEO,
                    "JPA 연관관계 매핑 (1:N · N:1)",
                    "연관관계 주인과 지연 로딩을 예제 코드로 설명합니다.",
                    2, 1_860, null, 0),
            new SampleContent(SAMPLE_CONTENT_ID_BASE + 5, ContentType.DOCUMENT,
                    "학습 자료 — 영속성 컨텍스트 정리",
                    "1차 캐시, 변경 감지, 플러시 시점을 그림과 함께 정리했습니다.",
                    2, null, 15, 0));

    private static SampleContent sampleContent(Long id) {
        if (id == null) {
            return null;
        }
        return SAMPLE_CONTENTS.stream().filter(c -> c.id() == id).findFirst().orElse(null);
    }

    /** 「학습 콘텐츠」 목록 화면 행. */
    public List<LearningContentView> learningContents() {
        return SAMPLE_CONTENTS.stream().map(SampleScreenData::learningContentView).toList();
    }

    /** 「차시별 학습」 아코디언 — 목록과 같은 정의에서 나오므로 두 화면의 숫자가 어긋나지 않는다. */
    public List<LearningSessionGroup> learningGroups() {
        List<LearningSessionGroup> groups = new ArrayList<>(SAMPLE_SESSION_NAMES.length);
        for (int s = 0; s < SAMPLE_SESSION_NAMES.length; s++) {
            final int idx = s;
            List<LearningContentView> items = SAMPLE_CONTENTS.stream()
                    .filter(c -> c.sessionIndex() == idx)
                    .map(SampleScreenData::learningContentView)
                    .toList();
            int avg = (int) Math.round(
                    items.stream().mapToInt(LearningContentView::progressRate).average().orElse(0));
            groups.add(new LearningSessionGroup(
                    SAMPLE_SESSION_ID_BASE + s, SAMPLE_SESSION_NAMES[s], SAMPLE_SESSION_SEQ[s], avg, items));
        }
        return groups;
    }

    /** 차시별 학습 화면의 과정 선택 옵션. */
    public List<CourseOption> courseOptions() {
        return List.of(new CourseOption(SAMPLE_COURSE_ID, COURSE));
    }

    /** 과정 전체 진도율 — 실제 화면과 같은 규칙(콘텐츠 진도율의 평균)으로 계산한다. */
    public int courseProgress() {
        return (int) Math.round(
                SAMPLE_CONTENTS.stream().mapToInt(SampleContent::progressRate).average().orElse(0));
    }

    /**
     * 재생 화면({@code /trainee/contents/{id}/play})이 보여줄 콘텐츠 상세.
     * 예시 id 가 아니거나 기능이 꺼져 있으면 {@code null} — 컨트롤러가 실제 조회로 넘어간다.
     */
    public ContentView contentDetail(Long id) {
        SampleContent c = enabled ? sampleContent(id) : null;
        if (c == null) {
            return null;
        }
        return new ContentView(
                c.id(), c.type(), c.type().getLabel(), c.title(), c.description(),
                SAMPLE_COURSE_ID, COURSE,
                SAMPLE_SESSION_ID_BASE + c.sessionIndex(), SAMPLE_SESSION_NAMES[c.sessionIndex()],
                c.durationSeconds(), c.pageCount(), durationLabel(c),
                c.sessionIndex() + 1, true,
                ContentStatus.ACTIVE, ContentStatus.ACTIVE.getLabel(),
                // VIDEO 는 파일을 두지 않는다(poster 로 대체). DOCUMENT 는 SVG 지면을 그대로 보여준다.
                c.type() == ContentType.DOCUMENT ? SAMPLE_DOC_PAGE : null,
                c.type() == ContentType.DOCUMENT ? "sample-doc-page.svg" : null,
                LocalDateTime.now().minusDays(20L - c.sessionIndex()));
    }

    /**
     * 예시 콘텐츠의 내 진도. 재생 화면의 초기값이자,
     * 진도 저장 API 가 예시 id 로 호출됐을 때 DB 대신 돌려줄 응답이다.
     */
    public ProgressResponse contentProgress(Long id) {
        SampleContent c = sampleContent(id);
        if (c == null) {
            return new ProgressResponse(id, 0, false, null, null);
        }
        return new ProgressResponse(id, c.progressRate(), isCompleted(c),
                lastPositionSeconds(c), maxPageReached(c));
    }

    private static LearningContentView learningContentView(SampleContent c) {
        return new LearningContentView(
                c.id(), c.type(), c.type().getLabel(), c.title(),
                SAMPLE_COURSE_ID, COURSE,
                SAMPLE_SESSION_ID_BASE + c.sessionIndex(), SAMPLE_SESSION_NAMES[c.sessionIndex()],
                SAMPLE_SESSION_SEQ[c.sessionIndex()],
                c.durationSeconds(), c.durationSeconds() != null ? c.durationSeconds() / 60 : 0,
                c.progressRate(), isCompleted(c),
                lastPositionSeconds(c), maxPageReached(c),
                c.type() == ContentType.DOCUMENT ? SAMPLE_DOC_PAGE : null);
    }

    private static boolean isCompleted(SampleContent c) {
        return c.progressRate() >= ProgressService.COMPLETION_RATE;
    }

    /** 이어보기 지점 — 진도율에서 역산한다(실제 데이터의 관계와 같게 유지). */
    private static Integer lastPositionSeconds(SampleContent c) {
        if (c.type() != ContentType.VIDEO || c.durationSeconds() == null) {
            return null;
        }
        return c.durationSeconds() * c.progressRate() / 100;
    }

    private static Integer maxPageReached(SampleContent c) {
        if (c.type() != ContentType.DOCUMENT || c.pageCount() == null) {
            return null;
        }
        return c.pageCount() * c.progressRate() / 100;
    }

    /** {@code ContentView.durationLabel} 과 같은 표기 — 재생 화면 헤더의 "동영상 / 24분 0초". */
    private static String durationLabel(SampleContent c) {
        if (c.type() == ContentType.VIDEO && c.durationSeconds() != null) {
            int m = c.durationSeconds() / 60;
            int s = c.durationSeconds() % 60;
            return m > 0 ? m + "분 " + s + "초" : s + "초";
        }
        if (c.type() == ContentType.DOCUMENT && c.pageCount() != null) {
            return c.pageCount() + "페이지";
        }
        return "-";
    }
}
