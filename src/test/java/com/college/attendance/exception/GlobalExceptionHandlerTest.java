package com.college.attendance.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Error messages for outages and unexpected failures (Phase 2b). */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void databaseOutageIs503WithRetryAfter() {
        ResponseEntity<Map<String, Object>> r = handler.databaseUnavailable(
                new CannotGetJdbcConnectionException("Connection refused to mysql.example:3306"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        assertThat((String) r.getBody().get("message")).contains("database cannot be reached")
                .doesNotContain("mysql.example");   // no server details leak to the browser
    }

    @Test
    void transactionThatCannotStartIs503() {
        ResponseEntity<Map<String, Object>> r = handler.databaseUnavailable(
                new CannotCreateTransactionException("Could not open JPA EntityManager"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void deadlockIs409() {
        ResponseEntity<Map<String, Object>> r = handler.concurrentChange(new CannotAcquireLockException("Deadlock found"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) r.getBody().get("message")).contains("same time");
    }

    @Test
    void unexpectedErrorHidesTheDetails() {
        ResponseEntity<Map<String, Object>> r = handler.unexpected(new IllegalStateException("SELECT * FROM users failed"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat((String) r.getBody().get("message")).isEqualTo("Something went wrong on the server. Please try again.");
    }

    @Test
    void springStatusErrorsKeepTheirStatus() {
        ResponseEntity<Map<String, Object>> r = handler.unexpected(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
