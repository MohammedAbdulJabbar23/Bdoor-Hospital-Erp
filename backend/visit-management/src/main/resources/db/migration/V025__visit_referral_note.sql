-- Free-text note captured when a visit is created as a forward/order (why the patient was sent
-- to this department). Null for direct visits. Surfaced to the receiving department and on the
-- originating bed-stay case's order list.
ALTER TABLE visit ADD COLUMN referral_note text;
