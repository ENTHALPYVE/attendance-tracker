package com.college.attendance.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Give a session to a substitute teacher.
 *  - For an existing session only teacherId is needed.
 *  - For an upcoming day (sessions not created yet) send timetableEntryId and date too.
 * allowClash = true saves even if the substitute is busy at that time (after the warning).
 */
public record SubstituteRequest(@NotNull(message = "Choose a teacher.") Long teacherId,
                                Long timetableEntryId, LocalDate date, boolean allowClash) {
}
