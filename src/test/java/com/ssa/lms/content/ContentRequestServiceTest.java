package com.ssa.lms.content;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.content.entity.ContentLibraryItem;
import com.ssa.lms.content.entity.ContentLibraryStatus;
import com.ssa.lms.content.entity.ContentType;
import com.ssa.lms.content.repository.ContentLibraryItemRepository;
import com.ssa.lms.content.request.ContentRequestDecisionForm;
import com.ssa.lms.content.request.ContentRequestForm;
import com.ssa.lms.content.request.ContentRequestService;
import com.ssa.lms.content.request.ContentRequestStatus;
import com.ssa.lms.course.entity.EnrollmentStatus;
import com.ssa.lms.course.repository.EnrollmentRepository;
import com.ssa.lms.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ContentRequestServiceTest {

    @Autowired ContentRequestService service;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired UserRepository userRepository;
    @Autowired ContentLibraryItemRepository libraryItemRepository;

    @Test
    @DisplayName("훈련생 요청을 접수하고 공용 콘텐츠를 과정에 제공한다")
    void createAndFulfill() {
        var trainee = userRepository.findByLoginId("trainee1").orElseThrow();
        var enrollment = enrollmentRepository.findByTraineeIdOrderByAppliedAtDesc(trainee.getId()).stream()
                .filter(row -> row.getStatus() == EnrollmentStatus.APPROVED || row.getStatus() == EnrollmentStatus.COMPLETED)
                .findFirst().orElseThrow();
        ContentRequestForm request = new ContentRequestForm();
        request.setCourseId(enrollment.getCourse().getId());
        request.setPreferredType(ContentType.DOCUMENT);
        request.setTitle("추가 실습 자료");
        request.setReason("수업 내용을 복습할 예제가 필요합니다.");
        Long requestId = service.create(trainee.getId(), request);

        ContentLibraryItem item = libraryItemRepository.save(ContentLibraryItem.builder()
                .type(ContentType.DOCUMENT).title("복습 실습지").description("단계별 실습")
                .fileUrl("/uploads/test-review.pdf").originalFileName("review.pdf")
                .mimeType("application/pdf").status(ContentLibraryStatus.PUBLISHED).build());
        var admin = userRepository.findByLoginId("admin").orElseThrow();
        ContentRequestDecisionForm decision = new ContentRequestDecisionForm();
        decision.setLibraryItemId(item.getId());
        decision.setOrderNo(99);
        decision.setNote("요청한 보충 자료를 배치했습니다.");
        service.fulfill(requestId, decision, new LoginUser(admin));

        var result = service.mine(trainee.getId()).stream().filter(row -> row.id().equals(requestId)).findFirst().orElseThrow();
        assertThat(result.status()).isEqualTo(ContentRequestStatus.FULFILLED);
        assertThat(result.fulfilledTitle()).isEqualTo("복습 실습지");
        assertThat(result.decisionNote()).contains("배치");
    }
}
