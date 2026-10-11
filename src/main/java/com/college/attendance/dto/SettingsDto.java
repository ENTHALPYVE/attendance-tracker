package com.college.attendance.dto;

import java.time.LocalDate;

/** Settings the pages need (read-only for everyone). */
public record SettingsDto(int threshold, boolean countFreeSlotsAsPresent, LocalDate semesterStart) {
}
