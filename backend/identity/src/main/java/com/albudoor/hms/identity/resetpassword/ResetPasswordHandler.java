package com.albudoor.hms.identity.resetpassword;

import com.albudoor.hms.identity.domain.User;
import com.albudoor.hms.identity.infrastructure.UserRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ResetPasswordHandler {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public ResetPasswordHandler(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public void handle(UUID id, ResetPasswordCommand cmd) {
        User user = users.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        user.changePassword(encoder.encode(cmd.newPassword()));
        users.save(user);
    }
}
