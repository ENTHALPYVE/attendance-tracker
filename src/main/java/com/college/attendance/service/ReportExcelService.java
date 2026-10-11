package com.college.attendance.service;

import com.college.attendance.dto.report.DayReportDto;
import com.college.attendance.dto.report.RangeReportDto;
import com.college.attendance.dto.report.RangeReportDto.SubjectCell;
import com.college.attendance.dto.report.RangeReportDto.SubjectColumn;
import com.college.attendance.service.calc.AttendanceCalculator.Status;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Excel (.xlsx) exports with Apache POI: the class report (one sheet per class) and the defaulter list.
 * Percentages are written as numbers (not text) so they can be sorted and filtered in Excel.
 */
@Service
public class ReportExcelService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** Class report for a period: Roll No, Name, Batch, T / L / C % per subject, totals, status, note. */
    public byte[] classReport(List<RangeReportDto> reports) {
        return write(wb -> {
            Styles st = new Styles(wb);
            for (RangeReportDto r : reports) {
                Sheet sheet = wb.createSheet(sheetName(wb, r.className()));
                int rowNo = title(sheet, st, r.className() + " - " + r.title(),
                        r.from().format(DATE) + " to " + r.to().format(DATE) + "   |   Sessions taken: " + r.sessionsTaken()
                                + "   |   Threshold: " + r.threshold() + "%");

                // Two header rows: subject codes, then T / L / C under each subject.
                Row h1 = sheet.createRow(rowNo++);
                Row h2 = sheet.createRow(rowNo++);
                int col = 0;
                for (String h : List.of("Roll No", "Name", "Batch")) {
                    header(h1, col, h, st);
                    header(h2, col, "", st);
                    sheet.addMergedRegion(new CellRangeAddress(h1.getRowNum(), h2.getRowNum(), col, col));
                    col++;
                }
                List<List<String>> parts = new ArrayList<>();
                for (SubjectColumn s : r.subjects()) {
                    List<String> p = parts(s);
                    parts.add(p);
                    header(h1, col, s.code(), st);
                    for (int i = 0; i < p.size(); i++) {
                        if (i > 0) header(h1, col + i, "", st);
                        header(h2, col + i, p.get(i).substring(0, 1), st);
                    }
                    if (p.size() > 1) sheet.addMergedRegion(new CellRangeAddress(h1.getRowNum(), h1.getRowNum(), col, col + p.size() - 1));
                    col += p.size();
                }
                for (String h : List.of("Attended", "Held", "Overall %", "Status", "Note")) {
                    header(h1, col, h, st);
                    header(h2, col, "", st);
                    sheet.addMergedRegion(new CellRangeAddress(h1.getRowNum(), h2.getRowNum(), col, col));
                    col++;
                }

                for (RangeReportDto.Row row : r.rows()) {
                    Row x = sheet.createRow(rowNo++);
                    int c = 0;
                    text(x, c++, row.rollNo(), st.text);
                    text(x, c++, row.name(), st.text);
                    text(x, c++, row.batchName(), st.text);
                    for (int i = 0; i < r.subjects().size(); i++) {
                        SubjectCell cell = row.subjects().get(r.subjects().get(i).subjectId());
                        for (String part : parts.get(i)) {
                            Double value = cell == null ? null : switch (part) {
                                case "Theory" -> cell.theoryPercent();
                                case "Lab" -> cell.labPercent();
                                default -> cell.combinedPercent();
                            };
                            boolean low = value != null && !part.equals("Combined") && value < r.threshold();
                            percent(x, c++, value, low ? st.percentLow : st.percent);
                        }
                    }
                    number(x, c++, row.attended(), st.number);
                    number(x, c++, row.held(), st.number);
                    percent(x, c++, row.percent(), row.status() == Status.DEFAULTER ? st.percentLow : st.percentBold);
                    text(x, c++, statusText(row.status()), st.text);
                    text(x, c, note(row, r.threshold()), st.text);
                }
                finish(sheet, col, rowNo, 3, 2);
            }
        });
    }

    /** Defaulter list: students below the threshold overall or in any subject part. */
    public byte[] defaulters(List<RangeReportDto> reports) {
        return write(wb -> {
            Styles st = new Styles(wb);
            for (RangeReportDto r : reports) {
                Sheet sheet = wb.createSheet(sheetName(wb, r.className()));
                int rowNo = title(sheet, st, r.className() + " - Defaulters - " + r.title(),
                        r.from().format(DATE) + " to " + r.to().format(DATE) + "   |   Below " + r.threshold()
                                + "% overall or in the theory / lab of any subject");
                Row h = sheet.createRow(rowNo++);
                List<String> heads = List.of("Roll No", "Name", "Batch", "Attended", "Held", "Overall %", "Status",
                        "Below threshold in", "Sessions needed to reach " + r.threshold() + "%");
                for (int i = 0; i < heads.size(); i++) header(h, i, heads.get(i), st);

                Predicate<RangeReportDto.Row> isDefaulter = row -> row.status() == Status.DEFAULTER || !row.belowIn().isEmpty();
                int count = 0;
                for (RangeReportDto.Row row : r.rows()) {
                    if (!isDefaulter.test(row)) continue;
                    count++;
                    Row x = sheet.createRow(rowNo++);
                    text(x, 0, row.rollNo(), st.text);
                    text(x, 1, row.name(), st.text);
                    text(x, 2, row.batchName(), st.text);
                    number(x, 3, row.attended(), st.number);
                    number(x, 4, row.held(), st.number);
                    percent(x, 5, row.percent(), row.status() == Status.DEFAULTER ? st.percentLow : st.percentBold);
                    text(x, 6, statusText(row.status()), st.text);
                    text(x, 7, String.join(", ", row.belowIn()), st.text);
                    if (row.needed() != null) number(x, 8, row.needed(), st.number);
                }
                if (count == 0) {
                    text(sheet.createRow(rowNo++), 0, "No defaulters.", st.text);
                }
                finish(sheet, heads.size(), rowNo, 3, 1);
            }
        });
    }

    /** Day report: Roll No, Name, Batch, attended, held, day %, status, absent in. */
    public byte[] dayReport(List<DayReportDto> reports) {
        return write(wb -> {
            Styles st = new Styles(wb);
            for (DayReportDto r : reports) {
                Sheet sheet = wb.createSheet(sheetName(wb, r.className()));
                int rowNo = title(sheet, st, r.className() + " - Daily attendance",
                        r.date().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))
                                + "   |   Sessions taken: " + r.sessionsTaken()
                                + (r.sessionsPending() > 0 ? "   |   Not marked yet: " + r.sessionsPending() : ""));
                Row h = sheet.createRow(rowNo++);
                List<String> heads = List.of("Roll No", "Name", "Batch", "Attended", "Held", "Day %", "Status", "Absent in");
                for (int i = 0; i < heads.size(); i++) header(h, i, heads.get(i), st);
                for (DayReportDto.Row row : r.rows()) {
                    Row x = sheet.createRow(rowNo++);
                    text(x, 0, row.rollNo(), st.text);
                    text(x, 1, row.name(), st.text);
                    text(x, 2, row.batchName(), st.text);
                    number(x, 3, row.attended(), st.number);
                    number(x, 4, row.held(), st.number);
                    percent(x, 5, row.percent(), row.status() == Status.DEFAULTER ? st.percentLow : st.percentBold);
                    text(x, 6, statusText(row.status()), st.text);
                    text(x, 7, String.join(", ", row.absentIn()), st.text);
                }
                finish(sheet, heads.size(), rowNo, 3, 1);
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    private interface Content {
        void fill(Workbook wb);
    }

    private static byte[] write(Content content) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            content.fill(wb);
            if (wb.getNumberOfSheets() == 0) wb.createSheet("Report");
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the Excel file", e);
        }
    }

    /** Cell styles, created once per workbook (Excel limits the number of styles). */
    private static final class Styles {
        final CellStyle title, subtitle, header, text, number, percent, percentBold, percentLow;

        Styles(Workbook wb) {
            DataFormat fmt = wb.createDataFormat();
            Font bold = wb.createFont();
            bold.setBold(true);
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 13);
            Font grey = wb.createFont();
            grey.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            Font red = wb.createFont();
            red.setBold(true);
            red.setColor(IndexedColors.DARK_RED.getIndex());

            title = wb.createCellStyle();
            title.setFont(big);
            subtitle = wb.createCellStyle();
            subtitle.setFont(grey);
            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setBorderBottom(BorderStyle.THIN);
            text = wb.createCellStyle();
            number = wb.createCellStyle();
            number.setDataFormat(fmt.getFormat("0"));
            percent = wb.createCellStyle();
            percent.setDataFormat(fmt.getFormat("0.0"));
            percentBold = wb.createCellStyle();
            percentBold.setDataFormat(fmt.getFormat("0.0"));
            percentBold.setFont(bold);
            percentLow = wb.createCellStyle();
            percentLow.setDataFormat(fmt.getFormat("0.0"));
            percentLow.setFont(red);
        }
    }

    private static int title(Sheet sheet, Styles st, String title, String subtitle) {
        text(sheet.createRow(0), 0, title, st.title);
        text(sheet.createRow(1), 0, subtitle, st.subtitle);
        return 3;   // leave row 3 empty, the table starts on row 4
    }

    /** Column widths, frozen names / headings, and filter buttons on the last heading row. */
    private static void finish(Sheet sheet, int columns, int lastRow, int headerRow, int headerRows) {
        for (int c = 0; c < columns; c++) {
            sheet.autoSizeColumn(c);
            sheet.setColumnWidth(c, Math.min(Math.max(sheet.getColumnWidth(c), 7 * 256), 60 * 256));
        }
        int filterRow = headerRow + headerRows - 1;
        sheet.createFreezePane(3, filterRow + 1);
        sheet.setAutoFilter(new CellRangeAddress(filterRow, Math.max(lastRow - 1, filterRow), 0, columns - 1));
    }

    private static List<String> parts(SubjectColumn s) {
        List<String> p = new ArrayList<>();
        if (s.hasTheory()) p.add("Theory");
        if (s.hasLab()) p.add("Lab");
        if (s.hasTheory() && s.hasLab()) p.add("Combined");
        return p;
    }

    private static String statusText(Status s) {
        return switch (s) {
            case DEFAULTER -> "Defaulter";
            case AT_RISK -> "At risk";
            case SAFE -> "Safe";
            case NO_DATA -> "-";
        };
    }

    private static String note(RangeReportDto.Row row, int threshold) {
        List<String> parts = new ArrayList<>();
        if (row.needed() != null) parts.add("Needs " + row.needed() + " more to reach " + threshold + "%");
        if (row.canMiss() != null) parts.add("Can miss " + row.canMiss());
        if (!row.belowIn().isEmpty()) parts.add("Below " + threshold + "% in " + String.join(", ", row.belowIn()));
        return String.join("; ", parts);
    }

    /** Excel sheet names: max 31 characters, no []:*?/\ and unique within the workbook. */
    private static String sheetName(Workbook wb, String name) {
        String base = name.replaceAll("[\\[\\]:*?/\\\\]", "-");
        if (base.length() > 28) base = base.substring(0, 28);
        String candidate = base;
        for (int i = 2; wb.getSheet(candidate) != null; i++) candidate = base + " " + i;
        return candidate;
    }

    private static void header(Row row, int col, String value, Styles st) {
        text(row, col, value, st.header);
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    private static void number(Row row, int col, int value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void percent(Row row, int col, Double value, CellStyle style) {
        Cell c = row.createCell(col);
        if (value == null) {
            c.setCellValue("-");
        } else {
            c.setCellValue(value);
            c.setCellStyle(style);
        }
    }
}
