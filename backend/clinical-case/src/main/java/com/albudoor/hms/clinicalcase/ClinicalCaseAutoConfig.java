package com.albudoor.hms.clinicalcase;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.clinicalcase")
@EntityScan(basePackages = "com.albudoor.hms.clinicalcase.domain")
@EnableJpaRepositories(basePackages = "com.albudoor.hms.clinicalcase.infrastructure")
public class ClinicalCaseAutoConfig {
}
