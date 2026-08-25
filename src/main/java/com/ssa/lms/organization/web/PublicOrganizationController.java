package com.ssa.lms.organization.web;

import com.ssa.lms.organization.entity.OrganizationExposurePosition;
import com.ssa.lms.organization.entity.OrganizationExposureSite;
import com.ssa.lms.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v2/api/organizations")
@RequiredArgsConstructor
public class PublicOrganizationController {

    private final OrganizationService organizationService;

    @GetMapping
    public List<PublicOrganizationView> list(
            @RequestParam(required = false) OrganizationExposureSite site,
            @RequestParam(required = false) OrganizationExposurePosition position) {
        return organizationService.publicOrganizations(site, position);
    }
}
