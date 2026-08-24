package com.ssa.lms.web.admin.identity;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.identity.entity.IdentityAccessDeniedException;
import com.ssa.lms.identity.entity.IdentityGoneException;
import com.ssa.lms.identity.entity.IdentitySessionStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 신분확인 운영 화면 전용 예외 매핑 (P0-2).
 *
 * <p><b>왜 필요한가</b><br>
 * {@link IdentityAccessDeniedException} 은 Spring Security 의 {@code AccessDeniedException} 이
 * 아니라 평범한 {@code RuntimeException} 이다. 공용 {@code AccessDeniedAdvice} 는 전자만 처리하고
 * {@code basePackages} 에 이 패키지도 없다. 그래서 비담당 강사가 상세 URL 이나 판정 POST 를
 * 직접 호출하면 <b>403 이 아니라 500</b> 이 났다. 이미지 endpoint 만 자체 catch 로 403 을 냈다.</p>
 *
 * <p><b>공용 advice 를 수정하지 않았다.</b> {@code AccessDeniedAdvice} 는 다른 개발자의 화면까지
 * 걸려 있는 공유 파일이라, 이 기능 전용 advice 를 따로 둔다. 범위는
 * {@code assignableTypes} 로 이 컨트롤러 하나에만 묶어 다른 영역에 새지 않게 했다.</p>
 *
 * <p><b>정보 노출 정책</b>: 없는 세션과 권한 없는 세션을 화면에서 구별시키지 않는다.
 * 둘 다 같은 안내 화면으로 보내고, 상태 위반만 사유를 그대로 보여 준다.</p>
 *
 * <p>이 advice 는 조회 실패 경로에서만 동작하므로 상태·감사 로그를 남기지 않는다 —
 * 서비스가 권한 검사를 통과하기 <b>전에</b> 던지기 때문이다.</p>
 */
@Slf4j
@ControllerAdvice(assignableTypes = AdminIdentityController.class)
public class AdminIdentityAdvice {

    @ExceptionHandler(IdentityAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String denied(IdentityAccessDeniedException e,
                         @AuthenticationPrincipal LoginUser loginUser,
                         Model model) {
        log.info("[신분확인 접근거부] user={} role={} 사유={}",
                loginUser == null ? "-" : loginUser.getId(),
                loginUser == null ? "-" : loginUser.getRole(),
                e.getMessage());
        return render(model, loginUser, e.getMessage());
    }

    /**
     * 보존기간이 지나 파기된 자료. 410 으로 내려 "없어진 것" 과 "못 보는 것" 을 구분한다.
     * 파기 사실 자체는 권한이 있는 운영진에게만 의미가 있으므로 이 경로까지 온 요청은 이미 통과했다.
     */
    @ExceptionHandler(IdentityGoneException.class)
    @ResponseStatus(HttpStatus.GONE)
    public String gone(IdentityGoneException e,
                       @AuthenticationPrincipal LoginUser loginUser,
                       Model model) {
        return render(model, loginUser, e.getMessage());
    }

    /**
     * 없는 세션 조회. 권한 거부와 <b>같은 화면</b>으로 보낸다 — 세션 존재 여부를 흘리지 않는다.
     *
     * <p>판정 POST 의 상태 위반은 각 핸들러가 이미 flash 메시지로 잡으므로 여기까지 오지 않는다.
     * 여기 오는 것은 조회 경로의 "없는 세션" 뿐이다.</p>
     */
    @ExceptionHandler(IdentitySessionStateException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String missing(IdentitySessionStateException e,
                          @AuthenticationPrincipal LoginUser loginUser,
                          Model model) {
        return render(model, loginUser, e.getMessage());
    }

    private String render(Model model, LoginUser loginUser, String reason) {
        model.addAttribute("reason", reason);
        model.addAttribute("role", loginUser == null ? null : loginUser.getRole().name());
        model.addAttribute("homeUrl", loginUser == null ? "/login"
                : switch (loginUser.getRole()) {
            case ADMIN -> "/admin";
            case INSTRUCTOR -> "/instructor";
            case TRAINEE -> "/trainee";
        });
        return "error/access-denied";
    }
}
