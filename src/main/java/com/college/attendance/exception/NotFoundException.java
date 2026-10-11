package com.college.attendance.exception;

/** Thrown when a requested record does not exist. Returned as HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
