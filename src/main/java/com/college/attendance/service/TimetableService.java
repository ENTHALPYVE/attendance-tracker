package com.college.attendance.service;

import com.college.attendance.dto.TimetableDto;
import com.college.attendance.dto.TimetableEntryDto;
import com.college.attendance.dto.TimetableEntryRequest;
import com.college.attendance.exception.BadRequestException;
import com.college.attendance.exception.ClashException;
import com.college.attendance.exception.NotFoundException;
import com.college.attendance.model.*;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.TimetableEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.college.attendance.util.Text.clean;

/**
 * The weekly timetable of a class.
 *
 * Rules for one cell (class + day + slot):
 *   - either ONE theory entry for the whole class,
 *   - or up to ONE lab entry per batch (different batches in different labs at the same time).
 * The subject must have the chosen part (theory / lab).
 * A teacher who is already teaching elsewhere at an overlapping time is reported as a clash;
 * the admin may still save after confirming (allowClash).
 */
@Service
@Transactional
public class TimetableService {

    private final TimetableEntryRepository entryRepository;
    private final ClassSessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final ClassService classService;
    private final SubjectService subjectService;
    private final TeacherService teacherService;
    private final Clock clock;

    public TimetableService(TimetableEntryRepository entryRepository, ClassSessionRepository sessionRepository,
                            AttendanceRecordRepository attendanceRepository, ClassService classService,
                            SubjectService subjectService, TeacherService teacherService, Clock clock) {
        this.entryRepository = entryRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.classService = classService;
        this.subjectService = subjectService;
        this.teacherService = teacherService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TimetableDto getTimetable(Long classId) {
        CollegeClass c = classService.findClass(classId);
        List<TimetableEntryDto> entries = entryRepository.findByCollegeClassId(classId).stream()
                .sorted(Comparator.comparing(TimetableEntry::getDayOfWeek)
                        .thenComparing(TimetableEntry::getSlotNo)
                        .thenComparing(e -> e.getBatch() == null ? "" : e.getBatch().getName()))
                .map(TimetableEntryDto::from)
                .toList();
        return new TimetableDto(c.getId(), c.getName(), classService.getClass(classId).batches(), entries);
    }

    public TimetableEntryDto create(TimetableEntryRequest request) {
        CollegeClass c = classService.findClass(request.classId());
        Checked v = check(request, c, null);
        TimetableEntry entry = new TimetableEntry(c, request.dayOfWeek(), request.slotNo(),
                request.startTime(), request.endTime(), v.subject, request.sessionType(), v.batch, v.teacher,
                emptyToNull(request.room()));
        return TimetableEntryDto.from(entryRepository.save(entry));
    }

    public TimetableEntryDto update(Long id, TimetableEntryRequest request) {
        TimetableEntry entry = find(id);
        if (!entry.getCollegeClass().getId().equals(request.classId())) {
            throw new BadRequestException("An entry cannot be moved to another class.");
        }
        Checked v = check(request, entry.getCollegeClass(), id);
        removeUnusedUpcomingSessions(entry);

        entry.setDayOfWeek(request.dayOfWeek());
        entry.setSlotNo(request.slotNo());
        entry.setStartTime(request.startTime());
        entry.setEndTime(request.endTime());
        entry.setSubject(v.subject);
        entry.setSessionType(request.sessionType());
        entry.setBatch(v.batch);
        entry.setTeacher(v.teacher);
        entry.setRoom(emptyToNull(request.room()));
        return TimetableEntryDto.from(entry);
    }

    /**
     * Deletes the entry. Sessions already taken stay (with no link to the timetable);
     * today's and later sessions that are still pending and empty are removed.
     */
    public void delete(Long id) {
        TimetableEntry entry = find(id);
        removeUnusedUpcomingSessions(entry);
        entryRepository.delete(entry);
    }

    // ---------------------------------------------------------------- rules

    /** The looked-up records for a valid request. */
    private record Checked(Subject subject, Batch batch, User teacher) {
    }

    private Checked check(TimetableEntryRequest r, CollegeClass c, Long ownId) {
        if (!r.endTime().isAfter(r.startTime())) {
            throw new BadRequestException("End time must be after start time.");
        }
        Subject subject = subjectService.find(r.subjectId());
        User teacher = teacherService.find(r.teacherId());

        Batch batch = null;
        if (r.sessionType() == SessionType.THEORY) {
            if (!subject.isHasTheory()) {
                throw new BadRequestException(subject.getCode() + " has no theory part. Choose Lab or another subject.");
            }
            if (r.batchId() != null) {
                throw new BadRequestException("A theory lecture is for the whole class; do not choose a batch.");
            }
        } else {
            if (!subject.isHasLab()) {
                throw new BadRequestException(subject.getCode() + " has no lab part. Choose Theory or another subject.");
            }
            if (r.batchId() == null) {
                throw new BadRequestException("Choose the batch for this lab.");
            }
            batch = classService.findBatch(r.batchId());
            if (!batch.getCollegeClass().getId().equals(c.getId())) {
                throw new BadRequestException("That batch belongs to another class.");
            }
        }

        checkCellIsFree(r, c, batch, ownId);
        if (!r.allowClash()) {
            checkTeacherIsFree(r, teacher, ownId);
        }
        return new Checked(subject, batch, teacher);
    }

    private void checkCellIsFree(TimetableEntryRequest r, CollegeClass c, Batch batch, Long ownId) {
        List<TimetableEntry> cell = entryRepository
                .findByCollegeClassIdAndDayOfWeekAndSlotNo(c.getId(), r.dayOfWeek(), r.slotNo()).stream()
                .filter(e -> !e.getId().equals(ownId))
                .toList();
        String where = dayName(r.dayOfWeek()) + " slot " + r.slotNo();

        if (r.sessionType() == SessionType.THEORY && !cell.isEmpty()) {
            throw new BadRequestException(where + " already has entries. A theory lecture needs the whole slot.");
        }
        for (TimetableEntry other : cell) {
            if (other.getSessionType() == SessionType.THEORY) {
                throw new BadRequestException(where + " already has " + other.getSubject().getCode()
                        + " theory for the whole class.");
            }
            if (batch != null && other.getBatch().getId().equals(batch.getId())) {
                throw new BadRequestException(where + " already has " + other.getSubject().getCode()
                        + " lab for batch " + batch.getName() + ".");
            }
        }
    }

    /** Same teacher, same day, overlapping time, in any class. */
    private void checkTeacherIsFree(TimetableEntryRequest r, User teacher, Long ownId) {
        for (TimetableEntry other : entryRepository.findByTeacherIdAndDayOfWeek(teacher.getId(), r.dayOfWeek())) {
            boolean overlaps = r.startTime().isBefore(other.getEndTime()) && other.getStartTime().isBefore(r.endTime());
            if (!other.getId().equals(ownId) && overlaps) {
                throw new ClashException(teacher.getName() + " already has " + describe(other) + " on "
                        + dayName(other.getDayOfWeek()) + ", " + other.getStartTime() + "–" + other.getEndTime() + ".");
            }
        }
    }

    /** Removes today's and later sessions of this entry that nobody has marked yet, so they are re-created. */
    private void removeUnusedUpcomingSessions(TimetableEntry entry) {
        LocalDate today = LocalDate.now(clock);
        List<ClassSession> upcoming = sessionRepository
                .findByTimetableEntryIdAndSessionDateGreaterThanEqualAndStatus(entry.getId(), today, SessionStatus.PENDING)
                .stream()
                .filter(s -> !attendanceRepository.existsBySessionId(s.getId()))
                .toList();
        sessionRepository.deleteAll(upcoming);
        sessionRepository.flush();
    }

    // ---------------------------------------------------------------- helpers

    private TimetableEntry find(Long id) {
        return entryRepository.findById(id).orElseThrow(() -> new NotFoundException("Timetable entry not found."));
    }

    private static String describe(TimetableEntry e) {
        String what = e.getCollegeClass().getName() + " " + e.getSubject().getCode();
        return e.getSessionType() == SessionType.THEORY
                ? what + " theory"
                : what + " lab (" + Objects.requireNonNull(e.getBatch()).getName() + ")";
    }

    static String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private static String emptyToNull(String text) {
        String cleaned = clean(text);
        return cleaned.isEmpty() ? null : cleaned;
    }
}
