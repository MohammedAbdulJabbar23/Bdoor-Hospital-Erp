-- Forwarded child visits completed before resultsLastUpdatedAt was stamped at completion
-- (fix on 2026-06-11) have NULL there, leaving their order rows on "Awaiting results"
-- despite results being present. Backfill with the completion time.
UPDATE visit
SET results_last_updated_at = ended_at
WHERE parent_visit_id IS NOT NULL
  AND status = 'COMPLETED'
  AND results_last_updated_at IS NULL;
