package com.albudoor.hms.identity.updateuser;

import com.albudoor.hms.identity.domain.Role;
import com.albudoor.hms.identity.domain.User;
import com.albudoor.hms.identity.infrastructure.UserRepository;
import com.albudoor.hms.platform.exception.ConflictException;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateUserHandler {

    private final UserRepository users;

    public UpdateUserHandler(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public User handle(UUID id, UpdateUserCommand cmd, UUID actingUserId) {
        User user = users.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        boolean editingSelf = id.equals(actingUserId);
        boolean willHaveAdmin = cmd.roles().contains(Role.ADMIN);

        // Self-lockout guards: an admin must not be able to lock their own account out mid-session.
        if (editingSelf && !cmd.active()) {
            throw new DomainException("CANNOT_DEACTIVATE_SELF",
                    "You cannot deactivate your own account.");
        }
        if (editingSelf && !willHaveAdmin) {
            throw new DomainException("CANNOT_DEMOTE_SELF",
                    "You cannot remove your own administrator access.");
        }

        // Last-admin guard: the system must always retain at least one active administrator.
        boolean wasActiveAdmin = user.isActive() && user.getRoles().contains(Role.ADMIN);
        boolean willBeActiveAdmin = cmd.active() && willHaveAdmin;
        if (wasActiveAdmin && !willBeActiveAdmin && users.countActiveAdmins() <= 1) {
            throw new ConflictException("LAST_ADMIN",
                    "This is the last active administrator; assign ADMIN to another active user first.");
        }

        user.rename(cmd.fullName());
        user.replaceRoles(cmd.roles());
        if (cmd.active()) {
            user.activate();
        } else {
            user.deactivate();
        }
        return users.save(user);
    }
}
