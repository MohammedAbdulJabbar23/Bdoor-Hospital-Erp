package com.albudoor.hms.emergency;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.emergency")
@EntityScan(basePackages = "com.albudoor.hms.emergency.domain")
@EnableJpaRepositories(basePackages = "com.albudoor.hms.emergency.infrastructure")
public class EmergencyAutoConfig {
}
