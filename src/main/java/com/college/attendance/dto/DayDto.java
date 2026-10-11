package com.college.attendance.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One class on one date.
 *  - open: sessions exist for this date and can be marked (semester start .. today).
 *  - holiday: the reason if the date is a holiday, else null.
 */
public record DayDto(LocalDate date, boolean open, String holiday, List<SessionDto> sessions) {
}
