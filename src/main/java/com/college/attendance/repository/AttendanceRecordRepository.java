package com.college.attendance.repository;

import com.college.attendance.model.AttendanceRecord;
import com.college.attendance.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    boolean existsBySessionId(Long sessionId);

    List<AttendanceRecord> findBySessionId(Long sessionId);

    /**
     * Rows of [studentId, sessionId, status] for the TAKEN sessions of a class in a date range.
     * This is all the data the report calculations need.
     */
    @Query("select a.student.id, a.session.id, a.status from AttendanceRecord a "
            + "where a.session.collegeClass.id = :classId and a.session.status = :status "
            + "and a.session.sessionDate between :from and :to")
    List<Object[]> marksOfClass(Long classId, SessionStatus status, LocalDate from, LocalDate to);

    /** Rows of [sessionId, status, count] for showing "18 present / 2 absent" on session cards. */
    @Query("select a.session.id, a.status, count(a) from AttendanceRecord a "
            + "where a.session.id in :sessionIds group by a.session.id, a.status")
    List<Object[]> countBySession(Collection<Long> sessionIds);
}
