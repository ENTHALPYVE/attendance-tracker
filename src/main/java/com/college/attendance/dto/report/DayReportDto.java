package com.college.attendance.dto.report;

import com.college.attendance.service.calc.AttendanceCalculator.Status;

import java.time.LocalDate;
import java.util.List;

/**
 * Day-wise attendance of a class on one date.
 * Only TAKEN sessions count; sessionsPending tells how many are still to be marked.
 */
public record DayReportDto(Long classId, String className, LocalDate date, int threshold,
                           boolean countFreeSlotsAsPresent, String holiday,
                           int sessionsTaken, int sessionsPending, int sessionsCancelled,
                           List<Row> rows) {

    /**
     * One student. freeSlots = slots in which only other batches had a session.
     * absentIn lists what the student missed, e.g. ["AOA", "Java Lab"].
     */
    public record Row(Long studentId, String rollNo, String name, String batchName,
                      int attended, int held, int freeSlots, Double percent, Status status,
                      List<String> absentIn) {
    }
}
