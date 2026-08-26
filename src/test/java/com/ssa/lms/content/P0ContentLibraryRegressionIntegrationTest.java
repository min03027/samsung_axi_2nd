package com.ssa.lms.content;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.config.P0DemoDataInitializer;
import com.ssa.lms.content.entity.ContentLibraryStatus;
import com.ssa.lms.content.entity.ContentType;
import com.ssa.lms.content.repository.ContentLibraryItemRepository;
import com.ssa.lms.content.repository.ContentLibraryLinkRepository;
import com.ssa.lms.content.repository.ContentLibraryVersionRepository;
import com.ssa.lms.content.repository.ContentRepository;
import com.ssa.lms.content.repository.ProgressRepository;
import com.ssa.lms.content.service.ContentLibraryService;
import com.ssa.lms.content.service.ProgressService;
import com.ssa.lms.content.web.ContentLibraryDeployForm;
import com.ssa.lms.content.web.ContentLibraryForm;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.CourseStatus;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "lms.demo.seed-data=true",
        "lms.demo.password=test-only-password",
        "lms.content.upload-dir=${java.io.tmpdir}/axi-p0-content-library-tests"
})
@ActiveProfiles("demo")
@AutoConfigureMockMvc
@Transactional
class P0ContentLibraryRegressionIntegrationTest {

    @Autowired ContentLibraryService libraryService;
    @Autowired ProgressService progressService;
    @Autowired ContentLibraryItemRepository itemRepository;
    @Autowired ContentLibraryVersionRepository versionRepository;
    @Autowired ContentLibraryLinkRepository linkRepository;
    @Autowired ContentRepository contentRepository;
    @Autowired ProgressRepository progressRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;

    @Test
    void originalDeployVersionSyncAndTraineeReadUsePersistedDemoData() {
        var instructorEntity = userRepository.findByLoginId(P0DemoDataInitializer.INSTRUCTOR_LOGIN_ID).orElseThrow();
        var trainee = userRepository.findByLoginId(P0DemoDataInitializer.TRAINEE_LOGIN_ID).orElseThrow();
        var course = courseRepository.findByCourseCode(P0DemoDataInitializer.COURSE_CODE).orElseThrow();
        LoginUser instructor = new LoginUser(instructorEntity);

        long itemCountBefore = itemRepository.count();
        ContentLibraryForm v1 = form("[DEMO] 데이터 전처리 가이드", "최초 문서 등록");
        MockMultipartFile document = new MockMultipartFile(
                "file", "demo-preprocessing-guide.txt", "text/plain",
                "DEMO ONLY: preprocessing guide v1".getBytes());

        Long itemId = libraryService.create(v1, document);
        entityManager.flush();
        entityManager.clear();

        assertThat(itemRepository.count()).isEqualTo(itemCountBefore + 1);
        assertThat(itemRepository.findById(itemId)).get()
                .satisfies(item -> {
                    assertThat(item.getCurrentVersion()).isEqualTo(1);
                    assertThat(item.getTitle()).isEqualTo(v1.getTitle());
                    assertThat(item.getOriginalFileName()).isEqualTo("demo-preprocessing-guide.txt");
                });
        assertThat(versionRepository.findByLibraryItemIdOrderByVersionNoDesc(itemId))
                .singleElement().satisfies(version -> {
                    assertThat(version.getVersionNo()).isEqualTo(1);
                    assertThat(version.getChangeSummary()).isEqualTo("최초 문서 등록");
                });

        long contentCountBefore = contentRepository.count();
        ContentLibraryDeployForm deploy = deploy(course.getId(), false);
        Long contentId = libraryService.deploy(itemId, deploy, instructor);
        entityManager.flush();
        entityManager.clear();

        var link = linkRepository.findByContentId(contentId).orElseThrow();
        Long linkId = link.getId();
        assertThat(contentRepository.count()).isEqualTo(contentCountBefore + 1);
        assertThat(link.getLibraryItem().getId()).isEqualTo(itemId);
        assertThat(link.getContent().getCourse().getId()).isEqualTo(course.getId());
        assertThat(link.getAppliedVersion()).isEqualTo(1);
        assertThat(link.isAutoSync()).isFalse();

        long linkCount = linkRepository.count();
        assertThatThrownBy(() -> libraryService.deploy(itemId, deploy, instructor))
                .isInstanceOf(IllegalStateException.class);
        assertThat(contentRepository.count()).isEqualTo(contentCountBefore + 1);
        assertThat(linkRepository.count()).isEqualTo(linkCount);

        progressService.complete(trainee.getId(), contentId);
        Long progressId = progressRepository.findByUserIdAndContentId(trainee.getId(), contentId)
                .orElseThrow().getId();

        ContentLibraryForm v2 = libraryService.editForm(itemId);
        v2.setTitle("[DEMO] 데이터 전처리 가이드 v2");
        v2.setDescription("결측치 처리 예제와 실습 설명을 보강했습니다.");
        v2.setChangeSummary("전처리 실습 설명 및 예제 수정");
        int autoSynced = libraryService.publish(itemId, v2, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(autoSynced).isZero();
        assertThat(versionRepository.findByLibraryItemIdOrderByVersionNoDesc(itemId))
                .extracting(version -> version.getVersionNo()).containsExactly(2, 1);
        assertThat(versionRepository.findByLibraryItemIdOrderByVersionNoDesc(itemId).get(0).getChangeSummary())
                .isEqualTo("전처리 실습 설명 및 예제 수정");
        assertThat(libraryService.links(itemId)).singleElement().satisfies(view -> {
            assertThat(view.appliedVersion()).isEqualTo(1);
            assertThat(view.currentVersion()).isEqualTo(2);
            assertThat(view.updateAvailable()).isTrue();
        });
        assertThat(contentRepository.findById(contentId).orElseThrow().getTitle()).isEqualTo(v1.getTitle());

        long versionCount = versionRepository.count();
        libraryService.syncNow(itemId, linkId, instructor);
        libraryService.syncNow(itemId, linkId, instructor);
        entityManager.flush();
        entityManager.clear();

        var syncedLink = linkRepository.findById(linkId).orElseThrow();
        assertThat(syncedLink.getAppliedVersion()).isEqualTo(2);
        assertThat(contentRepository.findById(contentId).orElseThrow().getTitle()).isEqualTo(v2.getTitle());
        assertThat(linkRepository.count()).isEqualTo(linkCount);
        assertThat(contentRepository.count()).isEqualTo(contentCountBefore + 1);
        assertThat(versionRepository.count()).isEqualTo(versionCount);
        assertThat(progressRepository.findByUserIdAndContentId(trainee.getId(), contentId)).get()
                .satisfies(progress -> {
                    assertThat(progress.getId()).isEqualTo(progressId);
                    assertThat(progress.isCompleted()).isTrue();
                    assertThat(progress.getProgressRate()).isEqualTo(100);
                });
        assertThat(progressService.myLearningContents(trainee.getId()))
                .anySatisfy(content -> {
                    assertThat(content.contentId()).isEqualTo(contentId);
                    assertThat(content.title()).isEqualTo(v2.getTitle());
                });
    }

    @Test
    void instructorCanDeployOnlyToAssignedCoursesWhileAdminCanSeeAll() {
        LoginUser instructor = new LoginUser(userRepository
                .findByLoginId(P0DemoDataInitializer.INSTRUCTOR_LOGIN_ID).orElseThrow());
        LoginUser admin = new LoginUser(userRepository
                .findByLoginId(P0DemoDataInitializer.ADMIN_LOGIN_ID).orElseThrow());
        Course demoCourse = courseRepository.findByCourseCode(P0DemoDataInitializer.COURSE_CODE).orElseThrow();
        Course unassigned = courseRepository.save(Course.builder()
                .courseCode("P0-UNASSIGNED-001").courseName("[TEST] 비담당 과정")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(30))
                .capacity(10).status(CourseStatus.DRAFT).build());
        Long itemId = libraryService.create(form("[DEMO] 권한 검증 문서", "권한 검증용 v1"),
                new MockMultipartFile("file", "permission.txt", "text/plain", "permission".getBytes()));

        assertThat(libraryService.courseOptions(instructor)).extracting(Course::getId)
                .containsExactly(demoCourse.getId());
        assertThat(libraryService.courseOptions(admin)).extracting(Course::getId)
                .contains(demoCourse.getId(), unassigned.getId());
        assertThatThrownBy(() -> libraryService.deploy(itemId, deploy(unassigned.getId(), false), instructor))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(linkRepository.countByLibraryItemId(itemId)).isZero();
    }

    @Test
    void demoInstructorAndTraineeOpenTheRealLibraryAndContentRoutes() throws Exception {
        MockHttpSession instructorSession = login(P0DemoDataInitializer.INSTRUCTOR_LOGIN_ID, "/instructor");
        mvc.perform(get("/instructor/content-library").session(instructorSession))
                .andExpect(status().isOk());

        MockHttpSession traineeSession = login(P0DemoDataInitializer.TRAINEE_LOGIN_ID, "/trainee");
        mvc.perform(get("/trainee/contents").session(traineeSession))
                .andExpect(status().isOk());
    }

    private MockHttpSession login(String loginId, String redirect) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", loginId).param("password", "test-only-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(redirect))
                .andReturn().getRequest().getSession(false);
    }

    private ContentLibraryForm form(String title, String changeSummary) {
        ContentLibraryForm form = new ContentLibraryForm();
        form.setType(ContentType.DOCUMENT);
        form.setTitle(title);
        form.setDescription("P0 콘텐츠 라이브러리 회귀 검증용 자체 제작 문서입니다.");
        form.setPageCount(4);
        form.setIndustryTags("AI, 데이터 전처리");
        form.setStatus(ContentLibraryStatus.PUBLISHED);
        form.setChangeSummary(changeSummary);
        return form;
    }

    private ContentLibraryDeployForm deploy(Long courseId, boolean autoSync) {
        ContentLibraryDeployForm form = new ContentLibraryDeployForm();
        form.setCourseId(courseId);
        form.setOrderNo(90);
        form.setRequired(true);
        form.setAutoSync(autoSync);
        return form;
    }
}
