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
 * 정적 LXP 데모가 파일로만 존재하고 운영 메뉴에서 고립되는 회귀를 막는다.
 */
class StaticV2FeatureEntryNavigationTest {

    private static final Path ROOT = Paths.get("src/main/resources");

    private String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("훈련생 운영 메뉴와 주요 목록 화면에서 새 데모 3종으로 진입할 수 있다")
    void traineeEntryPointsAreConnected() throws Exception {
        String fragment = read("templates/fragments/trainee.html");
        String contents = read("templates/trainee/contents.html");
        String attendance = read("templates/trainee/attendance.html");

        assertThat(fragment)
                .contains("/v2/lxp/trainee/learning-presence-check.html")
                .contains("/v2/lxp/trainee/live-class-precheck.html")
                .contains("/v2/lxp/trainee/attendance.html")
                .contains("/v2/lxp/trainee/exam-workspace.html")
                .contains("/v2/lxp/trainee/points-ranking.html");
        assertThat(contents)
                .contains("/v2/lxp/trainee/learning-presence-check.html")
                .contains("/v2/lxp/trainee/live-class-precheck.html");
        assertThat(attendance).contains("/v2/lxp/trainee/attendance.html");
    }

    @Test
    @DisplayName("관리자 운영 메뉴와 출결 화면에서 새 운영 데모 3종으로 진입할 수 있다")
    void adminEntryPointsAreConnected() throws Exception {
        String fragment = read("templates/fragments/management.html");
        String attendance = read("templates/admin/admin-05-attendance/admin-attendance.html");

        assertThat(fragment)
                .contains("data-page=\"/admin/completion\"")
                .contains("onclick=\"handleMenuClick(this, '/admin/completion')\"")
                .contains(">이수 관리</span>")
                .contains("/v2/admin/attendance.html")
                .contains("/v2/admin/presence-monitor.html")
                .contains("/v2/admin/live-class-monitor.html")
                .contains("/v2/admin/engagement-points.html")
                .contains("/v2/admin/evidence-library.html");
        assertThat(attendance)
                .contains("/v2/admin/attendance.html")
                .contains("/v2/admin/presence-monitor.html")
                .contains("/v2/admin/live-class-monitor.html");
    }

    @Test
    @DisplayName("정적 데모 셸에서도 훈련생·관리자 기능 사이를 계속 이동할 수 있다")
    void staticShellKeepsAllFeatureRoutes() throws Exception {
        String shell = read("static/v2/assets/exam-legacy-shell.js");

        assertThat(shell)
                .contains("/v2/lxp/trainee/learning-presence-check.html")
                .contains("/v2/lxp/trainee/live-class-precheck.html")
                .contains("/v2/lxp/trainee/attendance.html")
                .contains("/v2/admin/attendance.html")
                .contains("/v2/admin/presence-monitor.html")
                .contains("/v2/admin/live-class-monitor.html")
                .contains("/v2/lxp/trainee/exam-workspace.html")
                .contains("/v2/lxp/trainee/points-ranking.html")
                .contains("/v2/admin/engagement-points.html")
                .contains("/v2/admin/evidence-library.html");
    }

    @Test
    @DisplayName("화상강의는 사전 점검에서 강의실로 들어가고 나가면 사전 점검으로 돌아온다")
    void liveClassRoundTripIsConnected() throws Exception {
        String precheck = read("static/v2/lxp/trainee/live-class-precheck.html");
        String classroom = read("static/v2/assets/live-classroom.js");

        assertThat(precheck).contains("href=\"/v2/lxp/trainee/live-classroom.html\"");
        assertThat(classroom).contains("window.location.href = \"/v2/lxp/trainee/live-class-precheck.html\"");
    }
}
