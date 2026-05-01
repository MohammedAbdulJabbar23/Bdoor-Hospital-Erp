package com.albudoor.hms.identity;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.identity")
@EntityScan(basePackages = "com.albudoor.hms.identity.domain")
@EnableJpaRepositories(basePackages = "com.albudoor.hms.identity.infrastructure")
public class IdentityAutoConfig {
}
