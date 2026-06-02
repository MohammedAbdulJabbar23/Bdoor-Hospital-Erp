package com.albudoor.hms.platform.exception;

/**
 * Thrown when a login attempt fails authentication (unknown username, wrong password,
 * or deactivated account). Mapped to HTTP 401 Unauthorized — distinct from
 * {@link DomainException} (422) so failed logins surface the correct auth status.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
