package com.albudoor.hms.doctorappointment.createdoctor;

import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateDoctorHandler {

    private final DoctorRepository repo;

    public CreateDoctorHandler(DoctorRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Doctor handle(CreateDoctorCommand cmd) {
        Doctor d = Doctor.create(
                cmd.userId(),
                cmd.fullName(),
                cmd.specialty(),
                cmd.consultationFee(),
                cmd.currency() == null || cmd.currency().isBlank() ? "IQD" : cmd.currency(),
                cmd.phone()
        );
        return repo.save(d);
    }
}
