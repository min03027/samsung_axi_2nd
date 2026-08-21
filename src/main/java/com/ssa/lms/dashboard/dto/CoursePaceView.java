package com.ssa.lms.dashboard.dto;

/**
 * 우리 반에서 내 학습 위치 — 훈련생 대시보드의 "달리기 트랙"에 쓴다.
 *
 * <p><b>다른 훈련생의 이름은 담지 않는다.</b> 순위 비교 화면에 남의 이름이 뜨면
 * 개인정보이고, 훈련생 사이에 불필요한 비교를 만든다. 내 위치와 앞·뒤 주자의
 * <b>진도율만</b> 익명으로 보여준다.</p>
 *
 * @param courseName  과정명
 * @param myRank      내 등수 (1부터)
 * @param total       비교 대상 인원 (승인·수료 상태 수강생)
 * @param myProgress  내 진도율(%)
 * @param aheadProgress 바로 앞 주자의 진도율(%). 내가 1등이면 null
 * @param behindProgress 바로 뒤 주자의 진도율(%). 내가 꼴찌면 null
 * @param topProgress 1등 진도율(%) — 트랙에서 선두 위치를 잡는 데 쓴다
 * @param classAverage 반 평균 진도율(%) — 내 위치를 판단할 기준선
 * @param othersProgress 나를 뺀 나머지 주자들의 진도율(%). <b>이름 없이 값만</b>
 * @param runners 등수순 전체 주자(나 포함). 세로 트랙에 위에서부터 늘어놓는다
 */
public record CoursePaceView(
        String courseName,
        int myRank,
        int total,
        int myProgress,
        Integer aheadProgress,
        Integer behindProgress,
        int topProgress,
        int classAverage,
        java.util.List<Integer> othersProgress,
        java.util.List<Runner> runners
) {

    /**
     * 트랙에 세울 주자 한 명.
     *
     * <p><b>이름은 담지 않는다.</b> 내가 아닌 사람은 등수와 진도만 보인다 —
     * 순위 화면에 남의 이름이 뜨면 개인정보이고 훈련생 사이에 불필요한 비교를 만든다.</p>
     */
    public record Runner(int rank, int progress, boolean me) {
    }

    /** 앞사람과의 격차(%p). 1등이면 null. */
    public Integer gapToAhead() {
        return aheadProgress == null ? null : aheadProgress - myProgress;
    }

    /** 순위보다 현재 페이스를 안심시키는 홈 화면용 문장. */
    public String reassuranceMessage() {
        int averageGap = myProgress - classAverage;
        if (averageGap >= 0) {
            return "반 평균보다 " + averageGap + "% 앞서 있어요. 지금 속도면 충분해요.";
        }
        return "반 평균까지 " + Math.abs(averageGap)
                + "% 남았어요. 오늘 한 걸음씩 이어가면 충분해요.";
    }
}
