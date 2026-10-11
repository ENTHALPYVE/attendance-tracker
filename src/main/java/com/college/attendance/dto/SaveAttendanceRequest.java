package com.college.attendance.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Saving a session: only the absent students are sent; everyone else in the list is present.
 * (Usually only a few students are absent, so this keeps the request small.)
 */
public record SaveAttendanceRequest(@NotNull(message = "Send the list of absent students (may be empty).")
                                    List<Long> absentStudentIds) {
}
