package com.ssa.lms.web.admin.site;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminSeoController {

    @GetMapping("/admin/site/seo")
    public String seoManagement() {
        return "admin/site/seo-management";
    }
}
