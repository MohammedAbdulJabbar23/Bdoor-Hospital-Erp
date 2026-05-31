-- HMS-BRD-REC-004 Emergency admission spine: bed inventory + bed-stay case.

CREATE TABLE emerg_bed (
    id          UUID PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL,
    room        VARCHAR(100),
    status      VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(100),
    CONSTRAINT uk_emerg_bed_code UNIQUE (code),
    CONSTRAINT chk_emerg_bed_status CHECK (status IN ('AVAILABLE', 'PENDING_PAYMENT', 'OCCUPIED'))
);

CREATE TABLE emerg_case (
    id                    UUID PRIMARY KEY,
    visit_id              UUID         NOT NULL,
    visit_display_id      VARCHAR(30)  NOT NULL,
    patient_id            UUID         NOT NULL,
    patient_mrn           VARCHAR(30)  NOT NULL,
    patient_name          VARCHAR(300) NOT NULL,
    bed_id                UUID         NOT NULL,
    bed_code              VARCHAR(30)  NOT NULL,
    service_item_id       UUID         NOT NULL,
    service_code          VARCHAR(50)  NOT NULL,
    service_name          VARCHAR(300) NOT NULL,
    status                VARCHAR(30)  NOT NULL,
    stay_value            INTEGER      NOT NULL,
    stay_unit             VARCHAR(10)  NOT NULL,
    admitted_at           TIMESTAMPTZ  NOT NULL,
    stay_expires_at       TIMESTAMPTZ  NOT NULL,
    treatment_finished_at TIMESTAMPTZ,
    closed_at             TIMESTAMPTZ,
    initial_payment_id    UUID,
    final_payment_id      UUID,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    created_by            VARCHAR(100),
    updated_at            TIMESTAMPTZ,
    updated_by            VARCHAR(100),
    CONSTRAINT uk_emerg_case_visit UNIQUE (visit_id),
    CONSTRAINT fk_emerg_case_bed FOREIGN KEY (bed_id) REFERENCES emerg_bed(id),
    CONSTRAINT chk_emerg_case_status CHECK (status IN
        ('AWAITING_INITIAL_PAYMENT', 'UNDER_TREATMENT', 'TREATMENT_FINISHED',
         'AWAITING_DISCHARGE_PAYMENT', 'CLOSED', 'CANCELLED')),
    CONSTRAINT chk_emerg_case_stay_unit CHECK (stay_unit IN ('HOURS', 'DAYS'))
);
CREATE INDEX idx_emerg_case_status ON emerg_case (status);
CREATE INDEX idx_emerg_case_bed ON emerg_case (bed_id);
CREATE INDEX idx_emerg_case_initial_payment ON emerg_case (initial_payment_id);
CREATE INDEX idx_emerg_case_final_payment ON emerg_case (final_payment_id);

-- Dedicated discharge fee item (admin-configurable; default 0), billed for the FINAL payment.
INSERT INTO service_item (id, category, code, name_en, name_ar, fee, currency, sort_order, active, forward_to, created_at, created_by) VALUES
(gen_random_uuid(), 'EMERGENCY', 'EM-DISCHARGE', 'Emergency Discharge', 'خروج الطوارئ', 0, 'IQD', 99, TRUE, NULL, NOW(), 'flyway');

INSERT INTO emerg_bed (id, code, room, status, active, created_at, created_by) VALUES
(gen_random_uuid(), 'EMRG-01', 'Bay 1', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-02', 'Bay 1', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-03', 'Bay 1', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-04', 'Bay 2', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-05', 'Bay 2', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-06', 'Bay 2', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-07', 'Resus', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-08', 'Resus', 'AVAILABLE', TRUE, NOW(), 'flyway');
