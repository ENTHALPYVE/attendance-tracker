package com.college.attendance.dto;

import com.college.attendance.model.Holiday;

import java.time.LocalDate;

/** cancelledSessions: how many sessions on that date are cancelled because of the holiday. */
public record HolidayDto(Long id, LocalDate date, String reason, int cancelledSessions) {

    public static HolidayDto from(Holiday h, int cancelledSessions) {
        return new HolidayDto(h.getId(), h.getDate(), h.getReason(), cancelledSessions);
    }
}
