package com.college.attendance.dto;

import com.college.attendance.model.AttendanceStatus;

import java.util.List;

/**
 * The student list for marking one session.
 *  - canEdit: the current user may mark it (their own session, or admin).
 *  - saved: attendance has been saved before (the statuses are the saved ones);
 *    otherwise every student starts as PRESENT.
 */
public record AttendanceSheetDto(SessionDto session, boolean canEdit, boolean saved, List<Row> students) {

    public record Row(Long studentId, String rollNo, String name, String batchName, AttendanceStatus status) {
    }
}
