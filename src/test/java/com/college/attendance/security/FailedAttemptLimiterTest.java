package com.college.attendance.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Lock after too many wrong passwords, unlock after 15 minutes, reset on success. */
class FailedAttemptLimiterTest {

    /** A clock the test can move forward. */
    private static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-12T09:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final TestClock clock = new TestClock();
    private final FailedAttemptLimiter limiter = new FailedAttemptLimiter(clock);

    @Test
    void locksAfterTheLimitAndUnlocksLater() {
        for (int i = 0; i < 4; i++) limiter.recordFailure("k", 5);
        assertThat(limiter.lockedFor("k")).isEmpty();

        limiter.recordFailure("k", 5);
        assertThat(limiter.lockedFor("k")).contains(Duration.ofMinutes(15));

        clock.advance(Duration.ofMinutes(14));
        assertThat(limiter.lockedFor("k")).contains(Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.lockedFor("k")).isEmpty();

        limiter.recordFailure("k", 5);                       // counting starts again from zero
        assertThat(limiter.lockedFor("k")).isEmpty();
    }

    @Test
    void oldFailuresDoNotCount() {
        for (int i = 0; i < 4; i++) limiter.recordFailure("k", 5);
        clock.advance(Duration.ofMinutes(16));               // outside the 15-minute window
        limiter.recordFailure("k", 5);
        assertThat(limiter.lockedFor("k")).isEmpty();
    }

    @Test
    void successResetsAndKeysAreSeparate() {
        for (int i = 0; i < 4; i++) limiter.recordFailure("a", 5);
        limiter.reset("a");
        limiter.recordFailure("a", 5);
        assertThat(limiter.lockedFor("a")).isEmpty();

        for (int i = 0; i < 5; i++) limiter.recordFailure("b", 5);
        assertThat(limiter.lockedFor("b")).isPresent();
        assertThat(limiter.lockedFor("a")).isEmpty();
    }

    @Test
    void minutesAreRoundedUp() {
        assertThat(FailedAttemptLimiter.minutes(Duration.ofSeconds(30))).isEqualTo("1 minute");
        assertThat(FailedAttemptLimiter.minutes(Duration.ofSeconds(61))).isEqualTo("2 minutes");
        assertThat(FailedAttemptLimiter.minutes(Duration.ofMinutes(15))).isEqualTo("15 minutes");
    }
}
