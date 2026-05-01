package com.albudoor.hms.app;

import com.albudoor.hms.identity.domain.Role;
import com.albudoor.hms.identity.domain.User;
import com.albudoor.hms.identity.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Idempotent seeder for development. Adds any of the canonical role-based users
 * that don't yet exist; never modifies existing users (so changing a seeded
 * password in the DB won't get overwritten on next boot).
 *
 * Username == password for every seeded account — convenient for dev only.
 * Replace before production.
 */
@Component
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public DevDataSeeder(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        List<SeedUser> seedUsers = List.of(
                new SeedUser("admin",       "Dr. Ahmed Al-Saadi",       Set.of(Role.ADMIN, Role.RECEPTIONIST)),
                new SeedUser("receptionist","Layla Hassan",             Set.of(Role.RECEPTIONIST)),
                new SeedUser("doctor",      "Dr. Kareem Al-Janabi",     Set.of(Role.DOCTOR)),
                new SeedUser("nurse",       "Mariam Abdullah",          Set.of(Role.NURSE)),
                new SeedUser("cashier",     "Yusuf Al-Bayati",          Set.of(Role.CASHIER)),
                new SeedUser("lab",         "Zainab Al-Mosawi",         Set.of(Role.LAB_STAFF)),
                new SeedUser("radiology",   "Omar Al-Tikriti",          Set.of(Role.RADIOLOGY_STAFF)),
                new SeedUser("eco",         "Hala Al-Khafaji",          Set.of(Role.ECO_STAFF)),
                new SeedUser("emergency",   "Dr. Hassan Al-Obeidi",     Set.of(Role.EMERGENCY_STAFF, Role.DOCTOR)),
                new SeedUser("premature",   "Dr. Noor Al-Rubaie",       Set.of(Role.PREMATURE_STAFF, Role.DOCTOR)),
                new SeedUser("pharmacist",  "Sarah Al-Hashimi",         Set.of(Role.PHARMACIST))
        );

        int created = 0;
        for (SeedUser su : seedUsers) {
            if (users.existsByUsername(su.username)) {
                continue;
            }
            User user = User.create(su.username, encoder.encode(su.username), su.fullName, su.roles);
            users.save(user);
            created++;
            log.info("Seeded user '{}' ({}) — password = '{}'", su.username, su.fullName, su.username);
        }
        if (created == 0) {
            log.info("DevDataSeeder: all canonical users already exist; nothing to add.");
        } else {
            log.warn("DevDataSeeder: created {} dev users with username==password. CHANGE BEFORE PRODUCTION.", created);
        }
    }

    private record SeedUser(String username, String fullName, Set<Role> roles) {}
}
