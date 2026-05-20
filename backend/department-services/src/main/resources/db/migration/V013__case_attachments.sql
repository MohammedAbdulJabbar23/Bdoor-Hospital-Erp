-- File attachments for department case service lines: PDFs, scans, DICOM exports, echo
-- video stills, etc. The file bytes live on disk (storage_key resolves via FileStorage);
-- the DB row carries metadata + audit trail.

CREATE TABLE case_attachment (
    id                  UUID PRIMARY KEY,
    case_id             UUID         NOT NULL REFERENCES department_case(id) ON DELETE CASCADE,
    service_item_id     UUID         NOT NULL REFERENCES service_item(id),
    file_name           VARCHAR(300) NOT NULL,
    content_type        VARCHAR(150) NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,
    uploaded_at         TIMESTAMPTZ  NOT NULL,
    uploaded_by         UUID,
    CONSTRAINT chk_attachment_size CHECK (size_bytes >= 0)
);
CREATE INDEX idx_case_attachment_case            ON case_attachment (case_id);
CREATE INDEX idx_case_attachment_case_service    ON case_attachment (case_id, service_item_id);
