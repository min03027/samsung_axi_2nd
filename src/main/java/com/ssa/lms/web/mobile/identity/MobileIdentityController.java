package com.ssa.lms.web.mobile.identity;

import com.ssa.lms.identity.dto.IdentityViews;
import com.ssa.lms.identity.entity.IdentitySessionStateException;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.storage.privatefile.PrivateFileException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * QR 로 열리는 모바일 전용 신분증 제출 화면.
 *
 * <p><b>인가 방식이 다르다.</b> 훈련생은 휴대폰에서 LXP 에 로그인돼 있지 않다.
 * 그래서 이 경로만은 로그인 세션이 아니라 <b>QR 토큰</b>으로 인가한다.
 * 토큰이 examId·userId·만료·사용횟수를 모두 담고 있으므로 신원은 토큰이 보장한다.</p>
 *
 * <p>토큰이 URL 에 그대로 들어가므로 TTL 을 짧게(10분) 잡고 1회 발급 시 이전 토큰을 폐기한다.</p>
 */
@Controller
@RequestMapping("/m/id")
@RequiredArgsConstructor
public class MobileIdentityController {

    private final ExamIdentityService identityService;

    /** QR 스캔으로 들어오는 첫 화면. 토큰이 유효할 때만 업로드 폼을 준다. */
    @GetMapping("/{token}")
    public String page(@PathVariable String token, Model model) {
        IdentityViews.Mobile view = identityService.describeMobile(token);

        model.addAttribute("blocked", view.blocked());
        if (view.blocked()) {
            model.addAttribute("message", view.message());
            model.addAttribute("reason", view.reason());
            return "mobile/identity/upload";
        }
        model.addAttribute("token", token);
        model.addAttribute("examTitle", view.examTitle());
        model.addAttribute("traineeName", view.traineeName());
        model.addAttribute("status", view.status());
        model.addAttribute("statusLabel", view.statusLabel());
        model.addAttribute("decisionReason", view.decisionReason());
        model.addAttribute("remainingSeconds", view.remainingSeconds());
        model.addAttribute("hasIdCard", view.hasIdCard());
        model.addAttribute("hasFaceCheck", view.hasFaceCheck());
        return "mobile/identity/upload";
    }

    /**
     * 신분증 업로드. 자동 승인은 없다.
     *
     * <p><b>안내 문구는 세션 상태에 따라 달라진다</b> (P1-2). 얼굴 사진이 아직 없으면
     * 검토 대기가 아니라 "PC 로 돌아가 얼굴 사진을 제출하라" 고 알려야 한다.
     * 예전에는 무조건 검토 대기라고 표시해, 실제로는 아직 아무것도 검토될 수 없는 상태에서
     * 훈련생이 기다리게 됐다.</p>
     */
    @PostMapping("/{token}/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upload(@PathVariable String token,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "consent", defaultValue = "false") boolean consent,
                                                      HttpServletRequest request) {
        if (!consent) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "message", "개인정보 수집·이용 동의가 필요합니다."));
        }
        try {
            ExamIdentityService.SubmitResult r = identityService.submitIdCardAndDescribe(
                    token, file, clientIp(request));
            Map<String, Object> body = new HashMap<>();
            body.put("ok", true);
            body.put("status", r.status());
            body.put("statusLabel", r.statusLabel());
            body.put("hasIdCard", r.hasIdCard());
            body.put("hasFaceCheck", r.hasFaceCheck());
            body.put("submissionComplete", r.submissionComplete());
            body.put("message", r.message());
            return ResponseEntity.ok(body);
        } catch (IdentitySessionStateException | PrivateFileException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        }
    }

    /** 모바일에서 검토 결과를 확인한다. 승인/반려 사유가 여기로 내려온다. */
    @GetMapping("/{token}/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status(@PathVariable String token) {
        /* 판정이 끝나면 토큰은 SESSION_CLOSED 로 막히지만, 결과는 보여줘야 한다. */
        return identityService.mobileStatus(token)
                .<ResponseEntity<Map<String, Object>>>map(v -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("ok", true);
                    body.put("status", v.status());
                    body.put("statusLabel", v.statusLabel());
                    body.put("reason", v.reason());
                    body.put("hasIdCard", v.hasIdCard());
                    body.put("hasFaceCheck", v.hasFaceCheck());
                    body.put("submissionComplete", v.submissionComplete());
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("ok", false, "message", "유효하지 않은 주소입니다.")));
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff == null || xff.isBlank()) ? req.getRemoteAddr() : xff.split(",")[0].trim();
    }
}
