package com.ssa.lms.content;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.content.entity.Content;
import com.ssa.lms.content.repository.ContentLibraryLinkRepository;
import com.ssa.lms.content.repository.ContentRepository;
import com.ssa.lms.content.service.ContentLibraryService;
import com.ssa.lms.content.web.ContentLibraryForm;
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
class ContentLibraryServiceTest {

    @Autowired ContentLibraryService libraryService;
    @Autowired ContentRepository contentRepository;
    @Autowired ContentLibraryLinkRepository linkRepository;
    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("기존 과정 콘텐츠를 원본으로 승격하고 새 버전을 자동 반영한다")
    void promoteAndPublishAutoSync() {
        Content content = contentRepository.findAll().get(0);
        LoginUser admin = new LoginUser(userRepository.findByLoginId("admin").orElseThrow());
        Long libraryId = libraryService.promoteExisting(content.getId(), admin);

        ContentLibraryForm form = libraryService.editForm(libraryId);
        form.setTitle("업데이트된 공용 콘텐츠");
        form.setChangeSummary("현업 수요를 반영한 실습 개편");

        int synced = libraryService.publish(libraryId, form, null);

        Content updated = contentRepository.findById(content.getId()).orElseThrow();
        var link = linkRepository.findByContentId(content.getId()).orElseThrow();
        assertThat(synced).isEqualTo(1);
        assertThat(updated.getTitle()).isEqualTo("업데이트된 공용 콘텐츠");
        assertThat(link.getAppliedVersion()).isEqualTo(2);
        assertThat(libraryService.versions(libraryId)).hasSize(2);
        assertThat(libraryService.versions(libraryId).get(0).changeSummary())
                .isEqualTo("현업 수요를 반영한 실습 개편");
    }
}
