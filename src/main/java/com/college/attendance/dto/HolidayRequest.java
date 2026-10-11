package com.college.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HolidayRequest(
        @NotNull(message = "Choose a date.") LocalDate date,
        @NotBlank(message = "Enter a reason, e.g. Diwali.")
        @Size(max = 150, message = "Reason can be at most 150 characters.") String reason) {
}
