-- Optimistic locking: every aggregate root (and one peer entity, drug_batch) gets a
-- `version` column. Hibernate uses it as @Version and refuses to commit if the column
-- has changed since the read — preventing lost updates from concurrent cashier approvals,
-- concurrent mark-given on the same drug, etc.
--
-- Backfill default = 0 so existing rows remain readable; new writes start incrementing.

ALTER TABLE patient            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE visit              ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE appointment        ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE doctor             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE department_case    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE doctor_exam        ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pharmacy_dispense  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE service_item       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE drug_batch         ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_user           ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
