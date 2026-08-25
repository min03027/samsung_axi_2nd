package com.ssa.lms.demand;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.CourseStatus;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class DemandSignalServiceTest {

    @Autowired DemandSignalService service;
    @Autowired CourseRepository courseRepository;
    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("산업수요 키워드와 일치하는 과정을 추천하고 검토 상태를 저장한다")
    void recommendAndReview() {
        Course course = courseRepository.save(Course.builder()
                .courseCode("TEST-DEMAND-" + System.nanoTime()).courseName("RAG 업무 자동화")
                .cohort("테스트").category("RAG").description("RAG LLM 실무 적용")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(1))
                .capacity(20).status(CourseStatus.DRAFT).build());
        DemandSignalForm form = new DemandSignalForm();
        form.setTitle("RAG 개발 수요 증가");
        form.setIndustry("IT 서비스");
        form.setJobRole("AI 개발");
        form.setSkills("RAG, LLM");
        form.setDemandScore(90);
        form.setObservedOn(LocalDate.now());
        form.setSourceName("기업 인터뷰");

        Long signalId = service.create(form);
        var recommendation = service.recommendations(signalId).stream()
                .filter(row -> row.courseName().equals(course.getCourseName())).findFirst().orElseThrow();
        Long adminId = userRepository.findByLoginId("admin").orElseThrow().getId();
        service.review(signalId, recommendation.id(), DemandRecommendationStatus.APPLIED, "다음 개편에 반영", adminId);

        var reviewed = service.recommendations(signalId).stream()
                .filter(row -> row.id().equals(recommendation.id())).findFirst().orElseThrow();
        assertThat(reviewed.status()).isEqualTo(DemandRecommendationStatus.APPLIED);
        assertThat(reviewed.matchedKeywords()).contains("rag");
        assertThat(reviewed.reviewNote()).contains("개편");
    }
}
