package com.ssa.lms.notice.service;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.course.repository.CourseInstructorRepository;
import com.ssa.lms.notice.dto.NoticeDetail;
import com.ssa.lms.notice.dto.NoticeForm;
import com.ssa.lms.notice.dto.NoticeListRow;
import com.ssa.lms.notice.dto.NoticeSearchCond;
import com.ssa.lms.notice.entity.Notice;
import com.ssa.lms.notice.entity.NoticeCategory;
import com.ssa.lms.notice.repository.NoticeCategoryRepository;
import com.ssa.lms.notice.repository.NoticeRepository;
import com.ssa.lms.user.entity.User;
import com.ssa.lms.user.entity.Role;
import com.ssa.lms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

/**
 * 공지사항 서비스.
 *
 * 권한: SecurityConfig 의 /admin/notice/** → ADMIN, INSTRUCTOR.
 * 훈련생 조회는 /trainee/notice/** 로 열리며, 여기서는 게시된 공지만 내보낸다.
 *
 * open-in-view=false 이므로 화면에 넘길 값은 전부 이 트랜잭션 안에서 DTO 로 펼친다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    /** JPQL 의 in (:courseIds) 는 빈 컬렉션을 못 받는다. 절대 매칭되지 않는 값으로 채운다. */
    private static final List<Long> NO_COURSE = List.of(-1L);

    private final NoticeRepository noticeRepository;
    private final NoticeCategoryRepository noticeCategoryRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final NotificationService notificationService;

    /** 관리자 목록 — 고정 공지 먼저, 그 다음 최신순. 정렬은 Pageable 로 컨트롤러가 준다. */
    public Page<NoticeListRow> search(NoticeSearchCond cond, Pageable pageable) {
        return noticeRepository.search(
                        cond.categoryIdOrNull(), cond.fromOrNull(), cond.toOrNull(),
                        cond.keywordOrNull(), pageable)
                .map(NoticeListRow::of);
    }

    /** 훈련생/강사 목록 — 게시된 것만. courseIds 는 그 사용자가 볼 수 있는 과정 범위. */
    public Page<NoticeListRow> searchPublished(Collection<Long> courseIds, String keyword, Pageable pageable) {
        Collection<Long> ids = (courseIds == null || courseIds.isEmpty()) ? NO_COURSE : courseIds;
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return noticeRepository.searchPublished(LocalDateTime.now(), ids, kw, pageable)
                .map(NoticeListRow::of);
    }

    public List<NoticeCategory> activeCategories() {
        return noticeCategoryRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    public List<Course> selectableCourses() {
        return courseRepository.findAll();
    }

    /**
     * 상세 조회. increaseView=true 면 조회수를 1 올린다.
     *
     * 조회수 증가는 벌크 UPDATE 라 영속성 컨텍스트를 비운 뒤(clearAutomatically) 다시 읽어야
     * 화면에 증가된 값이 보인다. 그래서 UPDATE 를 먼저 하고 엔티티를 조회한다.
     */
    @Transactional
    public NoticeDetail getDetail(Long id, boolean increaseView) {
        if (increaseView) {
            noticeRepository.increaseViewCount(id);
        }
        Notice notice = noticeRepository.findWithDetailById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다. id=" + id));
        return NoticeDetail.of(notice,
                noticeRepository.findPrev(id).orElse(null),
                noticeRepository.findNext(id).orElse(null));
    }

    /** 수강/담당 과정 밖의 공지를 URL로 직접 열람하는 것을 막는다. */
    @Transactional
    public NoticeDetail getPublishedDetail(Long id, Collection<Long> courseIds, boolean increaseView) {
        Notice notice = noticeRepository.findWithDetailById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다. id=" + id));
        Collection<Long> ids = courseIds == null ? List.of() : courseIds;
        if (notice.getPublishedAt() == null || notice.getPublishedAt().isAfter(LocalDateTime.now())
                || (notice.getCourse() != null && !ids.contains(notice.getCourse().getId()))) {
            throw new AccessDeniedException("열람 권한이 없는 공지입니다.");
        }
        if (increaseView) {
            noticeRepository.increaseViewCount(id);
            notice = noticeRepository.findWithDetailById(id).orElseThrow();
        }
        return NoticeDetail.of(notice, noticeRepository.findPrev(id).orElse(null),
                noticeRepository.findNext(id).orElse(null));
    }

    public NoticeForm loadForm(Long id) {
        return NoticeForm.from(getOrThrow(id));
    }

    public Notice getOrThrow(Long id) {
        return noticeRepository.findWithDetailById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다. id=" + id));
    }

    @Transactional
    public Long create(NoticeForm form, Long authorId, Role role) {
        assertCanManageCourse(form.getCourseId(), authorId, role);
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("작성자를 찾을 수 없습니다. id=" + authorId));

        Notice notice = Notice.builder()
                .category(findCategory(form.getCategoryId()))
                .course(findCourse(form.getCourseId()))
                .title(form.getTitle())
                .content(form.getContent())
                .author(author)
                .pinned(form.isPinned())
                .publishedAt(form.isPublished() ? LocalDateTime.now() : null)
                .popupOnLogin(form.isPopupOnLogin())
                .emailNotify(form.isEmailNotify())
                .build();
        noticeRepository.save(notice);
        if (notice.getPublishedAt() != null) notificationService.dispatchNotice(notice);
        return notice.getId();
    }

    @Transactional
    public void update(Long id, NoticeForm form, Long actorId, Role role) {
        Notice notice = getOrThrow(id);
        assertCanManageCourse(notice.getCourse() == null ? null : notice.getCourse().getId(), actorId, role);
        assertCanManageCourse(form.getCourseId(), actorId, role);
        boolean wasPublished = notice.getPublishedAt() != null;
        boolean popupWasEnabled = notice.isPopupOnLogin();
        boolean emailWasEnabled = notice.isEmailNotify();
        notice.update(findCategory(form.getCategoryId()), form.getTitle(), form.getContent(), form.isPinned(),
                form.isPopupOnLogin(), form.isEmailNotify());
        notice.changeCourse(findCourse(form.getCourseId()));
        if (form.isPublished()) {
            notice.publish(LocalDateTime.now());
        } else {
            notice.unpublish();
        }
        if (!wasPublished && notice.getPublishedAt() != null) {
            notificationService.dispatchNotice(notice);
        } else if (notice.getPublishedAt() != null) {
            notificationService.synchronizeNotice(notice,
                    !popupWasEnabled && notice.isPopupOnLogin(),
                    !emailWasEnabled && notice.isEmailNotify());
        } else if (wasPublished) {
            notificationService.withdrawNotice(notice.getId());
        }
    }

    /** 서버 재시작 시 기존 게시 공지 중 팝업 지정 건을 수신자 데이터와 한 번 동기화한다. */
    @Transactional
    public int synchronizePublishedPopups() {
        List<Notice> popups = noticeRepository.findPublishedLoginPopups(LocalDateTime.now());
        popups.forEach(notice -> notificationService.synchronizeNotice(notice, false, false));
        return popups.size();
    }

    /**
     * 삭제. Notice 에 @SQLDelete 가 걸려 있어 물리 삭제가 아니라 is_deleted 마킹만 된다
     * (내역서 3년 보존 요건 — CLAUDE.md 코드 컨벤션).
     */
    @Transactional
    public void delete(List<Long> ids, Long actorId, Role role) {
        for (Long id : ids) {
            Notice notice = getOrThrow(id);
            assertCanManageCourse(notice.getCourse() == null ? null : notice.getCourse().getId(), actorId, role);
        }
        noticeRepository.deleteAllById(ids);
    }

    /* ===== 내부 ===== */

    private NoticeCategory findCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return noticeCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("공지 분류를 찾을 수 없습니다. id=" + categoryId));
    }

    private Course findCourse(Long courseId) {
        if (courseId == null) {
            return null;
        }
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("과정을 찾을 수 없습니다. id=" + courseId));
    }

    private void assertCanManageCourse(Long courseId, Long actorId, Role role) {
        if (role == Role.ADMIN) return;
        if (role != Role.INSTRUCTOR || courseId == null
                || !courseInstructorRepository.existsByCourseIdAndInstructorId(courseId, actorId)) {
            throw new AccessDeniedException("담당 과정의 공지만 관리할 수 있습니다.");
        }
    }
}
