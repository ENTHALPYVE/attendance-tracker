package com.college.attendance.service;

import com.college.attendance.dto.report.DayReportDto;
import com.college.attendance.dto.report.RangeReportDto;
import com.college.attendance.dto.report.StudentReportDto;
import com.college.attendance.dto.report.RangeReportDto.SubjectCell;
import com.college.attendance.dto.report.RangeReportDto.SubjectColumn;
import com.college.attendance.exception.ForbiddenException;
import com.college.attendance.exception.NotFoundException;
import com.college.attendance.model.*;
import com.college.attendance.repository.AttendanceRecordRepository;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.HolidayRepository;
import com.college.attendance.repository.StudentRepository;
import com.college.attendance.security.AppUserPrincipal;
import com.college.attendance.service.calc.AttendanceCalculator;
import com.college.attendance.service.calc.AttendanceCalculator.Count;
import com.college.attendance.service.calc.AttendanceCalculator.Mark;
import com.college.attendance.service.calc.AttendanceCalculator.SubjectResult;
import com.college.attendance.util.RollNumbers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static com.college.attendance.service.calc.AttendanceCalculator.*;

/**
 * Builds the attendance reports of a class from the database, using {@link AttendanceCalculator}
 * for all the arithmetic.
 *
 * Which sessions count for a student: the TAKEN sessions in which the student was marked
 * (present or absent). Attendance is saved for every student listed at that time, so this is
 * exactly "theory sessions of the class + lab sessions of the student's batch", and a student
 * who joined later is not counted absent for sessions before they joined.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ClassService classService;
    private final StudentRepository studentRepository;
    private final ClassSessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final HolidayRepository holidayRepository;
    private final SettingsService settingsService;
    private final ReportAccessService accessService;
    private final Clock clock;

    public ReportService(ClassService classService, StudentRepository studentRepository,
                         ClassSessionRepository sessionRepository, AttendanceRecordRepository attendanceRepository,
                         HolidayRepository holidayRepository, SettingsService settingsService,
                         ReportAccessService accessService, Clock clock) {
        this.classService = classService;
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.holidayRepository = holidayRepository;
        this.settingsService = settingsService;
        this.accessService = accessService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- day report

    /**
     * Day-wise report. The caller should run SessionGenerator.ensureSessions(date) first
     * (outside this transaction) so that sessions not marked yet are counted as pending.
     */
    public DayReportDto dayReport(Long classId, LocalDate date, AppUserPrincipal user) {
        Data d = load(classId, date, date, user);
        int threshold = settingsService.threshold();
        boolean countFree = settingsService.countFreeSlotsAsPresent();

        List<DayReportDto.Row> rows = new ArrayList<>();
        for (Student s : d.students) {
            List<Mark> marks = d.marks.getOrDefault(s.getId(), List.of());
            int free = marks.isEmpty() ? 0 : d.freeSlots(s, date);
            Count c = withFreeSlots(count(marks), free, countFree);
            List<String> absentIn = new ArrayList<>();
            for (Mark m : marks) {
                if (!m.present()) absentIn.add(d.label(m));
            }
            rows.add(new DayReportDto.Row(s.getId(), s.getRollNo(), s.getName(), s.getBatch().getName(),
                    c.attended(), c.held(), free, displayPercent(c), status(c, threshold), absentIn));
        }

        int taken = 0, pending = 0, cancelled = 0;
        for (ClassSession s : d.visibleSessions()) {
            switch (s.getStatus()) {
                case TAKEN -> taken++;
                case PENDING -> pending++;
                case CANCELLED -> cancelled++;
            }
        }
        String holiday = holidayRepository.findByDate(date).map(Holiday::getReason).orElse(null);
        return new DayReportDto(d.cls.getId(), d.cls.getName(), date, threshold, countFree, holiday,
                taken, pending, cancelled, rows);
    }

    // ---------------------------------------------------------------- month / any range

    public RangeReportDto monthReport(Long classId, YearMonth month, AppUserPrincipal user) {
        return rangeReport(classId, ReportPeriod.month(month), user);
    }

    public RangeReportDto rangeReport(Long classId, ReportPeriod period, AppUserPrincipal user) {
        return rangeReport(classId, period.from(), period.to(), period.title(), user);
    }

    /** The whole semester so far: from the semester start (or the class's first session) to today. */
    public ReportPeriod overall(Long classId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = settingsService.semesterStart()
                .or(() -> sessionRepository.firstDateOf(classId))
                .orElse(today);
        return ReportPeriod.overall(start.isAfter(today) ? today : start, today);
    }

    public RangeReportDto rangeReport(Long classId, LocalDate from, LocalDate to, String title, AppUserPrincipal user) {
        LocalDate today = LocalDate.now(clock);
        if (to.isAfter(today)) {
            to = today;   // nothing can be taken in the future
        }
        Data d = load(classId, from, to, user);
        int threshold = settingsService.threshold();
        boolean countFree = settingsService.countFreeSlotsAsPresent();

        List<SubjectColumn> columns = d.subjectColumns();
        List<RangeReportDto.Row> rows = new ArrayList<>();
        for (Student s : d.students) {
            List<Mark> marks = d.marks.getOrDefault(s.getId(), List.of());

            Map<LocalDate, Integer> freeByDate = new HashMap<>();
            for (Mark m : marks) {
                freeByDate.computeIfAbsent(m.date(), date -> d.freeSlots(s, date));
            }
            Count overall = rangeTotal(marks, freeByDate, countFree);

            Map<Long, SubjectCell> cells = new LinkedHashMap<>();
            List<String> belowIn = new ArrayList<>();
            Map<Long, SubjectResult> bySubject = bySubject(marks);
            for (SubjectColumn col : columns) {
                SubjectResult r = bySubject.get(col.subjectId());
                if (r == null) continue;
                boolean defaulter = isDefaulter(r, threshold);
                cells.put(col.subjectId(), new SubjectCell(
                        r.theory().attended(), r.theory().held(), displayPercent(r.theory()),
                        r.lab().attended(), r.lab().held(), displayPercent(r.lab()),
                        displayPercent(r.combined()), defaulter));
                if (status(r.theory(), threshold) == Status.DEFAULTER) belowIn.add(col.code() + " Theory");
                if (status(r.lab(), threshold) == Status.DEFAULTER) belowIn.add(col.code() + " Lab");
            }

            rows.add(new RangeReportDto.Row(s.getId(), s.getRollNo(), s.getName(), s.getBatch().getName(),
                    overall.attended(), overall.held(), displayPercent(overall), status(overall, threshold),
                    sessionsNeeded(overall, threshold), sessionsCanMiss(overall, threshold), belowIn, cells));
        }
        int taken = (int) d.visibleSessions().stream().filter(x -> x.getStatus() == SessionStatus.TAKEN).count();
        return new RangeReportDto(d.cls.getId(), d.cls.getName(), title, from, to, threshold, countFree,
                taken, columns, rows);
    }

    // ---------------------------------------------------------------- one student

    /** The student detail page: overall, subject-wise, day by day and week by week. */
    public StudentReportDto studentReport(Long studentId, ReportPeriod period, AppUserPrincipal user) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found."));
        Long classId = student.getCollegeClass().getId();
        Set<Long> visible = accessService.visibleBatches(user, classId);
        if (!ReportAccessService.isVisible(visible, student.getBatch().getId())) {
            throw new ForbiddenException("You can only see students of the batches you teach.");
        }
        LocalDate to = period.to().isAfter(LocalDate.now(clock)) ? LocalDate.now(clock) : period.to();
        Data d = load(classId, period.from(), to, ReportAccessService.ALL_BATCHES);
        int threshold = settingsService.threshold();
        boolean countFree = settingsService.countFreeSlotsAsPresent();
        List<Mark> marks = d.marks.getOrDefault(studentId, List.of());

        // day by day
        Map<LocalDate, List<Mark>> byDate = new TreeMap<>();
        marks.forEach(m -> byDate.computeIfAbsent(m.date(), k -> new ArrayList<>()).add(m));
        List<StudentReportDto.Day> days = new ArrayList<>();
        Map<LocalDate, Integer> freeByDate = new HashMap<>();
        for (Map.Entry<LocalDate, List<Mark>> e : byDate.entrySet()) {
            int free = d.freeSlots(student, e.getKey());
            freeByDate.put(e.getKey(), free);
            Count c = withFreeSlots(count(e.getValue()), free, countFree);
            List<String> absentIn = e.getValue().stream().filter(m -> !m.present()).map(d::label).toList();
            days.add(new StudentReportDto.Day(e.getKey(), c.attended(), c.held(), free,
                    displayPercent(c), status(c, threshold), absentIn));
        }

        // week by week (weeks start on Monday)
        Map<LocalDate, Count> byWeek = new TreeMap<>();
        for (StudentReportDto.Day day : days) {
            LocalDate monday = ReportPeriod.week(day.date()).from();
            byWeek.merge(monday, new Count(day.attended(), day.held()), Count::plus);
        }
        List<StudentReportDto.Week> weeks = new ArrayList<>();
        Count running = Count.ZERO;
        for (Map.Entry<LocalDate, Count> w : byWeek.entrySet()) {
            running = running.plus(w.getValue());
            weeks.add(new StudentReportDto.Week(w.getKey(), w.getValue().attended(), w.getValue().held(),
                    displayPercent(w.getValue()), displayPercent(running)));
        }

        // subject-wise
        Map<Long, SubjectResult> bySubject = bySubject(marks);
        List<StudentReportDto.SubjectRow> subjects = new ArrayList<>();
        for (SubjectColumn col : d.subjectColumns()) {
            SubjectResult r = bySubject.get(col.subjectId());
            if (r == null) continue;
            Count c = r.combined();
            subjects.add(new StudentReportDto.SubjectRow(col.subjectId(), col.code(), col.name(),
                    r.theory().attended(), r.theory().held(), displayPercent(r.theory()),
                    r.lab().attended(), r.lab().held(), displayPercent(r.lab()),
                    c.attended(), c.held(), displayPercent(c), isDefaulter(r, threshold)));
        }

        Count overall = rangeTotal(marks, freeByDate, countFree);
        return new StudentReportDto(student.getId(), student.getRollNo(), student.getName(), classId,
                student.getCollegeClass().getName(), student.getBatch().getName(),
                period.from(), to, period.title(), threshold, countFree,
                new StudentReportDto.Overall(overall.attended(), overall.held(), displayPercent(overall),
                        status(overall, threshold), sessionsNeeded(overall, threshold), sessionsCanMiss(overall, threshold)),
                subjects, days, weeks);
    }

    // ---------------------------------------------------------------- loading

    /** Everything about one class in a date range that the reports need, loaded with a few queries. */
    private Data load(Long classId, LocalDate from, LocalDate to, AppUserPrincipal user) {
        return load(classId, from, to, accessService.visibleBatches(user, classId));
    }

    /** Loads without an access check; visible limits the students to some batches (or ALL_BATCHES). */
    Data load(Long classId, LocalDate from, LocalDate to, Set<Long> visible) {
        CollegeClass cls = classService.findClass(classId);

        List<Student> students = studentRepository.findByCollegeClassId(classId).stream()
                .filter(s -> ReportAccessService.isVisible(visible, s.getBatch().getId()))
                .sorted(RollNumbers.BY_ROLL_NO)
                .toList();
        List<ClassSession> sessions = sessionRepository.findByCollegeClassIdAndSessionDateBetween(classId, from, to);
        Map<Long, ClassSession> sessionById = new HashMap<>();
        sessions.forEach(s -> sessionById.put(s.getId(), s));

        Map<Long, List<Mark>> marks = new HashMap<>();
        for (Object[] row : attendanceRepository.marksOfClass(classId, SessionStatus.TAKEN, from, to)) {
            ClassSession s = sessionById.get((Long) row[1]);
            marks.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add(new Mark(
                    s.getSessionDate(), s.getSlotNo(), s.getSubject().getId(), s.getSessionType(),
                    row[2] == AttendanceStatus.PRESENT));
        }
        return new Data(cls, visible, students, sessions, sessionById, marks);
    }

    /** The loaded data plus small helpers. */
    record Data(CollegeClass cls, Set<Long> visibleBatches, List<Student> students,
                        List<ClassSession> sessions, Map<Long, ClassSession> sessionById,
                        Map<Long, List<Mark>> marks) {

        /** Sessions a teacher with limited access may see (theory + their batches' labs). */
        List<ClassSession> visibleSessions() {
            return sessions.stream()
                    .filter(s -> s.getBatch() == null || ReportAccessService.isVisible(visibleBatches, s.getBatch().getId()))
                    .toList();
        }

        /** Free slots of this student's batch on a date (only TAKEN sessions are considered). */
        int freeSlots(Student student, LocalDate date) {
            Set<Integer> classSlots = new HashSet<>();
            Set<Integer> batchSlots = new HashSet<>();
            Long batchId = student.getBatch().getId();
            for (ClassSession s : sessions) {
                if (s.getStatus() != SessionStatus.TAKEN || !s.getSessionDate().equals(date)) continue;
                classSlots.add(s.getSlotNo());
                if (s.getBatch() == null || s.getBatch().getId().equals(batchId)) {
                    batchSlots.add(s.getSlotNo());
                }
            }
            return AttendanceCalculator.freeSlots(classSlots, batchSlots);
        }

        /** "AOA" for theory, "AOA Lab" for a lab. */
        String label(Mark m) {
            String code = sessions.stream().filter(s -> s.getSubject().getId() == m.subjectId())
                    .findFirst().map(s -> s.getSubject().getCode()).orElse("?");
            return m.type() == SessionType.LAB ? code + " Lab" : code;
        }

        /** Subjects with at least one TAKEN session in the range, by code. */
        List<SubjectColumn> subjectColumns() {
            Map<Long, boolean[]> parts = new HashMap<>();
            Map<Long, Subject> subjects = new HashMap<>();
            for (ClassSession s : visibleSessions()) {
                if (s.getStatus() != SessionStatus.TAKEN) continue;
                subjects.put(s.getSubject().getId(), s.getSubject());
                boolean[] p = parts.computeIfAbsent(s.getSubject().getId(), k -> new boolean[2]);
                p[s.getSessionType() == SessionType.THEORY ? 0 : 1] = true;
            }
            return subjects.values().stream()
                    .sorted(Comparator.comparing(Subject::getCode, String.CASE_INSENSITIVE_ORDER))
                    .map(sub -> new SubjectColumn(sub.getId(), sub.getCode(), sub.getName(),
                            parts.get(sub.getId())[0], parts.get(sub.getId())[1]))
                    .toList();
        }
    }
}
