package com.albudoor.hms.identity.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "app_user", uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uk_user_username", columnNames = "username")
})
public class User extends AggregateRoot {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false)
    private boolean active;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    public static User create(String username, String passwordHash, String fullName, Set<Role> roles) {
        if (username == null || username.isBlank()) {
            throw new DomainException("USER_USERNAME_REQUIRED", "Username is required");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new DomainException("USER_PASSWORD_REQUIRED", "Password is required");
        }
        if (roles == null || roles.isEmpty()) {
            throw new DomainException("USER_ROLES_REQUIRED", "At least one role is required");
        }
        User u = new User();
        u.id = UUID.randomUUID();
        u.username = username.toLowerCase().trim();
        u.passwordHash = passwordHash;
        u.fullName = fullName;
        u.active = true;
        u.roles = new HashSet<>(roles);
        return u;
    }

    public void deactivate() {
        this.active = false;
    }

    public void changePassword(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new DomainException("USER_PASSWORD_REQUIRED", "Password is required");
        }
        this.passwordHash = newPasswordHash;
    }
}
