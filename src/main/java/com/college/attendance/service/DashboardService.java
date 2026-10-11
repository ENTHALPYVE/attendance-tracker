package com.college.attendance.service;

import com.college.attendance.dto.TeacherCardDto;
import com.college.attendance.model.*;
import com.college.attendance.repository.ClassSessionRepository;
import com.college.attendance.repository.SubjectRepository;
import com.college.attendance.repository.TimetableEntryRepository;
import com.college.attendance.security.AppUserPrincipal;
import com.college.attendance.service.calc.AttendanceCalculator.Count;
import com.college.attendance.service.calc.AttendanceCalculator.Mark;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

import static com.college.attendance.service.calc.AttendanceCalculator.*;

/**
 * The teacher dashboard: one card per subject part a teacher teaches
 * (theory to a whole class, or a lab to one batch), over the semester so far.
 */
@Service
public class DashboardService {

    /** Days back from today for which missing sessions are created, so "to mark" is complete. */
    static final int CATCH_UP_DAYS = 14;
    private static final int MAX_PENDING_LISTED = 5;

    private final TimetableEntryRepository entryRepository;
    private final ClassSessionRepository sessionRepository;
    private final SubjectRepository subjectRepository;
    private final ReportService reportService;
    private final SettingsService settingsService;
    private final SessionGenerator generator;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    public DashboardService(TimetableEntryRepository entryRepository, ClassSessionRepository sessionRepository,
                            SubjectRepository subjectRepository, ReportService reportService,
                            SettingsService settingsService, SessionGenerator generator,
                            PlatformTransactionManager transactionManager, Clock clock) {
        this.entryRepository = entryRepository;
        this.sessionRepository = sessionRepository;
        this.subjectRepository = subjectRepository;
        this.reportService = reportService;
        this.settingsService = settingsService;
        this.generator = generator;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    /** A teaching group: class + subject + theory/lab + batch (null for theory). */
    private record Group(Long classId, Long subjectId, SessionType type, Long batchId) {
    }

    public List<TeacherCardDto> teacherCards(AppUserPrincipal user) {
        LocalDate today = LocalDate.now(clock);
        // Create the recent days' sessions first (each in its own transaction), so that
        // days nobody opened still show up as "to mark".
        for (int back = CATCH_UP_DAYS; back >= 0; back--) {
            generator.ensureSessions(today.minusDays(back));
        }
        return readOnly.execute(status -> buildCards(user, today));
    }

    private List<TeacherCardDto> buildCards(AppUserPrincipal user, LocalDate today) {
        Set<Group> groups = new LinkedHashSet<>();
        List<Object[]> rows = new ArrayList<>(entryRepository.groupsOf(user.getId()));
        rows.addAll(sessionRepository.groupsOf(user.getId()));
        for (Object[] r : rows) {
            groups.add(new Group((Long) r[0], (Long) r[1], (SessionType) r[2], (Long) r[3]));
        }
        int threshold = settingsService.threshold();

        // Load each class once.
        Map<Long, ReportService.Data> dataByClass = new HashMap<>();
        for (Group g : groups) {
            dataByClass.computeIfAbsent(g.classId(), classId -> {
                ReportPeriod semester = reportService.overall(classId);
                return reportService.load(classId, semester.from(), today, ReportAccessService.ALL_BATCHES);
            });
        }

        List<TeacherCardDto> cards = new ArrayList<>();
        for (Group g : groups) {
            ReportService.Data d = dataByClass.get(g.classId());
            Subject subject = subjectRepository.findById(g.subjectId()).orElseThrow();
            List<ClassSession> groupSessions = d.sessions().stream().filter(s -> inGroup(s, g)).toList();

            int taken = (int) groupSessions.stream().filter(s -> s.getStatus() == SessionStatus.TAKEN).count();
            List<TeacherCardDto.PendingSession> pending = groupSessions.stream()
                    .filter(s -> s.getStatus() == SessionStatus.PENDING
                            && s.getTeacher().getId().equals(user.getId())
                            && !s.getSessionDate().isAfter(today))
                    .sorted(Comparator.comparing(ClassSession::getSessionDate).thenComparing(ClassSession::getSlotNo))
                    .map(s -> new TeacherCardDto.PendingSession(s.getId(), s.getSessionDate(), s.getSlotNo()))
                    .toList();

            // each student's % in this subject part
            List<Student> students = d.students().stream()
                    .filter(st -> g.batchId() == null || st.getBatch().getId().equals(g.batchId()))
                    .toList();
            double sum = 0;
            int withData = 0, defaulters = 0;
            for (Student st : students) {
                List<Mark> part = d.marks().getOrDefault(st.getId(), List.of()).stream()
                        .filter(m -> m.subjectId() == g.subjectId() && m.type() == g.type())
                        .toList();
                Count c = count(part);
                if (c.held() == 0) continue;
                sum += c.percent();
                withData++;
                if (status(c, threshold) == Status.DEFAULTER) defaulters++;
            }
            Double average = withData == 0 ? null : Math.floor(sum / withData * 10) / 10;

            String batchName = g.batchId() == null ? null : students.isEmpty()
                    ? groupSessions.stream().filter(s -> s.getBatch() != null).findFirst().map(s -> s.getBatch().getName()).orElse("?")
                    : students.get(0).getBatch().getName();
            cards.add(new TeacherCardDto(g.classId(), d.cls().getName(), subject.getId(), subject.getCode(),
                    subject.getName(), g.type(), g.batchId(), batchName, students.size(), taken, average, defaulters,
                    pending.size(), pending.size() > MAX_PENDING_LISTED ? pending.subList(0, MAX_PENDING_LISTED) : pending));
        }
        cards.sort(Comparator.comparing(TeacherCardDto::className)
                .thenComparing(TeacherCardDto::subjectCode)
                .thenComparing(TeacherCardDto::sessionType)
                .thenComparing(c -> c.batchName() == null ? "" : c.batchName()));
        return cards;
    }

    private static boolean inGroup(ClassSession s, Group g) {
        return s.getSubject().getId().equals(g.subjectId())
                && s.getSessionType() == g.type()
                && Objects.equals(s.getBatch() == null ? null : s.getBatch().getId(), g.batchId());
    }
}
