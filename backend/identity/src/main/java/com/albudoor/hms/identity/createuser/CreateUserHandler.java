package com.albudoor.hms.identity.createuser;

import com.albudoor.hms.identity.domain.User;
import com.albudoor.hms.identity.infrastructure.UserRepository;
import com.albudoor.hms.platform.exception.ConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateUserHandler {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public CreateUserHandler(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public User handle(CreateUserCommand cmd) {
        String username = cmd.username().toLowerCase().trim();
        if (users.existsByUsername(username)) {
            throw new ConflictException("USERNAME_TAKEN", "Username already in use: " + username);
        }
        User user = User.create(username, encoder.encode(cmd.password()), cmd.fullName(), cmd.roles());
        return users.save(user);
    }
}
