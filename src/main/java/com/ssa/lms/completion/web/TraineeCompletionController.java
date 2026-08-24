package com.ssa.lms.completion.web;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.completion.service.CertificateService;
import com.ssa.lms.completion.service.CompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * 훈련생 이수관리 — <b>본인</b> 이수 현황 조회 + 이수증(PDF) 다운로드.
 *
 * <p>권한 경계: 목록은 인증 주체 것만 조회한다. 이수증 다운로드는 completionId 로 접근하므로
 * 본인 소유인지 반드시 검증하고({@link CompletionService#isOwnedByTrainee}), 아니면 404 로
 * 존재 여부 자체를 노출하지 않는다. PDF 생성은 관리자와 동일한 {@link CertificateService} 를 재사용한다.</p>
 */
@Controller
@RequestMapping("/trainee/completion-management")
@RequiredArgsConstructor
public class TraineeCompletionController {

    private final CompletionService completionService;
    private final CertificateService certificateService;

    @GetMapping
    public String completion(@AuthenticationPrincipal LoginUser user, Model model) {
        model.addAttribute("completions", completionService.viewsByTrainee(user.getId()));
        return "trainee/completion-management";
    }

    /** 이수증 PDF — 본인 소유 & 이수 확정(PASS+CONFIRMED) 건만. 새 탭 인라인 표시. */
    @GetMapping("/{completionId}/certificate")
    public ResponseEntity<byte[]> certificate(@PathVariable Long completionId,
                                              @AuthenticationPrincipal LoginUser user) {
        if (!completionService.isOwnedByTrainee(completionId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이수 정보를 찾을 수 없습니다.");
        }
        byte[] pdf = certificateService.generate(completionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("certificate-" + completionId + ".pdf").build().toString())
                .body(pdf);
    }
}
