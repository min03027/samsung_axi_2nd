package com.ssa.lms.demand;
import com.ssa.lms.auth.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @RequestMapping("/instructor/demand-signals") @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')") @RequiredArgsConstructor
public class DemandSignalController{
 private final DemandSignalService service;
 @GetMapping public String list(Model m){m.addAttribute("rows",service.list());m.addAttribute("pendingCount",service.pendingCount());if(!m.containsAttribute("form"))m.addAttribute("form",new DemandSignalForm());return "instructor/demand-signal-list";}
 @PostMapping public String create(@Valid @ModelAttribute("form")DemandSignalForm f,BindingResult e,Model m){if(e.hasErrors()){m.addAttribute("rows",service.list());m.addAttribute("pendingCount",service.pendingCount());return "instructor/demand-signal-list";}return "redirect:/instructor/demand-signals/"+service.create(f);}
 @PostMapping("/import") public String csv(@RequestParam("file")MultipartFile file,RedirectAttributes ra){int n=service.importCsv(file);ra.addFlashAttribute("message",n+"건의 산업수요 데이터를 등록하고 과정 추천을 생성했습니다.");return "redirect:/instructor/demand-signals";}
 @GetMapping("/{id}") public String detail(@PathVariable Long id,Model m){m.addAttribute("row",service.view(id));m.addAttribute("recommendations",service.recommendations(id));return "instructor/demand-signal-detail";}
 @PostMapping("/{signalId}/recommendations/{id}") public String review(@PathVariable Long signalId,@PathVariable Long id,@RequestParam DemandRecommendationStatus status,@RequestParam(required=false)String note,@AuthenticationPrincipal LoginUser u,RedirectAttributes ra){service.review(signalId,id,status,note,u.getId());ra.addFlashAttribute("message","추천 검토 결과를 저장했습니다.");return "redirect:/instructor/demand-signals/"+signalId;}
}
