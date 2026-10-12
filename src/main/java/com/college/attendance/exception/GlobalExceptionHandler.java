package com.college.attendance.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into JSON error responses of the form
 * {"message": "...", "fieldErrors": {"name": "must not be blank"}}
 * so the frontend can show a clear message.
 * Unexpected errors are logged on the server; the browser only gets a short, safe message
 * (never a stack trace or SQL).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> badRequest(BadRequestException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ClashException.class)
    public ResponseEntity<Map<String, Object>> clash(ClashException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", e.getMessage());
        body.put("clash", true);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(ForbiddenException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** Too many wrong passwords: 429 with the number of seconds to wait. */
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Map<String, Object>> tooManyAttempts(TooManyAttemptsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, e.getRetryAfter().toSeconds())))
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> accessDenied(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "You do not have permission to do this.");
    }

    /** Bean Validation errors on @Valid request bodies. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Please correct the highlighted fields.");
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** A database UNIQUE / FOREIGN KEY rule was broken (safety net behind the service checks). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException e) {
        return error(HttpStatus.CONFLICT, "This change conflicts with existing records (for example a duplicate).");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.BAD_REQUEST, "The file is too large (maximum 5 MB).");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> malformed(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "The request was not in the expected format.");
    }

    /**
     * The database cannot be reached (Aiven down, network problem, all connections busy).
     * 503 + Retry-After tells the browser it is temporary; nothing was half-saved because
     * every change runs in a transaction.
     */
    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class,
            QueryTimeoutException.class})
    public ResponseEntity<Map<String, Object>> databaseUnavailable(Exception e) {
        log.warn("Database unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(Map.of("message", "The database cannot be reached right now. Nothing was changed; "
                        + "please try again in a minute."));
    }

    /** Two people changed the same records at the same moment (e.g. a database deadlock). */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Map<String, Object>> concurrentChange(ConcurrencyFailureException e) {
        log.warn("Concurrent update: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "Someone else changed this at the same time. Reload the page and try again.");
    }

    /** Anything else: keep Spring's status (e.g. 404, 405) but always answer with a short JSON message. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        if (e instanceof ErrorResponse er) {
            HttpStatusCode status = er.getStatusCode();
            return ResponseEntity.status(status).body(Map.of("message", status.value() == 404
                    ? "Not found." : "The request could not be handled (error " + status.value() + ")."));
        }
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server. Please try again.");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
