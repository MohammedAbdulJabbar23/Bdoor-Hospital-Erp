-- REC-003 §6.4 minimum imaging types: previously seeded X-Ray, CT, MRI, US, Mammography.
-- Adds the missing Fluoroscopy and Nuclear Medicine to close the BRD letter.

INSERT INTO service_item (id, category, code, name_en, name_ar, fee, currency, sort_order, active, forward_to, created_at, created_by)
VALUES
    (gen_random_uuid(), 'IMAGING', 'IMG-FLUORO',  'Fluoroscopy',                   'تنظير شعاعي',         50000, 'IQD', 11, TRUE, NULL, NOW(), 'flyway'),
    (gen_random_uuid(), 'IMAGING', 'IMG-NM-BONE', 'Nuclear Medicine — Bone Scan',  'مسح عظمي نووي',      120000, 'IQD', 12, TRUE, NULL, NOW(), 'flyway'),
    (gen_random_uuid(), 'IMAGING', 'IMG-NM-THY',  'Nuclear Medicine — Thyroid',    'مسح غدة نووي',       100000, 'IQD', 13, TRUE, NULL, NOW(), 'flyway');
