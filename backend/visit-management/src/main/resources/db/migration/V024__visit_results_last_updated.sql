-- Notification fix: the Topbar bell must surface the ORIGINATING visit that just received
-- forwarded results, not the closed child. `results_summary` already records the snapshot,
-- but there was no timestamp to drive a "within last 24h" filter (the parent's `ended_at`
-- stays null while it's still in progress). This column is set whenever a child returns
-- results to its parent.
ALTER TABLE visit ADD COLUMN results_last_updated_at TIMESTAMPTZ;
