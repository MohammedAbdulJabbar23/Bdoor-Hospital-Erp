package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** BRD REC-005 P6 — Patient Case Form (inpatient file), 1:1 with the admission. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "prem_patient_case")
public class PatientCaseForm extends AggregateRoot {

    @Id
    private UUID id;
    @Column(name = "admission_id", nullable = false)
    private UUID admissionId;

    @Column(name = "ward_number", length = 60) private String wardNumber;
    @Column(name = "next_of_kin_address", length = 500) private String nextOfKinAddress;
    @Column(name = "next_of_kin_phone", length = 60) private String nextOfKinPhone;
    @Column(name = "treating_specialist", length = 200) private String treatingSpecialist;
    @Column(name = "initial_diagnosis", length = 2000) private String initialDiagnosis;
    @Column(name = "final_diagnosis", length = 2000) private String finalDiagnosis;

    public static PatientCaseForm create(UUID admissionId) {
        if (admissionId == null) throw new DomainException("ADMISSION_REQUIRED", "admission is required");
        PatientCaseForm f = new PatientCaseForm();
        f.id = UUID.randomUUID();
        f.admissionId = admissionId;
        return f;
    }

    public void update(PatientCaseData d) {
        this.wardNumber = d.wardNumber();
        this.nextOfKinAddress = d.nextOfKinAddress();
        this.nextOfKinPhone = d.nextOfKinPhone();
        this.treatingSpecialist = d.treatingSpecialist();
        this.initialDiagnosis = d.initialDiagnosis();
        this.finalDiagnosis = d.finalDiagnosis();
    }
}
