package com.college.attendance.dto;

import com.college.attendance.model.SessionType;

import java.time.LocalDate;
import java.util.List;

/**
 * One card on the teacher dashboard: a subject the teacher teaches to a class (theory)
 * or to a batch (lab), over the semester so far.
 *  - averagePercent: average of the students' % in this subject part
 *  - defaulters: students below the threshold in this subject part
 *  - pendingCount: sessions of this group assigned to the teacher (up to today) still not marked;
 *    pending lists the oldest few of them so they can be opened directly
 */
public record TeacherCardDto(Long classId, String className, Long subjectId, String subjectCode, String subjectName,
                             SessionType sessionType, Long batchId, String batchName,
                             int students, int sessionsTaken, Double averagePercent, int defaulters,
                             int pendingCount, List<PendingSession> pending) {

    public record PendingSession(Long id, LocalDate date, int slotNo) {
    }
}
