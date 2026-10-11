package com.college.attendance.service;

import com.college.attendance.dto.AttendanceSheetDto;
import com.college.attendance.dto.SaveAttendanceRequest;
import com.college.attendance.dto.SessionDto;
import com.college.attendance.exception.BadRequestException;
import com.college.attendance.model.*;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.StudentRepository;
import com.college.attendance.security.AppUserPrincipal;
import com.college.attendance.util.RollNumbers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

/**
 * Marking attendance for one session.
 *
 *  - THEORY: every student of the class; LAB: only the students of that batch.
 *  - A session that has not been saved yet starts with everyone PRESENT.
 *  - Saving sends only the absent students; all others are stored as PRESENT,
 *    and the session becomes TAKEN. Saving again (editing) overwrites the old marks.
 */
@Service
@Transactional
public class AttendanceService {

    private final SessionService sessionService;
    private final StudentRepository studentRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final Clock clock;

    public AttendanceService(SessionService sessionService, StudentRepository studentRepository,
                             AttendanceRecordRepository attendanceRepository, Clock clock) {
        this.sessionService = sessionService;
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.clock = clock;
    }

    /** The student list of a session with each student's current mark. */
    @Transactional(readOnly = true)
    public AttendanceSheetDto sheet(Long sessionId, AppUserPrincipal user) {
        ClassSession session = sessionService.findForChange(sessionId, user);
        return buildSheet(session);
    }

    /** Saves the marks: the given students are ABSENT, everyone else in the list is PRESENT. */
    public AttendanceSheetDto save(Long sessionId, SaveAttendanceRequest request, AppUserPrincipal user) {
        ClassSession session = sessionService.findForChange(sessionId, user);
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new BadRequestException("This session is cancelled. Reopen it before marking attendance.");
        }
        if (session.getSessionDate().isAfter(LocalDate.now(clock))) {
            throw new BadRequestException("Attendance cannot be marked for a future date.");
        }

        List<Student> students = studentsOf(session);
        Set<Long> inList = new HashSet<>();
        students.forEach(s -> inList.add(s.getId()));
        Set<Long> absent = new HashSet<>(request.absentStudentIds());
        if (!inList.containsAll(absent)) {
            throw new BadRequestException("Some of the absent students are not in this session's list. Reload the page.");
        }

        Map<Long, AttendanceRecord> existing = new HashMap<>();
        for (AttendanceRecord r : attendanceRepository.findBySessionId(sessionId)) {
            existing.put(r.getStudent().getId(), r);
        }
        List<AttendanceRecord> toSave = new ArrayList<>();
        for (Student student : students) {
            AttendanceStatus status = absent.contains(student.getId()) ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;
            AttendanceRecord record = existing.get(student.getId());
            if (record == null) {
                toSave.add(new AttendanceRecord(session, student, status));
            } else {
                record.setStatus(status);   // saved automatically at the end of the transaction
            }
        }
        attendanceRepository.saveAll(toSave);
        session.setStatus(SessionStatus.TAKEN);
        attendanceRepository.flush();
        return buildSheet(session);
    }

    // ---------------------------------------------------------------- helpers

    private AttendanceSheetDto buildSheet(ClassSession session) {
        Map<Long, AttendanceStatus> saved = new HashMap<>();
        for (AttendanceRecord r : attendanceRepository.findBySessionId(session.getId())) {
            saved.put(r.getStudent().getId(), r.getStatus());
        }
        List<AttendanceSheetDto.Row> rows = studentsOf(session).stream()
                .map(s -> new AttendanceSheetDto.Row(s.getId(), s.getRollNo(), s.getName(), s.getBatch().getName(),
                        saved.getOrDefault(s.getId(), AttendanceStatus.PRESENT)))
                .toList();

        int present = (int) rows.stream().filter(r -> r.status() == AttendanceStatus.PRESENT).count();
        SessionDto dto = saved.isEmpty()
                ? SessionDto.from(session)
                : SessionDto.from(session, present, rows.size() - present);
        return new AttendanceSheetDto(dto, true, !saved.isEmpty(), rows);
    }

    /** THEORY: the whole class. LAB: only that batch. Sorted by roll number. */
    private List<Student> studentsOf(ClassSession session) {
        List<Student> students = session.getSessionType() == SessionType.THEORY
                ? studentRepository.findByCollegeClassId(session.getCollegeClass().getId())
                : studentRepository.findByBatchId(session.getBatch().getId());
        return students.stream().sorted(RollNumbers.BY_ROLL_NO).toList();
    }
}
