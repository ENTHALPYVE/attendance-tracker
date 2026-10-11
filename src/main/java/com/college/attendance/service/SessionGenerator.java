package com.college.attendance.service;

import com.college.attendance.model.ClassSession;
import com.college.attendance.model.SessionStatus;
import com.college.attendance.model.TimetableEntry;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.HolidayRepository;
import com.college.attendance.repository.TimetableEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

/**
 * Creates the dated sessions for a day from the weekly timetable.
 *
 * Called whenever a date is opened (teacher's day list, class day view). It only adds
 * sessions that are missing, so calling it again for the same date changes nothing.
 *
 *  - Only for dates from the semester start up to today (not the future).
 *  - On a holiday the sessions are created as CANCELLED.
 *  - A slot that already has a session (e.g. an extra lecture, or a session made from an
 *    older version of the timetable) is not filled again.
 */
@Service
public class SessionGenerator {

    private final TimetableEntryRepository entryRepository;
    private final ClassSessionRepository sessionRepository;
    private final HolidayRepository holidayRepository;
    private final SettingsService settingsService;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public SessionGenerator(TimetableEntryRepository entryRepository, ClassSessionRepository sessionRepository,
                            HolidayRepository holidayRepository, SettingsService settingsService,
                            PlatformTransactionManager transactionManager, Clock clock) {
        this.entryRepository = entryRepository;
        this.sessionRepository = sessionRepository;
        this.holidayRepository = holidayRepository;
        this.settingsService = settingsService;
        this.transaction = new TransactionTemplate(transactionManager);
        // Always a separate transaction that commits before the method returns.
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** True if sessions can exist for this date (semester start .. today). */
    public boolean isOpenDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(clock))) {
            return false;
        }
        return settingsService.semesterStart().map(start -> !date.isBefore(start)).orElse(true);
    }

    /**
     * Creates the missing sessions of all classes for the date. Returns how many were created.
     * "synchronized" + its own transaction: two teachers opening the same day at the same moment
     * cannot both create the same session (the first one commits before the second one checks).
     */
    public synchronized int ensureSessions(LocalDate date) {
        if (!isOpenDate(date)) {
            return 0;
        }
        Integer created = transaction.execute(status -> createMissing(date));
        return created == null ? 0 : created;
    }

    private int createMissing(LocalDate date) {
        List<TimetableEntry> entries = entryRepository.findByDayOfWeek(date.getDayOfWeek());
        if (entries.isEmpty()) {
            return 0;
        }

        // What already exists that day, grouped by "classId:slotNo".
        Map<String, List<ClassSession>> existing = new HashMap<>();
        for (ClassSession s : sessionRepository.findBySessionDate(date)) {
            existing.computeIfAbsent(key(s.getCollegeClass().getId(), s.getSlotNo()), k -> new ArrayList<>()).add(s);
        }

        boolean holiday = holidayRepository.findByDate(date).isPresent();
        List<ClassSession> toCreate = new ArrayList<>();
        for (TimetableEntry entry : entries) {
            List<ClassSession> inSlot = existing.computeIfAbsent(
                    key(entry.getCollegeClass().getId(), entry.getSlotNo()), k -> new ArrayList<>());
            Long batchId = entry.getBatch() == null ? null : entry.getBatch().getId();
            if (!SlotRules.fits(inSlot, entry.getSessionType(), batchId)) {
                continue;
            }
            ClassSession session = ClassSession.fromTimetable(entry, date);
            if (holiday) {
                session.setStatus(SessionStatus.CANCELLED);
            }
            inSlot.add(session);
            toCreate.add(session);
        }
        sessionRepository.saveAll(toCreate);
        return toCreate.size();
    }

    private static String key(Long classId, int slotNo) {
        return classId + ":" + slotNo;
    }
}
