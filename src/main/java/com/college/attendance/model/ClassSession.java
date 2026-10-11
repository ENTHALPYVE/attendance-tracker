package com.college.attendance.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * An actual lecture or lab on a given date (table "sessions").
 * Named ClassSession so it is not confused with an HTTP session.
 * timetableEntry is null for an extra session that is not in the timetable.
 */
@Entity
@Table(name = "sessions")
public class ClassSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_entry_id")
    private TimetableEntry timetableEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id")
    private CollegeClass collegeClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false)
    private SessionType sessionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "slot_no", nullable = false)
    private int slotNo;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    private String room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.PENDING;

    protected ClassSession() {
    }

    /** Creates the session for a timetable entry on the given date. */
    public static ClassSession fromTimetable(TimetableEntry entry, LocalDate date) {
        ClassSession s = new ClassSession();
        s.timetableEntry = entry;
        s.collegeClass = entry.getCollegeClass();
        s.subject = entry.getSubject();
        s.sessionType = entry.getSessionType();
        s.batch = entry.getBatch();
        s.teacher = entry.getTeacher();
        s.sessionDate = date;
        s.slotNo = entry.getSlotNo();
        s.startTime = entry.getStartTime();
        s.endTime = entry.getEndTime();
        s.room = entry.getRoom();
        return s;
    }

    /** Creates an extra session that is not part of the timetable. */
    public static ClassSession extra(CollegeClass collegeClass, Subject subject, SessionType type,
                                     Batch batch, User teacher, LocalDate date, int slotNo,
                                     LocalTime startTime, LocalTime endTime, String room) {
        ClassSession s = new ClassSession();
        s.collegeClass = collegeClass;
        s.subject = subject;
        s.sessionType = type;
        s.batch = batch;
        s.teacher = teacher;
        s.sessionDate = date;
        s.slotNo = slotNo;
        s.startTime = startTime;
        s.endTime = endTime;
        s.room = room;
        return s;
    }

    public Long getId() { return id; }
    public TimetableEntry getTimetableEntry() { return timetableEntry; }
    public CollegeClass getCollegeClass() { return collegeClass; }
    public Subject getSubject() { return subject; }
    public SessionType getSessionType() { return sessionType; }
    public Batch getBatch() { return batch; }
    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }
    public LocalDate getSessionDate() { return sessionDate; }
    public int getSlotNo() { return slotNo; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getRoom() { return room; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
}
