-- Enforce uniqueness of the adult national_id at the DB level so concurrent inserts
-- cannot create two patients sharing one national ID. Partial index: NULLs are exempt
-- (national_id is optional, and the e2e/test data omits it entirely).
--
-- The non-unique idx_patient_national_id (V003) is dropped — this unique index supersedes
-- it for both lookup and uniqueness.
DROP INDEX IF EXISTS idx_patient_national_id;

CREATE UNIQUE INDEX uk_patient_national_id
    ON patient (national_id)
    WHERE national_id IS NOT NULL;
