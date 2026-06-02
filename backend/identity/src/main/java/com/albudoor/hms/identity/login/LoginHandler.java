package com.albudoor.hms.identity.login;

import com.albudoor.hms.identity.domain.User;
import com.albudoor.hms.identity.infrastructure.UserRepository;
import com.albudoor.hms.identity.infrastructure.security.JwtService;
import com.albudoor.hms.platform.exception.InvalidCredentialsException;
import com.albudoor.hms.platform.exception.TooManyAttemptsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginHandler {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginAttemptService attempts;

    public LoginHandler(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                        LoginAttemptService attempts) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.attempts = attempts;
    }

    public LoginResponse handle(LoginRequest req) {
        String username = req.username().toLowerCase().trim();

        if (attempts.isLocked(username)) {
            throw new TooManyAttemptsException("Too many failed attempts. Try again later.");
        }

        User user = users.findByUsername(username).orElse(null);
        if (user == null || !user.isActive()
                || !encoder.matches(req.password(), user.getPasswordHash())) {
            attempts.recordFailure(username);
            // Re-check so the attempt that crosses the threshold is itself rejected as locked.
            if (attempts.isLocked(username)) {
                throw new TooManyAttemptsException("Too many failed attempts. Try again later.");
            }
            throw new InvalidCredentialsException("Invalid username or password");
        }

        attempts.recordSuccess(username);

        JwtService.Issued issued = jwt.issue(user);
        return new LoginResponse(
                issued.token(),
                issued.expiresAt(),
                new LoginResponse.UserSummary(user.getId(), user.getUsername(), user.getFullName(),
                        user.getRoles().stream().toList())
        );
    }
}
