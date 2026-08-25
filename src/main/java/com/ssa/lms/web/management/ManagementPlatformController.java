package com.ssa.lms.web.management;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.user.entity.Role;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 통합 관리자 IA의 B등급 화면 진입점.
 *
 * <p>관리자와 강사 URL을 기존 보안 경계 아래에 각각 두되 같은 템플릿을 사용한다.
 * 데이터 저장 기능은 만들지 않고, 실제 기능으로 가는 링크와 향후 화면 구조만 제공한다.</p>
 */
@Controller
public class ManagementPlatformController {

    @GetMapping({"/admin/care", "/instructor/care"})
    public String care(@AuthenticationPrincipal LoginUser user, Model model) {
        addScope(user, model);
        return "management/care";
    }

    @GetMapping({"/admin/care/diary", "/instructor/care/diary"})
    public String diary(@AuthenticationPrincipal LoginUser user, Model model) {
        addScope(user, model);
        return "management/diary";
    }

    @GetMapping({"/admin/care/follow-ups", "/instructor/care/follow-ups"})
    public String followUps(@AuthenticationPrincipal LoginUser user, Model model) {
        addScope(user, model);
        return "management/follow-ups";
    }

    @GetMapping({"/admin/quality", "/instructor/quality"})
    public String quality(@AuthenticationPrincipal LoginUser user, Model model) {
        addScope(user, model);
        return "management/quality";
    }

    private void addScope(LoginUser user, Model model) {
        boolean admin = user != null && user.getRole() == Role.ADMIN;
        model.addAttribute("managementScope", admin ? "전체 과정·수강생" : "내 담당 과정·수강생");
        model.addAttribute("managementBase", admin ? "/admin" : "/instructor");
    }
}
