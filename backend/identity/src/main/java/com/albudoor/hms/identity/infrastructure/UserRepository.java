package com.albudoor.hms.identity.infrastructure;

import com.albudoor.hms.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    /**
     * Number of active users holding the ADMIN role. Used by the edit guard to refuse any change
     * that would leave the system with zero active administrators. A derived query over the
     * {@code @ElementCollection} role set is unreliable, so this is explicit JPQL.
     */
    @Query("select count(u) from User u join u.roles r "
            + "where r = com.albudoor.hms.identity.domain.Role.ADMIN and u.active = true")
    long countActiveAdmins();
}
