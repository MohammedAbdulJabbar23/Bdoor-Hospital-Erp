package com.albudoor.hms.app;

import com.albudoor.hms.cashier.CashierAutoConfig;
import com.albudoor.hms.catalogue.CatalogueAutoConfig;
import com.albudoor.hms.clinicalcase.ClinicalCaseAutoConfig;
import com.albudoor.hms.departmentservices.DepartmentServicesAutoConfig;
import com.albudoor.hms.doctorappointment.DoctorAppointmentAutoConfig;
import com.albudoor.hms.identity.IdentityAutoConfig;
import com.albudoor.hms.patientregistry.PatientRegistryAutoConfig;
import com.albudoor.hms.pharmacy.PharmacyAutoConfig;
import com.albudoor.hms.platform.PlatformAutoConfig;
import com.albudoor.hms.emergency.EmergencyAutoConfig;
import com.albudoor.hms.premature.PrematureAutoConfig;
import com.albudoor.hms.visitmanagement.VisitManagementAutoConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@Import({
        PlatformAutoConfig.class,
        IdentityAutoConfig.class,
        PatientRegistryAutoConfig.class,
        CatalogueAutoConfig.class,
        VisitManagementAutoConfig.class,
        CashierAutoConfig.class,
        DoctorAppointmentAutoConfig.class,
        DepartmentServicesAutoConfig.class,
        ClinicalCaseAutoConfig.class,
        PharmacyAutoConfig.class,
        PrematureAutoConfig.class,
        EmergencyAutoConfig.class
})
public class HmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmsApplication.class, args);
    }
}
