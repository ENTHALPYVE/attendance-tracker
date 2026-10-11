package com.college.attendance.dto;

import java.util.List;

/** A class's weekly timetable: its batches (grid columns inside a cell) and all entries. */
public record TimetableDto(Long classId, String className, List<BatchDto> batches, List<TimetableEntryDto> entries) {
}
