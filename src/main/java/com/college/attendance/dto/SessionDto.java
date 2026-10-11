package com.college.attendance.dto;

import com.college.attendance.model.ClassSession;
import com.college.attendance.model.SessionStatus;
import com.college.attendance.model.SessionType;
import com.college.attendance.model.TimetableEntry;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One dated session as shown on the timetable and the teacher's "My sessions today" list.
 *  - extra = true when it was added by hand and is not in the timetable.
 *  - presentCount / absentCount are filled once attendance has been saved.
 *  - For a future date, "planned" rows come straight from the timetable: id and status are null.
 *  - timetableEntryId links the row to its timetable entry (null for extra sessions).
 */
public record SessionDto(Long id, Long classId, String className, LocalDate date, int slotNo,
                         LocalTime startTime, LocalTime endTime,
                         Long subjectId, String subjectCode, String subjectName, SessionType sessionType,
                         Long batchId, String batchName, Long teacherId, String teacherName, String room,
                         SessionStatus status, boolean extra, Integer presentCount, Integer absentCount,
                         Long timetableEntryId) {

    public static SessionDto from(ClassSession s) {
        return from(s, null, null);
    }

    public static SessionDto from(ClassSession s, Integer present, Integer absent) {
        return new SessionDto(s.getId(), s.getCollegeClass().getId(), s.getCollegeClass().getName(),
                s.getSessionDate(), s.getSlotNo(), s.getStartTime(), s.getEndTime(),
                s.getSubject().getId(), s.getSubject().getCode(), s.getSubject().getName(), s.getSessionType(),
                s.getBatch() == null ? null : s.getBatch().getId(),
                s.getBatch() == null ? null : s.getBatch().getName(),
                s.getTeacher().getId(), s.getTeacher().getName(), s.getRoom(),
                s.getStatus(), s.getTimetableEntry() == null, present, absent,
                s.getTimetableEntry() == null ? null : s.getTimetableEntry().getId());
    }

    /** A timetable entry shown for a date that has no sessions yet (future dates). */
    public static SessionDto planned(TimetableEntry e, LocalDate date) {
        return new SessionDto(null, e.getCollegeClass().getId(), e.getCollegeClass().getName(),
                date, e.getSlotNo(), e.getStartTime(), e.getEndTime(),
                e.getSubject().getId(), e.getSubject().getCode(), e.getSubject().getName(), e.getSessionType(),
                e.getBatch() == null ? null : e.getBatch().getId(),
                e.getBatch() == null ? null : e.getBatch().getName(),
                e.getTeacher().getId(), e.getTeacher().getName(), e.getRoom(),
                null, false, null, null, e.getId());
    }
}
