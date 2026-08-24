package com.ssa.lms.content.web;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.content.entity.ContentType;
import com.ssa.lms.content.service.ContentService;
import com.ssa.lms.content.service.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 훈련생 콘텐츠 학습 화면 — 학습 콘텐츠 목록/차시별 학습/시청(VOD·문서) 페이지.
 * 진도 저장은 {@link ProgressApiController}(REST) 가 담당한다.
 *
 * <p>경로 {@code /trainee/**} 는 SecurityConfig catch-all(TRAINEE,ADMIN)로 커버된다.</p>
 */
@Controller
@RequestMapping("/trainee")
@RequiredArgsConstructor
public class TraineeLearningController {

    private static final String VIEW_DIR = "trainee/";

    private final ProgressService progressService;
    private final ContentService contentService;

    /** 내 학습 콘텐츠 전체 목록(승인/수료 과정). */
    @GetMapping("/contents")
    public String contents(@AuthenticationPrincipal LoginUser user, Model model) {
        model.addAttribute("contents", progressService.myLearningContents(user.getId()));
        return VIEW_DIR + "contents";
    }

    /** 차시별 학습(아코디언) — 과정 선택. courseId 미지정 시 학습 중인 첫 과정. */
    @GetMapping("/learning")
    public String learning(@RequestParam(required = false) Long courseId,
                           @AuthenticationPrincipal LoginUser user, Model model) {
        var courses = progressService.enrolledCourseOptions(user.getId());
        Long targetCourseId = courseId != null ? courseId
                : (courses.isEmpty() ? null : courses.get(0).id());
        var groups = targetCourseId != null
                ? progressService.learningGroups(user.getId(), targetCourseId)
                : java.util.List.<LearningSessionGroup>of();
        int progress = targetCourseId != null
                ? progressService.courseProgressRatio(user.getId(), targetCourseId) : 0;

        model.addAttribute("courses", courses);
        model.addAttribute("selectedCourseId", targetCourseId);
        model.addAttribute("groups", groups);
        model.addAttribute("courseProgress", progress);
        model.addAttribute("nextLearning", targetCourseId == null
                ? null
                : progressService.nextLearningContent(user.getId(), targetCourseId).orElse(null));
        return VIEW_DIR + "learning";
    }

    /**
     * 콘텐츠 시청(재생/열람) — 유형별 페이지로 분기, 이어보기용 진도 동봉.
     *
     * <p>등록된 실제 콘텐츠만 렌더한다. 존재하지 않는 id 는 기존 not-found 처리로 넘긴다.</p>
     */
    @GetMapping("/contents/{id}/play")
    public String play(@PathVariable Long id, @AuthenticationPrincipal LoginUser user, Model model) {
        ContentView content = contentService.view(id);
        model.addAttribute("content", content);
        model.addAttribute("progress", progressService.getProgress(user.getId(), id));
        return VIEW_DIR + (content.type() == ContentType.DOCUMENT ? "play-document" : "play-video");
    }
}
