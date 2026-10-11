package com.college.attendance.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/** A date on which all sessions are treated as cancelled. */
@Entity
@Table(name = "holidays")
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate date;

    @Column(nullable = false)
    private String reason;

    protected Holiday() {
    }

    public Holiday(LocalDate date, String reason) {
        this.date = date;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
