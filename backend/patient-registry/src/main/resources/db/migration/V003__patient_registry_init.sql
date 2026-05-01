-- Patient registry: single Patient table with adult and infant column groups.
-- Only one of (adult_*) or (mother_* / infant *) is populated, gated by patient_type.

CREATE SEQUENCE mrn_seq START 1 INCREMENT 1;

CREATE TABLE patient (
    id                 UUID PRIMARY KEY,
    mrn                VARCHAR(30)  NOT NULL,
    patient_type       VARCHAR(20)  NOT NULL,
    full_name          VARCHAR(300) NOT NULL,
    gender             VARCHAR(20)  NOT NULL,
    date_of_birth      DATE         NOT NULL,
    is_vip             BOOLEAN      NOT NULL DEFAULT FALSE,
    archived           BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Adult details (nullable for infants)
    national_id              VARCHAR(50),
    mobile_number            VARCHAR(30),
    address                  VARCHAR(500),
    occupation               VARCHAR(200),
    emergency_contact_name   VARCHAR(200),
    emergency_contact_mobile VARCHAR(30),

    -- Infant details (nullable for adults)
    mother_patient_id        UUID,
    mother_name              VARCHAR(200),
    mother_national_id       VARCHAR(50),
    mother_mobile            VARCHAR(30),
    father_name              VARCHAR(200),
    father_mobile            VARCHAR(30),
    dob_time                 TIME,
    place_of_birth           VARCHAR(30),
    delivery_type            VARCHAR(30),
    apgar_1min               INTEGER,
    apgar_5min               INTEGER,
    birth_weight_kg          NUMERIC(5,3),
    length_cm                NUMERIC(5,2),
    ofc_cm                   NUMERIC(5,2),
    gestational_age_weeks    INTEGER,
    gestational_age_days     INTEGER,
    guardian_name            VARCHAR(200),
    guardian_relationship    VARCHAR(100),
    guardian_mobile          VARCHAR(30),
    guardian_national_id     VARCHAR(50),

    created_at         TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(100),
    updated_at         TIMESTAMPTZ,
    updated_by         VARCHAR(100),

    CONSTRAINT uk_patient_mrn UNIQUE (mrn),
    CONSTRAINT fk_patient_mother
        FOREIGN KEY (mother_patient_id) REFERENCES patient(id) ON DELETE SET NULL,
    CONSTRAINT chk_patient_type
        CHECK (patient_type IN ('ADULT', 'INFANT')),
    CONSTRAINT chk_patient_gender
        CHECK (gender IN ('MALE', 'FEMALE'))
);

CREATE INDEX idx_patient_full_name      ON patient (LOWER(full_name));
CREATE INDEX idx_patient_mobile         ON patient (mobile_number);
CREATE INDEX idx_patient_national_id    ON patient (national_id);
CREATE INDEX idx_patient_mother_mobile  ON patient (mother_mobile);
CREATE INDEX idx_patient_archived       ON patient (archived);
