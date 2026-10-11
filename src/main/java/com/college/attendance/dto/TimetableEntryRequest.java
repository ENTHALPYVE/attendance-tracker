package com.college.attendance.dto;

import com.college.attendance.model.SessionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Create or change one timetable entry.
 * batchId must be empty for THEORY and set for LAB.
 * allowClash = true saves even if the teacher is busy elsewhere at that time
 * (the admin has seen the warning and confirmed).
 */
public record TimetableEntryRequest(
        @NotNull(message = "Choose a class.") Long classId,
        @NotNull(message = "Choose a day.") DayOfWeek dayOfWeek,
        @Min(value = 1, message = "Slot must be between 1 and 12.")
        @Max(value = 12, message = "Slot must be between 1 and 12.") int slotNo,
        @NotNull(message = "Enter a start time.") LocalTime startTime,
        @NotNull(message = "Enter an end time.") LocalTime endTime,
        @NotNull(message = "Choose a subject.") Long subjectId,
        @NotNull(message = "Choose Theory or Lab.") SessionType sessionType,
        Long batchId,
        @NotNull(message = "Choose a teacher.") Long teacherId,
        @Size(max = 30, message = "Room can be at most 30 characters.") String room,
        boolean allowClash) {
}
