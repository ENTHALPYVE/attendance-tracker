package com.college.attendance.service;

import com.college.attendance.dto.report.DayReportDto;
import com.college.attendance.dto.report.RangeReportDto;
import com.college.attendance.service.calc.AttendanceCalculator.Status;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * CSV versions of the class reports (one row per student), for Excel, Google Sheets or
 * any other program. Uses the same numbers as the page, the PDF and the Excel file.
 *
 * Safety: a cell that starts with = + - @ could run as a formula when the file is opened
 * in a spreadsheet ("CSV injection"), so such text gets a leading apostrophe.
 */
@Service
public class ReportCsvService {

    /** Byte order mark: makes Excel read the file as UTF-8 (names with accents etc.). */
    private static final String BOM = "﻿";

    public byte[] dayReport(List<DayReportDto> reports) {
        StringBuilder csv = new StringBuilder(BOM);
        row(csv, "Class", "Date", "Roll No", "Name", "Batch", "Attended", "Held", "Percent", "Status", "Absent in");
        for (DayReportDto r : reports) {
            for (DayReportDto.Row x : r.rows()) {
                row(csv, r.className(), r.date().toString(), x.rollNo(), x.name(), x.batchName(),
                        String.valueOf(x.attended()), String.valueOf(x.held()), percent(x.percent()),
                        status(x.status()), String.join("; ", x.absentIn()));
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] rangeReport(List<RangeReportDto> reports) {
        StringBuilder csv = new StringBuilder(BOM);
        row(csv, "Class", "From", "To", "Roll No", "Name", "Batch", "Attended", "Held", "Percent", "Status",
                "Below threshold in");
        for (RangeReportDto r : reports) {
            for (RangeReportDto.Row x : r.rows()) {
                row(csv, r.className(), r.from().toString(), r.to().toString(), x.rollNo(), x.name(), x.batchName(),
                        String.valueOf(x.attended()), String.valueOf(x.held()), percent(x.percent()),
                        status(x.status()), String.join("; ", x.belowIn()));
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- helpers

    private static void row(StringBuilder csv, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(cell(cells[i]));
        }
        csv.append("\r\n");   // the line ending the CSV standard (RFC 4180) asks for
    }

    /** Quotes a cell when needed and neutralises spreadsheet formulas. */
    static String cell(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String percent(Double p) {
        return p == null ? "" : String.format(Locale.ROOT, "%.1f", p);
    }

    /** Same words as the Excel report. */
    private static String status(Status s) {
        if (s == null) return "";
        return switch (s) {
            case DEFAULTER -> "Defaulter";
            case AT_RISK -> "At risk";
            case SAFE -> "Safe";
            case NO_DATA -> "No data";
        };
    }
}
