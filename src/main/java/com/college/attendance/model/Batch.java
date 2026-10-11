package com.college.attendance.model;

import jakarta.persistence.*;

/** A lab batch inside a class, e.g. B1 of SE-CO-A. */
@Entity
@Table(name = "batches")
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id")
    private CollegeClass collegeClass;

    @Column(nullable = false)
    private String name;

    protected Batch() {
    }

    public Batch(CollegeClass collegeClass, String name) {
        this.collegeClass = collegeClass;
        this.name = name;
    }

    public Long getId() { return id; }
    public CollegeClass getCollegeClass() { return collegeClass; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
