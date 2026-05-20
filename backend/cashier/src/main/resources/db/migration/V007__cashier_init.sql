-- Cashier: one Payment per cashier-queue entry, with line items for each billed service.

CREATE SEQUENCE payment_id_seq START 1 INCREMENT 1;

CREATE TABLE payment (
    id                  UUID PRIMARY KEY,
    payment_display_id  VARCHAR(30)  NOT NULL,

    -- Snapshotted visit/patient identity
    visit_id            UUID         NOT NULL,
    visit_display_id    VARCHAR(30)  NOT NULL,
    visit_type          VARCHAR(30)  NOT NULL,
    patient_id          UUID         NOT NULL,
    patient_mrn         VARCHAR(30)  NOT NULL,
    patient_name        VARCHAR(300) NOT NULL,
    patient_was_vip     BOOLEAN      NOT NULL,

    stage               VARCHAR(30)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    vip_bypass          BOOLEAN      NOT NULL,
    total_due           NUMERIC(12,2) NOT NULL,
    currency            VARCHAR(10)  NOT NULL,

    payment_method      VARCHAR(30),
    cashier_user_id     UUID,
    decided_at          TIMESTAMPTZ,
    rejection_reason    VARCHAR(500),

    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ,
    updated_by          VARCHAR(100),

    CONSTRAINT uk_payment_display_id UNIQUE (payment_display_id),
    CONSTRAINT fk_payment_visit
        FOREIGN KEY (visit_id) REFERENCES visit(id),
    CONSTRAINT fk_payment_patient
        FOREIGN KEY (patient_id) REFERENCES patient(id),
    CONSTRAINT chk_payment_stage
        CHECK (stage IN ('INITIAL', 'REFERRAL', 'FINAL', 'STAY_EXTENSION', 'PHARMACY')),
    CONSTRAINT chk_payment_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_payment_method
        CHECK (payment_method IS NULL OR payment_method IN
               ('CASH', 'CARD', 'BANK_TRANSFER', 'VIP_BYPASS')),
    CONSTRAINT chk_payment_decided
        CHECK ((status = 'PENDING' AND decided_at IS NULL)
               OR (status <> 'PENDING' AND decided_at IS NOT NULL))
);

CREATE INDEX idx_payment_status_created ON payment (status, created_at);
CREATE INDEX idx_payment_visit          ON payment (visit_id);
CREATE INDEX idx_payment_patient        ON payment (patient_id);

CREATE TABLE payment_line_item (
    payment_id       UUID         NOT NULL REFERENCES payment(id) ON DELETE CASCADE,
    line_no          INTEGER      NOT NULL,
    service_item_id  UUID         NOT NULL,
    service_code     VARCHAR(50)  NOT NULL,
    service_name     VARCHAR(300) NOT NULL,
    unit_fee         NUMERIC(12,2) NOT NULL,
    quantity         INTEGER      NOT NULL,
    line_total       NUMERIC(12,2) NOT NULL,
    PRIMARY KEY (payment_id, line_no),
    CONSTRAINT fk_payment_line_service
        FOREIGN KEY (service_item_id) REFERENCES service_item(id)
);
CREATE INDEX idx_payment_line_payment ON payment_line_item (payment_id);
