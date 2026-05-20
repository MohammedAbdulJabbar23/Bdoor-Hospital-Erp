-- Pharmacy: one PharmacyDispense per finalized doctor exam that has prescriptions.
-- Lifecycle: PENDING (created from exam) → AWAITING_PAYMENT (charged, payment in cashier queue)
-- → READY_TO_GIVE (cashier approved) → DISPENSED (pharmacist handed meds to patient).
-- CANCELLED is terminal from any non-DISPENSED state.

CREATE SEQUENCE pharmacy_dispense_id_seq START 1 INCREMENT 1;

CREATE TABLE pharmacy_dispense (
    id                       UUID PRIMARY KEY,
    dispense_display_id      VARCHAR(30) NOT NULL,

    -- Snapshotted from the source exam
    exam_id                  UUID         NOT NULL,
    visit_id                 UUID         NOT NULL,
    visit_display_id         VARCHAR(30)  NOT NULL,
    patient_id               UUID         NOT NULL,
    patient_mrn              VARCHAR(30)  NOT NULL,
    patient_name             VARCHAR(300) NOT NULL,
    doctor_id                UUID         NOT NULL,
    doctor_name              VARCHAR(200) NOT NULL,

    status                   VARCHAR(30)  NOT NULL,

    -- Set when the dispense is charged. Lets the bridge find the dispense from a payment event.
    charge_payment_id        UUID,

    -- Snapshotted timestamps
    charged_at               TIMESTAMPTZ,
    paid_at                  TIMESTAMPTZ,
    given_at                 TIMESTAMPTZ,
    given_by_user_id         UUID,
    cancelled_at             TIMESTAMPTZ,
    cancelled_reason         VARCHAR(500),

    created_at               TIMESTAMPTZ  NOT NULL,
    created_by               VARCHAR(100),
    updated_at               TIMESTAMPTZ,
    updated_by               VARCHAR(100),

    CONSTRAINT uk_pharmacy_dispense_display UNIQUE (dispense_display_id),
    CONSTRAINT uk_pharmacy_dispense_exam    UNIQUE (exam_id),
    CONSTRAINT fk_pharmacy_dispense_visit
        FOREIGN KEY (visit_id) REFERENCES visit(id),
    CONSTRAINT fk_pharmacy_dispense_patient
        FOREIGN KEY (patient_id) REFERENCES patient(id),
    CONSTRAINT chk_pharmacy_dispense_status
        CHECK (status IN ('PENDING', 'AWAITING_PAYMENT', 'READY_TO_GIVE', 'DISPENSED', 'CANCELLED'))
);

CREATE INDEX idx_pharmacy_dispense_status_created ON pharmacy_dispense (status, created_at);
CREATE INDEX idx_pharmacy_dispense_patient        ON pharmacy_dispense (patient_id);
CREATE INDEX idx_pharmacy_dispense_visit          ON pharmacy_dispense (visit_id);
CREATE INDEX idx_pharmacy_dispense_payment        ON pharmacy_dispense (charge_payment_id);

-- Drug lines snapshotted from the prescription. A line is "priced" only if drug_service_item_id
-- resolves to a catalogue entry; otherwise it's a free-text Rx (e.g. doctor wrote "panadol" without
-- picking from the catalogue) and the line shows on the dispense for record-keeping but is not billed.
CREATE TABLE pharmacy_dispense_line (
    dispense_id              UUID         NOT NULL REFERENCES pharmacy_dispense(id) ON DELETE CASCADE,
    line_no                  INTEGER      NOT NULL,

    drug_service_item_id     UUID,
    drug_code                VARCHAR(50),
    drug_name                VARCHAR(300) NOT NULL,
    strength                 VARCHAR(100),
    dose                     VARCHAR(100),
    frequency                VARCHAR(100),
    duration                 VARCHAR(100),
    route                    VARCHAR(50),
    notes                    VARCHAR(500),

    -- Pricing snapshot at the moment the dispense was created. NULL when no catalogue match.
    unit_fee                 NUMERIC(12,2),
    quantity                 INTEGER      NOT NULL DEFAULT 1,
    line_total               NUMERIC(12,2),

    PRIMARY KEY (dispense_id, line_no)
);
CREATE INDEX idx_pharmacy_dispense_line_dispense ON pharmacy_dispense_line (dispense_id);
