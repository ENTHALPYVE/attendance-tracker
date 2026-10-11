package com.college.attendance.exception;

/**
 * The teacher is already teaching somewhere else at that time. Returned as HTTP 409 with
 * "clash": true, so the page can ask "Save anyway?" and resend with allowClash = true.
 */
public class ClashException extends RuntimeException {

    public ClashException(String message) {
        super(message);
    }
}
