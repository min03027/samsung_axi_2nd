package com.ssa.lms.course.web;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.entity.CourseStatus;
import com.ssa.lms.course.entity.PublicCourseCategory;
import com.ssa.lms.course.entity.PublicationSite;
import com.ssa.lms.course.entity.RecruitmentStatus;
import com.ssa.lms.course.service.CourseInstructorService;
import com.ssa.lms.course.service.CourseService;
import com.ssa.lms.course.service.CurriculumService;
import com.ssa.lms.course.service.DuplicateCourseCodeException;
import com.ssa.lms.course.service.EnrollmentService;
import com.ssa.lms.course.service.RecruitmentReadinessException;
import com.ssa.lms.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 관리자 과정 관리 — 목록/상세/등록/수정/삭제/상태변경.
 * 경로 {@code /admin/courses/**} 는 SecurityConfig 의 {@code /admin/**} (ADMIN) 규칙으로 커버된다.
 */
@Controller
@RequestMapping("/admin/courses")
@RequiredArgsConstructor
public class CourseAdminController {

    private static final String VIEW_DIR = "admin/admin-03-courses/";

    private final CourseService courseService;
    private final CurriculumService curriculumService;
    private final CourseInstructorService courseInstructorService;
    private final EnrollmentService enrollmentService;
    private final OrganizationService organizationService;

    /** 과정 목록 + 상단 통계. */
    @GetMapping
    public String list(Model model) {
        List<Course> courses = courseService.findAll();
        model.addAttribute("courses", courses);
        model.addAttribute("subjectCounts", courseService.subjectCounts(courses));
        model.addAttribute("totalCount", courses.size());
        model.addAttribute("inProgressCount",
                courses.stream().filter(c -> c.getStatus() == CourseStatus.IN_PROGRESS).count());
        model.addAttribute("recruitingCount",
                courses.stream().filter(c -> c.getStatus() == CourseStatus.RECRUITING).count());
        return VIEW_DIR + "admin-courses-edu";
    }

    /** 과정 등록 폼. */
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("courseForm", new CourseForm());
        prepareFormModel(model, "create", null);
        return VIEW_DIR + "admin-courses-edu-add";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute CourseForm courseForm,
                         BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, "create", null);
            return VIEW_DIR + "admin-courses-edu-add";
        }
        try {
            Long id = courseService.create(courseForm);
            return "redirect:/admin/courses/" + id;
        } catch (DuplicateCourseCodeException e) {
            bindingResult.rejectValue("courseCode", "duplicate", e.getMessage());
            prepareFormModel(model, "create", null);
            return VIEW_DIR + "admin-courses-edu-add";
        }
    }

    /** 과정 상세. */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("course", courseService.get(id));
        model.addAttribute("subjects", curriculumService.curriculum(id));
        model.addAttribute("instructors", courseInstructorService.instructorsOf(id));
        model.addAttribute("availableInstructors", courseInstructorService.availableInstructors());
        model.addAttribute("enrollments", enrollmentService.enrollmentsOf(id));
        model.addAttribute("statuses", CourseStatus.values());
        model.addAttribute("subjectForm", new SubjectForm());
        model.addAttribute("sessionForm", new SessionForm());
        var publication = courseService.publicationOf(id).orElse(null);
        model.addAttribute("publication", publication);
        model.addAttribute("publicationStatus", publication == null
                ? RecruitmentStatus.PRE_CONSULTATION : publication.getRecruitmentStatus());
        model.addAttribute("readinessMissing", courseService.recruitmentReadiness(id));
        model.addAttribute("nextRecruitmentStatus", courseService.nextRecruitmentStatus(id));
        return VIEW_DIR + "courses-detail";
    }

    /** 과정 수정 폼. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("courseForm", courseService.formForEdit(id));
        model.addAttribute("courseId", id);
        prepareFormModel(model, "edit", id);
        return VIEW_DIR + "admin-courses-edu-update";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute CourseForm courseForm,
                         BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("courseId", id);
            prepareFormModel(model, "edit", id);
            return VIEW_DIR + "admin-courses-edu-update";
        }
        courseService.update(id, courseForm);
        return "redirect:/admin/courses/" + id;
    }

    /** 상태 변경 (목록/상세의 상태 셀렉트). */
    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam CourseStatus status) {
        courseService.changeStatus(id, status);
        return "redirect:/admin/courses/" + id;
    }

    /** 홈페이지 모집 상태는 임의 변경하지 않고 정해진 다음 단계로만 이동한다. */
    @PostMapping("/{id}/publication/status")
    public String changeRecruitmentStatus(@PathVariable Long id,
                                          @RequestParam RecruitmentStatus status,
                                          RedirectAttributes redirectAttributes) {
        try {
            courseService.changeRecruitmentStatus(id, status);
            redirectAttributes.addFlashAttribute("publicationMessage",
                    status.getLabel() + " 상태로 변경했습니다.");
        } catch (RecruitmentReadinessException e) {
            redirectAttributes.addFlashAttribute("publicationError",
                    "모집중으로 전환하려면 누락된 정보를 먼저 입력해 주세요.");
            redirectAttributes.addFlashAttribute("publicationMissing", e.getMissingItems());
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("publicationError", e.getMessage());
        }
        return "redirect:/admin/courses/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        courseService.delete(id);
        return "redirect:/admin/courses";
    }

    private void prepareFormModel(Model model, String mode, Long courseId) {
        model.addAttribute("statuses", CourseStatus.values());
        model.addAttribute("publicationSites", PublicationSite.values());
        model.addAttribute("publicCategories", PublicCourseCategory.values());
        model.addAttribute("partnerOrganizations", organizationService.selectableOrganizations());
        model.addAttribute("mode", mode);
        var publication = courseId == null ? null : courseService.publicationOf(courseId).orElse(null);
        model.addAttribute("publicationStatus", publication == null
                ? RecruitmentStatus.PRE_CONSULTATION : publication.getRecruitmentStatus());
        model.addAttribute("readinessMissing", courseId == null
                ? List.of("과정을 저장한 뒤 담당 교사를 배정하세요.")
                : courseService.recruitmentReadiness(courseId));
    }
}
