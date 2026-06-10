-- HMS-BRD-REC-005 P6 — Patient Case Form (ملف المريض الراقد), one per admission.
-- Registry fields (name, mother, age, sex, address) are prefilled read-only, never stored.
-- One illegible header label on the source scan is excluded pending client confirmation.

CREATE TABLE prem_patient_case (
    id                  UUID PRIMARY KEY,
    admission_id        UUID         NOT NULL,
    ward_number         VARCHAR(60),
    next_of_kin_address VARCHAR(500),
    next_of_kin_phone   VARCHAR(60),
    treating_specialist VARCHAR(200),
    initial_diagnosis   VARCHAR(2000),
    final_diagnosis     VARCHAR(2000),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ,
    updated_by          VARCHAR(100),
    CONSTRAINT uk_prem_patient_case_admission UNIQUE (admission_id),
    CONSTRAINT fk_prem_patient_case_admission FOREIGN KEY (admission_id) REFERENCES prem_admission(id)
);
