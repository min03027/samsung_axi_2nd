package com.ssa.lms.completion.service.grade;

import com.ssa.lms.grading.service.GradeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link GradeCompletionProvider} 의 실제 구현 — B의 {@link GradeQueryService} 로 위임한다.
 *
 * <p>grading 패키지 통합(feat/b-grading-contract 머지)으로 계약이 들어와, 이 어댑터가 등록되면서
 * {@link GradeCompletionConfig} 의 fallback(false)은 {@code @ConditionalOnMissingBean} 으로 물러난다.
 * A는 여전히 Grade 엔티티/리포지토리를 직접 참조하지 않고 이 서비스만 호출한다.</p>
 */
@Component
@RequiredArgsConstructor
public class GradeQueryServiceAdapter implements GradeCompletionProvider {

    private final GradeQueryService gradeQueryService;

    @Override
    public boolean gradesConfirmed(Long userId, Long courseId) {
        return gradeQueryService.hasAllRequiredGradesConfirmed(userId, courseId);
    }

    @Override
    public Double averageConfirmedScore(Long userId, Long courseId) {
        return gradeQueryService.averageConfirmedScore(userId, courseId);
    }
}
