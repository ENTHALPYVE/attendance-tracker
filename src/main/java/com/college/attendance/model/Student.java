package com.college.attendance.model;

import jakarta.persistence.*;

/** A student record. Students do not log in. */
@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "roll_no", nullable = false)
    private String rollNo;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id")
    private CollegeClass collegeClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    protected Student() {
    }

    public Student(String rollNo, String name, CollegeClass collegeClass, Batch batch) {
        this.rollNo = rollNo;
        this.name = name;
        this.collegeClass = collegeClass;
        this.batch = batch;
    }

    public Long getId() { return id; }
    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CollegeClass getCollegeClass() { return collegeClass; }
    public Batch getBatch() { return batch; }
    public void setBatch(Batch batch) { this.batch = batch; }
}
