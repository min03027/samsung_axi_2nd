package com.ssa.lms.exam.dto;

/**
 * 훈련생 응시 목록 한 행 (templates/trainee/online-test.html).
 *
 * 필드 이름은 기존 더미 배열 static/js/trainee/online-test.js 의 {@code mockExams} 와 1:1 로 맞췄다.
 * 그래야 화면 JS 의 렌더/필터/정렬 로직을 그대로 두고 데이터만 서버 값으로 바꿔 끼울 수 있다.
 *
 * 날짜는 문자열로 내린다 (인라인 JS 직렬화 이슈 — QuestionListRow 와 같은 방침).
 * 정답·해설처럼 응시 전에 노출되면 안 되는 값은 여기에 절대 담지 않는다.
 */
public record ExamTakeRow(
        /** 화면 JS 가 data-exam-id 문자열과 === 비교하므로 문자열. */
        String id,
        String name,
        String courseId,
        String courseName,
        String sessionName,
        String windowStart,
        String windowEnd,
        int durationMin,
        int questionCount,
        String typeText,
        String retakePolicy,
        int attemptsUsed,
        int attemptsTotal,
        /** scheduled | available | in_progress | completed | ended (화면 JS 의 상태 코드) */
        String status,
        String note,
        /** 내역서 요건 — true 면 응시 시작 전 본인인증 모달을 띄운다. */
        boolean requireIdentityVerification,
        /**
         * QR 신분확인 + 웹캠 사전점검 대상인가 (LXP-015).
         * true 면 화면은 POST start 나 비밀번호 모달 대신 사전점검 화면으로 보낸다.
         * 판정은 {@code PrecheckPolicy.requiresPrecheck(exam)} 하나만 쓴다.
         */
        boolean precheckRequired,
        /** 이어하기 대상 응시 회차 id. 없으면 null. */
        String inProgressAttemptId,
        /** 결과 보기 대상(최근 제출 회차) id. 없으면 null. */
        String lastAttemptId,
        /** ExamQuestion 이 0건이면 false — 규칙만 있고 문항이 확정되지 않은 시험. */
        boolean ready,
        /** 시작 버튼을 눌러도 되는지. 화면이 회색 처리한다. */
        boolean startable,
        /** 시작할 수 없는 이유 (툴팁/모달 안내용). 시작 가능하면 null. */
        String blockReason,
        /** 연습(사전 모의) 시험이면 true — 목록에서 정식 시험과 구분 표시한다. */
        boolean practiceMode,
        /** 이 시험이 가진 문제 세트 수. 1이면 세트 기능을 쓰지 않는다. */
        int setCount,
        /** 성적 공개 정책 안내 문구 (예: "즉시 공개" / "채점 완료 후 공개" / "비공개"). */
        String resultReleaseText
) {

    /**
     * 기존 호출부 호환 생성자 (precheckRequired 없이 만들던 자리).
     *
     * <p>{@code precheckRequired} 를 추가하면서 정적 미리보기 데이터(SampleScreenData)까지
     * 고쳐야 했는데, 그 파일은 이번 작업의 변경 허용 목록 밖이다. 필드를 하나 더 받는 대신
     * <b>기존 시그니처를 그대로 두는 생성자</b>를 하나 둬서 그 파일을 건드리지 않는다.
     * 정적 미리보기는 사전점검 대상이 아니므로 false 가 맞다.</p>
     */
    public ExamTakeRow(String id, String name, String courseId, String courseName, String sessionName,
                       String windowStart, String windowEnd, int durationMin, int questionCount,
                       String typeText, String retakePolicy, int attemptsUsed, int attemptsTotal,
                       String status, String note, boolean requireIdentityVerification,
                       String inProgressAttemptId, String lastAttemptId, boolean ready,
                       boolean startable, String blockReason, boolean practiceMode,
                       int setCount, String resultReleaseText) {
        this(id, name, courseId, courseName, sessionName, windowStart, windowEnd, durationMin,
                questionCount, typeText, retakePolicy, attemptsUsed, attemptsTotal, status, note,
                requireIdentityVerification, false, inProgressAttemptId, lastAttemptId, ready,
                startable, blockReason, practiceMode, setCount, resultReleaseText);
    }
}
