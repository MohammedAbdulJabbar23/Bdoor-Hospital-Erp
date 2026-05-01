-- Visit aggregate: one row per arrival event. Forwarded sub-visits carry parent_visit_id.

CREATE SEQUENCE visit_id_seq START 1 INCREMENT 1;

CREATE TABLE visit (
    id                 UUID PRIMARY KEY,
    visit_display_id   VARCHAR(30)  NOT NULL,
    patient_id         UUID         NOT NULL,
    patient_mrn        VARCHAR(30)  NOT NULL,
    patient_name       VARCHAR(300) NOT NULL,
    visit_type         VARCHAR(30)  NOT NULL,
    origin             VARCHAR(30)  NOT NULL,
    status             VARCHAR(30)  NOT NULL,
    parent_visit_id    UUID,
    originating_type   VARCHAR(30),
    assigned_doctor_id UUID,
    started_at         TIMESTAMPTZ  NOT NULL,
    ended_at           TIMESTAMPTZ,
    closure_reason     VARCHAR(500),
    results_summary    VARCHAR(2000),
    created_at         TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(100),
    updated_at         TIMESTAMPTZ,
    updated_by         VARCHAR(100),

    CONSTRAINT uk_visit_display_id UNIQUE (visit_display_id),
    CONSTRAINT fk_visit_patient
        FOREIGN KEY (patient_id) REFERENCES patient(id),
    CONSTRAINT fk_visit_parent
        FOREIGN KEY (parent_visit_id) REFERENCES visit(id) ON DELETE SET NULL,
    CONSTRAINT chk_visit_type
        CHECK (visit_type IN
               ('DOCTOR_APPOINTMENT', 'LABORATORY', 'RADIOLOGY', 'ECO',
                'EMERGENCY', 'PREMATURE', 'PHARMACY')),
    CONSTRAINT chk_visit_origin
        CHECK (origin IN ('DIRECT_NEW', 'DIRECT_RETURNING', 'FORWARDED')),
    CONSTRAINT chk_visit_status
        CHECK (status IN
               ('CREATED', 'AWAITING_PAYMENT', 'IN_PROGRESS', 'AWAITING_RESULTS',
                'TREATMENT_FINISHED', 'AWAITING_FINAL_PAYMENT', 'COMPLETED',
                'CANCELLED', 'OUTSTANDING_BALANCE')),
    CONSTRAINT chk_visit_origin_parent
        CHECK ((origin = 'FORWARDED' AND parent_visit_id IS NOT NULL)
               OR (origin <> 'FORWARDED' AND parent_visit_id IS NULL))
);

CREATE INDEX idx_visit_patient        ON visit (patient_id);
CREATE INDEX idx_visit_type_status    ON visit (visit_type, status);
CREATE INDEX idx_visit_parent         ON visit (parent_visit_id);
CREATE INDEX idx_visit_started_at     ON visit (started_at DESC);
