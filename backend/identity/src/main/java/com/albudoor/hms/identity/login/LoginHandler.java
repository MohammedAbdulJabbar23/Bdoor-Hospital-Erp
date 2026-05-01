package com.albudoor.hms.identity.login;

import com.albudoor.hms.identity.domain.User;
import com.albudoor.hms.identity.infrastructure.UserRepository;
import com.albudoor.hms.identity.infrastructure.security.JwtService;
import com.albudoor.hms.platform.exception.DomainException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginHandler {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public LoginHandler(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public LoginResponse handle(LoginRequest req) {
        User user = users.findByUsername(req.username().toLowerCase().trim())
                .orElseThrow(() -> new DomainException("INVALID_CREDENTIALS", "Invalid username or password"));

        if (!user.isActive()) {
            throw new DomainException("USER_INACTIVE", "User account is deactivated");
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new DomainException("INVALID_CREDENTIALS", "Invalid username or password");
        }

        JwtService.Issued issued = jwt.issue(user);
        return new LoginResponse(
                issued.token(),
                issued.expiresAt(),
                new LoginResponse.UserSummary(user.getId(), user.getUsername(), user.getFullName(),
                        user.getRoles().stream().toList())
        );
    }
}
