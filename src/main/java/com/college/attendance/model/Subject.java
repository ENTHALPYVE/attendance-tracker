package com.college.attendance.model;

import jakarta.persistence.*;

/**
 * A subject such as AOA. One subject can have a theory part and a lab part;
 * both are the same subject with different session types.
 */
@Entity
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "has_theory", nullable = false)
    private boolean hasTheory;

    @Column(name = "has_lab", nullable = false)
    private boolean hasLab;

    protected Subject() {
    }

    public Subject(String code, String name, boolean hasTheory, boolean hasLab) {
        this.code = code;
        this.name = name;
        this.hasTheory = hasTheory;
        this.hasLab = hasLab;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isHasTheory() { return hasTheory; }
    public void setHasTheory(boolean hasTheory) { this.hasTheory = hasTheory; }
    public boolean isHasLab() { return hasLab; }
    public void setHasLab(boolean hasLab) { this.hasLab = hasLab; }
}
