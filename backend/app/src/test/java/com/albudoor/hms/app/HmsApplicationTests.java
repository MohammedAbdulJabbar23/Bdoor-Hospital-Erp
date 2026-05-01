package com.albudoor.hms.app;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Enable once Testcontainers Postgres profile is added")
class HmsApplicationTests {

    @Test
    void contextLoads() {
        // Placeholder — boot-up sanity check, enabled when test datasource is wired.
    }
}
