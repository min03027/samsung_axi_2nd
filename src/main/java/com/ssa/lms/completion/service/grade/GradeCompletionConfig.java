package com.ssa.lms.completion.service.grade;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link GradeCompletionProvider} 기본 fallback — B의 성적 도메인 어댑터가 없을 때만 등록된다.
 *
 * <p>성적 도메인 미통합 상태에서는 성적 요건을 "미확인"으로 본다(false 반환).
 * 이수 기준에서 {@code requireGradePass=false}(기본값)이면 이 값은 사용되지 않으므로
 * 진도+출결만으로 정상 판정된다. {@code requireGradePass=true} 로 켜면 성적 통합 전까지
 * 안전하게 미이수/판정대기로 남는다(잘못된 이수 확정 방지).</p>
 */
@Configuration
public class GradeCompletionConfig {

    @Bean
    @ConditionalOnMissingBean(GradeCompletionProvider.class)
    public GradeCompletionProvider fallbackGradeCompletionProvider() {
        return new GradeCompletionProvider() {
            @Override
            public boolean gradesConfirmed(Long userId, Long courseId) {
                return false;
            }

            @Override
            public Double averageConfirmedScore(Long userId, Long courseId) {
                return null;
            }
        };
    }
}
