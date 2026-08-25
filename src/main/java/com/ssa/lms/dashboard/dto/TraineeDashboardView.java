package com.ssa.lms.dashboard.dto;

import java.util.List;

/**
 * 훈련생 안심 홈의 서버 렌더링 모델. 화면 수치와 상태 문구는 이 모델의 실제 조회 결과만 사용한다.
 *
 * <p><b>본인 데이터만</b> 담긴다. 과정·과제·시험·설문·성적·알림 전부 loginUser.id 로 조회한 것이고,
 * 공지는 {@code NoticeVisibilityService.traineeCourseIds} 로 "전체 공지 + 본인 수강 과정 공지"만 남긴다.</p>
 *
 * @param notices 정부 제출 문서(양식3) "훈련생 유의사항 등재(정보제공)" 요건 — 초기화면에서
 *                실제 공지 텍스트를 보여줘야 한다. 기존 화면은 하드코딩 이미지 5장이었다.
 */
public record TraineeDashboardView(
        String userName,
        String today,
        Assurance assurance,
        List<Stat> stats,
        List<Todo> todos,
        List<ScheduleItem> todaySchedule,
        List<CourseCard> courses,
        CoachSummary coach,
        List<NoticeItem> notices,
        String noticeMoreHref
) {

    /**
     * 홈 최상단의 "안심 상태". 숫자를 화면에서 다시 해석하지 않고 서버가 같은 기준으로 문장을 만든다.
     * attendanceRate/recommendedProgress 가 null 이면 아직 판정할 실제 기록이 없다는 뜻이다.
     */
    public record Assurance(
            String tone,
            String headline,
            String detail,
            String courseName,
            int progressRate,
            Integer recommendedProgress,
            Integer attendanceRate,
            int todayActionCount,
            int totalTodoCount,
            int remainingAssignments,
            int remainingExams,
            String primaryActionLabel,
            String primaryActionHref,
            String progressMessage,
            String attendanceMessage
    ) {
    }

    /** hero 영역 요약 타일. k=라벨 / v=값 / s=보조설명. */
    public record Stat(String k, String v, String s) {
    }

    /**
     * 오늘 할 일 한 건.
     *
     * @param type TASK / EXAM / SURVEY — 화면 JS 가 배지 색과 버튼 문구를 이 값으로 고른다
     * @param dday 남은 일수. null 이면 D-day 배지를 그리지 않는다
     */
    public record Todo(String id, String type, String title, String meta,
                       String due, Integer dday, String href) {
    }

    /**
     * 오늘 타임라인 한 건. 수업 시작 시각은 현재 Session 스키마에 없으므로 임의 시각을 만들지 않는다.
     * timeLabel 이 "시간 미정"이면 날짜만 등록된 실제 차시다.
     */
    public record ScheduleItem(String timeLabel, String type, String typeLabel,
                               String title, String meta, String href, boolean current) {
    }

    public record CourseCard(String id, String name, String cohort, String startAt, String endAt,
                             String progressRate, String attendanceRate, String dday,
                             String continueHref, String noticeHref) {
    }

    /** 실제 1:1 튜터링 대화에서 가져온 최근 강사 답변 요약. */
    public record CoachSummary(boolean hasFeedback, String message, String coachName,
                               String context, String time, long unreadCount, String href) {
    }

    /**
     * 초기화면 공지 미리보기 한 건.
     *
     * @param scope "전체" 또는 과정명. 본인이 볼 수 있는 범위만 내려온다
     */
    public record NoticeItem(String id, String title, String category, String date,
                             boolean pinned, String scope, String href) {
    }
}
