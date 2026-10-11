package com.college.attendance.service;

import com.college.attendance.dto.report.DayReportDto;
import com.college.attendance.dto.report.RangeReportDto;
import com.college.attendance.dto.report.RangeReportDto.SubjectCell;
import com.college.attendance.dto.report.RangeReportDto.SubjectColumn;
import com.college.attendance.service.calc.AttendanceCalculator.Status;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns day and month reports into PDF files (OpenPDF library).
 * One class per page group; several classes can go into one file.
 * Colours follow the app: charcoal text, thin warm-grey lines, muted status colours.
 * Only plain ASCII text is used so the built-in Helvetica font shows every character.
 */
@Service
public class ReportPdfService {

    private static final Color TEXT = new Color(0x2B, 0x2B, 0x2B);
    private static final Color MUTED = new Color(0x6B, 0x68, 0x62);
    private static final Color LINE = new Color(0xE2, 0xDE, 0xD5);
    private static final Color HEADER_BG = new Color(0xF1, 0xEE, 0xE7);
    private static final Color RED = new Color(0x8A, 0x33, 0x29);
    private static final Color AMBER = new Color(0x7A, 0x56, 0x14);
    private static final Color GREEN = new Color(0x36, 0x5A, 0x40);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, TEXT);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);
    private static final Font HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, MUTED);
    private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, TEXT);
    private static final Font CELL_MUTED = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private final Clock clock;

    public ReportPdfService(Clock clock) {
        this.clock = clock;
    }

    // ---------------------------------------------------------------- day

    public byte[] dayPdf(List<DayReportDto> reports) {
        return build(PageSize.A4, doc -> {
            boolean first = true;
            for (DayReportDto r : reports) {
                if (!first) doc.newPage();
                first = false;

                heading(doc, r.className() + " - Daily attendance", r.date().format(LONG_DATE));
                List<String> facts = new ArrayList<>();
                facts.add("Sessions taken: " + r.sessionsTaken());
                if (r.sessionsPending() > 0) facts.add("Not marked yet: " + r.sessionsPending());
                if (r.sessionsCancelled() > 0) facts.add("Cancelled: " + r.sessionsCancelled());
                facts.add("Threshold: " + r.threshold() + "%");
                if (r.countFreeSlotsAsPresent()) facts.add("Free slots count as present");
                if (r.holiday() != null) facts.add("Holiday: " + r.holiday());
                line(doc, String.join("   |   ", facts));

                PdfPTable table = table(new float[]{8, 28, 7, 11, 7, 8, 11, 30});
                header(table, "Roll No", "Name", "Batch", "Attended", "Held", "%", "Status", "Absent in");
                for (DayReportDto.Row row : r.rows()) {
                    cell(table, row.rollNo(), CELL_MUTED, Element.ALIGN_LEFT);
                    cell(table, row.name(), CELL, Element.ALIGN_LEFT);
                    cell(table, row.batchName(), CELL, Element.ALIGN_LEFT);
                    cell(table, String.valueOf(row.attended()), CELL, Element.ALIGN_RIGHT);
                    cell(table, String.valueOf(row.held()), CELL, Element.ALIGN_RIGHT);
                    cell(table, percent(row.percent()), CELL, Element.ALIGN_RIGHT);
                    statusCell(table, row.status());
                    cell(table, String.join(", ", row.absentIn()), CELL_MUTED, Element.ALIGN_LEFT);
                }
                if (r.rows().isEmpty()) emptyRow(table, 8);
                doc.add(table);
                footer(doc);
            }
        });
    }

    // ---------------------------------------------------------------- month (or any range)

    public byte[] rangePdf(List<RangeReportDto> reports) {
        return build(PageSize.A4.rotate(), doc -> {
            boolean first = true;
            for (RangeReportDto r : reports) {
                if (!first) doc.newPage();
                first = false;

                heading(doc, r.className() + " - " + r.title(),
                        r.from().format(SHORT_DATE) + " to " + r.to().format(SHORT_DATE));
                line(doc, "Sessions taken: " + r.sessionsTaken() + "   |   Threshold: " + r.threshold()
                        + "%   |   Subject columns show combined theory + lab %; an asterisk marks a theory or lab part below "
                        + r.threshold() + "%.");

                List<SubjectColumn> subjects = r.subjects();
                float[] widths = new float[7 + subjects.size()];
                widths[0] = 5; widths[1] = 18; widths[2] = 5;
                for (int i = 0; i < subjects.size(); i++) widths[3 + i] = 6;
                widths[3 + subjects.size()] = 6;    // attended / held
                widths[4 + subjects.size()] = 6;    // %
                widths[5 + subjects.size()] = 8;    // status
                widths[6 + subjects.size()] = 16;   // below threshold in
                PdfPTable table = table(widths);

                List<String> heads = new ArrayList<>(List.of("Roll No", "Name", "Batch"));
                subjects.forEach(s -> heads.add(s.code()));
                heads.addAll(List.of("Attended", "%", "Status", "Below threshold in"));
                header(table, heads.toArray(String[]::new));

                for (RangeReportDto.Row row : r.rows()) {
                    cell(table, row.rollNo(), CELL_MUTED, Element.ALIGN_LEFT);
                    cell(table, row.name(), CELL, Element.ALIGN_LEFT);
                    cell(table, row.batchName(), CELL, Element.ALIGN_LEFT);
                    for (SubjectColumn s : subjects) {
                        SubjectCell c = row.subjects().get(s.subjectId());
                        String text = c == null ? "-" : percent(c.combinedPercent()) + (c.defaulter() ? "*" : "");
                        cell(table, text, c != null && c.defaulter() ? colored(RED) : CELL, Element.ALIGN_RIGHT);
                    }
                    cell(table, row.attended() + "/" + row.held(), CELL, Element.ALIGN_RIGHT);
                    cell(table, percent(row.percent()), CELL, Element.ALIGN_RIGHT);
                    statusCell(table, row.status());
                    cell(table, String.join(", ", row.belowIn()), CELL_MUTED, Element.ALIGN_LEFT);
                }
                if (r.rows().isEmpty()) emptyRow(table, widths.length);
                doc.add(table);
                footer(doc);
            }
        });
    }

    // ---------------------------------------------------------------- building blocks

    private interface Content {
        void write(Document doc) throws DocumentException;
    }

    private byte[] build(Rectangle pageSize, Content content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(pageSize, 32, 32, 32, 32);
        PdfWriter.getInstance(doc, out);
        doc.addTitle("Attendance report");
        doc.addCreator("Attendance Tracker");
        doc.open();
        try {
            content.write(doc);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not create the PDF", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private void heading(Document doc, String title, String subtitle) throws DocumentException {
        doc.add(new Paragraph(title, TITLE));
        Paragraph sub = new Paragraph(subtitle, SUBTITLE);
        sub.setSpacingAfter(4);
        doc.add(sub);
    }

    private void line(Document doc, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, SUBTITLE);
        p.setSpacingAfter(8);
        doc.add(p);
    }

    private void footer(Document doc) throws DocumentException {
        Paragraph p = new Paragraph("Generated by Attendance Tracker on "
                + LocalDateTime.now(clock).format(STAMP), SUBTITLE);
        p.setSpacingBefore(8);
        doc.add(p);
    }

    private static PdfPTable table(float[] widths) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);   // repeat the header on every page
        return table;
    }

    private static void header(PdfPTable table, String... titles) {
        for (String t : titles) {
            PdfPCell c = new PdfPCell(new Phrase(t, HEAD));
            c.setBackgroundColor(HEADER_BG);
            c.setBorderColor(LINE);
            c.setBorderWidth(0.5f);
            c.setPadding(4);
            table.addCell(c);
        }
    }

    private static void cell(PdfPTable table, String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setHorizontalAlignment(align);
        c.setBorderColor(LINE);
        c.setBorderWidth(0.5f);
        c.setPadding(4);
        table.addCell(c);
    }

    private static void statusCell(PdfPTable table, Status status) {
        String text;
        Color color;
        switch (status) {
            case DEFAULTER -> { text = "Defaulter"; color = RED; }
            case AT_RISK -> { text = "At risk"; color = AMBER; }
            case SAFE -> { text = "Safe"; color = GREEN; }
            default -> { text = "-"; color = MUTED; }
        }
        cell(table, text, colored(color), Element.ALIGN_LEFT);
    }

    private static void emptyRow(PdfPTable table, int columns) {
        PdfPCell c = new PdfPCell(new Phrase("No students.", CELL_MUTED));
        c.setColspan(columns);
        c.setBorderColor(LINE);
        c.setPadding(6);
        table.addCell(c);
    }

    private static Font colored(Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, color);
    }

    private static String percent(Double p) {
        return p == null ? "-" : String.format(Locale.ENGLISH, "%.1f", p);
    }
}
