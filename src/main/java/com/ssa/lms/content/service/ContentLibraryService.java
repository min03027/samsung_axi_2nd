package com.ssa.lms.content.service;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.content.entity.*;
import com.ssa.lms.content.repository.ContentLibraryItemRepository;
import com.ssa.lms.content.repository.ContentLibraryLinkRepository;
import com.ssa.lms.content.repository.ContentLibraryVersionRepository;
import com.ssa.lms.content.repository.ContentRepository;
import com.ssa.lms.content.web.*;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.Session;
import com.ssa.lms.course.repository.CourseInstructorRepository;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.SessionRepository;
import com.ssa.lms.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 공용 콘텐츠 라이브러리, 과정 배포, 원본 버전 발행과 자동 동기화를 하나의 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentLibraryService {

    private final ContentLibraryItemRepository itemRepository;
    private final ContentLibraryVersionRepository versionRepository;
    private final ContentLibraryLinkRepository linkRepository;
    private final ContentRepository contentRepository;
    private final CourseRepository courseRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final SessionRepository sessionRepository;
    private final FileStorageService fileStorageService;

    public List<ContentLibraryItemView> list(String keyword, ContentType type, ContentLibraryStatus status) {
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return itemRepository.findAllByOrderByUpdatedAtDescIdDesc().stream()
                .filter(item -> type == null || item.getType() == type)
                .filter(item -> status == null || item.getStatus() == status)
                .filter(item -> needle.isEmpty()
                        || contains(item.getTitle(), needle)
                        || contains(item.getDescription(), needle)
                        || contains(item.getIndustryTags(), needle)
                        || contains(item.getOriginalFileName(), needle))
                .map(this::toItemView)
                .toList();
    }

    public ContentLibraryDashboard dashboard() {
        List<ContentLibraryItem> items = itemRepository.findAll();
        long published = items.stream().filter(i -> i.getStatus() == ContentLibraryStatus.PUBLISHED).count();
        long links = items.stream().mapToLong(i -> linkRepository.countByLibraryItemId(i.getId())).sum();
        return new ContentLibraryDashboard(items.size(), published, links, versionRepository.count());
    }

    public ContentLibraryItem get(Long id) {
        return itemRepository.findById(id).orElseThrow(() -> new ContentLibraryNotFoundException(id));
    }

    public ContentLibraryItemView view(Long id) {
        return toItemView(get(id));
    }

    public ContentLibraryForm editForm(Long id) {
        return ContentLibraryForm.from(get(id));
    }

    public List<ContentLibraryVersionView> versions(Long libraryItemId) {
        return versionRepository.findByLibraryItemIdOrderByVersionNoDesc(libraryItemId).stream()
                .map(ContentLibraryVersionView::of)
                .toList();
    }

    public List<ContentLibraryVersionView> allVersions() {
        return versionRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(ContentLibraryVersionView::of)
                .toList();
    }

    public List<ContentLibraryLinkView> links(Long libraryItemId) {
        return linkRepository.findByLibraryItemIdOrderByIdAsc(libraryItemId).stream()
                .map(ContentLibraryLinkView::of)
                .toList();
    }

    public List<Course> courseOptions(LoginUser actor) {
        List<Course> courses = courseRepository.findAllByOrderByStartDateDesc();
        if (actor.getRole() == Role.ADMIN) {
            return courses;
        }
        Set<Long> assignedCourseIds = Set.copyOf(
                courseInstructorRepository.findCourseIdsByInstructorId(actor.getId()));
        return courses.stream().filter(course -> assignedCourseIds.contains(course.getId())).toList();
    }

    public List<SessionOption> sessionOptions(LoginUser actor) {
        return courseOptions(actor).stream()
                .flatMap(course -> sessionRepository
                        .findBySubjectCourseIdOrderBySubjectOrderNoAscSeqAsc(course.getId()).stream()
                        .map(session -> new SessionOption(session.getId(), course.getId(),
                                session.getSeq() + "차시 - " + session.getName())))
                .toList();
    }

    /** 공용 원본을 새로 등록하고 v1 스냅샷을 남긴다. */
    @Transactional
    public Long create(ContentLibraryForm form, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("공용 원본 파일을 첨부하세요.");
        }
        StoredFile stored = fileStorageService.store(file);
        ContentLibraryItem item = itemRepository.save(ContentLibraryItem.builder()
                .type(form.getType())
                .title(form.getTitle().trim())
                .description(trimToNull(form.getDescription()))
                .fileUrl(stored.fileUrl())
                .originalFileName(stored.originalFileName())
                .fileSize(stored.size())
                .mimeType(stored.mimeType())
                .durationSeconds(form.getDurationSeconds())
                .pageCount(form.getPageCount())
                .industryTags(normalizeTags(form.getIndustryTags()))
                .status(form.getStatus())
                .build());
        versionRepository.save(ContentLibraryVersion.snapshot(item,
                summaryOrDefault(form.getChangeSummary(), "공용 원본 최초 등록"), 0));
        return item.getId();
    }

    /** 기존 과정 콘텐츠를 공용 원본으로 승격하고 기존 콘텐츠는 첫 연결로 유지한다. */
    @Transactional
    public Long promoteExisting(Long contentId, LoginUser actor) {
        return linkRepository.findByContentId(contentId)
                .map(link -> {
                    assertCourseAccess(link.getContent().getCourse(), actor);
                    return link.getLibraryItem().getId();
                })
                .orElseGet(() -> promote(contentId, actor));
    }

    private Long promote(Long contentId, LoginUser actor) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
        assertCourseAccess(content.getCourse(), actor);
        ContentLibraryItem item = itemRepository.save(ContentLibraryItem.builder()
                .type(content.getType())
                .title(content.getTitle())
                .description(content.getDescription())
                .fileUrl(content.getFileUrl())
                .originalFileName(content.getOriginalFileName())
                .fileSize(content.getFileSize())
                .mimeType(content.getMimeType())
                .durationSeconds(content.getDurationSeconds())
                .pageCount(content.getPageCount())
                .status(ContentLibraryStatus.PUBLISHED)
                .build());
        linkRepository.save(ContentLibraryLink.builder()
                .libraryItem(item)
                .content(content)
                .autoSync(true)
                .appliedVersion(item.getCurrentVersion())
                .build());
        versionRepository.save(ContentLibraryVersion.snapshot(item,
                "과정 콘텐츠에서 공용 원본 생성", 1));
        return item.getId();
    }

    /**
     * 새 버전을 발행한다. PUBLISHED 상태이면 자동 동기화 연결의 과정 콘텐츠를 같은 트랜잭션에서 갱신한다.
     */
    @Transactional
    public int publish(Long id, ContentLibraryForm form, MultipartFile file) {
        ContentLibraryItem item = get(id);
        String fileUrl = item.getFileUrl();
        String originalName = item.getOriginalFileName();
        Long fileSize = item.getFileSize();
        String mimeType = item.getMimeType();
        if (file != null && !file.isEmpty()) {
            StoredFile stored = fileStorageService.store(file);
            fileUrl = stored.fileUrl();
            originalName = stored.originalFileName();
            fileSize = stored.size();
            mimeType = stored.mimeType();
            // 과거 버전에서 이전 파일을 참조하므로 즉시 삭제하지 않는다.
        }

        int versionNo = item.publish(form.getType(), form.getTitle().trim(), trimToNull(form.getDescription()),
                fileUrl, originalName, fileSize, mimeType,
                form.getDurationSeconds(), form.getPageCount(), normalizeTags(form.getIndustryTags()),
                form.getStatus());

        int synced = 0;
        if (item.getStatus() == ContentLibraryStatus.PUBLISHED) {
            List<ContentLibraryLink> links = linkRepository
                    .findByLibraryItemIdAndAutoSyncTrueOrderByIdAsc(item.getId());
            for (ContentLibraryLink link : links) {
                apply(item, link);
                synced += 1;
            }
        }
        versionRepository.save(ContentLibraryVersion.snapshot(item,
                summaryOrDefault(form.getChangeSummary(), "원본 콘텐츠 업데이트"), synced));
        return synced;
    }

    /** 공용 원본을 과정에 새 콘텐츠로 배치하고 동기화 연결을 만든다. */
    @Transactional
    public Long deploy(Long libraryItemId, ContentLibraryDeployForm form, LoginUser actor) {
        ContentLibraryItem item = get(libraryItemId);
        if (item.getStatus() == ContentLibraryStatus.ARCHIVED) {
            throw new IllegalStateException("보관된 원본은 과정에 배치할 수 없습니다.");
        }
        Course course = courseRepository.findById(form.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("과정을 찾을 수 없습니다: " + form.getCourseId()));
        assertCourseAccess(course, actor);
        Session session = resolveSession(course, form.getSessionId());
        ensureNotDuplicated(libraryItemId, course.getId(), session != null ? session.getId() : null);

        Content content = contentRepository.save(Content.builder()
                .course(course)
                .session(session)
                .type(item.getType())
                .title(item.getTitle())
                .description(item.getDescription())
                .fileUrl(item.getFileUrl())
                .originalFileName(item.getOriginalFileName())
                .fileSize(item.getFileSize())
                .mimeType(item.getMimeType())
                .durationSeconds(item.getDurationSeconds())
                .pageCount(item.getPageCount())
                .orderNo(form.getOrderNo())
                .required(form.getRequired())
                .status(item.getStatus() == ContentLibraryStatus.PUBLISHED
                        ? ContentStatus.ACTIVE : ContentStatus.ARCHIVED)
                .build());
        linkRepository.save(ContentLibraryLink.builder()
                .libraryItem(item)
                .content(content)
                .autoSync(form.getAutoSync())
                .appliedVersion(item.getCurrentVersion())
                .build());
        return content.getId();
    }

    /** 수동 연결 또는 누락된 연결 하나를 현재 원본 버전으로 즉시 맞춘다. */
    @Transactional
    public void syncNow(Long libraryItemId, Long linkId, LoginUser actor) {
        ContentLibraryItem item = get(libraryItemId);
        ContentLibraryLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠 연결을 찾을 수 없습니다: " + linkId));
        if (!link.getLibraryItem().getId().equals(item.getId())) {
            throw new IllegalArgumentException("해당 원본의 콘텐츠 연결이 아닙니다.");
        }
        assertCourseAccess(link.getContent().getCourse(), actor);
        apply(item, link);
    }

    @Transactional
    public void changeAutoSync(Long libraryItemId, Long linkId, boolean autoSync, LoginUser actor) {
        ContentLibraryItem item = get(libraryItemId);
        ContentLibraryLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠 연결을 찾을 수 없습니다: " + linkId));
        if (!link.getLibraryItem().getId().equals(item.getId())) {
            throw new IllegalArgumentException("해당 원본의 콘텐츠 연결이 아닙니다.");
        }
        assertCourseAccess(link.getContent().getCourse(), actor);
        link.changeAutoSync(autoSync);
        if (autoSync && link.getAppliedVersion() < item.getCurrentVersion()) {
            apply(item, link);
        }
    }

    @Transactional
    public void archive(Long id) {
        get(id).changeStatus(ContentLibraryStatus.ARCHIVED);
    }

    private void apply(ContentLibraryItem item, ContentLibraryLink link) {
        link.getContent().syncFromLibrary(item.getType(), item.getTitle(), item.getDescription(),
                item.getFileUrl(), item.getOriginalFileName(), item.getFileSize(), item.getMimeType(),
                item.getDurationSeconds(), item.getPageCount());
        link.markSynced(item.getCurrentVersion());
    }

    private void assertCourseAccess(Course course, LoginUser actor) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (actor.getRole() != Role.INSTRUCTOR
                || !courseInstructorRepository.existsByCourseIdAndInstructorId(course.getId(), actor.getId())) {
            throw new AccessDeniedException("담당 과정의 콘텐츠만 배치하거나 동기화할 수 있습니다.");
        }
    }

    private Session resolveSession(Course course, Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("차시를 찾을 수 없습니다: " + sessionId));
        if (!session.getSubject().getCourse().getId().equals(course.getId())) {
            throw new IllegalArgumentException("선택한 과정에 속한 차시가 아닙니다.");
        }
        return session;
    }

    private void ensureNotDuplicated(Long libraryItemId, Long courseId, Long sessionId) {
        boolean duplicated = linkRepository.findByLibraryItemIdOrderByIdAsc(libraryItemId).stream()
                .map(ContentLibraryLink::getContent)
                .anyMatch(content -> content.getCourse().getId().equals(courseId)
                        && equalIds(content.getSession() != null ? content.getSession().getId() : null, sessionId));
        if (duplicated) {
            throw new IllegalStateException("같은 원본이 이미 선택한 과정·차시에 연결되어 있습니다.");
        }
    }

    private ContentLibraryItemView toItemView(ContentLibraryItem item) {
        return ContentLibraryItemView.of(item,
                linkRepository.countByLibraryItemId(item.getId()),
                linkRepository.countByLibraryItemIdAndAutoSyncTrue(item.getId()));
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean equalIds(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return null;
        }
        return String.join(", ", List.of(tags.split(",")).stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList());
    }

    private String summaryOrDefault(String summary, String fallback) {
        return summary == null || summary.isBlank() ? fallback : summary.trim();
    }
}
