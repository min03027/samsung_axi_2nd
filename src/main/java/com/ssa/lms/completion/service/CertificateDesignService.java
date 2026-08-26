package com.ssa.lms.completion.service;

import com.ssa.lms.completion.entity.CertificateDesign;
import com.ssa.lms.completion.repository.CertificateDesignRepository;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 과정별 이수증 디자인을 저장하고 PDF·관리자 화면에 동일한 설정을 제공한다. */
@Service
@RequiredArgsConstructor
public class CertificateDesignService {

    private static final Set<String> PRESETS = Set.of("formal", "tech", "creative");
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final CertificateDesignRepository designRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public CertificateDesignView viewForCourse(Long courseId) {
        return designRepository.findByCourseId(courseId)
                .map(CertificateDesignView::from)
                .orElseGet(CertificateDesignView::formalDefault);
    }

    @Transactional
    public CertificateDesignView save(Long courseId, String preset, String title, String issuer, String statement,
                                      String accentColor, boolean showBirth, boolean showPeriod,
                                      boolean showMetrics, boolean showSeal) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("과정을 찾을 수 없습니다."));
        String normalizedPreset = normalizePreset(preset);
        String normalizedTitle = requireText(title, "이수증 제목", 40);
        String normalizedIssuer = requireText(issuer, "발급기관", 80);
        String normalizedStatement = requireText(statement, "수여 문구", 180);
        String normalizedAccent = normalizeAccent(accentColor);

        CertificateDesign design = designRepository.findByCourseId(courseId)
                .orElseGet(() -> CertificateDesign.create(course));
        design.update(normalizedPreset, normalizedTitle, normalizedIssuer, normalizedStatement, normalizedAccent,
                showBirth, showPeriod, showMetrics, showSeal);
        return CertificateDesignView.from(designRepository.save(design));
    }

    private String normalizePreset(String preset) {
        String value = preset == null ? "" : preset.trim().toLowerCase(Locale.ROOT);
        if (!PRESETS.contains(value)) {
            throw new IllegalArgumentException("지원하지 않는 이수증 템플릿입니다.");
        }
        return value;
    }

    private String normalizeAccent(String accentColor) {
        String value = accentColor == null ? "" : accentColor.trim();
        if (!HEX_COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException("포인트 색상 형식이 올바르지 않습니다.");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String requireText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + "을(를) 입력해 주세요.");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "은(는) " + maxLength + "자 이내로 입력해 주세요.");
        }
        return normalized;
    }

    public record CertificateDesignView(
            String preset,
            String title,
            String issuer,
            String statement,
            String accentColor,
            boolean showBirth,
            boolean showPeriod,
            boolean showMetrics,
            boolean showSeal
    ) {
        public static CertificateDesignView formalDefault() {
            return new CertificateDesignView(
                    "formal", "이 수 증", "Samsung Academy LXP",
                    "위 사람은 해당 교육과정을 성실히 이수하였기에 이 증서를 수여합니다.",
                    "#1c3d6e", true, true, true, true);
        }

        private static CertificateDesignView from(CertificateDesign design) {
            return new CertificateDesignView(
                    design.getPreset(), design.getTitle(), design.getIssuer(), design.getStatement(),
                    design.getAccentColor(), design.isShowBirth(), design.isShowPeriod(),
                    design.isShowMetrics(), design.isShowSeal());
        }
    }
}
