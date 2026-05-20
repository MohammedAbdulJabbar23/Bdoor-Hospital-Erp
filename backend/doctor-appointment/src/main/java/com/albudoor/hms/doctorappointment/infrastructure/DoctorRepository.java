package com.albudoor.hms.doctorappointment.infrastructure;

import com.albudoor.hms.doctorappointment.domain.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {
    List<Doctor> findAllByActiveOrderByFullNameAsc(boolean active);
    Optional<Doctor> findByUserId(UUID userId);
}
