package com.college.attendance.model;

import jakarta.persistence.*;

/** A class/division such as "SE-CO-A". Named CollegeClass because "Class" is taken by Java. */
@Entity
@Table(name = "classes")
public class CollegeClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    protected CollegeClass() {
    }

    public CollegeClass(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
