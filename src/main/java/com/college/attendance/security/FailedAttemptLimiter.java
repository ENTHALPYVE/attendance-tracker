package com.college.attendance.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after too many wrong passwords for the same key
 * (for example "login:email|ip") within 15 minutes, that key is locked for 15 minutes.
 *
 * Kept in memory: simple, and enough for one server. A restart clears the counters.
 */
@Component
public class FailedAttemptLimiter {

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK = Duration.ofMinutes(15);
    private static final int MAX_KEYS = 10_000;    // protects memory if someone tries many fake emails

    private record Attempts(int failures, Instant firstFailure, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public FailedAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** How long the key is still locked, or empty when it may try again. */
    public Optional<Duration> lockedFor(String key) {
        Attempts a = attempts.get(key);
        Instant now = clock.instant();
        if (a == null || a.lockedUntil() == null) {
            return Optional.empty();
        }
        if (now.isBefore(a.lockedUntil())) {
            return Optional.of(Duration.between(now, a.lockedUntil()));
        }
        attempts.remove(key, a);   // lock is over: start counting from zero
        return Optional.empty();
    }

    /** Counts one wrong password; locks the key when maxFailures is reached within the window. */
    public void recordFailure(String key, int maxFailures) {
        Instant now = clock.instant();
        if (attempts.size() >= MAX_KEYS) {
            removeExpired(now);
        }
        attempts.compute(key, (k, a) -> {
            if (a == null || (a.lockedUntil() == null && now.isAfter(a.firstFailure().plus(WINDOW)))) {
                a = new Attempts(0, now, null);
            }
            int failures = a.failures() + 1;
            return new Attempts(failures, a.firstFailure(), failures >= maxFailures ? now.plus(LOCK) : a.lockedUntil());
        });
    }

    /** A correct password clears the count. */
    public void reset(String key) {
        attempts.remove(key);
    }

    private void removeExpired(Instant now) {
        attempts.entrySet().removeIf(e -> {
            Attempts a = e.getValue();
            Instant end = a.lockedUntil() != null ? a.lockedUntil() : a.firstFailure().plus(WINDOW);
            return now.isAfter(end);
        });
    }

    /** "1 minute" / "12 minutes", rounded up, for messages. */
    public static String minutes(Duration d) {
        long m = Math.max(1, (d.toSeconds() + 59) / 60);
        return m == 1 ? "1 minute" : m + " minutes";
    }
}
