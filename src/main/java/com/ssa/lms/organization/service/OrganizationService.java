package com.ssa.lms.organization.service;

import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.repository.CourseRepository;
import com.ssa.lms.organization.entity.*;
import com.ssa.lms.organization.repository.CoursePartnerRepository;
import com.ssa.lms.organization.repository.PartnerOrganizationRepository;
import com.ssa.lms.organization.web.OrganizationForm;
import com.ssa.lms.organization.web.PublicOrganizationView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {

    private final PartnerOrganizationRepository organizationRepository;
    private final CoursePartnerRepository coursePartnerRepository;
    private final CourseRepository courseRepository;

    public List<PartnerOrganization> findAll(String query, OrganizationType type,
                                             OrganizationRelationshipType relationship,
                                             OrganizationStatus status, Boolean homepageExposure) {
        String keyword = query == null ? "" : normalizeName(query);
        return organizationRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .filter(item -> keyword.isBlank()
                        || item.getNormalizedName().contains(keyword)
                        || contains(item.getOneLineDescription(), keyword)
                        || contains(item.getWebsiteDomain(), keyword))
                .filter(item -> type == null || item.getType() == type)
                .filter(item -> relationship == null || item.getRelationshipTypes().contains(relationship))
                .filter(item -> status == null || item.getStatus() == status)
                .filter(item -> homepageExposure == null || item.isHomepageExposure() == homepageExposure)
                .toList();
    }

    public List<PartnerOrganization> selectableOrganizations() {
        return organizationRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .filter(item -> item.getStatus() == OrganizationStatus.ACTIVE)
                .toList();
    }

    public PartnerOrganization get(Long id) {
        return organizationRepository.findOneById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));
    }

    public List<CoursePartner> courseLinks(Long organizationId) {
        return coursePartnerRepository.findDetailedByOrganizationId(organizationId);
    }

    public OrganizationForm formForEdit(Long id) {
        return OrganizationForm.from(get(id), courseLinks(id));
    }

    @Transactional
    public Long create(OrganizationForm form) {
        String normalizedName = normalizeName(form.getName());
        String domain = websiteDomain(form.getWebsiteUrl());
        assertUnique(null, normalizedName, domain);
        PartnerOrganization saved = organizationRepository.save(PartnerOrganization.builder()
                .name(form.getName().trim())
                .normalizedName(normalizedName)
                .type(form.getType())
                .oneLineDescription(form.getOneLineDescription())
                .detailedDescription(form.getDetailedDescription())
                .logoUrl(form.getLogoUrl())
                .websiteUrl(form.getWebsiteUrl())
                .websiteDomain(domain)
                .relationshipTypes(form.getRelationshipTypes())
                .homepageExposure(form.isHomepageExposure())
                .exposureSites(form.getExposureSites())
                .exposurePositions(form.getExposurePositions())
                .displayOrder(form.getDisplayOrder())
                .internalNote(form.getInternalNote())
                .status(form.getStatus())
                .build());
        syncOrganizationCourses(saved, form.getProjectCourseIds(), form.getRecruitmentCourseIds());
        return saved.getId();
    }

    @Transactional
    public void update(Long id, OrganizationForm form) {
        PartnerOrganization organization = get(id);
        String normalizedName = normalizeName(form.getName());
        String domain = websiteDomain(form.getWebsiteUrl());
        assertUnique(id, normalizedName, domain);
        organization.update(form.getName().trim(), normalizedName, form.getType(),
                form.getOneLineDescription(), form.getDetailedDescription(), form.getLogoUrl(),
                form.getWebsiteUrl(), domain, form.getRelationshipTypes(), form.isHomepageExposure(),
                form.getExposureSites(), form.getExposurePositions(), form.getDisplayOrder(),
                form.getInternalNote(), form.getStatus());
        syncOrganizationCourses(organization, form.getProjectCourseIds(), form.getRecruitmentCourseIds());
    }

    public List<PublicOrganizationView> publicOrganizations(OrganizationExposureSite site,
                                                            OrganizationExposurePosition position) {
        return organizationRepository
                .findByStatusAndHomepageExposureTrueOrderByDisplayOrderAscNameAsc(OrganizationStatus.ACTIVE)
                .stream()
                .filter(item -> site == null || item.getExposureSites().contains(site))
                .filter(item -> position == null || item.getExposurePositions().contains(position))
                .map(PublicOrganizationView::of)
                .toList();
    }

    /** 과정 CMS에서 선택한 프로젝트 참여사만 동기화하고 채용 연계 표시는 보존한다. */
    @Transactional
    public void syncCourseProjectPartners(Course course, Set<Long> selectedOrganizationIds) {
        Set<Long> selected = selectedOrganizationIds == null ? Set.of() : selectedOrganizationIds;
        Map<Long, CoursePartner> existing = coursePartnerRepository.findDetailedByCourseId(course.getId()).stream()
                .collect(Collectors.toMap(link -> link.getOrganization().getId(), Function.identity()));
        Map<Long, PartnerOrganization> organizations = organizationRepository.findAllById(selected).stream()
                .collect(Collectors.toMap(PartnerOrganization::getId, Function.identity()));
        if (organizations.size() != selected.size()) {
            throw new IllegalArgumentException("선택한 기업·기관 중 존재하지 않는 항목이 있습니다.");
        }
        for (Long organizationId : selected) {
            CoursePartner link = existing.remove(organizationId);
            if (link == null) {
                coursePartnerRepository.save(CoursePartner.builder()
                        .course(course).organization(organizations.get(organizationId))
                        .projectParticipant(true).recruitmentLinked(false).build());
            } else {
                link.updateRelationship(true, link.isRecruitmentLinked());
            }
        }
        for (CoursePartner link : existing.values()) {
            if (!link.isProjectParticipant()) continue;
            if (link.isRecruitmentLinked()) {
                link.updateRelationship(false, true);
            } else {
                coursePartnerRepository.delete(link);
            }
        }
    }

    public List<CoursePartner> coursePartners(Long courseId) {
        return coursePartnerRepository.findDetailedByCourseId(courseId);
    }

    private void syncOrganizationCourses(PartnerOrganization organization,
                                         Set<Long> projectCourseIds,
                                         Set<Long> recruitmentCourseIds) {
        Set<Long> projectIds = projectCourseIds == null ? Set.of() : projectCourseIds;
        Set<Long> recruitmentIds = recruitmentCourseIds == null ? Set.of() : recruitmentCourseIds;
        Set<Long> desiredIds = new LinkedHashSet<>(projectIds);
        desiredIds.addAll(recruitmentIds);

        Map<Long, Course> courses = courseRepository.findAllById(desiredIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        if (courses.size() != desiredIds.size()) {
            throw new IllegalArgumentException("선택한 과정 중 존재하지 않는 항목이 있습니다.");
        }

        Map<Long, CoursePartner> existing = coursePartnerRepository
                .findDetailedByOrganizationId(organization.getId()).stream()
                .collect(Collectors.toMap(link -> link.getCourse().getId(), Function.identity()));
        for (Long courseId : desiredIds) {
            CoursePartner link = existing.remove(courseId);
            boolean project = projectIds.contains(courseId);
            boolean recruitment = recruitmentIds.contains(courseId);
            if (link == null) {
                coursePartnerRepository.save(CoursePartner.builder()
                        .course(courses.get(courseId)).organization(organization)
                        .projectParticipant(project).recruitmentLinked(recruitment).build());
            } else {
                link.updateRelationship(project, recruitment);
            }
        }
        coursePartnerRepository.deleteAll(existing.values());
    }

    private void assertUnique(Long id, String normalizedName, String domain) {
        boolean duplicateName = id == null
                ? organizationRepository.existsByNormalizedName(normalizedName)
                : organizationRepository.existsByNormalizedNameAndIdNot(normalizedName, id);
        if (duplicateName) {
            throw new DuplicateOrganizationException("같은 이름의 기업·기관이 이미 등록되어 있습니다.");
        }
        if (domain != null) {
            boolean duplicateDomain = id == null
                    ? organizationRepository.existsByWebsiteDomain(domain)
                    : organizationRepository.existsByWebsiteDomainAndIdNot(domain, id);
            if (duplicateDomain) {
                throw new DuplicateOrganizationException("같은 홈페이지 도메인의 기업·기관이 이미 등록되어 있습니다.");
            }
        }
    }

    static String normalizeName(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static String websiteDomain(String websiteUrl) {
        if (websiteUrl == null || websiteUrl.isBlank()) return null;
        String host = URI.create(websiteUrl.trim()).getHost();
        if (host == null || host.isBlank()) return null;
        host = host.toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private boolean contains(String value, String keyword) {
        return value != null && normalizeName(value).contains(keyword);
    }
}
