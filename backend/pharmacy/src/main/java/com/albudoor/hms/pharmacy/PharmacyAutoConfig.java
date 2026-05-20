package com.albudoor.hms.pharmacy;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.pharmacy")
@EntityScan(basePackages = "com.albudoor.hms.pharmacy.domain")
@EnableJpaRepositories(basePackages = "com.albudoor.hms.pharmacy.infrastructure")
public class PharmacyAutoConfig {
}
