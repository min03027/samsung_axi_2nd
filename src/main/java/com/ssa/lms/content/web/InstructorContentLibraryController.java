package com.ssa.lms.content.web;

import com.ssa.lms.auth.LoginUser;
import com.ssa.lms.content.entity.ContentLibraryStatus;
import com.ssa.lms.content.entity.ContentType;
import com.ssa.lms.content.service.ContentLibraryService;
import com.ssa.lms.content.service.FileStorageException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 강사·관리자 공용 콘텐츠 라이브러리와 버전 관리 화면. */
@Controller
@RequestMapping("/instructor/content-library")
@RequiredArgsConstructor
public class InstructorContentLibraryController {

    private static final String VIEW_DIR = "instructor/content-library-";

    private final ContentLibraryService libraryService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) ContentType type,
                       @RequestParam(required = false) ContentLibraryStatus status,
                       Model model) {
        model.addAttribute("items", libraryService.list(keyword, type, status));
        model.addAttribute("dashboard", libraryService.dashboard());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedStatus", status);
        addEnums(model);
        return VIEW_DIR + "list";
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) ContentType type, Model model) {
        ContentLibraryForm form = new ContentLibraryForm();
        form.setType(type != null ? type : ContentType.VIDEO);
        model.addAttribute("libraryForm", form);
        model.addAttribute("mode", "create");
        addEnums(model);
        return VIEW_DIR + "form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("libraryForm") ContentLibraryForm form,
                         BindingResult bindingResult,
                         @RequestParam("file") MultipartFile file,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "create");
            addEnums(model);
            return VIEW_DIR + "form";
        }
        try {
            Long id = libraryService.create(form, file);
            redirectAttributes.addFlashAttribute("message", "공용 콘텐츠 원본과 v1 이력을 등록했습니다.");
            return "redirect:/instructor/content-library/" + id;
        } catch (FileStorageException e) {
            bindingResult.reject("file", e.getMessage());
            model.addAttribute("mode", "create");
            addEnums(model);
            return VIEW_DIR + "form";
        }
    }

    @GetMapping("/versions")
    public String versions(Model model) {
        model.addAttribute("versions", libraryService.allVersions());
        model.addAttribute("dashboard", libraryService.dashboard());
        return VIEW_DIR + "versions";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("item", libraryService.view(id));
        model.addAttribute("versions", libraryService.versions(id));
        model.addAttribute("links", libraryService.links(id));
        return VIEW_DIR + "detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("libraryForm", libraryService.editForm(id));
        model.addAttribute("item", libraryService.view(id));
        model.addAttribute("libraryItemId", id);
        model.addAttribute("mode", "edit");
        addEnums(model);
        return VIEW_DIR + "form";
    }

    @PostMapping("/{id}")
    public String publish(@PathVariable Long id,
                          @Valid @ModelAttribute("libraryForm") ContentLibraryForm form,
                          BindingResult bindingResult,
                          @RequestParam(value = "file", required = false) MultipartFile file,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("item", libraryService.view(id));
            model.addAttribute("libraryItemId", id);
            model.addAttribute("mode", "edit");
            addEnums(model);
            return VIEW_DIR + "form";
        }
        int synced = libraryService.publish(id, form, file);
        redirectAttributes.addFlashAttribute("message",
                "새 버전을 발행하고 연결 콘텐츠 " + synced + "건을 동기화했습니다.");
        return "redirect:/instructor/content-library/" + id;
    }

    @GetMapping("/{id}/deploy")
    public String deployForm(@PathVariable Long id, @AuthenticationPrincipal LoginUser actor, Model model) {
        model.addAttribute("deployForm", new ContentLibraryDeployForm());
        addDeployRefs(id, actor, model);
        return VIEW_DIR + "deploy";
    }

    @PostMapping("/{id}/deploy")
    public String deploy(@PathVariable Long id,
                         @Valid @ModelAttribute("deployForm") ContentLibraryDeployForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal LoginUser actor,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addDeployRefs(id, actor, model);
            return VIEW_DIR + "deploy";
        }
        try {
            Long contentId = libraryService.deploy(id, form, actor);
            redirectAttributes.addFlashAttribute("message",
                    "과정 콘텐츠로 배치했습니다. 콘텐츠 ID: " + contentId);
            return "redirect:/instructor/content-library/" + id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            bindingResult.reject("deploy", e.getMessage());
            addDeployRefs(id, actor, model);
            return VIEW_DIR + "deploy";
        }
    }

    @PostMapping("/promote/{contentId}")
    public String promote(@PathVariable Long contentId, @AuthenticationPrincipal LoginUser actor,
                          RedirectAttributes redirectAttributes) {
        Long id = libraryService.promoteExisting(contentId, actor);
        redirectAttributes.addFlashAttribute("message", "기존 과정 콘텐츠를 공용 원본으로 등록했습니다.");
        return "redirect:/instructor/content-library/" + id;
    }

    @PostMapping("/{id}/links/{linkId}/sync")
    public String syncNow(@PathVariable Long id, @PathVariable Long linkId,
                          @AuthenticationPrincipal LoginUser actor,
                          RedirectAttributes redirectAttributes) {
        libraryService.syncNow(id, linkId, actor);
        redirectAttributes.addFlashAttribute("message", "현재 원본 버전을 과정 콘텐츠에 반영했습니다.");
        return "redirect:/instructor/content-library/" + id;
    }

    @PostMapping("/{id}/links/{linkId}/auto-sync")
    public String changeAutoSync(@PathVariable Long id, @PathVariable Long linkId,
                                 @RequestParam boolean enabled,
                                 @AuthenticationPrincipal LoginUser actor,
                                 RedirectAttributes redirectAttributes) {
        libraryService.changeAutoSync(id, linkId, enabled, actor);
        redirectAttributes.addFlashAttribute("message", enabled
                ? "자동 반영을 켰습니다. 최신 버전도 즉시 반영했습니다."
                : "자동 반영을 껐습니다. 이후 업데이트는 수동으로 반영해야 합니다.");
        return "redirect:/instructor/content-library/" + id;
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        libraryService.archive(id);
        redirectAttributes.addFlashAttribute("message", "공용 원본을 보관 상태로 변경했습니다.");
        return "redirect:/instructor/content-library/" + id;
    }

    private void addEnums(Model model) {
        model.addAttribute("contentTypes", ContentType.values());
        model.addAttribute("libraryStatuses", ContentLibraryStatus.values());
    }

    private void addDeployRefs(Long id, LoginUser actor, Model model) {
        model.addAttribute("item", libraryService.view(id));
        model.addAttribute("libraryItemId", id);
        model.addAttribute("courses", libraryService.courseOptions(actor));
        model.addAttribute("sessionOptions", libraryService.sessionOptions(actor));
    }
}
