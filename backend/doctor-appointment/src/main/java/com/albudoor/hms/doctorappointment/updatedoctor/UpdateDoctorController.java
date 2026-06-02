package com.albudoor.hms.doctorappointment.updatedoctor;

import com.albudoor.hms.doctorappointment.api.DoctorResponse;
import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Admin maintenance of a doctor record: update profile fields and activate/deactivate.
 * Deactivating hides the doctor from booking (the slot computation returns no slots) without
 * deleting history. All endpoints are ADMIN-only.
 */
@RestController
@RequestMapping("/api/doctors")
public class UpdateDoctorController {

    public record UpdateDoctorBody(
            @Size(max = 200) String fullName,
            @Size(max = 200) String specialty,
            @PositiveOrZero BigDecimal consultationFee,
            @Size(max = 10) String currency,
            @Size(max = 30) String phone
    ) {}

    private final DoctorRepository repo;

    public UpdateDoctorController(DoctorRepository repo) {
        this.repo = repo;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DoctorResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDoctorBody body) {
        Doctor doctor = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + id));
        doctor.update(body.fullName(), body.specialty(), body.consultationFee(),
                body.currency(), body.phone());
        return DoctorResponse.from(doctor);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DoctorResponse activate(@PathVariable UUID id) {
        Doctor doctor = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + id));
        doctor.activate();
        return DoctorResponse.from(doctor);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DoctorResponse deactivate(@PathVariable UUID id) {
        Doctor doctor = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + id));
        doctor.deactivate();
        return DoctorResponse.from(doctor);
    }
}
