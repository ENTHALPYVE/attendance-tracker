package com.college.attendance.dto.report;

import com.college.attendance.service.calc.AttendanceCalculator.Status;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything on the student detail page for one date range (default: the whole semester).
 *  - overall: totals, status and the "needs X more / can miss Y" numbers
 *  - subjects: theory, lab and combined per subject
 *  - days: one entry per day with attendance (for the calendar)
 *  - weeks: per-week % and running total % (for the trend chart)
 */
public record StudentReportDto(Long studentId, String rollNo, String name, Long classId, String className,
                               String batchName, LocalDate from, LocalDate to, String title, int threshold,
                               boolean countFreeSlotsAsPresent, Overall overall,
                               List<SubjectRow> subjects, List<Day> days, List<Week> weeks) {

    public record Overall(int attended, int held, Double percent, Status status, Integer needed, Integer canMiss) {
    }

    public record SubjectRow(Long subjectId, String code, String name,
                             int theoryAttended, int theoryHeld, Double theoryPercent,
                             int labAttended, int labHeld, Double labPercent,
                             int combinedAttended, int combinedHeld, Double combinedPercent,
                             boolean defaulter) {
    }

    public record Day(LocalDate date, int attended, int held, int freeSlots, Double percent, Status status,
                      List<String> absentIn) {
    }

    /** weekStart is a Monday; cumulativePercent is the total from the start of the range to that week. */
    public record Week(LocalDate weekStart, int attended, int held, Double percent, Double cumulativePercent) {
    }
}
