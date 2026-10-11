package com.college.attendance.dto;

import com.college.attendance.model.Student;

public record StudentDto(Long id, String rollNo, String name, Long batchId, String batchName) {

    public static StudentDto from(Student s) {
        return new StudentDto(s.getId(), s.getRollNo(), s.getName(),
                s.getBatch().getId(), s.getBatch().getName());
    }
}
