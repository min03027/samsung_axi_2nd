package com.ssa.lms.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LXP 잔여 작업 P0 화면 우선형 회귀 계약.
 *
 * <p>시연 메뉴에 가짜 링크가 다시 들어가거나 공통 오류·모바일 화면이 무스타일로
 * 돌아가는 문제를 배포 전에 차단한다. 실제 데이터 처리 흐름은 각 도메인 FlowTest가
 * 담당하고, 이 테스트는 사용자가 첫 화면에서 누르는 진입점과 화면 골격을 고정한다.</p>
 */
class P0ScreenFirstAuditTest {

    private static final Path RESOURCES = Paths.get("src/main/resources");
    private static final Path MAIN = Paths.get("src/main");

    @Test
    @DisplayName("역할별 공통 메뉴는 실제 경로만 링크로 제공하고 미구현 기능은 2차 항목으로 구분한다")
    void roleNavigationHasNoFakeClickableLinks() throws Exception {
        String trainee = read("templates/fragments/trainee.html");
        String management = read("templates/fragments/management.html");
        String staticShell = read("static/v2/assets/page-section-navigation.js");

        assertThat(trainee)
                .contains("href=\"/trainee/my-course\"")
                .contains("href=\"/trainee/assignment\"")
                .contains("href=\"/trainee/completion-management\"")
                .contains("href=\"/trainee/alarm\"")
                .doesNotContain("준비 중인 기능입니다.");

        assertThat(management)
                .contains("href=\"/admin/courses\"")
                .contains("data-page=\"/admin/completion\"")
                .contains("handleMenuClick(this, '/admin/completion')")
                .contains("href=\"/instructor/courses\"")
                .contains("href=\"/instructor/graduate\"")
                .doesNotContain("href=\"#\"")
                .doesNotContain("준비 중인 기능입니다.");

        assertThat(staticShell)
                .contains("sidebar__link--future", "aria-disabled=\"true\"")
                .doesNotContain("href=\"#\" onclick=\"alert('준비 중인 기능입니다.')");
    }

    @Test
    @DisplayName("404·500 안내 화면은 공통 스타일과 역할별 복귀 동선을 갖는다")
    void errorPageKeepsStyleAndRecoveryRoute() throws Exception {
        String error = read("templates/error/error.html");
        String controller = read("java/com/ssa/lms/web/GlobalErrorController.java");

        assertThat(error)
                .contains("name=\"viewport\"")
                .contains("href=\"/static/css/common-style.css\"")
                .contains("href=\"/static/css/btn-style.css\"")
                .contains("th:href=\"${homeUrl}\"")
                .contains("이전으로", "내 홈으로");
        assertThat(controller)
                .contains("case 404 -> \"페이지를 찾을 수 없습니다\"")
                .contains("case ADMIN -> \"/admin\"")
                .contains("case INSTRUCTOR -> \"/instructor\"")
                .contains("case TRAINEE -> \"/trainee\"");
    }

    @Test
    @DisplayName("관리자·훈련생 핵심 화면은 모바일 메뉴와 가로 잘림 방지 규칙을 유지한다")
    void coreLayoutsKeepResponsiveContracts() throws Exception {
        String managementCss = read("static/css/management-navigation.css");
        String traineeCss = read("static/css/basic-form-trainee.css");
        String traineeNav = read("static/js/trainee/navigation.js");
        String error = read("templates/error/error.html");

        assertThat(managementCss)
                .contains("@media (max-width: 900px)")
                .contains(".management-mobile-toggle")
                .contains(".management-nav-backdrop");
        assertThat(traineeCss)
                .contains("@media (max-width: 768px)")
                .contains("min-width: 0");
        assertThat(traineeNav)
                .contains("MOBILE_BREAKPOINT")
                .contains("aria-expanded")
                .contains("trainee-nav-open");
        assertThat(error).contains("max-width: 520px", "width: 100%");
    }

    @Test
    @DisplayName("시연 화면에는 눌러도 알림만 뜨는 버튼 대신 실제 동작 또는 명시적 2차 상태가 보인다")
    void demoScreensHaveNoNoOpControls() throws Exception {
        String adminPrototype = read("static/v2/admin/index.html");
        String questionBank = read("templates/admin/admin-04-evaluation/admin-evaluation-question-bank.html");
        String attendanceDetail = read("templates/admin/admin-05-attendance/admin-attendance-detail.html");

        assertThat(adminPrototype)
                .contains("감사 로그는 2차 운영 연동 항목입니다.")
                .doesNotContain("감사 로그 보기</a>");
        assertThat(questionBank)
                .contains("id=\"excelUploadBtn\"", "disabled", "엑셀로 등록 (2차)")
                .doesNotContain("onclick=\"alert('준비 중인 기능입니다.')\"");
        assertThat(attendanceDetail)
                .contains("자동 판정 상세는 위 근거 요약에 모두 표시됩니다.")
                .doesNotContain("[자동 판정 상세 보기");
    }

    private String read(String relativePath) throws IOException {
        Path resource = RESOURCES.resolve(relativePath);
        Path path = Files.exists(resource) ? resource : MAIN.resolve(relativePath);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
