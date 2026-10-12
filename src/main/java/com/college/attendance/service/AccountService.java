package com.college.attendance.service;

import com.college.attendance.dto.ChangePasswordRequest;
import com.college.attendance.exception.BadRequestException;
import com.college.attendance.exception.NotFoundException;
import com.college.attendance.exception.TooManyAttemptsException;
import com.college.attendance.model.User;
import com.college.attendance.repository.UserRepository;
import com.college.attendance.security.AppUserPrincipal;
import com.college.attendance.security.FailedAttemptLimiter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/**
 * The logged-in user's own account. Changing the password needs the current password,
 * so somebody using a computer that was left logged in cannot take over the account.
 * Wrong current passwords are limited like logins (5 per 15 minutes).
 */
@Service
@Transactional
public class AccountService {

    static final int MAX_WRONG_CURRENT_PASSWORDS = 5;
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FailedAttemptLimiter limiter;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder, FailedAttemptLimiter limiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
    }

    public void changePassword(AppUserPrincipal principal, ChangePasswordRequest request) {
        String key = "password-change:" + principal.getId();
        limiter.lockedFor(key).ifPresent(wait -> {
            throw new TooManyAttemptsException("Too many wrong passwords. Try again in "
                    + FailedAttemptLimiter.minutes(wait) + ".", wait);
        });

        // the id comes from the login session; the browser cannot choose whose password changes
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new NotFoundException("Your account no longer exists."));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            limiter.recordFailure(key, MAX_WRONG_CURRENT_PASSWORDS);
            throw new BadRequestException("Your current password is not correct.");
        }
        String newPassword = request.newPassword();
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BadRequestException("The new password is too long.");
        }
        if (newPassword.equals(request.currentPassword())) {
            throw new BadRequestException("Choose a new password that is different from the current one.");
        }
        if (newPassword.equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("Do not use your email address as your password.");
        }
        limiter.reset(key);
        user.setPasswordHash(passwordEncoder.encode(newPassword));   // saved at the end of the transaction
    }
}
