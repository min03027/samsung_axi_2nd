package com.ssa.lms.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class StaticV2EvidenceAndEngagementPagesTest {

    private static final Path ROOT = Paths.get("src/main/resources");

    private String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("기술 증빙 목록은 분류·검색·상태와 상세 이동을 제공한다")
    void evidenceLibraryHasCompleteScreenFlow() throws Exception {
        String list = read("static/v2/admin/evidence-library.html");
        String detail = read("static/v2/admin/evidence-detail.html");
        String script = read("static/v2/assets/evidence-library.js");

        assertThat(list)
                .contains("기술 증빙 자료실")
                .contains("id=\"evidenceSearch\"")
                .contains("id=\"evidenceCategory\"")
                .contains("id=\"evidenceState\"")
                .contains("id=\"evidenceBody\"");
        assertThat(detail)
                .contains("기술 증빙 상세")
                .contains("검토 완료 표시(데모)")
                .contains("/v2/admin/evidence-library.html");
        assertThat(script)
                .contains("/v2/admin/evidence-detail.html?id=")
                .contains("인증")
                .contains("운영실적")
                .contains("SLA")
                .contains("특허");
    }

    @Test
    @DisplayName("관리자 점수 기준에서 훈련생 점수 내역·랭킹으로 이동한다")
    void engagementRulesConnectToTraineeRanking() throws Exception {
        String admin = read("static/v2/admin/engagement-points.html");
        String trainee = read("static/v2/lxp/trainee/points-ranking.html");
        String script = read("static/v2/assets/engagement-points.js");

        assertThat(admin)
                .contains("학습 점수 기준 관리")
                .contains("id=\"criteriaList\"")
                .contains("/v2/lxp/trainee/points-ranking.html");
        assertThat(trainee)
                .contains("점수 내역·랭킹")
                .contains("id=\"pointHistoryBody\"")
                .contains("id=\"rankingBody\"")
                .contains("/trainee/growth");
        assertThat(script)
                .contains("lxp-demo-point-rules")
                .contains("window.localStorage")
                .contains("반영 완료");
    }

    @Test
    @DisplayName("요청된 1차 시연 화면들이 모두 정적 자산으로 존재한다")
    void allRequestedScreenFirstPagesExist() {
        assertThat(ROOT.resolve("static/v2/lxp/trainee/exam-workspace.html")).exists();
        assertThat(ROOT.resolve("static/v2/admin/proctor.html")).exists();
        assertThat(ROOT.resolve("static/v2/admin/proctor-review.html")).exists();
        assertThat(ROOT.resolve("static/v2/lxp/trainee/live-class-precheck.html")).exists();
        assertThat(ROOT.resolve("static/v2/lxp/trainee/live-classroom.html")).exists();
        assertThat(ROOT.resolve("static/v2/admin/live-class-monitor.html")).exists();
        assertThat(ROOT.resolve("static/v2/lxp/trainee/learning-presence-check.html")).exists();
        assertThat(ROOT.resolve("static/v2/admin/presence-monitor.html")).exists();
        assertThat(ROOT.resolve("static/v2/lxp/trainee/attendance.html")).exists();
        assertThat(ROOT.resolve("static/v2/admin/attendance.html")).exists();
        assertThat(ROOT.resolve("static/v2/admin/evidence-library.html")).exists();
        assertThat(ROOT.resolve("static/v2/admin/evidence-detail.html")).exists();
        assertThat(ROOT.resolve("static/v2/admin/engagement-points.html")).exists();
        assertThat(ROOT.resolve("static/v2/lxp/trainee/points-ranking.html")).exists();
    }
}
