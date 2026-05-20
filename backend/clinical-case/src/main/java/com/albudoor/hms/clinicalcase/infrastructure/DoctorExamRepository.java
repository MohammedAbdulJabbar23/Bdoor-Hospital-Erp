package com.albudoor.hms.clinicalcase.infrastructure;

import com.albudoor.hms.clinicalcase.domain.DoctorExam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorExamRepository extends JpaRepository<DoctorExam, UUID> {

    Optional<DoctorExam> findByVisitId(UUID visitId);

    List<DoctorExam> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
