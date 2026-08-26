package com.ssa.lms.web.admin.exam;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.exam.dto.ExamForm;
import com.ssa.lms.exam.dto.ExamListRow;
import com.ssa.lms.exam.dto.ExamSearchCond;
import com.ssa.lms.exam.service.ExamService;
import com.ssa.lms.web.PageView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 시험 생성/설정 화면.
 *
 * 패키지 위치는 CLAUDE.md 의 컨트롤러 규칙(com.ssa.lms.web.{admin|instructor|trainee}.{도메인})을 따른다.
 * 문제은행(AdminQuestionController) 과 같은 패키지에 나란히 둔다.
 *
 * 접근 권한: SecurityConfig 의 /admin/evaluation/** → ADMIN, INSTRUCTOR
 * 응시/제출(ExamAttempt, Answer)은 이 컨트롤러의 범위가 아니다.
 */
@Controller
@RequestMapping("/admin/evaluation/exams")
@RequiredArgsConstructor
public class AdminExamController {

    private static final String LIST_VIEW = "admin/admin-04-evaluation/admin-evaluation-test";
    private static final String ADD_VIEW = "admin/admin-04-evaluation/admin-evaluation-test-add";
    private static final String EDIT_VIEW = "admin/admin-04-evaluation/admin-evaluation-test-update";
    private static final String GRADER_VIEW = "admin/admin-04-evaluation/admin-evaluation-grader-settings";

    /** 문제은행·설문·Q&A 와 같은 값으로 맞춘다. 화면 페이지네이션 DOM 이 10건 기준이다. */
    private static final int PAGE_SIZE = 10;

    private final ExamService examService;

    /**
     * 시험 목록. 필터·페이징 모두 서버에서 처리하고 화면은 th:each 로 그린다.
     *
     * <p>서버 페이징이다 — 예전에는 필터링된 전체 행을 내려주고 화면 JS 가 숨겼다 보였다 했다.
     * 시험은 기수마다 쌓여서 한 번 열 때 수백 행이 오갔다. 문제은행
     * ({@code AdminQuestionController})과 같은 방식으로 한 페이지 분량만 내린다.</p>
     *
     * <p>강사는 담당 과정 시험만 본다 — 권한정의서(1) "△(담당 과정 한정)".
     * 이 제한은 {@code ExamService.searchScoped} 안에서 <b>쿼리 조건</b>으로 들어가므로
     * page 파라미터를 조작해도 담당 아닌 과정이 나오지 않는다.</p>
     */
    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser loginUser,
                       @ModelAttribute("cond") ExamSearchCond cond,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        Page<ExamListRow> result = examService.searchScoped(
                cond, loginUser == null ? null : loginUser.getId(), isAdmin(loginUser),
                PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE,
                        Sort.by(Sort.Direction.DESC, "id")));

        model.addAttribute("rows", result.getContent());
        model.addAttribute("page", PageView.of(result));
        model.addAttribute("courseOptions", examService.courseOptions());
        model.addAttribute("instructorOptions", examService.instructorOptions());
        return LIST_VIEW;
    }

    /** 등록 폼. */
    @GetMapping("/new")
    public String addForm(Model model) {
        model.addAttribute("form", new ExamForm());
        addFormModel(model, null);
        return ADD_VIEW;
    }

    /**
     * LXP-022~025 1차 시연용 그레이더 설정 화면.
     *
     * <p>자동·수동·혼합 채점, 채점 코드와 테스트 케이스, 부분점수 규칙을 한 흐름에서
     * 확인하는 화면이다. 실제 코드 격리 실행과 영속 저장은 후속 채점 엔진 범위이므로
     * 이 진입점은 화면 모델을 별도로 요구하지 않는다.</p>
     */
    @GetMapping("/graders")
    public String graderSettings() {
        return GRADER_VIEW;
    }

    /** 등록. */
    @PostMapping
    public String create(@AuthenticationPrincipal LoginUser loginUser,
                         @Valid @ModelAttribute("form") ExamForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addFormModel(model, form.getCourseId());
            return ADD_VIEW;
        }
        try {
            Long id = examService.create(form);
            redirectAttributes.addFlashAttribute("message", "시험을 등록했습니다.");
            return "redirect:/admin/evaluation/exams/" + id + "/edit";
        } catch (IllegalArgumentException e) {
            // 배점 합계·합격점수 같은 도메인 검증 실패. 화면에 그대로 보여준다.
            bindingResult.reject("exam.invalid", e.getMessage());
            addFormModel(model, form.getCourseId());
            return ADD_VIEW;
        }
    }

    /** 수정 폼. */
    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal LoginUser loginUser,
                           @PathVariable Long id, Model model) {
        examService.requireManageable(id, loginUser.getId(), isAdmin(loginUser));
        ExamForm form = examService.loadForm(id);
        model.addAttribute("form", form);
        model.addAttribute("examQuestions", examService.loadExamQuestions(id));
        model.addAttribute("targetTraineeCount", examService.countTargetTrainees(form.getCourseId()));
        addFormModel(model, form.getCourseId());
        return EDIT_VIEW;
    }

    /** 수정. */
    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal LoginUser loginUser,
                             @PathVariable Long id,
                         @Valid @ModelAttribute("form") ExamForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderEditWithErrors(id, form, model);
        }
        try {
            examService.requireManageable(id, loginUser.getId(), isAdmin(loginUser));
        examService.update(id, form);
            redirectAttributes.addFlashAttribute("message", "시험을 수정했습니다.");
            return "redirect:/admin/evaluation/exams/" + id + "/edit";
        } catch (IllegalArgumentException e) {
            bindingResult.reject("exam.invalid", e.getMessage());
            return renderEditWithErrors(id, form, model);
        }
    }

    /**
     * 자동 출제 규칙으로 문항 확정.
     * 규칙만 저장된 상태로는 응시 문항이 재현되지 않으므로, 이 시점에 ExamQuestion(fromRule=true)으로 내린다.
     */
    @PostMapping("/{id}/materialize")
    public String materialize(@AuthenticationPrincipal LoginUser loginUser,
                             @PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            examService.requireManageable(id, loginUser.getId(), isAdmin(loginUser));
            int added = examService.materializeRules(id);
            redirectAttributes.addFlashAttribute("message",
                    "출제 규칙으로 " + added + "문항을 확정했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            // IllegalState 는 문제은행 3배수 미달 / 응시 기록 있는 시험 수정 시도.
            // 관리자에게 500 을 보여주면 안 되고, 부족분 안내를 그대로 전달해야 한다.
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/evaluation/exams/" + id + "/edit";
    }

    /**
     * 성적 공개 설정만 변경.
     *
     * 시험 폼(update)은 응시 기록이 있으면 잠기지만(문항 구성이 갈리면 3년 재현이 깨진다),
     * 성적 공개 방식은 문항과 무관하고 "채점 끝나면 공개"가 정상 운영이라 잠그면 안 된다.
     */
    @PostMapping("/{id}/result-release")
    public String changeResultRelease(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long id,
            @RequestParam("resultRelease") String resultRelease,
            @RequestParam(value = "resultReleaseAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime resultReleaseAt,
            RedirectAttributes redirectAttributes) {
        try {
            examService.requireManageable(id, loginUser.getId(), isAdmin(loginUser));
        examService.changeResultRelease(id, resultRelease, resultReleaseAt);
            redirectAttributes.addFlashAttribute("message",
                    "성적 공개 설정을 '" + resultRelease + "' 로 변경했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/evaluation/exams/" + id + "/edit";
    }

    /** 선택 비활성화 (상태를 종료로 내린다). */
    @PostMapping("/deactivate")
    public String deactivate(@AuthenticationPrincipal LoginUser loginUser,
                             @RequestParam("ids") List<Long> ids,
                             RedirectAttributes redirectAttributes) {
        examService.assertCanManageAll(ids, loginUser.getId(), isAdmin(loginUser));
        examService.deactivate(ids);
        redirectAttributes.addFlashAttribute("message", ids.size() + "건을 비활성화했습니다.");
        return "redirect:/admin/evaluation/exams";
    }

    /** 선택 삭제 (soft delete). */
    @PostMapping("/delete")
    public String delete(@AuthenticationPrincipal LoginUser loginUser,
                             @RequestParam("ids") List<Long> ids,
                         RedirectAttributes redirectAttributes) {
        examService.assertCanManageAll(ids, loginUser.getId(), isAdmin(loginUser));
        examService.delete(ids);
        redirectAttributes.addFlashAttribute("message", ids.size() + "건을 삭제했습니다.");
        return "redirect:/admin/evaluation/exams";
    }

    /* ===== 내부 ===== */

    private String renderEditWithErrors(Long id, ExamForm form, Model model) {
        model.addAttribute("examQuestions", examService.loadExamQuestions(id));
        model.addAttribute("targetTraineeCount", examService.countTargetTrainees(form.getCourseId()));
        addFormModel(model, form.getCourseId());
        return EDIT_VIEW;
    }

    /** 폼 화면 공통 모델 — 셀렉트 옵션과 문제 선택 모달용 문제 목록. */
    private void addFormModel(Model model, Long courseId) {
        model.addAttribute("courseOptions", examService.courseOptions());
        model.addAttribute("subjectOptions", examService.subjectOptions(courseId));
        model.addAttribute("sessionOptions", examService.sessionOptions(courseId));
        model.addAttribute("instructorOptions", examService.instructorOptions());
        model.addAttribute("questionPool", examService.questionPool());
    }
    /** 관리자 여부 — 강사는 담당 과정으로 제한된다. */
    private static boolean isAdmin(LoginUser loginUser) {
        return loginUser != null && loginUser.getRole() == com.ssa.lms.user.entity.Role.ADMIN;
    }
}
