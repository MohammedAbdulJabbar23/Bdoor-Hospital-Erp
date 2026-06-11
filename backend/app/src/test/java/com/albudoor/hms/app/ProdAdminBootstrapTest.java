package com.albudoor.hms.app;

import com.albudoor.hms.identity.domain.Role;
import com.albudoor.hms.identity.domain.User;
import com.albudoor.hms.identity.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProdAdminBootstrap} with the repository and encoder mocked — the runner
 * itself only activates under the {@code prod} profile, which the IT suite (default profile)
 * never runs, so plain unit coverage is the right tool here.
 */
class ProdAdminBootstrapTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void emptyUserTable_createsActiveAdminWithEncodedInitialPassword() {
        when(users.count()).thenReturn(0L);
        when(encoder.encode("S3cret!")).thenReturn("encoded-hash");

        new ProdAdminBootstrap(users, encoder, "S3cret!").run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User admin = saved.getValue();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getFullName()).isEqualTo("System Administrator");
        assertThat(admin.getPasswordHash()).isEqualTo("encoded-hash");
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getRoles()).containsExactly(Role.ADMIN);
    }

    @Test
    void usersAlreadyExist_isStrictNoOp() {
        when(users.count()).thenReturn(3L);

        new ProdAdminBootstrap(users, encoder, "S3cret!").run();

        verify(users, never()).save(any());
    }

    @Test
    void emptyUserTable_withBlankInitialPassword_failsFast() {
        when(users.count()).thenReturn(0L);

        assertThatThrownBy(() -> new ProdAdminBootstrap(users, encoder, "  ").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HMS_ADMIN_INITIAL_PASSWORD");
        verify(users, never()).save(any());
    }
}
