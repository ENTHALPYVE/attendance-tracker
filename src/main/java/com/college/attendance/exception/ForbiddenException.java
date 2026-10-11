package com.college.attendance.exception;

/** The user is logged in but may not do this (e.g. mark another teacher's session). HTTP 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
