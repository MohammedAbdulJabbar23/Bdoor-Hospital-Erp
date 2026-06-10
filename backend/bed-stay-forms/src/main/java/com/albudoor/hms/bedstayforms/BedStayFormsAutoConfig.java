package com.albudoor.hms.bedstayforms;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.bedstayforms")
@EntityScan(basePackages = "com.albudoor.hms.bedstayforms.domain")
@EnableJpaRepositories(basePackages = "com.albudoor.hms.bedstayforms.infrastructure")
public class BedStayFormsAutoConfig {
}
