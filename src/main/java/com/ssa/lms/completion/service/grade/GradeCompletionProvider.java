package com.ssa.lms.completion.service.grade;

/**
 * 이수 판정의 <b>성적 요건</b> 조회 seam.
 *
 * <p>성적(Grade)은 B 도메인({@code com.ssa.lms.grading}) 소유이며, B가 제공할
 * {@code GradeQueryService#hasAllRequiredGradesConfirmed(userId, courseId)} 를 이수 로직이 소비해야 한다.
 * 그러나 B의 grading 패키지는 아직 없고 이 트랙에서 만들면 안 되므로(PARALLEL-P2.md),
 * completion 트랙 안에 이 포트 인터페이스를 두고 fallback 으로 단독 부팅한다.</p>
 *
 * <p>TODO(통합): B의 {@code grading} 패키지가 들어오면, 이 인터페이스를 구현하는 어댑터 빈
 * (예: {@code GradeQueryServiceAdapter implements GradeCompletionProvider})을 추가해
 * {@code GradeQueryService#hasAllRequiredGradesConfirmed} 로 위임하면 {@link GradeCompletionConfig}
 * 의 fallback 이 자동으로 물러난다({@code @ConditionalOnMissingBean}). A는 Grade 엔티티/리포지토리를
 * 직접 참조하지 않는다.</p>
 */
public interface GradeCompletionProvider {

    /** 해당 과정에서 사용자가 성적(평가) 이수 요건을 충족했는지. */
    boolean gradesConfirmed(Long userId, Long courseId);

    /** 해당 과정의 확정된 평가 평균 점수. 확정 성적이 없으면 null. */
    Double averageConfirmedScore(Long userId, Long courseId);
}
