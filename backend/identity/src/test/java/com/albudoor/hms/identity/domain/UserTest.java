package com.albudoor.hms.identity.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link User} aggregate's edit mutators. Pure domain — no Spring, no DB.
 * The invariant under test: a user always has a non-blank name and at least one role, and the
 * active flag flips cleanly. Username is never touched by edits.
 */
class UserTest {

    private User newUser() {
        return User.create("jdoe", "hash", "John Doe", Set.of(Role.RECEPTIONIST));
    }

    @Test
    void rename_updatesName_andTrims() {
        User u = newUser();
        u.rename("  Jane Roe  ");
        assertThat(u.getFullName()).isEqualTo("Jane Roe");
        assertThat(u.getUsername()).isEqualTo("jdoe"); // username untouched
    }

    @Test
    void rename_blank_isRejected() {
        User u = newUser();
        assertThatThrownBy(() -> u.rename("  "))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Full name");
    }

    @Test
    void replaceRoles_swapsTheRoleSet() {
        User u = newUser();
        u.replaceRoles(Set.of(Role.DOCTOR, Role.ADMIN));
        assertThat(u.getRoles()).containsExactlyInAnyOrder(Role.DOCTOR, Role.ADMIN);
    }

    @Test
    void replaceRoles_empty_isRejected() {
        User u = newUser();
        assertThatThrownBy(() -> u.replaceRoles(Set.of()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("role");
    }

    @Test
    void replaceRoles_null_isRejected() {
        User u = newUser();
        assertThatThrownBy(() -> u.replaceRoles(null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void deactivate_then_activate_flipsActiveFlag() {
        User u = newUser();
        assertThat(u.isActive()).isTrue(); // created active

        u.deactivate();
        assertThat(u.isActive()).isFalse();

        u.activate();
        assertThat(u.isActive()).isTrue();
    }

    @Test
    void changePassword_blank_isRejected() {
        User u = newUser();
        assertThatThrownBy(() -> u.changePassword(" "))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Password");
    }
}
