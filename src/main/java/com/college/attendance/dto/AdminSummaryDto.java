package com.college.attendance.dto;

/** Counts shown on the admin home page. */
public record AdminSummaryDto(long classes, long students, long subjects, long teachers) {
}
