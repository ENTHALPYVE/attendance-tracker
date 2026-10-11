package com.college.attendance.dto;

import com.college.attendance.model.SessionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * An extra lecture or lab that is not in the timetable.
 * teacherId is only used when an admin adds it; a teacher always adds it for themselves.
 */
public record ExtraSessionRequest(
        @NotNull(message = "Choose a class.") Long classId,
        @NotNull(message = "Choose a date.") LocalDate date,
        @Min(value = 1, message = "Slot must be between 1 and 12.")
        @Max(value = 12, message = "Slot must be between 1 and 12.") int slotNo,
        @NotNull(message = "Enter a start time.") LocalTime startTime,
        @NotNull(message = "Enter an end time.") LocalTime endTime,
        @NotNull(message = "Choose a subject.") Long subjectId,
        @NotNull(message = "Choose Theory or Lab.") SessionType sessionType,
        Long batchId,
        @Size(max = 30, message = "Room can be at most 30 characters.") String room,
        Long teacherId) {
}
