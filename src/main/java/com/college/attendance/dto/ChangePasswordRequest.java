package com.college.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The logged-in user changes their own password; the current one must be given again. */
public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password.")
        @Size(max = 72, message = "Password can be at most 72 characters.")
        String currentPassword,
        @NotBlank(message = "Enter a new password.")
        @Size(min = 8, max = 72, message = "The new password must be 8 to 72 characters.")
        String newPassword) {
}
