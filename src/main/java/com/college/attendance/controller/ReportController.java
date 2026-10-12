package com.college.attendance.controller;

import com.college.attendance.dto.BatchDto;
import com.college.attendance.dto.ClassDto;
import com.college.attendance.dto.report.DayReportDto;
import com.college.attendance.dto.report.RangeReportDto;
import com.college.attendance.dto.report.StudentReportDto;
import com.college.attendance.exception.BadRequestException;
import com.college.attendance.security.AppUserPrincipal;
import com.college.attendance.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Attendance reports: day-wise and month-wise, as JSON (for the page) and as PDF, Excel and CSV downloads.
 * Admin sees every class; a teacher sees the classes / batches they teach (ReportAccessService).
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportPdfService pdfService;
    private final ReportExcelService excelService;
    private final ReportCsvService csvService;
    private final ReportAccessService accessService;
    private final ClassService classService;
    private final SessionGenerator generator;
    private final StudentService studentService;
    private final Clock clock;

    public ReportController(ReportService reportService, ReportPdfService pdfService, ReportExcelService excelService,
                            ReportCsvService csvService, ReportAccessService accessService, ClassService classService,
                            SessionGenerator generator, StudentService studentService, Clock clock) {
        this.reportService = reportService;
        this.pdfService = pdfService;
        this.excelService = excelService;
        this.csvService = csvService;
        this.accessService = accessService;
        this.classService = classService;
        this.generator = generator;
        this.studentService = studentService;
        this.clock = clock;
    }

    /** Classes (and batches) the user may see reports of. */
    @GetMapping("/classes")
    public List<ClassDto> classes(@AuthenticationPrincipal AppUserPrincipal user) {
        Map<Long, Set<Long>> scope = accessService.teacherScope(user);
        return classService.listClasses().stream()
                .filter(c -> scope == null || scope.containsKey(c.id()))
                .map(c -> {
                    if (scope == null) return c;
                    Set<Long> visible = scope.get(c.id());
                    List<BatchDto> batches = c.batches().stream()
                            .filter(b -> ReportAccessService.isVisible(visible, b.id())).toList();
                    return new ClassDto(c.id(), c.name(), batches.stream().mapToLong(BatchDto::studentCount).sum(), batches);
                })
                .toList();
    }

    // ---------------------------------------------------------------- JSON for the page

    @GetMapping("/classes/{classId}/day")
    public DayReportDto day(@PathVariable Long classId,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @AuthenticationPrincipal AppUserPrincipal user) {
        LocalDate d = date == null ? LocalDate.now(clock) : date;
        generator.ensureSessions(d);   // so sessions not marked yet show as pending
        return reportService.dayReport(classId, d, user);
    }

    @GetMapping("/classes/{classId}/month")
    public RangeReportDto month(@PathVariable Long classId, @RequestParam(required = false) String month,
                                @AuthenticationPrincipal AppUserPrincipal user) {
        return reportService.monthReport(classId, parseMonth(month), user);
    }

    /**
     * Any period: period = WEEK (the week containing "date"), MONTH ("month" = 2026-10),
     * OVERALL (semester so far) or CUSTOM ("from" and "to").
     */
    @GetMapping("/classes/{classId}/range")
    public RangeReportDto range(@PathVariable Long classId, @RequestParam String period,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @RequestParam(required = false) String month,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @AuthenticationPrincipal AppUserPrincipal user) {
        return reportService.rangeReport(classId, period(classId, period, date, month, from, to), user);
    }

    /** One student's detail page (default period: OVERALL). */
    @GetMapping("/students/{studentId}")
    public StudentReportDto student(@PathVariable Long studentId,
                                    @RequestParam(defaultValue = "OVERALL") String period,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @RequestParam(required = false) String month,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @AuthenticationPrincipal AppUserPrincipal user) {
        Long classId = studentService.classIdOf(studentId);
        return reportService.studentReport(studentId, period(classId, period, date, month, from, to), user);
    }

    // ---------------------------------------------------------------- PDF downloads

    /** PDF for any period (see range above); leave out classId for all classes. */
    @GetMapping("/range.pdf")
    public ResponseEntity<byte[]> rangePdf(@RequestParam(required = false) Long classId, @RequestParam String period,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @RequestParam(required = false) String month,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @AuthenticationPrincipal AppUserPrincipal user) {
        List<RangeReportDto> reports = classIds(classId, user).stream()
                .map(id -> reportService.rangeReport(id, period(id, period, date, month, from, to), user))
                .toList();
        RangeReportDto first = reports.get(0);
        return pdf(pdfService.rangePdf(reports), "attendance-" + namePart(reports.size() == 1 ? first.className() : null)
                + "-" + period.toLowerCase() + "-" + first.from() + "-to-" + first.to() + ".pdf");
    }

    /** Daily PDF for one class, or for every class the user may see when classId is left out. */
    @GetMapping("/day.pdf")
    public ResponseEntity<byte[]> dayPdf(@RequestParam(required = false) Long classId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         @AuthenticationPrincipal AppUserPrincipal user) {
        LocalDate d = date == null ? LocalDate.now(clock) : date;
        generator.ensureSessions(d);
        List<DayReportDto> reports = classIds(classId, user).stream()
                .map(id -> reportService.dayReport(id, d, user))
                .toList();
        return pdf(pdfService.dayPdf(reports), "attendance-" + namePart(reports.size() == 1 ? reports.get(0).className() : null) + "-" + d + ".pdf");
    }

    /** Monthly PDF for one class, or for every class the user may see when classId is left out. */
    @GetMapping("/month.pdf")
    public ResponseEntity<byte[]> monthPdf(@RequestParam(required = false) Long classId,
                                           @RequestParam(required = false) String month,
                                           @AuthenticationPrincipal AppUserPrincipal user) {
        YearMonth m = parseMonth(month);
        List<RangeReportDto> reports = classIds(classId, user).stream()
                .map(id -> reportService.monthReport(id, m, user))
                .toList();
        return pdf(pdfService.rangePdf(reports), "attendance-" + namePart(reports.size() == 1 ? reports.get(0).className() : null) + "-" + m + ".pdf");
    }

    // ---------------------------------------------------------------- Excel downloads

    @GetMapping("/day.xlsx")
    public ResponseEntity<byte[]> dayExcel(@RequestParam(required = false) Long classId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @AuthenticationPrincipal AppUserPrincipal user) {
        LocalDate d = date == null ? LocalDate.now(clock) : date;
        generator.ensureSessions(d);
        List<DayReportDto> reports = classIds(classId, user).stream()
                .map(id -> reportService.dayReport(id, d, user))
                .toList();
        return excel(excelService.dayReport(reports),
                "attendance-" + namePart(reports.size() == 1 ? reports.get(0).className() : null) + "-" + d + ".xlsx");
    }

    /** Class report for any period, with theory / lab / combined % per subject. */
    @GetMapping("/range.xlsx")
    public ResponseEntity<byte[]> rangeExcel(@RequestParam(required = false) Long classId, @RequestParam String period,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                             @RequestParam(required = false) String month,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @AuthenticationPrincipal AppUserPrincipal user) {
        List<RangeReportDto> reports = rangeReports(classId, period, date, month, from, to, user);
        RangeReportDto first = reports.get(0);
        return excel(excelService.classReport(reports), "attendance-" + namePart(reports.size() == 1 ? first.className() : null)
                + "-" + period.toLowerCase() + "-" + first.from() + "-to-" + first.to() + ".xlsx");
    }

    /** Only the defaulters (below the threshold overall or in any theory / lab part). */
    @GetMapping("/defaulters.xlsx")
    public ResponseEntity<byte[]> defaultersExcel(@RequestParam(required = false) Long classId, @RequestParam String period,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                  @RequestParam(required = false) String month,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @AuthenticationPrincipal AppUserPrincipal user) {
        List<RangeReportDto> reports = rangeReports(classId, period, date, month, from, to, user);
        RangeReportDto first = reports.get(0);
        return excel(excelService.defaulters(reports), "defaulters-" + namePart(reports.size() == 1 ? first.className() : null)
                + "-" + period.toLowerCase() + "-" + first.from() + "-to-" + first.to() + ".xlsx");
    }

    // ---------------------------------------------------------------- CSV downloads

    /** One row per student for one day; leave out classId for every class the user may see. */
    @GetMapping("/day.csv")
    public ResponseEntity<byte[]> dayCsv(@RequestParam(required = false) Long classId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         @AuthenticationPrincipal AppUserPrincipal user) {
        LocalDate d = date == null ? LocalDate.now(clock) : date;
        generator.ensureSessions(d);
        List<DayReportDto> reports = classIds(classId, user).stream()
                .map(id -> reportService.dayReport(id, d, user))
                .toList();
        return csv(csvService.dayReport(reports),
                "attendance-" + namePart(reports.size() == 1 ? reports.get(0).className() : null) + "-" + d + ".csv");
    }

    /** One row per student for any period (see range above). */
    @GetMapping("/range.csv")
    public ResponseEntity<byte[]> rangeCsv(@RequestParam(required = false) Long classId, @RequestParam String period,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @RequestParam(required = false) String month,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @AuthenticationPrincipal AppUserPrincipal user) {
        List<RangeReportDto> reports = rangeReports(classId, period, date, month, from, to, user);
        RangeReportDto first = reports.get(0);
        return csv(csvService.rangeReport(reports), "attendance-" + namePart(reports.size() == 1 ? first.className() : null)
                + "-" + period.toLowerCase() + "-" + first.from() + "-to-" + first.to() + ".csv");
    }

    // ---------------------------------------------------------------- helpers

    private static ResponseEntity<byte[]> csv(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(content);
    }

    private List<RangeReportDto> rangeReports(Long classId, String period, LocalDate date, String month,
                                              LocalDate from, LocalDate to, AppUserPrincipal user) {
        return classIds(classId, user).stream()
                .map(id -> reportService.rangeReport(id, period(id, period, date, month, from, to), user))
                .toList();
    }

    private static ResponseEntity<byte[]> excel(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    private List<Long> classIds(Long classId, AppUserPrincipal user) {
        if (classId != null) {
            return List.of(classId);
        }
        List<Long> ids = classes(user).stream().map(ClassDto::id).toList();
        if (ids.isEmpty()) {
            throw new BadRequestException("There are no classes to report on.");
        }
        return ids;
    }

    private ReportPeriod period(Long classId, String period, LocalDate date, String month, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        return switch (period == null ? "" : period.toUpperCase()) {
            case "WEEK" -> ReportPeriod.week(date == null ? today : date);
            case "MONTH" -> ReportPeriod.month(parseMonth(month));
            case "OVERALL" -> reportService.overall(classId);
            case "CUSTOM" -> ReportPeriod.custom(from, to);
            default -> throw new BadRequestException("Period must be WEEK, MONTH, OVERALL or CUSTOM.");
        };
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(clock);
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Month must look like 2026-10.");
        }
    }

    private static String namePart(String className) {
        return className == null ? "all-classes" : className.replaceAll("[^A-Za-z0-9-]+", "-");
    }

    private static ResponseEntity<byte[]> pdf(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }
}
