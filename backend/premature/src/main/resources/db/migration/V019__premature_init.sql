-- HMS-BRD-REC-005 Premature admission spine.
-- 1) Extend the service catalogue to allow PREMATURE billing items + seed admission/discharge fees.
-- 2) Bed inventory (admin-managed).
-- 3) Premature admission (the bed-stay case), referencing the visit by id.

-- ---- 1. Catalogue: allow PREMATURE category + seed fee items -----------------------------
ALTER TABLE service_item DROP CONSTRAINT chk_service_category;
ALTER TABLE service_item ADD CONSTRAINT chk_service_category
    CHECK (category IN ('LAB', 'IMAGING', 'ECO', 'EMERGENCY', 'DRUG', 'PREMATURE'));
ALTER TABLE service_item DROP CONSTRAINT chk_service_forward_to;
ALTER TABLE service_item ADD CONSTRAINT chk_service_forward_to
    CHECK (forward_to IS NULL OR forward_to IN ('LAB', 'IMAGING', 'ECO', 'EMERGENCY', 'DRUG', 'PREMATURE'));

INSERT INTO service_item (id, category, code, name_en, name_ar, fee, currency, sort_order, active, forward_to, created_at, created_by) VALUES
(gen_random_uuid(), 'PREMATURE', 'PREM-ADM', 'Premature Admission', 'دخول الخدج',  50000, 'IQD', 1, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'PREMATURE', 'PREM-DIS', 'Premature Discharge', 'خروج الخدج', 30000, 'IQD', 2, TRUE, NULL, NOW(), 'flyway');

-- ---- 2. Bed inventory --------------------------------------------------------------------
CREATE TABLE prem_bed (
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
    CONSTRAINT uk_prem_bed_code UNIQUE (code),
    CONSTRAINT chk_prem_bed_status CHECK (status IN ('AVAILABLE', 'PENDING_PAYMENT', 'OCCUPIED'))
);

-- ---- 3. Premature admission (the bed-stay case) ------------------------------------------
CREATE TABLE prem_admission (
    id                    UUID PRIMARY KEY,
    visit_id              UUID         NOT NULL,
    visit_display_id      VARCHAR(30)  NOT NULL,
    patient_id            UUID         NOT NULL,
    patient_mrn           VARCHAR(30)  NOT NULL,
    patient_name          VARCHAR(300) NOT NULL,
    bed_id                UUID         NOT NULL,
    bed_code              VARCHAR(30)  NOT NULL,
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
    CONSTRAINT uk_prem_admission_visit UNIQUE (visit_id),
    CONSTRAINT fk_prem_admission_bed FOREIGN KEY (bed_id) REFERENCES prem_bed(id),
    CONSTRAINT chk_prem_admission_status CHECK (status IN
        ('AWAITING_ADMISSION_PAYMENT', 'UNDER_CARE', 'TREATMENT_FINISHED',
         'AWAITING_DISCHARGE_PAYMENT', 'CLOSED', 'CANCELLED')),
    CONSTRAINT chk_prem_admission_stay_unit CHECK (stay_unit IN ('HOURS', 'DAYS'))
);

CREATE INDEX idx_prem_admission_status ON prem_admission (status);
CREATE INDEX idx_prem_admission_bed ON prem_admission (bed_id);
CREATE INDEX idx_prem_admission_initial_payment ON prem_admission (initial_payment_id);
CREATE INDEX idx_prem_admission_final_payment ON prem_admission (final_payment_id);

-- ---- Seed a starter set of beds (admin can add/edit more) ---------------------------------
INSERT INTO prem_bed (id, code, room, status, active, created_at, created_by) VALUES
(gen_random_uuid(), 'PREM-01', 'Room A', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'PREM-02', 'Room A', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'PREM-03', 'Room A', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'PREM-04', 'Room B', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'PREM-05', 'Room B', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'PREM-06', 'Room B', 'AVAILABLE', TRUE, NOW(), 'flyway');
