package com.college.attendance.dto.report;

import com.college.attendance.service.calc.AttendanceCalculator.Status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Attendance of a class over a date range (a month, a week, the semester).
 * Overall % = total attended / total held in the range; subject columns show theory, lab and combined.
 */
public record RangeReportDto(Long classId, String className, String title, LocalDate from, LocalDate to,
                             int threshold, boolean countFreeSlotsAsPresent, int sessionsTaken,
                             List<SubjectColumn> subjects, List<Row> rows) {

    /** A subject that had sessions in the range; hasTheory / hasLab tell which parts were held. */
    public record SubjectColumn(Long subjectId, String code, String name, boolean hasTheory, boolean hasLab) {
    }

    /** One student's numbers for one subject. Percentages are null when that part was not held. */
    public record SubjectCell(int theoryAttended, int theoryHeld, Double theoryPercent,
                              int labAttended, int labHeld, Double labPercent,
                              Double combinedPercent, boolean defaulter) {
    }

    /**
     * One student.
     *  - needed: sessions to attend in a row to reach the threshold (when below it)
     *  - canMiss: sessions that can be missed and still stay at the threshold (when at or above it)
     *  - belowIn: the subject parts below the threshold, e.g. ["AOA Lab", "COA Theory"]
     *  - subjects: keyed by subject id
     */
    public record Row(Long studentId, String rollNo, String name, String batchName,
                      int attended, int held, Double percent, Status status,
                      Integer needed, Integer canMiss, List<String> belowIn,
                      Map<Long, SubjectCell> subjects) {
    }
}
