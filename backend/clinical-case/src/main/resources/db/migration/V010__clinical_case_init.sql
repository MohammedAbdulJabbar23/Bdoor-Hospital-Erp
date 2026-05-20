-- Doctor exam: one record per Visit, with embedded vitals + collections of diagnoses/prescriptions.

CREATE TABLE doctor_exam (
    id                          UUID PRIMARY KEY,
    visit_id                    UUID         NOT NULL,
    visit_display_id            VARCHAR(30)  NOT NULL,
    patient_id                  UUID         NOT NULL,
    patient_mrn                 VARCHAR(30)  NOT NULL,
    patient_name                VARCHAR(300) NOT NULL,
    doctor_id                   UUID         NOT NULL,
    doctor_name                 VARCHAR(200) NOT NULL,

    -- Embedded Vitals
    bp_systolic                 INTEGER,
    bp_diastolic                INTEGER,
    heart_rate                  INTEGER,
    respiratory_rate            INTEGER,
    temperature_c               NUMERIC(4,1),
    oxygen_saturation           INTEGER,
    weight_kg                   NUMERIC(5,2),
    height_cm                   NUMERIC(5,1),
    vitals_notes                VARCHAR(500),

    chief_complaint             VARCHAR(1000),
    history_of_present_illness  VARCHAR(4000),
    examination_notes           VARCHAR(4000),
    plan                        VARCHAR(4000),
    referral_instructions       VARCHAR(1000),

    status                      VARCHAR(20)  NOT NULL,
    finalized_at                TIMESTAMPTZ,
    finalized_by                UUID,

    created_at                  TIMESTAMPTZ  NOT NULL,
    created_by                  VARCHAR(100),
    updated_at                  TIMESTAMPTZ,
    updated_by                  VARCHAR(100),

    CONSTRAINT uk_doctor_exam_visit UNIQUE (visit_id),
    CONSTRAINT fk_doctor_exam_visit
        FOREIGN KEY (visit_id) REFERENCES visit(id),
    CONSTRAINT fk_doctor_exam_patient
        FOREIGN KEY (patient_id) REFERENCES patient(id),
    CONSTRAINT chk_exam_status
        CHECK (status IN ('DRAFT', 'FINALIZED'))
);
CREATE INDEX idx_doctor_exam_patient ON doctor_exam (patient_id);
CREATE INDEX idx_doctor_exam_doctor  ON doctor_exam (doctor_id);
CREATE INDEX idx_doctor_exam_status  ON doctor_exam (status);

CREATE TABLE doctor_exam_diagnosis (
    exam_id          UUID         NOT NULL REFERENCES doctor_exam(id) ON DELETE CASCADE,
    line_no          INTEGER      NOT NULL,
    dx_code          VARCHAR(30),
    dx_description   VARCHAR(500) NOT NULL,
    dx_is_primary    BOOLEAN      NOT NULL DEFAULT FALSE,
    dx_notes         VARCHAR(1000),
    PRIMARY KEY (exam_id, line_no)
);
CREATE INDEX idx_dx_exam ON doctor_exam_diagnosis (exam_id);

CREATE TABLE doctor_exam_prescription (
    exam_id          UUID         NOT NULL REFERENCES doctor_exam(id) ON DELETE CASCADE,
    line_no          INTEGER      NOT NULL,
    rx_drug_id       UUID,
    rx_drug_code     VARCHAR(50),
    rx_drug_name     VARCHAR(300) NOT NULL,
    rx_strength      VARCHAR(100),
    rx_dose          VARCHAR(100),
    rx_frequency     VARCHAR(100),
    rx_duration      VARCHAR(100),
    rx_route         VARCHAR(50),
    rx_notes         VARCHAR(500),
    PRIMARY KEY (exam_id, line_no),
    CONSTRAINT fk_rx_drug
        FOREIGN KEY (rx_drug_id) REFERENCES service_item(id) ON DELETE SET NULL
);
CREATE INDEX idx_rx_exam ON doctor_exam_prescription (exam_id);
