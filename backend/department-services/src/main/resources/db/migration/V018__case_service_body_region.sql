-- REC-003 §7.1 lists "body region" as an optional dedicated field on radiology service
-- lines. Previously overloaded onto the free-text `measurements` column.

ALTER TABLE department_case_service ADD COLUMN body_region VARCHAR(100);
