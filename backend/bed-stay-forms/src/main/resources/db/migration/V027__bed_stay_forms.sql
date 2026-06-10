-- HMS-BRD-REC-005 §6.6 / HMS-BRD-REC-004 §6.7 — clinical forms shared by Premature & Emergency
-- bed-stays: Medical History Sheet, Nursing Procedures log, Treatment Chart.

CREATE TABLE stay_medical_history (
    id                      UUID PRIMARY KEY,
    department              VARCHAR(20)  NOT NULL,
    stay_id                 UUID         NOT NULL,
    weight_kg               NUMERIC(6,2),
    height_cm               NUMERIC(6,2),
    doctor_name             VARCHAR(200),
    chief_complaint         VARCHAR(2000),
    present_illness_hx      VARCHAR(4000),
    ps_hx                   VARCHAR(2000),
    pm_hx                   VARCHAR(2000),
    family_hx               VARCHAR(2000),
    allergic_hx             VARCHAR(2000),
    social_smoker           VARCHAR(200),
    social_alcohol          VARCHAR(200),
    social_sleep            VARCHAR(200),
    drug_hx                 VARCHAR(2000),
    physical_examination    VARCHAR(4000),
    specialist_sign_key     VARCHAR(500),
    specialist_sign_name    VARCHAR(200),
    specialist_signed_by    UUID,
    specialist_signed_at    TIMESTAMPTZ,
    permanent_sign_key      VARCHAR(500),
    permanent_sign_name     VARCHAR(200),
    permanent_signed_by     UUID,
    permanent_signed_at     TIMESTAMPTZ,
    resident_sign_key       VARCHAR(500),
    resident_sign_name      VARCHAR(200),
    resident_signed_by      UUID,
    resident_signed_at      TIMESTAMPTZ,
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL,
    created_by              VARCHAR(100),
    updated_at              TIMESTAMPTZ,
    updated_by              VARCHAR(100),
    CONSTRAINT uk_stay_mh UNIQUE (department, stay_id),
    CONSTRAINT chk_stay_mh_dept CHECK (department IN ('PREMATURE', 'EMERGENCY'))
);

CREATE TABLE stay_nursing_procedure (
    id              UUID PRIMARY KEY,
    department      VARCHAR(20)  NOT NULL,
    stay_id         UUID         NOT NULL,
    procedure_name  VARCHAR(300) NOT NULL,
    performed_at    TIMESTAMPTZ  NOT NULL,
    note            VARCHAR(2000),
    nurse_name      VARCHAR(200),
    recorded_by     UUID,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT chk_stay_np_dept CHECK (department IN ('PREMATURE', 'EMERGENCY'))
);
CREATE INDEX idx_stay_np_stay ON stay_nursing_procedure (department, stay_id, performed_at DESC);

CREATE TABLE stay_treatment_chart (
    id              UUID PRIMARY KEY,
    department      VARCHAR(20)  NOT NULL,
    stay_id         UUID         NOT NULL,
    chart_date      DATE         NOT NULL,
    doctor_sign_key VARCHAR(500),
    doctor_sign_name VARCHAR(200),
    doctor_signed_by UUID,
    doctor_signed_at TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT uk_stay_tc UNIQUE (department, stay_id, chart_date),
    CONSTRAINT chk_stay_tc_dept CHECK (department IN ('PREMATURE', 'EMERGENCY'))
);

CREATE TABLE stay_treatment_chart_row (
    chart_id        UUID         NOT NULL REFERENCES stay_treatment_chart(id) ON DELETE CASCADE,
    position        INTEGER      NOT NULL,
    medicine_name   VARCHAR(300) NOT NULL,
    dose            VARCHAR(120),
    frequency       VARCHAR(120),
    timing1         VARCHAR(60),
    timing2         VARCHAR(60),
    timing3         VARCHAR(60),
    timing4         VARCHAR(60),
    timing5         VARCHAR(60),
    timing6         VARCHAR(60),
    PRIMARY KEY (chart_id, position)
);
