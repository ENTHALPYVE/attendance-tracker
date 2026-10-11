package com.college.attendance.dto;

import com.college.attendance.model.Role;
import com.college.attendance.security.AppUserPrincipal;

/** The logged-in user as returned to the browser (never includes the password hash). */
public record UserDto(Long id, String name, String email, Role role) {

    public static UserDto from(AppUserPrincipal p) {
        return new UserDto(p.getId(), p.getName(), p.getEmail(), p.getRole());
    }
}
