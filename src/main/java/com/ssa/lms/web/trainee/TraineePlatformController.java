package com.ssa.lms.web.trainee;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 수강생 플랫폼에서 데이터 연동 전 정보 구조를 확인하는 페이지의 진입점.
 *
 * <p>기존 도메인 서비스와 DB를 건드리지 않고 화면 자리만 제공한다. 과제·시험·출결처럼
 * 이미 구현된 기능은 이 컨트롤러를 거치지 않고 기존 URL과 컨트롤러를 그대로 사용한다.</p>
 */
@Controller
@RequestMapping("/trainee")
public class TraineePlatformController {

    @GetMapping("/evaluations")
    public String evaluations() {
        return "trainee/evaluations";
    }

    @GetMapping("/growth")
    public String growth() {
        return "trainee/growth";
    }

    @GetMapping("/career")
    public String career() {
        return "trainee/career";
    }
}
