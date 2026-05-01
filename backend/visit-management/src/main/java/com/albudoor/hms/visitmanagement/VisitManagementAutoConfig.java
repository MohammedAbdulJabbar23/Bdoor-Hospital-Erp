package com.albudoor.hms.visitmanagement;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.visitmanagement")
@EntityScan(basePackages = "com.albudoor.hms.visitmanagement.domain")
@EnableJpaRepositories(basePackages = "com.albudoor.hms.visitmanagement.infrastructure")
public class VisitManagementAutoConfig {
}
