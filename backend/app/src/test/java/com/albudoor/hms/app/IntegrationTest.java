package com.albudoor.hms.app;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for full-context integration tests. Boots the whole HMS app against a real
 * Postgres 16 container (Flyway migrates the schema), so cross-module bridges and
 * @TransactionalEventListener wiring are exercised exactly as in production.
 *
 * <p>Subclasses are intentionally NOT @Transactional: AFTER_COMMIT event listeners
 * (e.g. PaymentVisitBridge, PrematurePaymentBridge) only fire once the producing
 * transaction commits. Use unique data per test and assert on specific ids.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class IntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
}
