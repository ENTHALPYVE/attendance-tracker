package com.college.attendance.dto;

import com.college.attendance.model.User;

public record TeacherDto(Long id, String name, String email) {

    public static TeacherDto from(User u) {
        return new TeacherDto(u.getId(), u.getName(), u.getEmail());
    }
}
