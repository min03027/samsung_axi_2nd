package com.ssa.lms.completion.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.ssa.lms.completion.entity.Completion;
import com.ssa.lms.completion.repository.CompletionRepository;
import com.ssa.lms.completion.service.CertificateDesignService.CertificateDesignView;
import com.ssa.lms.course.entity.Course;
import com.ssa.lms.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 이수증(수료증) PDF 발급. 이수 확정(PASS+CONFIRMED)된 {@link Completion} 에 대해서만 발급한다.
 *
 * <p>HTML 템플릿 문자열을 openhtmltopdf 로 PDF 렌더링하며, 한글 표기를 위해 시스템 폰트를
 * 우선 사용하고 해당 경로가 없는 운영 컨테이너에서는 애플리케이션에 포함된 Paperlogy 한글 폰트를
 * 임베드한다. 외부 폰트 경로는 {@code lms.certificate.font-path}로 재정의할 수 있다.</p>
 */
@Slf4j
@Service
public class CertificateService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일");
    private static final String BUNDLED_FONT = "static/font/Paperlogy-5Medium.ttf";

    private final CompletionRepository completionRepository;
    private final CertificateDesignService certificateDesignService;
    private final String fontPath;

    public CertificateService(CompletionRepository completionRepository,
                              CertificateDesignService certificateDesignService,
                              @Value("${lms.certificate.font-path:C:/Windows/Fonts/malgun.ttf}") String fontPath) {
        this.completionRepository = completionRepository;
        this.certificateDesignService = certificateDesignService;
        this.fontPath = fontPath;
    }

    /** 이수증 PDF 바이트 생성. 발급 불가(미이수/미확정) 시 {@link IllegalArgumentException} → 404. */
    @Transactional(readOnly = true)
    public byte[] generate(Long completionId) {
        Completion c = completionRepository.findById(completionId)
                .orElseThrow(() -> new IllegalArgumentException("이수 정보를 찾을 수 없습니다: " + completionId));
        if (!c.isCertificateIssuable()) {
            // 확정 전 completion 의 certificate URL 직접 접근은 정상 상태(발급할 이수증이 아직 없음)다.
            // IllegalStateException 은 어떤 advice 도 잡지 않아 whitelabel 500 이 됐다. "없는 이수증"으로
            // 보고 GlobalExceptionHandler 의 404 채널(IllegalArgumentException)을 탄다.
            throw new IllegalArgumentException("이수 확정된 수강생만 이수증을 발급할 수 있습니다.");
        }

        Course course = c.getCourse();
        User trainee = c.getTrainee();
        CertificateDesignView design = certificateDesignService.viewForCourse(course.getId());
        LocalDate issueDate = c.getConfirmedAt() != null ? c.getConfirmedAt().toLocalDate() : LocalDate.now();
        String certNo = "CERT-" + course.getCourseCode() + "-" + String.format("%06d", c.getId());

        String html = buildHtml(
                certNo,
                esc(trainee.getName()),
                trainee.getBirthDate() != null ? esc(trainee.getBirthDate()) : "-",
                esc(course.getCourseName()),
                course.getCohort() != null ? esc(course.getCohort()) : "-",
                course.getStartDate(), course.getEndDate(),
                c.getProgressRate(), c.getAttendanceRate(),
                issueDate, design);

        return render(html);
    }

    private byte[] render(String html) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            File font = new File(fontPath);
            if (font.exists()) {
                builder.useFont(font, "certFont");
            } else if (new ClassPathResource(BUNDLED_FONT).exists()) {
                builder.useFont(
                        () -> CertificateService.class.getClassLoader().getResourceAsStream(BUNDLED_FONT),
                        "certFont");
            } else {
                log.warn("[certificate] 외부·내장 한글 폰트를 모두 찾을 수 없습니다. 외부 경로={}", fontPath);
            }
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("이수증 PDF 생성에 실패했습니다.", e);
        }
    }

    private String buildHtml(String certNo, String name, String birth, String courseName, String cohort,
                             LocalDate start, LocalDate end, int progressRate, int attendanceRate,
                             LocalDate issueDate, CertificateDesignView design) {
        // CSS 에 리터럴 '%' 가 있어 String.format/formatted 는 쓰지 않고 토큰 치환한다.
        String presetCss = switch (design.preset()) {
            case "tech" -> """
                body { background: #f4f7fc; }
                .frame { border: 0; border-top: 14px solid {{accent}}; padding: 28px 34px 38px; }
                h1 { text-align: left; letter-spacing: 2px; margin-left: 0; }
                .eyebrow, .cert-no { color: {{accent}}; }
                table.info th { background: #eef3ff; }
                """;
            case "creative" -> """
                body { background: #faf8ff; }
                .frame { border: 2px solid {{accent}}; border-radius: 24px; padding: 34px; }
                h1 { font-family: Georgia, 'certFont', serif; font-weight: normal; letter-spacing: 10px; }
                .eyebrow { letter-spacing: 1px; }
                table.info { border-top: 2px solid {{accent}}; }
                table.info th { background: #f5f0ff; }
                """;
            default -> """
                .frame { border: 6px double {{accent}}; padding: 28px 32px; }
                """;
        };
        String birthRow = design.showBirth()
                ? "<tr><th>생년월일</th><td>" + birth + "</td></tr>" : "";
        String periodRow = design.showPeriod()
                ? "<tr><th>교육기간</th><td>" + start + " ~ " + end + "</td></tr>" : "";
        String metricsRow = design.showMetrics()
                ? "<tr><th>이수 결과</th><td>진도율 " + progressRate + "% / 출석률 " + attendanceRate + "%</td></tr>" : "";
        String seal = design.showSeal() ? "<span class=\"seal\">AXI</span>" : "";
        String template = """
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8"/>
            <style>
              @page { size: A4; margin: 20mm 18mm; }
              body { margin: 0; font-family: 'certFont', sans-serif; color: #202735; }
              .frame { min-height: 245mm; box-sizing: border-box; background: white; }
              .cert-no { text-align: right; font-size: 11px; color: #666; }
              .brand { color: {{accent}}; font-size: 13px; font-weight: bold; letter-spacing: 1px; }
              .eyebrow { margin-top: 50px; color: {{accent}}; font-family: Georgia, serif; font-size: 13px; letter-spacing: 0.8px; text-align: center; text-transform: uppercase; }
              h1 { text-align: center; font-size: 40px; letter-spacing: 6px; color: {{accent}}; margin: 14px 0 42px; }
              .recipient { margin: 0 0 34px; text-align: center; }
              .recipient small { display: block; margin-bottom: 8px; color: #7d8798; font-size: 12px; }
              .recipient strong { border-bottom: 1px solid #8a93a2; padding: 0 12px 6px; font-family: Georgia, 'certFont', serif; font-size: 28px; letter-spacing: 5px; }
              table.info { width: 100%; border-collapse: collapse; margin: 0 auto 34px; font-size: 14px; }
              table.info th { width: 28%; text-align: left; padding: 11px 10px; background: #f2f5fa; border: 1px solid #d8e0ec; color: {{accent}}; }
              table.info td { padding: 12px 14px; border: 1px solid #d8e0ec; }
              .statement { text-align: center; font-size: 16px; line-height: 2; margin: 34px 24px 44px; }
              .issue-date { text-align: center; font-size: 15px; margin-top: 48px; }
              .org { text-align: center; font-size: 21px; font-weight: bold; letter-spacing: 0.5px; margin-top: 14px; }
              .seal { display: inline-block; margin-left: 12px; border: 2px solid {{accent}}; border-radius: 50%; padding: 10px 7px; color: {{accent}}; font-size: 10px; letter-spacing: 0; vertical-align: middle; }
              {{presetCss}}
            </style></head>
            <body>
            <div class="frame">
              <div class="brand">AXI · SAMSUNG ACADEMY LXP</div>
              <div class="cert-no">발급번호: {{certNo}}</div>
              <div class="eyebrow">Certificate of Completion</div>
              <h1>{{title}}</h1>
              <div class="recipient"><small>성명</small><strong>{{name}}</strong></div>
              <table class="info">
                <tr><th>과정명 / 기수</th><td>{{courseName}} / {{cohort}}</td></tr>
                {{birthRow}}
                {{periodRow}}
                {{metricsRow}}
              </table>
              <div class="statement">{{statement}}</div>
              <div class="issue-date">{{issueDate}}</div>
              <div class="org">{{issuer}}{{seal}}</div>
            </div>
            </body></html>
            """;
        return template
                .replace("{{accent}}", design.accentColor())
                .replace("{{presetCss}}", presetCss.replace("{{accent}}", design.accentColor()))
                .replace("{{certNo}}", certNo)
                .replace("{{title}}", esc(design.title()))
                .replace("{{name}}", name)
                .replace("{{courseName}}", courseName)
                .replace("{{cohort}}", cohort)
                .replace("{{birthRow}}", birthRow)
                .replace("{{periodRow}}", periodRow)
                .replace("{{metricsRow}}", metricsRow)
                .replace("{{statement}}", esc(design.statement()).replace("\n", "<br/>"))
                .replace("{{issueDate}}", issueDate.format(DATE))
                .replace("{{issuer}}", esc(design.issuer()))
                .replace("{{seal}}", seal);
    }

    /** XHTML 안전을 위한 최소 이스케이프(성명/과정명 등 사용자 입력). */
    private String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
