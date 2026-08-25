package com.ssa.lms.web.trainee.exam.precheck;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.identity.dto.IdentityViews;
import com.ssa.lms.identity.entity.IdentityAccessDeniedException;
import com.ssa.lms.identity.policy.PublicBaseUrl;
import com.ssa.lms.identity.entity.IdentitySessionStateException;
import com.ssa.lms.identity.service.ExamIdentityService;
import com.ssa.lms.storage.privatefile.PrivateFileException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * PC 응시 준비(사전점검) 화면 — QR 표시 · 상태 폴링 · 웹캠 점검.
 *
 * <p><b>이 화면에는 신분증 파일 입력이 없다.</b> 신분증은 QR 로 열리는 모바일 화면에서만 받는다.
 * 같은 PC 에서 업로드 화면을 여는 버튼도 두지 않는다 — 그러면 "모바일로 제출" 이라는 요건이
 * 강제되지 않는다.</p>
 */
@Controller
@RequestMapping("/trainee/exam/precheck")
@RequiredArgsConstructor
public class TraineePrecheckController {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 얼굴 사진 동의 문구 버전. 문구를 고치면 이 값을 올려 무엇에 동의했는지 되짚을 수 있게 한다. */
    private static final String FACE_CONSENT_VERSION = "face-consent-v1";

    private final ExamIdentityService identityService;

    /**
     * QR 에 넣을 공개 base URL.
     *
     * <p>요청 Host·X-Forwarded-Host 를 그대로 믿으면 공격자가 헤더를 바꿔 QR 을
     * 자기 도메인으로 만들 수 있다(호스트 헤더 주입). 설정값이 있으면 그것만 쓴다.</p>
     *
     * <p>운영: {@code LMS_IDENTITY_PUBLIC_BASE_URL=https://lms.samsungax.com}</p>
     */
    @Value("${lms.identity.public-base-url:}")
    private String publicBaseUrl;

    /** 사전점검 화면. 시험 시작 버튼이 여기로 보낸다. */
    @GetMapping("/{examId}")
    public String precheck(@PathVariable Long examId,
                           @AuthenticationPrincipal LoginUser loginUser,
                           HttpServletRequest request,
                           Model model) {
        IdentityViews.Precheck view;
        try {
            view = identityService.openPrecheck(examId, loginUser.getId(), clientIp(request));
        } catch (IdentityAccessDeniedException e) {
            /* 비수강·비대상 시험은 세션·토큰·문서·감사 로그를 남기지 않고 목록으로 되돌린다.
               다른 시험의 존재 여부를 화면으로 흘리지 않는다. */
            return "redirect:/trainee/exam";
        }

        model.addAttribute("examId", view.examId());
        model.addAttribute("examTitle", view.examTitle());
        model.addAttribute("sessionId", view.sessionId());
        model.addAttribute("traineeName", view.traineeName());
        model.addAttribute("status", view.status());
        return "trainee/exam-precheck";
    }

    /* ===================== QR ===================== */

    /** 새 QR 발급. 이전 토큰은 서버에서 폐기된다. */
    @PostMapping("/{sessionId}/identity/qr")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> issueQr(@PathVariable Long sessionId,
                                                       @AuthenticationPrincipal LoginUser loginUser,
                                                       HttpServletRequest request) {
        /* ★ 공개 주소를 <b>토큰 발급 전에</b> 확정한다 (P0-A).
           예전에는 issueToken() 이 먼저 돌았다. 그 트랜잭션은 기존 토큰 폐기 → 새 토큰 저장 →
           ISSUE_QR 감사 로그까지 모두 끝낸 뒤였고, 그다음에야 baseUrl() 이 잘못된 설정을 발견했다.
           결과적으로 503 을 돌려줘도 서버에는 쓰지 못할 토큰과 감사 로그가 남고,
           훈련생이 쓰고 있던 멀쩡한 QR 이 폐기됐다. 설정 오류로 QR 을 만들 수 없는 상황이면
           토큰 서비스에 <b>진입하지 않는 것</b>이 맞다. */
        String publicUrl;
        try {
            publicUrl = baseUrl(request);
        } catch (PublicBaseUrl.InvalidPublicBaseUrlException e) {
            /* 운영 설정 오류다. 요청 헤더로 대체하면 공격자가 QR 목적지를 정하게 된다.
               발급을 명확히 실패시키고 운영자가 고치게 한다 (P1-4). */
            return ResponseEntity.status(503).body(Map.of("ok", false, "message", e.getMessage()));
        }

        try {
            ExamIdentityService.IssuedToken issued =
                    identityService.issueToken(sessionId, loginUser.getId(), clientIp(request));
            Map<String, Object> body = new HashMap<>();
            body.put("ok", true);
            /* QR 에 담을 값 — 절대 경로여야 휴대폰에서 열린다. 개인정보는 넣지 않는다. */
            body.put("url", publicUrl + "/m/id/" + issued.rawToken());
            /* 실제로 <b>유효한</b> 설정을 썼는지를 나타낸다. 예전에는 원문이 비어 있지 않기만 하면
               true 였고, 값이 잘못돼 fallback 을 탄 경우에도 true 로 보고했다 (P1-4). */
            body.put("baseFromConfig", PublicBaseUrl.normalize(publicBaseUrl) != null);
            body.put("expiresAt", issued.expiresAt().format(CLOCK));
            body.put("remainingSeconds", issued.remainingSeconds());
            return ResponseEntity.ok(body);
        } catch (IdentityAccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "message", e.getMessage()));
        } catch (IdentitySessionStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        }
    }

    /** PC 가 주기적으로 부르는 상태 조회. localStorage 가 아니라 이 값이 판정 근거다. */
    @GetMapping("/{sessionId}/identity/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long sessionId,
                                                      @AuthenticationPrincipal LoginUser loginUser) {
        return identityService.statusOf(sessionId, loginUser.getId())
                .<ResponseEntity<Map<String, Object>>>map(v -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("ok", true);
                    body.put("status", v.status());
                    body.put("statusLabel", v.statusLabel());
                    body.put("canEnter", v.canEnter());
                    body.put("reason", v.reason());
                    body.put("resubmitCount", v.resubmitCount());
                    body.put("hasIdCard", v.hasIdCard());
                    body.put("hasFaceCheck", v.hasFaceCheck());
                    body.put("submissionComplete", v.submissionComplete());
                    body.put("approvalExpiresAt",
                            v.approvalExpiresAt() == null ? null : v.approvalExpiresAt().format(CLOCK));
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("ok", false, "message", "세션을 찾을 수 없습니다.")));
    }

    /** 웹캠 연결 확인 통과를 서버에 기록한다. 입장 게이트가 이 시각의 신선도를 본다. */
    @PostMapping("/{sessionId}/webcam-checked")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> webcamChecked(@PathVariable Long sessionId,
                                                             @AuthenticationPrincipal LoginUser loginUser) {
        try {
            identityService.markWebcamChecked(sessionId, loginUser.getId());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IdentityAccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "message", e.getMessage()));
        } catch (IdentitySessionStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        }
    }

    /* ===================== 얼굴 확인용 사진 ===================== */

    /**
     * 웹캠 점검 통과 후 훈련생이 <b>직접 버튼을 눌러</b> 제출한 정지 이미지 한 장.
     * 자동 촬영·반복 저장은 하지 않는다.
     */
    @PostMapping("/{sessionId}/face-check")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> faceCheck(@PathVariable Long sessionId,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam(value = "consent", defaultValue = "false") boolean consent,
                                                         @AuthenticationPrincipal LoginUser loginUser,
                                                         HttpServletRequest request) {
        try {
            identityService.submitFaceCheck(sessionId, loginUser.getId(), file,
                    consent, FACE_CONSENT_VERSION, clientIp(request));
            return ResponseEntity.ok(Map.of("ok", true, "message", "얼굴 확인용 사진을 제출했습니다."));
        } catch (IdentityAccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "message", e.getMessage()));
        } catch (IdentitySessionStateException | PrivateFileException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        }
    }

    /* ===================== 내부 ===================== */

    /**
     * QR 에 넣을 절대 주소. 휴대폰은 PC 와 다른 기기이므로 localhost 로는 열리지 않는다.
     * 운영에서는 리버스 프록시가 X-Forwarded-* 를 넣어 준다.
     */
    private String baseUrl(HttpServletRequest req) {
        /* 설정값이 유효하면 Host·X-Forwarded-Host·X-Forwarded-Proto 를 <b>아예 보지 않는다</b>.
           설정이 있는데 잘못됐으면 여기서 예외가 나고, fallback 으로 내려가지 않는다 (P1-4). */
        String configured = PublicBaseUrl.require(publicBaseUrl);
        if (configured != null) {
            return configured;
        }
        /* ⚠ 아래는 <b>로컬 개발용 fallback</b> 이다.
           요청 헤더를 신뢰하므로 운영에서는 반드시 lms.identity.public-base-url 을 설정해야 한다.
           (LMS_IDENTITY_PUBLIC_BASE_URL=https://lms.samsungax.com) */
        String proto = header(req, "X-Forwarded-Proto", req.getScheme());
        String host = header(req, "X-Forwarded-Host", null);
        if (host != null) {
            return proto + "://" + host;
        }
        int port = req.getServerPort();
        boolean defaultPort = ("http".equals(proto) && port == 80) || ("https".equals(proto) && port == 443);
        return proto + "://" + req.getServerName() + (defaultPort ? "" : ":" + port);
    }

    private static String header(HttpServletRequest req, String name, String fallback) {
        String v = req.getHeader(name);
        return (v == null || v.isBlank()) ? fallback : v.split(",")[0].trim();
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff == null || xff.isBlank()) ? req.getRemoteAddr() : xff.split(",")[0].trim();
    }
}
