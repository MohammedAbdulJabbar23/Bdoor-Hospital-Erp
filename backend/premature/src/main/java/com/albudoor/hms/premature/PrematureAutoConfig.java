package com.albudoor.hms.premature;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.premature")
@EntityScan(basePackages = "com.albudoor.hms.premature.domain")
@EnableJpaRepositories(basePackages = "com.albudoor.hms.premature.infrastructure")
public class PrematureAutoConfig {
}
