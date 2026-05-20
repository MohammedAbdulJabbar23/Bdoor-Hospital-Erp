package com.albudoor.hms.doctorappointment.listdoctors;

import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListDoctorsHandler {

    private final DoctorRepository repo;

    public ListDoctorsHandler(DoctorRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<Doctor> list(boolean activeOnly) {
        if (activeOnly) return repo.findAllByActiveOrderByFullNameAsc(true);
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public Doctor byId(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + id));
    }
}
