package com.college.attendance.service;

import com.college.attendance.dto.DayDto;
import com.college.attendance.dto.SessionDto;
import com.college.attendance.model.AttendanceStatus;
import com.college.attendance.model.ClassSession;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.HolidayRepository;
import com.college.attendance.repository.TimetableEntryRepository;
import com.college.attendance.util.RollNumbers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;

/**
 * Reading the sessions of a day: one class's day (class timetable view) and
 * one teacher's day ("My sessions today").
 *
 * First the missing sessions are created (in their own transaction), then they are read
 * in a new read-only transaction, so the freshly created rows are always visible.
 */
@Service
public class SessionQueryService {

    private static final Comparator<SessionDto> BY_SLOT_AND_BATCH =
            Comparator.comparingInt(SessionDto::slotNo)
                    .thenComparing(s -> s.batchName() == null ? "" : s.batchName(), RollNumbers.NATURAL);

    private final SessionGenerator generator;
    private final ClassSessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final TimetableEntryRepository entryRepository;
    private final HolidayRepository holidayRepository;
    private final ClassService classService;
    private final TransactionTemplate readOnly;

    public SessionQueryService(SessionGenerator generator, ClassSessionRepository sessionRepository,
                               AttendanceRecordRepository attendanceRepository,
                               TimetableEntryRepository entryRepository, HolidayRepository holidayRepository,
                               ClassService classService, PlatformTransactionManager transactionManager) {
        this.generator = generator;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.entryRepository = entryRepository;
        this.holidayRepository = holidayRepository;
        this.classService = classService;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * One class on one date. For dates that are open (semester start .. today) these are the real
     * sessions; for future dates the timetable entries are returned as "planned" rows.
     */
    public DayDto classDay(Long classId, LocalDate date) {
        classService.findClass(classId);
        boolean open = generator.isOpenDate(date);
        generator.ensureSessions(date);
        return readOnly.execute(status -> {
            String holiday = holidayRepository.findByDate(date).map(h -> h.getReason()).orElse(null);
            List<SessionDto> sessions;
            if (open) {
                sessions = withCounts(sessionRepository.findByCollegeClassIdAndSessionDate(classId, date));
            } else {
                // Future: sessions arranged in advance (e.g. with a substitute) + the rest of the timetable.
                List<ClassSession> arranged = sessionRepository.findByCollegeClassIdAndSessionDate(classId, date);
                Set<Long> arrangedEntries = new HashSet<>();
                arranged.forEach(s -> { if (s.getTimetableEntry() != null) arrangedEntries.add(s.getTimetableEntry().getId()); });
                sessions = new ArrayList<>(withCounts(arranged));
                entryRepository.findByCollegeClassIdAndDayOfWeek(classId, date.getDayOfWeek()).stream()
                        .filter(e -> !arrangedEntries.contains(e.getId()))
                        .map(e -> SessionDto.planned(e, date))
                        .forEach(sessions::add);
            }
            return new DayDto(date, open, holiday,
                    sessions.stream().sorted(BY_SLOT_AND_BATCH).toList());
        });
    }

    /** A teacher's sessions on one date across all classes, in time order. */
    public List<SessionDto> teacherDay(Long teacherId, LocalDate date) {
        generator.ensureSessions(date);
        return readOnly.execute(status ->
                withCounts(sessionRepository.findByTeacherIdAndSessionDate(teacherId, date)).stream()
                        .sorted(Comparator.comparing(SessionDto::startTime, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(SessionDto::className)
                                .thenComparing(BY_SLOT_AND_BATCH))
                        .toList());
    }

    /** Converts to DTOs, adding the present / absent counts of saved sessions (one query for all). */
    private List<SessionDto> withCounts(List<ClassSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, int[]> counts = new HashMap<>();   // sessionId -> {present, absent}
        List<Long> ids = sessions.stream().map(ClassSession::getId).toList();
        for (Object[] row : attendanceRepository.countBySession(ids)) {
            int[] c = counts.computeIfAbsent((Long) row[0], k -> new int[2]);
            c[row[1] == AttendanceStatus.PRESENT ? 0 : 1] = ((Long) row[2]).intValue();
        }
        List<SessionDto> result = new ArrayList<>();
        for (ClassSession s : sessions) {
            int[] c = counts.get(s.getId());
            result.add(c == null ? SessionDto.from(s) : SessionDto.from(s, c[0], c[1]));
        }
        return result;
    }
}
