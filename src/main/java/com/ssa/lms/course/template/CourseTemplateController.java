package com.ssa.lms.course.template;
import com.ssa.lms.auth.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @RequestMapping("/instructor/course-templates") @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')") @RequiredArgsConstructor
public class CourseTemplateController{
 private final CourseTemplateService service;
 @GetMapping public String list(@AuthenticationPrincipal LoginUser u,Model m){m.addAttribute("rows",service.list(u));return "instructor/course-template-list";}
 @GetMapping("/new") public String form(@AuthenticationPrincipal LoginUser u,Model m){m.addAttribute("form",new CourseTemplateForm());m.addAttribute("courses",service.courseOptions(u));return "instructor/course-template-form";}
 @PostMapping public String create(@Valid @ModelAttribute("form") CourseTemplateForm f,BindingResult e,@AuthenticationPrincipal LoginUser u,Model m){if(e.hasErrors()){m.addAttribute("courses",service.courseOptions(u));return "instructor/course-template-form";}return "redirect:/instructor/course-templates/"+service.create(f,u);}
 @GetMapping("/{id}") public String detail(@PathVariable Long id,@AuthenticationPrincipal LoginUser u,Model m){m.addAttribute("row",service.view(id,u));m.addAttribute("links",service.links(id,u));m.addAttribute("versions",service.versions(id,u));m.addAttribute("courses",service.courseOptions(u));m.addAttribute("deployForm",new CourseTemplateDeployForm());return "instructor/course-template-detail";}
 @PostMapping("/{id}/publish") public String publish(@PathVariable Long id,@RequestParam(required=false)String changeSummary,@AuthenticationPrincipal LoginUser u,RedirectAttributes ra){int n=service.publish(id,changeSummary,u);ra.addFlashAttribute("message","새 버전을 발행하고 "+n+"개 과정에 안전 변경을 반영했습니다.");return redirect(id);}
 @PostMapping("/{id}/deploy") public String deploy(@PathVariable Long id,@Valid @ModelAttribute("deployForm")CourseTemplateDeployForm f,BindingResult e,@AuthenticationPrincipal LoginUser u,RedirectAttributes ra,Model m){if(e.hasErrors()){return detail(id,u,m);}service.deploy(id,f,u);ra.addFlashAttribute("message","작성중 과정에 템플릿을 적용했습니다.");return redirect(id);}
 @PostMapping("/{id}/links/{linkId}/sync") public String sync(@PathVariable Long id,@PathVariable Long linkId,@AuthenticationPrincipal LoginUser u,RedirectAttributes ra){service.sync(id,linkId,u);ra.addFlashAttribute("message","삭제 없이 추가·수정 항목만 반영했습니다.");return redirect(id);}private String redirect(Long id){return "redirect:/instructor/course-templates/"+id;}
}
