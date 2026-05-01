package com.albudoor.hms.platform;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.platform")
@EntityScan(basePackages = "com.albudoor.hms.platform.outbox")
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class PlatformAutoConfig {
}
