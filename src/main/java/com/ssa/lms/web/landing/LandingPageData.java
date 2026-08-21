package com.ssa.lms.web.landing;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.List;

/**
 * 랜딩 페이지 초안 데이터.
 * 실제 과정/CMS 연동 전까지 화면 문구와 카드를 템플릿에서 분리해 관리한다.
 */
@Component
public class LandingPageData {

    public void addTo(Model model) {
        model.addAttribute("courses", List.of(
                new Course("로봇 AI 자율주행·협동로봇 개발자", "K-Digital Training", "2026.08–2027.03", "라이다 · 뎁스카메라",
                        "로봇 AI 영상기법을 활용해 산업용 자율주행과 협동로봇을 구현합니다.", "모집 중", "featured"),
                new Course("AIoT 빅데이터 산업솔루션 개발", "K-Digital Training", "2026.08–2027.03", "취업연계 부트캠프",
                        "인공지능과 IoT, 빅데이터 분석을 결합해 산업 현장의 문제를 해결합니다.", "모집 중", "blue"),
                new Course("풀스택 클라우드 웹·앱 개발자", "K-Digital Training", "2026.08–2027.03", "Java · Docker · 보안",
                        "클라우드 기반 웹·앱 서비스를 설계하고 배포하는 개발 역량을 완성합니다.", "모집 중", "dark")
        ));
        model.addAttribute("experiences", List.of(
                new Experience("01", "학습", "오늘 해야 할 학습과 진도를 한눈에 확인합니다."),
                new Experience("02", "프로젝트", "실제 문제를 해결하며 결과물을 포트폴리오로 남깁니다."),
                new Experience("03", "피드백", "강사·튜터의 구체적인 피드백으로 다음 시도를 설계합니다."),
                new Experience("04", "성장 관리", "출결·진도·평가 데이터를 바탕으로 학습 위험을 관리합니다."),
                new Experience("05", "취업·사후관리", "직무 로드맵과 상담으로 수료 후의 성장까지 연결합니다.")
        ));
        model.addAttribute("curriculum", List.of(
                new Curriculum("FOUNDATION", "AI 기초와 문제 정의", "01–04주", "Python · 데이터 리터러시 · 생성형 AI 활용"),
                new Curriculum("BUILD", "서비스 구현", "05–12주", "백엔드 · 데이터 파이프라인 · 모델 연동"),
                new Curriculum("PROJECT", "현업 프로젝트", "13–20주", "팀 협업 · 멘토 리뷰 · 배포 · 발표"),
                new Curriculum("CAREER", "커리어 완성", "21–24주", "포트폴리오 · 기술면접 · 직무 매칭")
        ));
        model.addAttribute("projects", List.of(
                new Project("01", "업무 지식 AI 어시스턴트", "RAG 기반으로 조직의 문서를 탐색하고 답하는 서비스를 설계합니다.", List.of("RAG", "LLM", "API")),
                new Project("02", "고객 데이터 인사이트", "분산된 고객 데이터를 정리해 행동 패턴과 실행 가능한 인사이트를 찾습니다.", List.of("Python", "SQL", "Dashboard")),
                new Project("03", "AI 서비스 프로토타입", "사용자 문제부터 배포 가능한 MVP까지 팀 단위로 완성합니다.", List.of("Product", "Cloud", "Team"))
        ));
    }

    public record Course(String title, String category, String duration, String format,
                         String description, String status, String tone) {}
    public record Experience(String number, String title, String description) {}
    public record Curriculum(String phase, String title, String weeks, String topics) {}
    public record Project(String number, String title, String description, List<String> tags) {}
}
