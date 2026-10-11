package com.college.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create or rename a class. batchNames is only used when creating
 * (e.g. ["B1", "B2", "B3"]); batches are managed separately afterwards.
 */
public record ClassRequest(
        @NotBlank(message = "Enter a class name.")
        @Size(max = 50, message = "Class name can be at most 50 characters.")
        String name,
        List<String> batchNames) {
}
