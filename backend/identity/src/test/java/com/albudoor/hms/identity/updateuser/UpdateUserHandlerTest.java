package com.albudoor.hms.identity.updateuser;

import com.albudoor.hms.identity.domain.Role;
import com.albudoor.hms.identity.domain.User;
import com.albudoor.hms.identity.infrastructure.UserRepository;
import com.albudoor.hms.platform.exception.ConflictException;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UpdateUserHandler}'s guard logic, with the repository mocked so each guard
 * can be exercised in isolation — including the {@code LAST_ADMIN} defense, which is only reachable
 * via the real auth model in a stale-token edge case (a deactivated admin whose JWT is still valid).
 */
class UpdateUserHandlerTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UpdateUserHandler handler = new UpdateUserHandler(users);

    private User stub(String username, String name, Set<Role> roles, boolean active) {
        User u = User.create(username, "hash", name, roles);
        if (!active) u.deactivate();
        when(users.findById(u.getId())).thenReturn(Optional.of(u));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        return u;
    }

    @Test
    void updates_name_roles_and_active_whenActorIsSomeoneElse() {
        User target = stub("bob", "Bob", Set.of(Role.RECEPTIONIST), true);
        when(users.countActiveAdmins()).thenReturn(5L);

        User result = handler.handle(target.getId(),
                new UpdateUserCommand("Robert", Set.of(Role.DOCTOR, Role.NURSE), false),
                UUID.randomUUID());

        assertThat(result.getFullName()).isEqualTo("Robert");
        assertThat(result.getRoles()).containsExactlyInAnyOrder(Role.DOCTOR, Role.NURSE);
        assertThat(result.isActive()).isFalse();
        verify(users).save(target);
    }

    @Test
    void unknownId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> handler.handle(id,
                new UpdateUserCommand("X", Set.of(Role.NURSE), true), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cannotDeactivateSelf() {
        User admin = stub("admin", "Admin", Set.of(Role.ADMIN), true);
        assertThatThrownBy(() -> handler.handle(admin.getId(),
                new UpdateUserCommand("Admin", Set.of(Role.ADMIN), false), admin.getId()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("deactivate your own");
        verify(users, never()).save(any());
    }

    @Test
    void cannotDemoteSelf() {
        User admin = stub("admin", "Admin", Set.of(Role.ADMIN, Role.RECEPTIONIST), true);
        assertThatThrownBy(() -> handler.handle(admin.getId(),
                new UpdateUserCommand("Admin", Set.of(Role.RECEPTIONIST), true), admin.getId()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("administrator access");
        verify(users, never()).save(any());
    }

    @Test
    void lastActiveAdmin_cannotBeDemoted_byAnotherActor() {
        // Target is an active admin and only ONE active admin exists; a different actor edits it.
        User target = stub("soleadmin", "Sole", Set.of(Role.ADMIN), true);
        when(users.countActiveAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> handler.handle(target.getId(),
                new UpdateUserCommand("Sole", Set.of(Role.RECEPTIONIST), true), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("last active administrator");
        verify(users, never()).save(any());
    }

    @Test
    void admin_canBeDemoted_whenAnotherActiveAdminRemains() {
        User target = stub("adminB", "Admin B", Set.of(Role.ADMIN), true);
        when(users.countActiveAdmins()).thenReturn(2L);

        User result = handler.handle(target.getId(),
                new UpdateUserCommand("Admin B", Set.of(Role.RECEPTIONIST), true), UUID.randomUUID());

        assertThat(result.getRoles()).containsExactly(Role.RECEPTIONIST);
        verify(users).save(target);
    }
}
