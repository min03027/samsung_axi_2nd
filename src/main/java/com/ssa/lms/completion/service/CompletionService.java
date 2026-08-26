package com.ssa.lms.completion.service;

import com.ssa.lms.attendance.service.AttendanceService;
import com.ssa.lms.completion.entity.*;
import com.ssa.lms.completion.repository.CompletionCriteriaRepository;
import com.ssa.lms.completion.repository.CompletionRepository;
import com.ssa.lms.completion.service.grade.GradeCompletionProvider;
import com.ssa.lms.completion.web.CompletionView;
import com.ssa.lms.content.service.ProgressQueryService;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.service.CourseQueryService;
import com.ssa.lms.course.service.EnrollmentService;
import com.ssa.lms.export.ExcelWriter;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.UserStatus;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 이수 기준 설정 + 자동 판정.
 *
 * <p>판정 근거는 <b>진도</b>({@link ProgressQueryService}, P2-A 제공/로컬은 fallback 0),
 * <b>출결</b>({@link AttendanceService}, 이 트랙), <b>성적</b>({@link GradeCompletionProvider} seam, B 통합 전 fallback)
 * 이다. 기준({@link CompletionCriteria})을 충족하면 이수(PASS), 아니면 미이수(FAIL) 로 자동 판정하고,
 * 관리자가 확정(CONFIRMED)하면 이수증 발급 대상이 된다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompletionService {

    private final CompletionRepository completionRepository;
    private final CompletionCriteriaRepository criteriaRepository;
    private final CourseRepository courseRepository;
    private final CourseQueryService courseQueryService;
    private final EnrollmentService enrollmentService;
    private final UserRepository userRepository;
    private final ProgressQueryService progressQueryService;
    private final AttendanceService attendanceService;
    private final GradeCompletionProvider gradeCompletionProvider;

    /* ===== 이수 기준 ===== */

    /**
     * 과정 이수 기준 — 저장된 값이 없으면 과정의 진도 기준({@code completionProgressRate})을 초기값으로 한
     * (미저장) 기본 기준을 돌려준다.
     */
    public CompletionCriteria criteriaOf(Long courseId) {
        return criteriaRepository.findByCourseId(courseId)
                .orElseGet(() -> {
                    Course course = getCourse(courseId);
                    return CompletionCriteria.builder()
                            .course(course)
                            .minProgressRate(course.getCompletionProgressRate())
                            .minAttendanceRate(80)
                            .minAverageScore(null)
                            .requireGradePass(false)
                            .build();
                });
    }

    @Transactional
    public void saveCriteria(Long courseId, int minProgressRate, int minAttendanceRate,
                             Integer minAverageScore, boolean requireGradePass, String note) {
        validateRate("최소 진도율", minProgressRate);
        validateRate("최소 출석률", minAttendanceRate);
        if (minAverageScore != null) {
            validateRate("최소 테스트 평균", minAverageScore);
        }
        criteriaRepository.findByCourseId(courseId).ifPresentOrElse(
                c -> c.update(minProgressRate, minAttendanceRate, minAverageScore, requireGradePass, note),
                () -> criteriaRepository.save(CompletionCriteria.builder()
                        .course(getCourse(courseId))
                        .minProgressRate(minProgressRate).minAttendanceRate(minAttendanceRate)
                        .minAverageScore(minAverageScore)
                        .requireGradePass(requireGradePass).note(note).build()));
    }

    /** 기존 호출부 호환용 — 테스트 평균 기준을 사용하지 않는 과정. */
    @Transactional
    public void saveCriteria(Long courseId, int minProgressRate, int minAttendanceRate,
                             boolean requireGradePass, String note) {
        saveCriteria(courseId, minProgressRate, minAttendanceRate, null, requireGradePass, note);
    }

    /* ===== 자동 판정 ===== */

    /**
     * 과정 수강생 전원을 대상으로 이수 자동 판정(재)실행. 이미 확정(CONFIRMED)된 건은 확정 상태를 유지하고
     * 판정 근거 스냅샷만 갱신한다.
     *
     * @return 판정된 수강생 수
     */
    @Transactional
    public int evaluate(Long courseId) {
        Course course = getCourse(courseId);
        CompletionCriteria criteria = criteriaOf(courseId);
        List<User> trainees = userRepository.findAllById(courseQueryService.findUserIdsByCourseId(courseId));
        LocalDateTime now = LocalDateTime.now();

        for (User trainee : trainees) {
            int progressRate = progressQueryService.completedRatio(trainee.getId(), courseId);
            int attendanceRate = attendanceService.attendanceRate(trainee.getId(), courseId);

            Double averageScore = null;
            Boolean gradesConfirmed = null;
            boolean gradesOk = true;
            boolean usesGrades = criteria.isRequireGradePass() || criteria.getMinAverageScore() != null;
            if (usesGrades) {
                gradesConfirmed = gradeCompletionProvider.gradesConfirmed(trainee.getId(), courseId);
                averageScore = gradeCompletionProvider.averageConfirmedScore(trainee.getId(), courseId);
                gradesOk = gradesConfirmed;
            }

            boolean scoreOk = criteria.getMinAverageScore() == null
                    || (averageScore != null && averageScore >= criteria.getMinAverageScore());

            boolean pass = progressRate >= criteria.getMinProgressRate()
                    && attendanceRate >= criteria.getMinAttendanceRate()
                    && gradesOk
                    && scoreOk;
            CompletionResult result = pass ? CompletionResult.PASS : CompletionResult.FAIL;

            Completion completion = completionRepository.findByCourseIdAndTraineeId(courseId, trainee.getId())
                    .orElse(null);
            if (completion != null && completion.getConfirmStatus() == ConfirmStatus.CONFIRMED) {
                continue; // 이미 발급된 공식 이수 기록은 재판정으로 소급 변경하지 않는다.
            }
            if (completion == null) {
                completion = completionRepository.save(Completion.builder()
                        .course(course).trainee(trainee)
                        .progressRate(progressRate).attendanceRate(attendanceRate)
                        .averageScore(averageScore)
                        .gradesConfirmed(gradesConfirmed)
                        .result(result).confirmStatus(ConfirmStatus.EXPECTED).evaluatedAt(now).build());
            } else {
                completion.applyEvaluation(progressRate, attendanceRate, averageScore, gradesConfirmed, result, now);
            }
            if (pass) {
                completion.confirm(now);
                enrollmentService.completeByCriteria(trainee.getId(), courseId, now);
            }
        }
        return trainees.size();
    }

    /* ===== 확정 ===== */

    @Transactional
    public void confirm(Long completionId) {
        get(completionId).confirm(LocalDateTime.now());
    }

    @Transactional
    public void changeConfirmStatus(Long completionId, ConfirmStatus status) {
        get(completionId).changeConfirmStatus(status, LocalDateTime.now());
    }

    /**
     * 관리자가 과정 이수를 직접 부여한다.
     *
     * <p>수강 등록 여부와 무관하게 공식 이수 기록을 만들 수 있는 운영 기능이다. 같은 과정·훈련생의
     * 기존 판정이 있으면 중복 행을 만들지 않고 입력한 근거로 갱신한 뒤 이수 확정한다.</p>
     *
     * @return true 면 신규 생성, false 면 기존 이수 기록 갱신
     */
    @Transactional
    public boolean grantManual(Long courseId, Long traineeId, int progressRate,
                               int attendanceRate, int averageScore, boolean gradesConfirmed) {
        validateRate("진도율", progressRate);
        validateRate("출석률", attendanceRate);
        validateRate("테스트 평균", averageScore);

        Course course = getCourse(courseId);
        User trainee = userRepository.findById(traineeId)
                .filter(user -> user.getRole() == Role.TRAINEE && user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("활성 상태의 훈련생을 선택해주세요."));
        LocalDateTime now = LocalDateTime.now();

        Completion completion = completionRepository.findByCourseIdAndTraineeId(courseId, traineeId)
                .orElse(null);
        boolean created = completion == null;
        if (created) {
            completion = completionRepository.save(Completion.builder()
                    .course(course)
                    .trainee(trainee)
                    .progressRate(progressRate)
                    .attendanceRate(attendanceRate)
                    .averageScore((double) averageScore)
                    .gradesConfirmed(gradesConfirmed ? Boolean.TRUE : null)
                    .result(CompletionResult.PASS)
                    .confirmStatus(ConfirmStatus.EXPECTED)
                    .evaluatedAt(now)
                    .build());
        } else {
            completion.applyEvaluation(progressRate, attendanceRate, (double) averageScore,
                    gradesConfirmed ? Boolean.TRUE : null, CompletionResult.PASS, now);
        }
        completion.confirm(now);
        return created;
    }

    /* ===== 조회 ===== */

    public List<Completion> findByCourse(Long courseId) {
        return completionRepository.findByCourseId(courseId);
    }

    /** 이수 관리 화면용 행 뷰(트랜잭션 안에서 지연 로딩 필드 materialize — open-in-view=false). */
    public List<CompletionView> viewsByCourse(Long courseId) {
        return completionRepository.findByCourseId(courseId).stream()
                .map(CompletionView::of).toList();
    }

    /** 수강생 본인 이수 현황 행 뷰(훈련생 이수관리 화면). */
    public List<CompletionView> viewsByTrainee(Long traineeId) {
        return completionRepository.findByTraineeId(traineeId).stream()
                .map(CompletionView::of).toList();
    }

    /** 관리자 직접 이수 부여용 활성 훈련생 목록. */
    public List<User> activeTrainees() {
        return userRepository.findByRoleOrderByNameAsc(Role.TRAINEE).stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .toList();
    }

    /* ===== 엑셀 다운로드 ===== */

    /** 이수 현황 xlsx 의 헤더 — 이수 관리 화면 표 컬럼과 같은 순서. */
    private static final String[] COMPLETION_HEADERS = {
            "번호", "이름", "생년월일", "과정", "진도율(%)", "출석률(%)",
            "테스트 평균", "성적확정", "이수결과", "이수확정상태", "확정일시"
    };

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 과정별 이수 현황을 xlsx 한 벌로 만든다 — 관리자 이수 관리 화면의 표를 그대로 내려받는 용도.
     *
     * <p>화면 표({@link #viewsByCourse})와 같은 데이터/순서를 쓰므로 화면과 다운로드가 어긋나지 않는다.
     * 판정된 이수가 0건이면 빈 시트 대신 안내 행 하나를 남긴다({@link ExcelWriter#emptyNote}).</p>
     */
    public byte[] completionExcel(Long courseId) {
        Course course = getCourse(courseId);
        List<CompletionView> rows = viewsByCourse(courseId);

        try (ExcelWriter writer = ExcelWriter.create()) {
            writer.sheet(sheetName(course), COMPLETION_HEADERS);
            if (rows.isEmpty()) {
                writer.emptyNote("판정된 이수 정보가 없습니다.");
            } else {
                int no = 1;
                for (CompletionView r : rows) {
                    writer.row(
                            no++,
                            r.traineeName(),
                            r.birthDate(),
                            r.courseLabel(),
                            r.progressRate(),
                            r.attendanceRate(),
                            r.averageScore() == null ? "미적용" : Math.round(r.averageScore() * 10.0) / 10.0,
                            gradesText(r.gradesConfirmed()),
                            r.resultLabel(),
                            r.confirmStatusLabel(),
                            r.confirmedAt() == null ? null : r.confirmedAt().format(TS));
                }
            }
            return writer.toBytes();
        }
    }

    /** 다운로드 파일명/시트명에 쓸 과정 라벨 — 확정 이수 여부와 무관하게 항상 만든다. */
    public String courseLabel(Long courseId) {
        return sheetName(getCourse(courseId));
    }

    private static String sheetName(Course course) {
        String cohort = course.getCohort();
        return course.getCourseName() + (cohort != null ? "_" + cohort : "");
    }

    /** 성적 요건을 끄고 판정하면 {@code gradesConfirmed} 가 null 이다 — "미적용"으로 구분해 표시. */
    private static String gradesText(Boolean confirmed) {
        if (confirmed == null) {
            return "미적용";
        }
        return confirmed ? "확정" : "미확정";
    }

    /**
     * 이수 정보가 해당 수강생 본인의 것인지 — 훈련생 이수증 다운로드 권한 경계.
     * 존재하지 않거나 남의 것이면 false(호출부에서 404 처리해 존재 여부 노출을 막는다).
     */
    public boolean isOwnedByTrainee(Long completionId, Long traineeId) {
        return completionRepository.findById(completionId)
                .map(c -> c.getTrainee().getId().equals(traineeId))
                .orElse(false);
    }

    public Completion get(Long completionId) {
        return completionRepository.findById(completionId)
                .orElseThrow(() -> new IllegalArgumentException("이수 정보를 찾을 수 없습니다: " + completionId));
    }

    private Course getCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("과정을 찾을 수 없습니다: " + courseId));
    }

    private static void validateRate(String label, int rate) {
        if (rate < 0 || rate > 100) {
            throw new IllegalArgumentException(label + "은 0~100 사이로 입력해주세요.");
        }
    }
}
