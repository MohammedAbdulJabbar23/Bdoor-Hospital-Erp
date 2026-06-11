-- Per-patient timeline queries (HistoryContributor) — parity with other per-patient tables.
CREATE INDEX idx_prem_admission_patient ON prem_admission (patient_id, admitted_at DESC);
CREATE INDEX idx_emerg_case_patient ON emerg_case (patient_id, admitted_at DESC);
