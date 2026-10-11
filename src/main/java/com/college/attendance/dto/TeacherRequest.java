package com.college.attendance.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create or edit a teacher. When editing, a blank password keeps the current one. */
public record TeacherRequest(
        @NotBlank(message = "Enter the teacher's name.")
        @Size(max = 100, message = "Name can be at most 100 characters.")
        String name,
        @NotBlank(message = "Enter an email address.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 150, message = "Email can be at most 150 characters.")
        String email,
        @Size(max = 72, message = "Password can be at most 72 characters.")
        String password) {
}
