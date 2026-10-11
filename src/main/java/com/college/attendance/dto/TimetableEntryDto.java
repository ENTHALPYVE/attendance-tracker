package com.college.attendance.dto;

import com.college.attendance.model.SessionType;
import com.college.attendance.model.TimetableEntry;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record TimetableEntryDto(Long id, DayOfWeek dayOfWeek, int slotNo, LocalTime startTime, LocalTime endTime,
                                Long subjectId, String subjectCode, String subjectName, SessionType sessionType,
                                Long batchId, String batchName, Long teacherId, String teacherName, String room) {

    public static TimetableEntryDto from(TimetableEntry e) {
        return new TimetableEntryDto(e.getId(), e.getDayOfWeek(), e.getSlotNo(), e.getStartTime(), e.getEndTime(),
                e.getSubject().getId(), e.getSubject().getCode(), e.getSubject().getName(), e.getSessionType(),
                e.getBatch() == null ? null : e.getBatch().getId(),
                e.getBatch() == null ? null : e.getBatch().getName(),
                e.getTeacher().getId(), e.getTeacher().getName(), e.getRoom());
    }
}
