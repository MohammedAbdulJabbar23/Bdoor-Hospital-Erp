package com.albudoor.hms.departmentservices;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.departmentservices")
@EntityScan(basePackages = "com.albudoor.hms.departmentservices.domain")
@EnableJpaRepositories(basePackages = "com.albudoor.hms.departmentservices.infrastructure")
public class DepartmentServicesAutoConfig {
}
