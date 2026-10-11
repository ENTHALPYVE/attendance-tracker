package com.college.attendance.model;

import jakarta.persistence.*;

/** Whether one student was present or absent in one session (table "attendance"). */
@Entity
@Table(name = "attendance")
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private ClassSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    protected AttendanceRecord() {
    }

    public AttendanceRecord(ClassSession session, Student student, AttendanceStatus status) {
        this.session = session;
        this.student = student;
        this.status = status;
    }

    public Long getId() { return id; }
    public ClassSession getSession() { return session; }
    public Student getStudent() { return student; }
    public AttendanceStatus getStatus() { return status; }
    public void setStatus(AttendanceStatus status) { this.status = status; }
}
