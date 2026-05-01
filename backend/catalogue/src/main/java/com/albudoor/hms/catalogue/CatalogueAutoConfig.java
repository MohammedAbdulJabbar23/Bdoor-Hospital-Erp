package com.albudoor.hms.catalogue;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.catalogue")
@EntityScan(basePackages = "com.albudoor.hms.catalogue.domain")
@EnableJpaRepositories(basePackages = "com.albudoor.hms.catalogue.infrastructure")
public class CatalogueAutoConfig {
}
