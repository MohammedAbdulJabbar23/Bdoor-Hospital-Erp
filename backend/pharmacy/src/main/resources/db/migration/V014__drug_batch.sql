-- Pharmacy inventory: a drug catalogue item can have many physical batches in stock.
-- A batch has its own expiry date, supplier, and remaining quantity. Dispensing consumes
-- units from the earliest-expiring non-empty batch (FEFO). Receiving stock appends a new
-- batch row.

CREATE TABLE drug_batch (
    id                       UUID PRIMARY KEY,
    drug_service_item_id     UUID         NOT NULL REFERENCES service_item(id),
    batch_no                 VARCHAR(80)  NOT NULL,
    expiry_date              DATE         NOT NULL,
    qty_received             INTEGER      NOT NULL,
    qty_remaining            INTEGER      NOT NULL,
    unit_cost                NUMERIC(12,2),
    supplier                 VARCHAR(200),
    received_at              TIMESTAMPTZ  NOT NULL,
    received_by              UUID,

    CONSTRAINT chk_drug_batch_qty_received  CHECK (qty_received >= 0),
    CONSTRAINT chk_drug_batch_qty_remaining CHECK (qty_remaining >= 0 AND qty_remaining <= qty_received)
);
CREATE INDEX idx_drug_batch_drug_expiry  ON drug_batch (drug_service_item_id, expiry_date);
CREATE INDEX idx_drug_batch_expiry       ON drug_batch (expiry_date) WHERE qty_remaining > 0;
