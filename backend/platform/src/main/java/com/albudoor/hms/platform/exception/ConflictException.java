package com.albudoor.hms.platform.exception;

public class ConflictException extends DomainException {
    public ConflictException(String code, String message) {
        super(code, message);
    }
}
