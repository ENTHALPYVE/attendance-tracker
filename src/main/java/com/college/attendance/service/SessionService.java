package com.college.attendance.service;

import com.college.attendance.dto.ExtraSessionRequest;
import com.college.attendance.dto.SessionDto;
import com.college.attendance.exception.BadRequestException;
import com.college.attendance.exception.ClashException;
import com.college.attendance.exception.ForbiddenException;
import com.college.attendance.exception.NotFoundException;
import com.college.attendance.model.*;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.HolidayRepository;
import com.college.attendance.repository.TimetableEntryRepository;
import com.college.attendance.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.college.attendance.util.Text.clean;

/**
 * Actions on a single session: cancel, reopen, add an extra session, delete an extra session.
 *
 * Permission rule (checked here in the backend, not only in the pages):
 * a teacher may act only on sessions assigned to them; an admin may act on any session.
 */
@Service
@Transactional
public class SessionService {

    private final ClassSessionRepository sessionRepository;
    private final TimetableEntryRepository timetableRepository;
    private final HolidayRepository holidayRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final ClassService classService;
    private final SubjectService subjectService;
    private final TeacherService teacherService;
    private final SessionGenerator generator;
    private final Clock clock;

    public SessionService(ClassSessionRepository sessionRepository, TimetableEntryRepository timetableRepository,
                          HolidayRepository holidayRepository, AttendanceRecordRepository attendanceRepository,
                          ClassService classService,
                          SubjectService subjectService, TeacherService teacherService,
                          SessionGenerator generator, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.timetableRepository = timetableRepository;
        this.holidayRepository = holidayRepository;
        this.attendanceRepository = attendanceRepository;
        this.classService = classService;
        this.subjectService = subjectService;
        this.teacherService = teacherService;
        this.generator = generator;
        this.clock = clock;
    }

    /** Loads a session and checks that the user may change it. */
    public ClassSession findForChange(Long sessionId, AppUserPrincipal user) {
        ClassSession s = sessionRepository.findWithDetailsById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found."));
        if (!canChange(s, user)) {
            throw new ForbiddenException("This session is assigned to " + s.getTeacher().getName()
                    + ". Only that teacher or an admin can change it.");
        }
        return s;
    }

    public static boolean canChange(ClassSession s, AppUserPrincipal user) {
        return user.isAdmin() || s.getTeacher().getId().equals(user.getId());
    }

    /** Marks the session as not held. Saved attendance is kept but does not count while cancelled. */
    public SessionDto cancel(Long sessionId, AppUserPrincipal user) {
        ClassSession s = findForChange(sessionId, user);
        s.setStatus(SessionStatus.CANCELLED);
        return SessionDto.from(s);
    }

    /** Undoes a cancel: back to TAKEN if attendance had been saved, otherwise PENDING. */
    public SessionDto reopen(Long sessionId, AppUserPrincipal user) {
        ClassSession s = findForChange(sessionId, user);
        if (s.getStatus() != SessionStatus.CANCELLED) {
            throw new BadRequestException("Only a cancelled session can be reopened.");
        }
        s.setStatus(attendanceRepository.existsBySessionId(s.getId()) ? SessionStatus.TAKEN : SessionStatus.PENDING);
        return SessionDto.from(s);
    }

    /**
     * Adds a lecture or lab that is not in the timetable.
     * Call SessionGenerator.ensureSessions(date) first, outside this transaction.
     */
    public SessionDto createExtra(ExtraSessionRequest r, AppUserPrincipal user) {
        CollegeClass c = classService.findClass(r.classId());
        if (!generator.isOpenDate(r.date())) {
            throw new BadRequestException(r.date().isAfter(LocalDate.now(clock))
                    ? "An extra session cannot be in the future. Add it on the day it is held."
                    : "That date is before the start of the semester.");
        }
        if (!r.endTime().isAfter(r.startTime())) {
            throw new BadRequestException("End time must be after start time.");
        }
        Subject subject = subjectService.find(r.subjectId());
        Batch batch = null;
        if (r.sessionType() == SessionType.THEORY) {
            if (!subject.isHasTheory()) {
                throw new BadRequestException(subject.getCode() + " has no theory part.");
            }
        } else {
            if (!subject.isHasLab()) {
                throw new BadRequestException(subject.getCode() + " has no lab part.");
            }
            if (r.batchId() == null) {
                throw new BadRequestException("Choose the batch for this lab.");
            }
            batch = classService.findBatch(r.batchId());
            if (!batch.getCollegeClass().getId().equals(c.getId())) {
                throw new BadRequestException("That batch belongs to another class.");
            }
        }

        // Teachers add sessions for themselves; an admin chooses the teacher.
        User teacher;
        if (user.isAdmin()) {
            if (r.teacherId() == null) {
                throw new BadRequestException("Choose the teacher who takes this session.");
            }
            teacher = teacherService.find(r.teacherId());
        } else {
            teacher = teacherService.find(user.getId());
        }

        // The day's timetable sessions were created by the controller before this transaction began,
        // so this check sees them.
        var inSlot = sessionRepository.findByCollegeClassIdAndSessionDateAndSlotNo(c.getId(), r.date(), r.slotNo());
        if (!SlotRules.fits(inSlot, r.sessionType(), batch == null ? null : batch.getId())) {
            throw new BadRequestException("Slot " + r.slotNo() + " of " + c.getName() + " on this date already has "
                    + (r.sessionType() == SessionType.THEORY ? "a session" : "a session for this batch")
                    + ". Use another slot number.");
        }

        // An extra session on a holiday stays PENDING: it was held on purpose.
        ClassSession s = ClassSession.extra(c, subject, r.sessionType(), batch, teacher, r.date(), r.slotNo(),
                r.startTime(), r.endTime(), emptyToNull(r.room()));
        return SessionDto.from(sessionRepository.save(s));
    }

    // ---------------------------------------------------------------- substitute teacher (admin)

    /** Gives an existing session to another teacher. */
    public SessionDto reassign(Long sessionId, Long teacherId, boolean allowClash) {
        ClassSession s = sessionRepository.findWithDetailsById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found."));
        User substitute = teacherService.find(teacherId);
        if (!allowClash) {
            checkTeacherFree(substitute, s.getSessionDate(), s.getStartTime(), s.getEndTime(), s.getId(), null);
        }
        s.setTeacher(substitute);
        return SessionDto.from(s);
    }

    /**
     * Arranges a substitute for an upcoming day (up to 14 days ahead): the session is created now
     * from the timetable entry, with the substitute as its teacher. On the day itself the
     * timetable will not create it again, because the slot is already filled.
     */
    public SessionDto reassignUpcoming(Long entryId, LocalDate date, Long teacherId, boolean allowClash) {
        LocalDate today = LocalDate.now(clock);
        if (date == null || !date.isAfter(today) || date.isAfter(today.plusDays(14))) {
            throw new BadRequestException("A substitute can be arranged for the next 14 days.");
        }
        TimetableEntry entry = timetableRepository.findById(entryId)
                .orElseThrow(() -> new NotFoundException("Timetable entry not found."));
        if (entry.getDayOfWeek() != date.getDayOfWeek()) {
            throw new BadRequestException("That entry is not on " + date.getDayOfWeek() + ".");
        }
        if (holidayRepository.findByDate(date).isPresent()) {
            throw new BadRequestException("That date is a holiday.");
        }
        User substitute = teacherService.find(teacherId);
        if (!allowClash) {
            checkTeacherFree(substitute, date, entry.getStartTime(), entry.getEndTime(), null, entry.getId());
        }
        List<ClassSession> inSlot = sessionRepository.findByCollegeClassIdAndSessionDateAndSlotNo(
                entry.getCollegeClass().getId(), date, entry.getSlotNo());
        ClassSession session = inSlot.stream()
                .filter(x -> x.getTimetableEntry() != null && x.getTimetableEntry().getId().equals(entry.getId()))
                .findFirst()
                .orElseGet(() -> {
                    Long batchId = entry.getBatch() == null ? null : entry.getBatch().getId();
                    if (!SlotRules.fits(inSlot, entry.getSessionType(), batchId)) {
                        throw new BadRequestException("That slot already has another session on this date.");
                    }
                    return sessionRepository.save(ClassSession.fromTimetable(entry, date));
                });
        session.setTeacher(substitute);
        return SessionDto.from(session);
    }

    /**
     * The substitute must not already teach at an overlapping time that day:
     * neither a session on that date, nor a timetable entry of that weekday that has no session yet.
     */
    private void checkTeacherFree(User teacher, LocalDate date, LocalTime start, LocalTime end,
                                  Long ignoreSessionId, Long ignoreEntryId) {
        if (start == null || end == null) return;
        List<ClassSession> sessions = sessionRepository.findByTeacherIdAndSessionDate(teacher.getId(), date);
        for (ClassSession other : sessions) {
            if (other.getId().equals(ignoreSessionId) || other.getStatus() == SessionStatus.CANCELLED) continue;
            if (overlaps(start, end, other.getStartTime(), other.getEndTime())) {
                throw new ClashException(teacher.getName() + " already has " + other.getCollegeClass().getName() + " "
                        + other.getSubject().getCode() + " at " + other.getStartTime() + "-" + other.getEndTime() + " that day.");
            }
        }
        Set<Long> entriesWithSession = new HashSet<>();
        sessionRepository.findBySessionDate(date).forEach(x -> {
            if (x.getTimetableEntry() != null) entriesWithSession.add(x.getTimetableEntry().getId());
        });
        for (TimetableEntry e : timetableRepository.findByTeacherIdAndDayOfWeek(teacher.getId(), date.getDayOfWeek())) {
            if (e.getId().equals(ignoreEntryId) || entriesWithSession.contains(e.getId())) continue;
            if (overlaps(start, end, e.getStartTime(), e.getEndTime())) {
                throw new ClashException(teacher.getName() + " already has " + e.getCollegeClass().getName() + " "
                        + e.getSubject().getCode() + " at " + e.getStartTime() + "-" + e.getEndTime() + " that day.");
            }
        }
    }

    private static boolean overlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        return s2 != null && e2 != null && s1.isBefore(e2) && s2.isBefore(e1);
    }

    /** Deletes an extra session added by mistake (timetable sessions can only be cancelled). */
    public void deleteExtra(Long sessionId, AppUserPrincipal user) {
        ClassSession s = findForChange(sessionId, user);
        if (s.getTimetableEntry() != null) {
            throw new BadRequestException("Timetable sessions cannot be deleted. Mark it as cancelled instead.");
        }
        sessionRepository.delete(s);   // its attendance rows go with it (ON DELETE CASCADE)
    }

    private static String emptyToNull(String text) {
        String cleaned = clean(text);
        return cleaned.isEmpty() ? null : cleaned;
    }
}
