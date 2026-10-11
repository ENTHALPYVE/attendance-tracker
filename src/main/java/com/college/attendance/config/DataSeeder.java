package com.college.attendance.config;

import com.college.attendance.model.*;
import com.college.attendance.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static java.time.DayOfWeek.*;

/**
 * Fills an empty database with demo data so the app can be tried straight away:
 * 1 admin, 8 teachers, class SE-CO-A (batches B1/B2/B3, 20 students each),
 * 7 subjects, a full weekly timetable and about 6 weeks of random attendance.
 *
 * Runs once at start-up, only if app.seed-demo-data=true and the users table is empty.
 * A fixed random seed is used so the demo data is the same on every machine.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Default demo passwords (used on your own computer and by the tests). */
    public static final String ADMIN_PASSWORD = "admin123";
    public static final String TEACHER_PASSWORD = "teacher123";

    /** Slot number -> {start, end}. Slots 4 and 5 are two-hour lab slots. */
    private static final LocalTime[][] SLOT_TIMES = {
            null,
            {LocalTime.of(9, 0), LocalTime.of(10, 0)},
            {LocalTime.of(10, 0), LocalTime.of(11, 0)},
            {LocalTime.of(11, 15), LocalTime.of(12, 15)},
            {LocalTime.of(13, 0), LocalTime.of(15, 0)},
            {LocalTime.of(15, 0), LocalTime.of(17, 0)},
    };

    private static final String[] STUDENT_NAMES = {
            "Aarav Mehta", "Aditi Kulkarni", "Akash Pawar", "Ananya Iyer", "Aniket Jadhav",
            "Anjali Shetty", "Arjun Nair", "Bhavna Desai", "Chinmay Gokhale", "Deepak Yadav",
            "Divya Menon", "Gaurav Patil", "Harsh Vora", "Isha Bhatt", "Jay Chavan",
            "Kavya Rao", "Kunal Sawant", "Manasi Joshi", "Mihir Shah", "Neha Gupta",
            "Nikhil More", "Omkar Salunkhe", "Pooja Naik", "Pranav Kadam", "Priya Pillai",
            "Rahul Mishra", "Riya Thakur", "Rohan Deshpande", "Sakshi Gaikwad", "Sameer Khan",
            "Sanika Bhosale", "Saurabh Tiwari", "Shreya Kamat", "Siddharth Rane", "Sneha D'Souza",
            "Soham Parab", "Tanvi Mhatre", "Tejas Wagh", "Urvi Sheth", "Varun Pandey",
            "Vedant Kale", "Vidhi Agarwal", "Yash Dalvi", "Zoya Shaikh", "Abhishek Sonawane",
            "Aishwarya Lokhande", "Atharva Ghosh", "Diya Fernandes", "Hrishikesh Pande", "Janhavi Raut",
            "Kartik Singh", "Mansi Chougule", "Nishant Bendre", "Prachi Thorat", "Rutuja Shinde",
            "Sahil Ansari", "Shruti Mane", "Tushar Koli", "Vaishnavi Hegde", "Yogesh Bhagat",
    };

    private final UserRepository userRepository;
    private final CollegeClassRepository classRepository;
    private final BatchRepository batchRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final TimetableEntryRepository timetableRepository;
    private final ClassSessionRepository sessionRepository;
    private final HolidayRepository holidayRepository;
    private final SettingRepository settingRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed-demo-data:false}")
    private boolean enabled;

    /** On a public server these come from the ADMIN_PASSWORD / TEACHER_PASSWORD environment variables. */
    @Value("${app.seed.admin-password:" + ADMIN_PASSWORD + "}")
    private String adminPassword;

    @Value("${app.seed.teacher-password:" + TEACHER_PASSWORD + "}")
    private String teacherPassword;

    private final Random random = new Random(2026);

    // Filled while seeding, used to build the timetable.
    private CollegeClass seCoA;
    private final Map<String, Batch> batches = new HashMap<>();
    private final Map<String, Subject> subjects = new HashMap<>();
    private final Map<String, User> teachers = new HashMap<>();
    private final List<TimetableEntry> timetable = new ArrayList<>();

    public DataSeeder(UserRepository userRepository, CollegeClassRepository classRepository,
                      BatchRepository batchRepository, StudentRepository studentRepository,
                      SubjectRepository subjectRepository, TimetableEntryRepository timetableRepository,
                      ClassSessionRepository sessionRepository, HolidayRepository holidayRepository,
                      SettingRepository settingRepository, JdbcTemplate jdbcTemplate,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.classRepository = classRepository;
        this.batchRepository = batchRepository;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.timetableRepository = timetableRepository;
        this.sessionRepository = sessionRepository;
        this.holidayRepository = holidayRepository;
        this.settingRepository = settingRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || userRepository.count() > 0) {
            return;
        }
        log.info("Empty database found - loading demo data...");
        seedUsers();
        List<Student> students = seedClassAndStudents();
        seedSubjects();
        seedTimetable();
        int attendanceRows = seedPastAttendance(students);
        log.info("Demo data loaded: {} students, {} timetable entries, {} attendance rows.",
                students.size(), timetable.size(), attendanceRows);
        boolean defaults = adminPassword.equals(ADMIN_PASSWORD) && teacherPassword.equals(TEACHER_PASSWORD);
        log.info("Logins: admin@college.edu and the teachers (e.g. anita.deshmukh@college.edu), "
                + (defaults ? "passwords admin123 / teacher123" : "with the passwords from ADMIN_PASSWORD / TEACHER_PASSWORD"));
    }

    // ---------------------------------------------------------------- users

    private void seedUsers() {
        userRepository.save(new User("Dr. Suresh Menon", "admin@college.edu",
                passwordEncoder.encode(adminPassword), Role.ADMIN));

        // key -> name; the key is only used inside this class to build the timetable
        String[][] staff = {
                {"T1", "Anita Deshmukh"},   // AOA theory + lab
                {"T2", "Rahul Kulkarni"},   // DSGT
                {"T3", "Sneha Patil"},      // Maths
                {"T4", "Vikram Joshi"},     // COA theory + lab
                {"T5", "Meera Iyer"},       // Java lab
                {"T6", "Sanjay Shinde"},    // EDP practical
                {"T7", "Kavita Nair"},      // ESE practical
                {"T8", "Amit Bhosale"},     // second AOA / COA lab teacher
        };
        String teacherHash = passwordEncoder.encode(teacherPassword);
        for (String[] t : staff) {
            String email = t[1].toLowerCase().replace(' ', '.') + "@college.edu";
            teachers.put(t[0], userRepository.save(new User(t[1], email, teacherHash, Role.TEACHER)));
        }
    }

    // ---------------------------------------------------------------- class, batches, students

    private List<Student> seedClassAndStudents() {
        seCoA = classRepository.save(new CollegeClass("SE-CO-A"));
        for (String b : List.of("B1", "B2", "B3")) {
            batches.put(b, batchRepository.save(new Batch(seCoA, b)));
        }
        List<Student> students = new ArrayList<>();
        for (int i = 0; i < STUDENT_NAMES.length; i++) {
            Batch batch = batches.get("B" + (i / 20 + 1));   // rolls 01-20 -> B1, 21-40 -> B2, 41-60 -> B3
            String rollNo = String.format("%02d", i + 1);
            students.add(new Student(rollNo, STUDENT_NAMES[i], seCoA, batch));
        }
        return studentRepository.saveAll(students);
    }

    // ---------------------------------------------------------------- subjects

    private void seedSubjects() {
        addSubject("AOA", "Analysis of Algorithms", true, true);
        addSubject("DSGT", "Discrete Structures and Graph Theory", true, false);
        addSubject("Maths", "Engineering Mathematics IV", true, false);
        addSubject("COA", "Computer Organization and Architecture", true, true);
        addSubject("Java", "Java Programming Lab", false, true);
        addSubject("EDP", "Entrepreneurship Development Practical", false, true);
        addSubject("ESE", "Environmental Science and Engineering Practical", false, true);
    }

    private void addSubject(String code, String name, boolean theory, boolean lab) {
        subjects.put(code, subjectRepository.save(new Subject(code, name, theory, lab)));
    }

    // ---------------------------------------------------------------- timetable

    /**
     * Monday is the example day from the project brief:
     * slots 1-3 theory for all; slot 4 B1 AOA Lab, B2 COA Lab, B3 Java Lab;
     * slot 5 B1 free, B2 EDP, B3 ESE.
     * No teacher is in two places in the same slot.
     */
    private void seedTimetable() {
        theory(MONDAY, 1, "AOA", "T1");
        theory(MONDAY, 2, "DSGT", "T2");
        theory(MONDAY, 3, "Maths", "T3");
        lab(MONDAY, 4, "B1", "AOA", "T1");
        lab(MONDAY, 4, "B2", "COA", "T4");
        lab(MONDAY, 4, "B3", "Java", "T5");
        lab(MONDAY, 5, "B2", "EDP", "T6");
        lab(MONDAY, 5, "B3", "ESE", "T7");

        theory(TUESDAY, 1, "COA", "T4");
        theory(TUESDAY, 2, "AOA", "T1");
        theory(TUESDAY, 3, "DSGT", "T2");
        lab(TUESDAY, 4, "B1", "COA", "T4");
        lab(TUESDAY, 4, "B2", "Java", "T5");
        lab(TUESDAY, 4, "B3", "AOA", "T8");
        lab(TUESDAY, 5, "B1", "EDP", "T6");
        lab(TUESDAY, 5, "B2", "ESE", "T7");

        theory(WEDNESDAY, 1, "Maths", "T3");
        theory(WEDNESDAY, 2, "COA", "T4");
        theory(WEDNESDAY, 3, "AOA", "T1");
        lab(WEDNESDAY, 4, "B1", "Java", "T5");
        lab(WEDNESDAY, 4, "B2", "AOA", "T8");
        lab(WEDNESDAY, 4, "B3", "COA", "T4");
        lab(WEDNESDAY, 5, "B1", "ESE", "T7");
        lab(WEDNESDAY, 5, "B3", "EDP", "T6");

        theory(THURSDAY, 1, "DSGT", "T2");
        theory(THURSDAY, 2, "Maths", "T3");
        theory(THURSDAY, 3, "COA", "T4");
        lab(THURSDAY, 4, "B1", "AOA", "T1");
        lab(THURSDAY, 4, "B2", "Java", "T5");
        lab(THURSDAY, 4, "B3", "COA", "T8");

        theory(FRIDAY, 1, "AOA", "T1");
        theory(FRIDAY, 2, "DSGT", "T2");
        theory(FRIDAY, 3, "Maths", "T3");
        lab(FRIDAY, 4, "B1", "COA", "T8");
        lab(FRIDAY, 4, "B2", "AOA", "T1");
        lab(FRIDAY, 4, "B3", "Java", "T5");

        timetableRepository.saveAll(timetable);
    }

    private void theory(DayOfWeek day, int slot, String subject, String teacher) {
        timetable.add(new TimetableEntry(seCoA, day, slot, SLOT_TIMES[slot][0], SLOT_TIMES[slot][1],
                subjects.get(subject), SessionType.THEORY, null, teachers.get(teacher), "Room 301"));
    }

    private void lab(DayOfWeek day, int slot, String batch, String subject, String teacher) {
        String room = switch (subject) {
            case "AOA" -> "Lab 1";
            case "COA" -> "Lab 2";
            case "Java" -> "Lab 3";
            case "EDP" -> "Lab 4";
            default -> "Room 305";
        };
        timetable.add(new TimetableEntry(seCoA, day, slot, SLOT_TIMES[slot][0], SLOT_TIMES[slot][1],
                subjects.get(subject), SessionType.LAB, batches.get(batch), teachers.get(teacher), room));
    }

    // ---------------------------------------------------------------- past sessions and attendance

    /**
     * Creates sessions for every timetable entry from the Monday six weeks ago up to yesterday,
     * with one holiday and a few cancelled sessions, and random attendance for each taken session.
     * Each student gets a personal attendance rate so that a few of them end up as defaulters.
     */
    private int seedPastAttendance(List<Student> students) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusWeeks(6).with(TemporalAdjusters.previousOrSame(MONDAY));
        LocalDate holiday = start.plusWeeks(3).plusDays(2);   // a Wednesday

        settingRepository.save(new Setting(Setting.SEMESTER_START, start.toString()));
        holidayRepository.save(new Holiday(holiday, "Institute holiday"));

        Map<Long, Double> rate = new HashMap<>();
        for (Student s : students) {
            boolean weak = random.nextDouble() < 0.15;
            rate.put(s.getId(), weak ? 0.55 + random.nextDouble() * 0.2 : 0.80 + random.nextDouble() * 0.18);
        }

        List<Object[]> rows = new ArrayList<>();
        for (LocalDate date = start; date.isBefore(today); date = date.plusDays(1)) {
            if (date.equals(holiday)) {
                continue;
            }
            for (TimetableEntry entry : timetable) {
                if (entry.getDayOfWeek() != date.getDayOfWeek()) {
                    continue;
                }
                ClassSession session = ClassSession.fromTimetable(entry, date);
                boolean cancelled = random.nextDouble() < 0.03;
                session.setStatus(cancelled ? SessionStatus.CANCELLED : SessionStatus.TAKEN);
                sessionRepository.save(session);
                if (cancelled) {
                    continue;
                }
                for (Student s : students) {
                    boolean inSession = entry.getSessionType() == SessionType.THEORY
                            || s.getBatch().getId().equals(entry.getBatch().getId());
                    if (inSession) {
                        boolean present = random.nextDouble() < rate.get(s.getId());
                        rows.add(new Object[]{session.getId(), s.getId(),
                                present ? AttendanceStatus.PRESENT.name() : AttendanceStatus.ABSENT.name()});
                    }
                }
            }
        }
        // Plain JDBC batch insert: much faster than saving thousands of entities one by one.
        jdbcTemplate.batchUpdate("INSERT INTO attendance (session_id, student_id, status) VALUES (?, ?, ?)", rows);
        return rows.size();
    }
}
