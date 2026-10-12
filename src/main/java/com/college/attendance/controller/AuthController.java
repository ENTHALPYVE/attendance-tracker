package com.college.attendance.controller;

import com.college.attendance.dto.ChangePasswordRequest;
import com.college.attendance.dto.UserDto;
import com.college.attendance.repository.UserRepository;
import com.college.attendance.security.AppUserPrincipal;
import com.college.attendance.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Login and logout are handled by Spring Security (see SecurityConfig).
 * This controller tells the frontend who is logged in and lets users change their own password.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final AccountService accountService;

    public AuthController(UserRepository userRepository, AccountService accountService) {
        this.userRepository = userRepository;
        this.accountService = accountService;
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

    /** Changes the logged-in user's own password (the current password is required). */
    @PostMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@AuthenticationPrincipal AppUserPrincipal user,
                                                              @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(user, request);
        return ResponseEntity.ok(Map.of("message", "Your password has been changed."));
    }
}
