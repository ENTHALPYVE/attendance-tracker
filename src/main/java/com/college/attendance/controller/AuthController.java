package com.college.attendance.controller;

import com.college.attendance.dto.UserDto;
import com.college.attendance.repository.UserRepository;
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

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * The user id comes only from the login session; the details are re-read from the
     * users table so that a changed name shows up without logging in again.
     */
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AppUserPrincipal user) {
        return userRepository.findById(user.getId())
                .map(u -> new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole()))
                .orElseGet(() -> UserDto.from(user));
    }
}
