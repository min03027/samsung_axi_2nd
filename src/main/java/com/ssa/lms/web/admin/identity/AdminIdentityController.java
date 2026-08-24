package com.ssa.lms.web.admin.identity;

import com.ssa.lms.auth.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import com.ssa.lms.identity.dto.IdentityViews;
import com.ssa.lms.identity.entity.IdentityAccessDeniedException;
import com.ssa.lms.identity.entity.IdentityGoneException;
import com.ssa.lms.identity.entity.IdentitySessionStateException;
import com.ssa.lms.identity.service.ExamIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 응시 전 신분증 승인 대기열 (LXP-015) + 시험 중·후 조회 (LXP-016).
 *
 * <p>사후 검토 화면(recordings/proctor-review)과 <b>역할을 섞지 않는다.</b>
 * 여기는 응시 전 최초 판정을 내리는 곳이고, 사후 화면은 이미 판정된 자료를 재확인하는 곳이다.</p>
 *
 * <p>메뉴 등록(`fragments/admin.html`)은 이 브랜치에서 하지 않는다 — 팀원이 같은 파일의
 * 126줄을 재작성 중이라 병합 충돌이 난다. 병합 후 1줄 추가하는 후속 작업으로 남긴다.</p>
 */
@Controller
@RequestMapping("/admin/evaluation/identity")
@RequiredArgsConstructor
public class AdminIdentityController {

    private final ExamIdentityService identityService;

    @GetMapping
    public String queue(@RequestParam(value = "q", required = false) String keyword,
                        @RequestParam(value = "status", required = false) String status,
                        @AuthenticationPrincipal LoginUser loginUser,
                        Model model) {
        List<IdentityViews.Row> rows = identityService.queueRows(loginUser.getId(), role(loginUser)).stream()
                .filter(r -> status == null || status.isBlank() || status.equals("all") || r.status().equals(status))
                .filter(r -> keyword == null || keyword.isBlank()
                        || r.traineeName().contains(keyword) || r.examTitle().contains(keyword))
                .toList();

        model.addAttribute("rows", rows);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("status", status == null ? "all" : status);
        model.addAttribute("waiting", rows.stream().filter(r -> r.status().equals("SUBMITTED")).count());
        return "admin/admin-04-evaluation/admin-evaluation-identity";
    }

    @GetMapping("/{sessionId}")
    public String detail(@PathVariable Long sessionId,
                         @AuthenticationPrincipal LoginUser loginUser,
                         Model model) {
        /* GET 은 조회만 한다 — 상태 변경은 아래 /review POST 로 분리했다 (P0-5). */
        String role = role(loginUser);
        IdentityViews.Row row = identityService.detailRow(sessionId, loginUser.getId(), role);
        model.addAttribute("auditTrail", identityService.auditTrail(sessionId, loginUser.getId(), role));
        model.addAttribute("row", row);
        model.addAttribute("idDocId", row.idDocumentId());
        model.addAttribute("faceDocId", row.faceDocumentId());
        model.addAttribute("maxResubmit", com.ssa.lms.identity.entity.ExamIdentitySession.MAX_RESUBMIT);
        return "admin/admin-04-evaluation/admin-evaluation-identity-detail";
    }

    /** 검토 시작 — CSRF 보호되는 POST 로만 상태를 바꾼다. */
    @PostMapping("/{sessionId}/review")
    public String openReview(@PathVariable Long sessionId,
                             @AuthenticationPrincipal LoginUser loginUser,
                             HttpServletRequest request,
                             RedirectAttributes ra) {
        try {
            identityService.openReview(sessionId, loginUser.getId(), role(loginUser), clientIp(request));
        } catch (IdentitySessionStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/evaluation/identity/" + sessionId;
    }

    @PostMapping("/{sessionId}/approve")
    public String approve(@PathVariable Long sessionId,
                          @AuthenticationPrincipal LoginUser loginUser,
                          HttpServletRequest request,
                          RedirectAttributes ra) {
        try {
            identityService.approve(sessionId, loginUser.getId(), role(loginUser), clientIp(request));
            ra.addFlashAttribute("message", "승인했습니다.");
        } catch (IdentitySessionStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/evaluation/identity";
    }

    @PostMapping("/{sessionId}/reject")
    public String reject(@PathVariable Long sessionId,
                         @RequestParam("reason") String reason,
                         @RequestParam(value = "resubmit", defaultValue = "false") boolean resubmit,
                         @AuthenticationPrincipal LoginUser loginUser,
                         HttpServletRequest request,
                         RedirectAttributes ra) {
        try {
            identityService.reject(sessionId, loginUser.getId(), role(loginUser),
                    reason, resubmit, clientIp(request));
            ra.addFlashAttribute("message", resubmit ? "재제출을 요청했습니다." : "반려했습니다.");
        } catch (IdentitySessionStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/evaluation/identity/" + sessionId;
        }
        return "redirect:/admin/evaluation/identity";
    }

    /**
     * 제출 이미지 스트리밍.
     *
     * <p>URL 로 직접 접근해도 <b>매 요청마다</b> 권한을 다시 본다. 저장 키는 절대 노출하지 않고
     * document id 로만 접근한다. 캐시에 남기지 않는다 — 개인정보다.</p>
     */
    @GetMapping("/document/{documentId}/image")
    @ResponseBody
    public ResponseEntity<byte[]> image(@PathVariable Long documentId,
                                        @AuthenticationPrincipal LoginUser loginUser,
                                        HttpServletRequest request) {
        try {
            ExamIdentityService.ImageBytes img = identityService.readImageFor(
                    documentId, loginUser.getId(), role(loginUser), clientIp(request));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(img.contentType()))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Referrer-Policy", "no-referrer")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(img.bytes());
        } catch (IdentityAccessDeniedException e) {
            return ResponseEntity.status(403).build();
        } catch (IdentityGoneException e) {
            /* 파기된 자료는 410 — 500 을 던지지 않는다. */
            return ResponseEntity.status(410).build();
        }
    }

    private static String role(LoginUser u) {
        return u.getRole() == null ? "" : u.getRole().name();
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff == null || xff.isBlank()) ? req.getRemoteAddr() : xff.split(",")[0].trim();
    }

    /* ===================== 내부 ===================== */
}
