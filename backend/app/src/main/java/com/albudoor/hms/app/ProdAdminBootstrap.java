package com.albudoor.hms.app;

import com.albudoor.hms.identity.domain.Role;
import com.albudoor.hms.identity.domain.User;
import com.albudoor.hms.identity.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Production counterpart of {@link DevDataSeeder}: on a virgin database (no users at all) it
 * creates a single active ADMIN account, username {@code admin}, with the password supplied via
 * {@code hms.admin.initial-password} (env {@code HMS_ADMIN_INITIAL_PASSWORD}).
 *
 * <p>Once any user exists the runner is a strict no-op, so the initial password is consumed
 * exactly once and never overwrites anything. If the database is empty and no initial password
 * was provided, startup fails fast — a production system with zero users and no way to log in
 * is a misconfiguration, not something to boot through silently.
 *
 * <p>OPERATIONS.md instructs operators to change this password immediately after first login.
 */
@Component
@Profile("prod")
public class ProdAdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProdAdminBootstrap.class);

    static final String ADMIN_USERNAME = "admin";
    static final String ADMIN_FULL_NAME = "System Administrator";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String initialPassword;

    public ProdAdminBootstrap(UserRepository users,
                              PasswordEncoder encoder,
                              @Value("${hms.admin.initial-password:}") String initialPassword) {
        this.users = users;
        this.encoder = encoder;
        this.initialPassword = initialPassword;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            log.info("ProdAdminBootstrap: users already exist; nothing to do.");
            return;
        }
        if (initialPassword == null || initialPassword.isBlank()) {
            throw new IllegalStateException(
                    "User table is empty and HMS_ADMIN_INITIAL_PASSWORD is not set. "
                            + "Provide an initial admin password to bootstrap the first ADMIN user.");
        }
        User admin = User.create(ADMIN_USERNAME, encoder.encode(initialPassword),
                ADMIN_FULL_NAME, Set.of(Role.ADMIN));
        users.save(admin);
        log.warn("ProdAdminBootstrap: created initial ADMIN user '{}'. "
                + "Change this password immediately after first login.", ADMIN_USERNAME);
    }
}
