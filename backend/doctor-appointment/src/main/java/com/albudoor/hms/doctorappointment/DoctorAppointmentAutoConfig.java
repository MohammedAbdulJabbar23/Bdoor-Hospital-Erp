package com.albudoor.hms.doctorappointment;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.doctorappointment")
@EntityScan(basePackages = "com.albudoor.hms.doctorappointment.domain")
@EnableJpaRepositories(basePackages = "com.albudoor.hms.doctorappointment.infrastructure")
public class DoctorAppointmentAutoConfig {
}
