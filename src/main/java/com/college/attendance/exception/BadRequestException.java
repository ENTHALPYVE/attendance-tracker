package com.college.attendance.exception;

/** Thrown when the request breaks a business rule (e.g. duplicate roll number). Returned as HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
