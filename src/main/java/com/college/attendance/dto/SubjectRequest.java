package com.college.attendance.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubjectRequest(
        @NotBlank(message = "Enter a subject code.")
        @Size(max = 20, message = "Code can be at most 20 characters.")
        String code,
        @NotBlank(message = "Enter a subject name.")
        @Size(max = 100, message = "Name can be at most 100 characters.")
        String name,
        boolean hasTheory,
        boolean hasLab) {

    @AssertTrue(message = "Tick theory, lab or both.")
    public boolean isTheoryOrLab() {
        return hasTheory || hasLab;
    }
}
