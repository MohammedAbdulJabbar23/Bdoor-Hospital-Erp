-- For non-DRUG service items the embedded DrugDetails is null and Hibernate writes NULL
-- across every drug_* column. drug_controlled was declared NOT NULL DEFAULT FALSE which
-- only fits when the item is a drug; relax it so non-drug rows can leave the entire
-- drug_* column group null.

ALTER TABLE service_item ALTER COLUMN drug_controlled DROP NOT NULL;
ALTER TABLE service_item ALTER COLUMN drug_controlled DROP DEFAULT;
