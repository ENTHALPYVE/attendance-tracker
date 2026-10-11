package com.college.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BatchRequest(
        @NotBlank(message = "Enter a batch name.")
        @Size(max = 20, message = "Batch name can be at most 20 characters.")
        String name) {
}
