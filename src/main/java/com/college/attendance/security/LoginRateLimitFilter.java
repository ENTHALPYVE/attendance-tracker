package com.college.attendance.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Limits password guessing on POST /api/auth/login (runs before Spring checks the password).
 *
 *  - 5 wrong passwords for one email from one IP address  -> that pair waits 15 minutes
 *  - 20 wrong passwords for one email from any IP address -> that email waits 15 minutes
 *    (stops an attacker who keeps changing IP address)
 *
 * The counting is done by FailedAttemptLimiter; SecurityConfig records failures and successes.
 * Unknown emails are counted the same way, so the answer never reveals which emails exist.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_PER_EMAIL_AND_IP = 5;
    static final int MAX_PER_EMAIL = 20;
    private static final String LOGIN_URL = "/api/auth/login";

    private final FailedAttemptLimiter limiter;
    private final ObjectMapper objectMapper;

    public LoginRateLimitFilter(FailedAttemptLimiter limiter, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && (request.getContextPath() + LOGIN_URL).equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Duration> wait = keys(request).stream()
                .map(limiter::lockedFor)
                .flatMap(Optional::stream)
                .max(Duration::compareTo);
        if (wait.isPresent()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, wait.get().toSeconds())));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), Map.of("message",
                    "Too many failed login attempts. Try again in " + FailedAttemptLimiter.minutes(wait.get()) + "."));
            return;
        }
        chain.doFilter(request, response);
    }

    /** Call after a wrong email or password. */
    public void recordFailure(HttpServletRequest request) {
        List<String> keys = keys(request);
        limiter.recordFailure(keys.get(0), MAX_PER_EMAIL_AND_IP);
        limiter.recordFailure(keys.get(1), MAX_PER_EMAIL);
    }

    /** Call after a successful login. */
    public void recordSuccess(HttpServletRequest request) {
        keys(request).forEach(limiter::reset);
    }

    /** [email + IP, email]. The IP is the real client address (Render's proxy header is read by Spring). */
    private static List<String> keys(HttpServletRequest request) {
        String email = request.getParameter("email");
        email = email == null ? "" : email.trim().toLowerCase();
        if (email.length() > 150) email = email.substring(0, 150);
        return List.of("login:" + email + "|" + request.getRemoteAddr(), "login:" + email);
    }
}
