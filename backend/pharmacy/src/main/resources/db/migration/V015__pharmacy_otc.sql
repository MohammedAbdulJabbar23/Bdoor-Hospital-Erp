-- Allow walk-in OTC sales: no source exam, no visit, no prescribing doctor.
-- Existing exam/visit-linked dispenses are unaffected.

ALTER TABLE pharmacy_dispense ALTER COLUMN exam_id          DROP NOT NULL;
ALTER TABLE pharmacy_dispense ALTER COLUMN visit_id         DROP NOT NULL;
ALTER TABLE pharmacy_dispense ALTER COLUMN visit_display_id DROP NOT NULL;
ALTER TABLE pharmacy_dispense ALTER COLUMN doctor_id        DROP NOT NULL;
ALTER TABLE pharmacy_dispense ALTER COLUMN doctor_name      DROP NOT NULL;

-- Replace the plain UNIQUE on exam_id with a partial unique index, so multiple OTC sales
-- (each with exam_id IS NULL) are allowed but one exam still yields at most one dispense.
ALTER TABLE pharmacy_dispense DROP CONSTRAINT uk_pharmacy_dispense_exam;
CREATE UNIQUE INDEX uk_pharmacy_dispense_exam_not_null
    ON pharmacy_dispense (exam_id) WHERE exam_id IS NOT NULL;
