package com.albudoor.hms.platform.exception;

/** Thrown when finish-treatment is attempted while ordered department results are still open. */
public class ResultsPendingException extends ConflictException {
    public ResultsPendingException(String message) {
        super("RESULTS_PENDING", message);
    }
}
