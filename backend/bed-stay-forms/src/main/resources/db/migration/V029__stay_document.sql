-- Direct document uploads on a bed-stay (incl. BRD REC-005 P7c scanned statistics forms).
-- Result attachments from forwarded Lab/Rad/ECO visits are NOT copied here — they are
-- aggregated at read time from case_attachment via the stay's forwarded visits.

CREATE TABLE stay_document (
    id              UUID PRIMARY KEY,
    department      VARCHAR(20)  NOT NULL,
    stay_id         UUID         NOT NULL,
    patient_id      UUID         NOT NULL,
    file_name       VARCHAR(300) NOT NULL,
    content_type    VARCHAR(150) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    sha256          VARCHAR(64)  NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    label           VARCHAR(200),
    uploaded_by     UUID,
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT chk_stay_doc_dept CHECK (department IN ('PREMATURE', 'EMERGENCY'))
);
CREATE INDEX idx_stay_doc_stay ON stay_document (department, stay_id, created_at DESC);
CREATE INDEX idx_stay_doc_patient ON stay_document (patient_id);
