package com.college.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Add or edit a single student. Changing batchId moves the student to another batch. */
public record StudentRequest(
        @NotBlank(message = "Enter a roll number.")
        @Size(max = 20, message = "Roll number can be at most 20 characters.")
        String rollNo,
        @NotBlank(message = "Enter the student's name.")
        @Size(max = 100, message = "Name can be at most 100 characters.")
        String name,
        @NotNull(message = "Choose a batch.")
        Long batchId) {
}
