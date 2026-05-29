package com.albudoor.hms.app;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for full-context integration tests. A single Postgres 16 container is started once
 * per JVM and shared across every IT class (all share Spring's cached application context,
 * since they use identical configuration). It is intentionally never stopped per-class —
 * the JVM reaps it on exit — which avoids the per-class start/stop churn that caused
 * intermittent connection failures when multiple IT classes ran in one Surefire JVM.
 *
 * <p>Subclasses are NOT @Transactional: AFTER_COMMIT event listeners (PaymentVisitBridge,
 * PrematurePaymentBridge) only fire once the producing transaction commits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
