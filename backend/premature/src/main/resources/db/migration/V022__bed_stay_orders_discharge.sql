-- Bed-stay clinical loop: free-text discharge note + finish-treatment override reason.
ALTER TABLE prem_admission ADD COLUMN discharge_note text;
ALTER TABLE prem_admission ADD COLUMN finish_override_reason text;

ALTER TABLE emerg_case ADD COLUMN discharge_note text;
ALTER TABLE emerg_case ADD COLUMN finish_override_reason text;
