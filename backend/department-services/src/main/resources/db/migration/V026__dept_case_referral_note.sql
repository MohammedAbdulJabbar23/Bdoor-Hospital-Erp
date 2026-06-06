-- Snapshot of the originating visit's referral note (why the patient was sent to this department),
-- captured when the department case is opened. Lets the receiving department see the order's note.
ALTER TABLE department_case ADD COLUMN referral_note text;
