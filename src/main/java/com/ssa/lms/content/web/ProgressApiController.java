package com.ssa.lms.content.web;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.content.service.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 진도 추적 REST API — 훈련생 시청 화면의 JS 가 재생 위치/도달 페이지를 주기적으로 서버에 저장한다
 * (기존 프론트의 클라이언트 진도 로직을 서버 영속화로 대체).
 *
 * <p>경로 {@code /trainee/**} 는 SecurityConfig 의 catch-all(TRAINEE,ADMIN)로 커버된다.
 * POST 는 CSRF 토큰이 필요하므로 시청 템플릿이 메타 태그로 토큰을 내려주고 JS 가 헤더로 전송한다.</p>
 */
@RestController
@RequestMapping("/trainee/contents")
@RequiredArgsConstructor
public class ProgressApiController {

    private final ProgressService progressService;

    /** 현재 진도 조회 (이어보기 초기값). */
    @GetMapping("/{contentId}/progress")
    public ProgressResponse getProgress(@PathVariable Long contentId,
                                        @AuthenticationPrincipal LoginUser user) {
        return progressService.getProgress(user.getId(), contentId);
    }

    /**
     * 진도 갱신 (재생 위치/도달 페이지 → 진도율·완료 판정).
     *
     * <p>실제 등록 콘텐츠의 진도만 저장한다.</p>
     */
    @PostMapping("/{contentId}/progress")
    public ProgressResponse updateProgress(@PathVariable Long contentId,
                                           @RequestBody ProgressRequest request,
                                           @AuthenticationPrincipal LoginUser user) {
        return progressService.record(user.getId(), contentId, request);
    }

    /** 수동 완료 처리. */
    @PostMapping("/{contentId}/complete")
    public ProgressResponse complete(@PathVariable Long contentId,
                                     @AuthenticationPrincipal LoginUser user) {
        return progressService.complete(user.getId(), contentId);
    }
}
