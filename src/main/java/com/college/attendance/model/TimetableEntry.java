package com.college.attendance.model;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * One cell of the weekly timetable. A THEORY entry is for the whole class (batch is null);
 * a LAB entry is for a single batch.
 */
@Entity
@Table(name = "timetable_entries")
public class TimetableEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id")
    private CollegeClass collegeClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "slot_no", nullable = false)
    private int slotNo;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

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

    private String room;

    protected TimetableEntry() {
    }

    public TimetableEntry(CollegeClass collegeClass, DayOfWeek dayOfWeek, int slotNo,
                          LocalTime startTime, LocalTime endTime, Subject subject,
                          SessionType sessionType, Batch batch, User teacher, String room) {
        this.collegeClass = collegeClass;
        this.dayOfWeek = dayOfWeek;
        this.slotNo = slotNo;
        this.startTime = startTime;
        this.endTime = endTime;
        this.subject = subject;
        this.sessionType = sessionType;
        this.batch = batch;
        this.teacher = teacher;
        this.room = room;
    }

    public Long getId() { return id; }
    public CollegeClass getCollegeClass() { return collegeClass; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public int getSlotNo() { return slotNo; }
    public void setSlotNo(int slotNo) { this.slotNo = slotNo; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }
    public SessionType getSessionType() { return sessionType; }
    public void setSessionType(SessionType sessionType) { this.sessionType = sessionType; }
    public Batch getBatch() { return batch; }
    public void setBatch(Batch batch) { this.batch = batch; }
    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
}
