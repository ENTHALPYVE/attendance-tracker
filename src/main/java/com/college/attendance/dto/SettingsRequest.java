package com.college.attendance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Attendance rules the admin can change. */
public record SettingsRequest(
        @Min(value = 50, message = "Threshold must be between 50 and 95.")
        @Max(value = 95, message = "Threshold must be between 50 and 95.") int threshold,
        boolean countFreeSlotsAsPresent,
        @NotNull(message = "Choose the first day of the semester.") LocalDate semesterStart) {
}
