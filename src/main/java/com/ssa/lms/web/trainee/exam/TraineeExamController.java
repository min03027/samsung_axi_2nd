package com.ssa.lms.web.trainee.exam;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.demo.SampleScreenData;
import com.ssa.lms.exam.dto.AttemptResultView;
import com.ssa.lms.exam.dto.AttemptView;
import com.ssa.lms.exam.service.ExamAttemptService;
import com.ssa.lms.exam.service.ExamTakeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 훈련생 시험 응시/제출.
 *
 * 접근 권한: SecurityConfig 의 {@code /trainee/exam/**} → TRAINEE 전용 (관리자도 들어올 수 없다).
 * 그 위에 서비스가 "본인 회차인지"를 한 번 더 본다 — URL 의 attemptId 만 바꿔서 남의 답안을 볼 수 없어야 하기 때문.
 *
 * 화면은 기존 정적 화면 두 개를 전환해서 쓴다 (새로 만들지 않는다).
 *  - templates/trainee/online-test.html : 응시 가능 시험 목록
 *  - templates/trainee/do-test.html     : 실제 응시 화면 + 제출 결과 패널
 */
@Slf4j
@Controller
@RequestMapping("/trainee/exam")
@RequiredArgsConstructor
public class TraineeExamController {

    private static final String LIST_VIEW = "trainee/online-test";
    private static final String TAKE_VIEW = "trainee/do-test";

    private final ExamAttemptService examAttemptService;
    private final SampleScreenData sampleScreenData;

    /* ===================== 화면 ===================== */

    /** 응시 가능 시험 목록. 필터/정렬/페이징은 기존 화면 JS 가 그대로 처리한다. */
    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        var filled = sampleScreenData.fill(
                examAttemptService.availableExams(loginUser.getId()),
                sampleScreenData::exams);
        model.addAttribute("rows", filled.rows());
        model.addAttribute("sampleData", filled.sample());
        return LIST_VIEW;
    }

    /**
     * 응시 시작 / 이어하기.
     * 본인인증 자격값은 화면 모달이 보낸다. 최근 인증 이력이 있으면 비어 있어도 통과한다.
     */
    @PostMapping("/{examId}/start")
    public String start(@PathVariable Long examId,
                        @RequestParam(value = "verifyMethod", required = false) String verifyMethod,
                        @RequestParam(value = "credential", required = false) String credential,
                        @RequestParam(value = "precheckSessionId", required = false) Long precheckSessionId,
                        @AuthenticationPrincipal LoginUser loginUser,
                        HttpServletRequest request,
                        RedirectAttributes redirectAttributes) {
        try {
            Long attemptId = examAttemptService.start(
                    examId, loginUser.getId(), verifyMethod, credential,
                    clientIp(request), request.getHeader("User-Agent"), precheckSessionId);
            return "redirect:/trainee/exam/attempt/" + attemptId;
        } catch (ExamTakeException e) {
            redirectAttributes.addFlashAttribute("errorCode", e.getCode());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            /* IDENTITY_REQUIRED 를 전부 사전점검으로 보내면, 비밀번호 인증만 필요한 시험의
               비밀번호 오류까지 엉뚱한 QR 화면으로 간다. 사전점검 대상 시험만 보낸다.
               판정은 서비스의 동일 정책(PrecheckPolicy)을 쓴다 — 여기서 다시 하드코딩하지 않는다. */
            if ("IDENTITY_REQUIRED".equals(e.getCode()) && examAttemptService.requiresPrecheck(examId)) {
                return "redirect:/trainee/exam/precheck/" + examId;
            }
            return "redirect:/trainee/exam";
        }
    }

    /**
     * 응시 화면. 이미 제출/자동제출된 회차면 같은 템플릿의 결과 패널이 뜬다.
     * (결과 전용 화면은 다음 슬라이스(시험 채점) 소관이라 여기서 새로 만들지 않는다)
     */
    @GetMapping("/attempt/{attemptId}")
    public String attempt(@PathVariable Long attemptId,
                          @AuthenticationPrincipal LoginUser loginUser,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        try {
            AttemptView view = examAttemptService.loadAttempt(attemptId, loginUser.getId());
            model.addAttribute("attempt", view);
            if (!"IN_PROGRESS".equals(view.status())) {
                model.addAttribute("result", examAttemptService.loadResult(attemptId, loginUser.getId()));
            }
            return TAKE_VIEW;
        } catch (ExamTakeException e) {
            redirectAttributes.addFlashAttribute("errorCode", e.getCode());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/trainee/exam";
        }
    }

    /**
     * 최종 제출.
     * 마감 판정은 서버가 ExamAttempt.expiresAt 으로만 한다 — 만료 후 제출은 AUTO_SUBMITTED 로 기록되고
     * 응시자에게 그 사실이 결과 패널과 안내 문구로 나간다.
     */
    @PostMapping("/attempt/{attemptId}/submit")
    public String submit(@PathVariable Long attemptId,
                         @AuthenticationPrincipal LoginUser loginUser,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        try {
            AttemptResultView result =
                    examAttemptService.submit(attemptId, loginUser.getId(), clientIp(request));
            redirectAttributes.addFlashAttribute("message", result.autoSubmitted()
                    ? "제한시간이 만료되어 서버가 자동 제출했습니다."
                    : "답안을 제출했습니다.");
            return "redirect:/trainee/exam/attempt/" + attemptId;
        } catch (ExamTakeException e) {
            redirectAttributes.addFlashAttribute("errorCode", e.getCode());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/trainee/exam";
        }
    }

    /* ===================== AJAX ===================== */

    /**
     * 답안 임시저장. (attempt, question) 한 행을 upsert 하므로 몇 번을 눌러도 행이 늘지 않는다.
     * 만료됐으면 저장하지 않고 expired=true 를 돌려준다 → 화면이 즉시 제출로 넘어간다.
     */
    @PostMapping("/attempt/{attemptId}/answers")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveAnswer(
            @PathVariable Long attemptId,
            @RequestParam("questionId") Long questionId,
            @RequestParam(value = "choiceId", required = false) Long choiceId,
            @RequestParam(value = "answerText", required = false) String answerText,
            @AuthenticationPrincipal LoginUser loginUser) {
        try {
            LocalDateTime savedAt = examAttemptService.saveAnswer(
                    attemptId, loginUser.getId(), questionId, choiceId, answerText);
            Map<String, Object> body = new HashMap<>();
            body.put("ok", true);
            body.put("savedAt", savedAt.toString());
            return ResponseEntity.ok(body);
        } catch (ExamTakeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(e));
        }
    }

    /**
     * 부정행위/입퇴장 이벤트 기록 (append-only).
     * 화면은 Exam.blockTabSwitch / blockCopyPaste 가 true 일 때만 이벤트를 보낸다.
     */
    @PostMapping("/attempt/{attemptId}/events")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> recordEvent(
            @PathVariable Long attemptId,
            @RequestParam("eventType") String eventType,
            @RequestParam(value = "detail", required = false) String detail,
            @AuthenticationPrincipal LoginUser loginUser,
            HttpServletRequest request) {
        try {
            boolean saved = examAttemptService.recordEvent(
                    attemptId, loginUser.getId(), eventType, detail, clientIp(request));
            Map<String, Object> body = new HashMap<>();
            body.put("ok", saved);
            if (!saved) {
                body.put("message", "알 수 없는 이벤트 유형입니다: " + eventType);
            }
            return ResponseEntity.ok(body);
        } catch (ExamTakeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(e));
        }
    }

    /* ===================== 내부 ===================== */

    private Map<String, Object> errorBody(ExamTakeException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("ok", false);
        body.put("code", e.getCode());
        body.put("message", e.getMessage());
        body.put("expired", "EXPIRED".equals(e.getCode()));
        return body;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}
