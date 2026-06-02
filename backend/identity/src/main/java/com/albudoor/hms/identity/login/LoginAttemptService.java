package com.albudoor.hms.identity.login;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-username failed-login lockout. After {@link #MAX_ATTEMPTS} consecutive
 * FAILED attempts a username is locked for {@link #LOCK_DURATION}; a successful login (or
 * the lock expiring) resets the counter. Successful logins never increment the counter, so
 * repeated legitimate logins (e.g. the IT suite) never trip the lock.
 *
 * <p>State lives only in this JVM — fine for the single-node deployment; a multi-node prod
 * would replace this with a shared store (Redis). No external dependency is added.
 */
@Component
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private record Attempt(int failures, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** True if the username is currently within its lock window. */
    public boolean isLocked(String username) {
        Attempt a = attempts.get(key(username));
        return a != null && a.lockedUntil() != null && Instant.now().isBefore(a.lockedUntil());
    }

    /** Records a failed attempt; locks the username once the threshold is reached. */
    public void recordFailure(String username) {
        String key = key(username);
        attempts.compute(key, (k, prev) -> {
            // Drop a stale/expired lock before counting afresh.
            int base = (prev == null || expired(prev)) ? 0 : prev.failures();
            int failures = base + 1;
            Instant lockedUntil = failures >= MAX_ATTEMPTS ? Instant.now().plus(LOCK_DURATION) : null;
            return new Attempt(failures, lockedUntil);
        });
    }

    /** Clears all failure state for the username (called on successful login). */
    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private boolean expired(Attempt a) {
        return a.lockedUntil() != null && !Instant.now().isBefore(a.lockedUntil());
    }

    private String key(String username) {
        return username == null ? "" : username.toLowerCase().trim();
    }
}
