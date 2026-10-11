package com.college.attendance.controller;

import com.college.attendance.dto.UserDto;
import com.college.attendance.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login and logout are handled by Spring Security (see SecurityConfig).
 * This controller only tells the frontend who is logged in.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AppUserPrincipal user) {
        return UserDto.from(user);
    }
}
