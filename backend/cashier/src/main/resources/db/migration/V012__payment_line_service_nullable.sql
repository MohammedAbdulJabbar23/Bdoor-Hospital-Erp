-- Allow ad-hoc payment lines without a catalogue service item (e.g. doctor consult fees,
-- where the price is sourced from doctor.consultation_fee rather than a catalogue row).
ALTER TABLE payment_line_item ALTER COLUMN service_item_id DROP NOT NULL;
