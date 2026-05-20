-- Service Department engine: one DepartmentCase per Visit per category, with embedded
-- service lines that carry findings.

CREATE TABLE department_case (
    id                  UUID PRIMARY KEY,
    category            VARCHAR(30)  NOT NULL,
    visit_id            UUID         NOT NULL,
    visit_display_id    VARCHAR(30)  NOT NULL,
    visit_origin        VARCHAR(30)  NOT NULL,
    parent_visit_id     UUID,
    patient_id          UUID         NOT NULL,
    patient_mrn         VARCHAR(30)  NOT NULL,
    patient_name        VARCHAR(300) NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    payment_id          UUID,
    finalized_at        TIMESTAMPTZ,
    results_summary     VARCHAR(4000),
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ,
    updated_by          VARCHAR(100),

    CONSTRAINT uk_dept_case_visit UNIQUE (visit_id),
    CONSTRAINT fk_dept_case_visit
        FOREIGN KEY (visit_id) REFERENCES visit(id),
    CONSTRAINT fk_dept_case_parent_visit
        FOREIGN KEY (parent_visit_id) REFERENCES visit(id) ON DELETE SET NULL,
    CONSTRAINT fk_dept_case_patient
        FOREIGN KEY (patient_id) REFERENCES patient(id),
    CONSTRAINT fk_dept_case_payment
        FOREIGN KEY (payment_id) REFERENCES payment(id) ON DELETE SET NULL,
    CONSTRAINT chk_dept_category
        CHECK (category IN ('LAB', 'RADIOLOGY', 'ECO')),
    CONSTRAINT chk_dept_visit_origin
        CHECK (visit_origin IN ('DIRECT_NEW', 'DIRECT_RETURNING', 'FORWARDED')),
    CONSTRAINT chk_dept_status
        CHECK (status IN ('NEW', 'AWAITING_PAYMENT', 'AWAITING_STUDY',
                          'FINDINGS_COMPLETE', 'CLOSED', 'RETURNED', 'CANCELLED'))
);
CREATE INDEX idx_dept_case_category_status ON department_case (category, status);
CREATE INDEX idx_dept_case_visit            ON department_case (visit_id);
CREATE INDEX idx_dept_case_patient          ON department_case (patient_id);

CREATE TABLE department_case_service (
    case_id          UUID         NOT NULL REFERENCES department_case(id) ON DELETE CASCADE,
    line_no          INTEGER      NOT NULL,
    service_item_id  UUID         NOT NULL REFERENCES service_item(id),
    service_code     VARCHAR(50)  NOT NULL,
    service_name     VARCHAR(300) NOT NULL,
    fee              NUMERIC(12,2),
    line_status      VARCHAR(20)  NOT NULL,
    text_findings    VARCHAR(4000),
    numeric_value    NUMERIC(18,4),
    unit             VARCHAR(50),
    reference_range  VARCHAR(100),
    flag             VARCHAR(20),
    measurements     VARCHAR(2000),
    comments         VARCHAR(2000),
    diagnosis        VARCHAR(2000),
    uploaded_at      TIMESTAMPTZ,
    uploaded_by      UUID,
    PRIMARY KEY (case_id, line_no),
    CONSTRAINT chk_line_status CHECK (line_status IN ('PENDING', 'RESULT_UPLOADED'))
);
CREATE INDEX idx_dept_case_service_case ON department_case_service (case_id);
