-- Service catalogue: lab tests, imaging, ECO, emergency services, drugs.
-- Codes are unique within a category; fees are configurable IQD amounts.

CREATE TABLE service_item (
    id              UUID PRIMARY KEY,
    category        VARCHAR(30)   NOT NULL,
    code            VARCHAR(50)   NOT NULL,
    name_en         VARCHAR(300)  NOT NULL,
    name_ar         VARCHAR(300),
    description     VARCHAR(1000),
    fee             NUMERIC(12,2),
    currency        VARCHAR(10)   NOT NULL DEFAULT 'IQD',
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    forward_to      VARCHAR(30),

    -- Drug-specific (only populated for category = 'DRUG')
    drug_generic_name  VARCHAR(200),
    drug_dosage_form   VARCHAR(100),
    drug_strength      VARCHAR(100),
    drug_unit          VARCHAR(50),
    drug_controlled    BOOLEAN       NOT NULL DEFAULT FALSE,
    drug_supplier      VARCHAR(200),
    drug_barcode       VARCHAR(100),

    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),

    CONSTRAINT uk_service_item_category_code UNIQUE (category, code),
    CONSTRAINT chk_service_category
        CHECK (category IN ('LAB', 'IMAGING', 'ECO', 'EMERGENCY', 'DRUG')),
    CONSTRAINT chk_service_forward_to
        CHECK (forward_to IS NULL OR forward_to IN ('LAB', 'IMAGING', 'ECO', 'EMERGENCY', 'DRUG'))
);

CREATE INDEX idx_service_item_category_active ON service_item (category, active);
CREATE INDEX idx_service_item_name_en ON service_item (LOWER(name_en));

-- =============================================================================
-- Seed: 40 Emergency services from HMS-BRD-REC-004 §6.6 (Albudoor Hospital sheet)
-- Items 37–40 (Echo / Lab / Sonar / X-Ray) are forward-to-target pointers per the
-- locked decision: forwarded patients pay the receiving department's fee at the
-- central cashier, not an in-Emergency line item.
-- Fees below are placeholder IQD values; admin can edit in /admin/catalogues.
-- =============================================================================

INSERT INTO service_item (id, category, code, name_en, name_ar, fee, currency, sort_order, active, forward_to, created_at, created_by) VALUES
(gen_random_uuid(), 'EMERGENCY', 'EM-001', 'Emergency Admission',                    'دخول طوارئ',                       25000,  'IQD',  1, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-002', 'Additional Stay',                        'رقود اضافي',                       15000,  'IQD',  2, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-003', 'Doctor Consultation',                    'كشفية دكتور',                      15000,  'IQD',  3, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-004', 'Injection',                              'زرق ابرة',                          5000,  'IQD',  4, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-005', 'Urinary Catheter Insertion',             'وضع صوندة ادرار',                  10000,  'IQD',  5, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-006', 'ECG',                                    'تخطيط قلب',                        10000,  'IQD',  6, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-007', 'Cannula',                                'كانيولا',                           5000,  'IQD',  7, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-008', 'Patient Follow-up in Ward',              'متابعة المرضى داخل الردهة',        10000,  'IQD',  8, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-009', 'Nasogastric Tube Insertion',             'وضع صوندة معدة',                   12000,  'IQD',  9, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-010', 'Gastric Lavage',                         'غسل معدة',                         15000,  'IQD', 10, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-012', 'Gastric Fluid Aspiration',               'سحب سوائل المعدة',                 12000,  'IQD', 12, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-013', 'Blood and Blood Products Administration','اعطاء الدم ومشتقاتها',            30000,  'IQD', 13, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-014', 'Superficial Wound Suture <5 Stitches',   'خياطة جرح سطحي اقل من 5 غرز',      15000,  'IQD', 14, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-015', 'Superficial Wound Suture >5 Stitches',   'خياطة جرح سطحي كثر من 5 غرز',      25000,  'IQD', 15, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-016', 'Wound Dressing',                         'تضميد الجروح',                      8000,  'IQD', 16, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-017', 'Diabetic Foot Care',                     'تنظيف قدم السكري',                 15000,  'IQD', 17, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-018', 'Cardiac Post-Op Dressing',               'ضماد عمليات القلب',                20000,  'IQD', 18, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-019', 'Nebuliser',                              'جهاز تبخير',                       10000,  'IQD', 19, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-020', 'Blood Glucose Measurement',              'قياس سكر',                          3000,  'IQD', 20, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-021', 'Chest Tube Removal',                     'رفع صوندة الصدر',                  15000,  'IQD', 21, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-022', 'Chest Tube Insertion',                   'وضع صوندة الصدر',                  35000,  'IQD', 22, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-023', 'Joint Relocation',                       'اعادة مفصل مخلوع',                 30000,  'IQD', 23, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-024', 'Skin Traction',                          'سحب جلدي',                         20000,  'IQD', 24, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-025', 'Below-Elbow Cast',                       'تجبيس تحت المرفق',                 25000,  'IQD', 25, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-026', 'Above-Elbow Cast',                       'تجبيس فوق المرفق',                 30000,  'IQD', 26, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-027', 'Below-Knee Cast',                        'تجبيس تحت الركبة',                 30000,  'IQD', 27, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-028', 'Above-Knee Cast',                        'تجبيس فوق الركبة',                 40000,  'IQD', 28, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-029', 'Foot/Ankle/Hand Cast',                   'تجبيس القدم او الكاحل او اليد',    25000,  'IQD', 29, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-030', 'Dressing Change',                        'تبديل ضماد',                        5000,  'IQD', 30, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-031', 'Small Wound Suture Removal',             'رفع خيط جرح صغير',                  5000,  'IQD', 31, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-032', 'Large Wound Suture Removal',             'رفع خيط جرح كبير',                 10000,  'IQD', 32, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-033', 'Ambulance Transfer with Paramedic',      'نقل اسعاف مع مسعف',                50000,  'IQD', 33, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-034', 'Stress Test',                            'فحص الجهد',                        50000,  'IQD', 34, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-035', 'Blood Pressure Measurement',             'قياس ضغط',                          2000,  'IQD', 35, TRUE, NULL,        NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-036', 'IV Infusion',                            'تسريب علاج',                       10000,  'IQD', 36, TRUE, NULL,        NOW(), 'flyway'),
-- Items 37–40 are forward-to-target pointers (no in-Emergency fee).
(gen_random_uuid(), 'EMERGENCY', 'EM-037', 'Echo (forward to ECO dept.)',            'ايكو (إحالة لقسم تخطيط القلب)',     NULL,  'IQD', 37, TRUE, 'ECO',       NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-038', 'Lab Tests (forward to Lab dept.)',       'تحاليل (إحالة للمختبر)',            NULL,  'IQD', 38, TRUE, 'LAB',       NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-039', 'Sonar / Ultrasound (forward to Radiology)', 'سونر (إحالة للأشعة)',           NULL,  'IQD', 39, TRUE, 'IMAGING',   NOW(), 'flyway'),
(gen_random_uuid(), 'EMERGENCY', 'EM-040', 'X-Ray (forward to Radiology)',           'اشعة (إحالة للأشعة)',               NULL,  'IQD', 40, TRUE, 'IMAGING',   NOW(), 'flyway');

-- =============================================================================
-- Seed: stub LAB items (Ahmed to deliver the actual catalogue from BRD-REC-002 §6.4).
-- =============================================================================
INSERT INTO service_item (id, category, code, name_en, name_ar, fee, currency, sort_order, active, forward_to, created_at, created_by) VALUES
(gen_random_uuid(), 'LAB', 'LAB-CBC',         'Complete Blood Count (CBC)',  'تعداد الدم الكامل',       8000, 'IQD',  1, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'LAB', 'LAB-LFT',         'Liver Function Test (LFT)',   'فحص وظائف الكبد',        12000, 'IQD',  2, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'LAB', 'LAB-RFT',         'Renal Function Test',         'فحص وظائف الكلى',        10000, 'IQD',  3, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'LAB', 'LAB-FBS',         'Fasting Blood Sugar',         'سكر الدم صائم',           5000, 'IQD',  4, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'LAB', 'LAB-RBS',         'Random Blood Sugar',          'سكر الدم عشوائي',         5000, 'IQD',  5, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'LAB', 'LAB-HBA1C',       'HbA1c',                       'الهيموغلوبين السكري',    15000, 'IQD',  6, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'LAB', 'LAB-LIPID',       'Lipid Profile',               'فحص الدهون',             12000, 'IQD',  7, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'LAB', 'LAB-URINE',       'Urine Routine',               'فحص ادرار روتيني',        5000, 'IQD',  8, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'LAB', 'LAB-STOOL',       'Stool Routine',               'فحص براز روتيني',         5000, 'IQD',  9, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'LAB', 'LAB-CULTURE',     'Bacterial Culture',           'زرع جرثومي',             20000, 'IQD', 10, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'LAB', 'LAB-TFT',         'Thyroid Function Test',       'فحص وظائف الغدة الدرقية',15000, 'IQD', 11, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'LAB', 'LAB-CRP',         'C-Reactive Protein (CRP)',    'البروتين التفاعلي C',     8000, 'IQD', 12, TRUE, NULL, NOW(), 'flyway');

-- =============================================================================
-- Seed: stub IMAGING items (radiology imaging types, BRD-REC-003 §6.4).
-- =============================================================================
INSERT INTO service_item (id, category, code, name_en, name_ar, fee, currency, sort_order, active, forward_to, created_at, created_by) VALUES
(gen_random_uuid(), 'IMAGING', 'IMG-CXR',     'Chest X-Ray',          'أشعة صدر',           15000, 'IQD',  1, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'IMAGING', 'IMG-AXR',     'Abdominal X-Ray',      'أشعة بطن',           15000, 'IQD',  2, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'IMAGING', 'IMG-LIMB',    'Limb X-Ray',           'أشعة طرف',           12000, 'IQD',  3, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'IMAGING', 'IMG-CT-BR',   'CT Scan — Brain',      'مفراس دماغ',         60000, 'IQD',  4, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'IMAGING', 'IMG-CT-AB',   'CT Scan — Abdomen',    'مفراس بطن',          80000, 'IQD',  5, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'IMAGING', 'IMG-MRI-BR',  'MRI — Brain',          'رنين دماغ',         150000, 'IQD',  6, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'IMAGING', 'IMG-MRI-SP',  'MRI — Spine',          'رنين عمود فقري',    180000, 'IQD',  7, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'IMAGING', 'IMG-US-AB',   'Ultrasound — Abdominal', 'سونار بطن',        25000, 'IQD',  8, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'IMAGING', 'IMG-US-PEL',  'Ultrasound — Pelvic',    'سونار حوض',        25000, 'IQD',  9, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'IMAGING', 'IMG-MAMMO',   'Mammography',          'تصوير الثدي',        45000, 'IQD', 10, TRUE, NULL, NOW(), 'flyway');

-- =============================================================================
-- Seed: stub ECO items (BRD-REC-006 §6.4).
-- =============================================================================
INSERT INTO service_item (id, category, code, name_en, name_ar, fee, currency, sort_order, active, forward_to, created_at, created_by) VALUES
(gen_random_uuid(), 'ECO', 'ECO-2D',      '2D Echocardiogram',         'تخطيط قلب ثنائي الأبعاد',    40000, 'IQD', 1, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'ECO', 'ECO-DOPPLER', 'Color Doppler Echo',        'تخطيط دوبلر ملون',           50000, 'IQD', 2, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'ECO', 'ECO-STRESS',  'Stress Echo',               'إيكو تحت الجهد',             80000, 'IQD', 3, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'ECO', 'ECO-TEE',     'Trans-Esophageal Echo',     'إيكو عبر المريء',           120000, 'IQD', 4, TRUE, NULL, NOW(), 'flyway');

-- =============================================================================
-- Seed: stub DRUG items (pharmacy catalogue starter set).
-- =============================================================================
INSERT INTO service_item (id, category, code, name_en, name_ar, fee, currency, sort_order, active,
                          drug_generic_name, drug_dosage_form, drug_strength, drug_unit, drug_controlled,
                          created_at, created_by) VALUES
(gen_random_uuid(), 'DRUG', 'DRG-PARA-500', 'Paracetamol 500mg',       'باراسيتامول 500ملغ',         500, 'IQD', 1, TRUE, 'Paracetamol', 'Tablet',    '500mg',     'tablet', FALSE, NOW(), 'flyway'),
(gen_random_uuid(), 'DRUG', 'DRG-IBU-400',  'Ibuprofen 400mg',         'إيبوبروفين 400ملغ',          750, 'IQD', 2, TRUE, 'Ibuprofen',   'Tablet',    '400mg',     'tablet', FALSE, NOW(), 'flyway'),
(gen_random_uuid(), 'DRUG', 'DRG-AMOX-500', 'Amoxicillin 500mg',       'أموكسيسيلين 500ملغ',        1000, 'IQD', 3, TRUE, 'Amoxicillin', 'Capsule',   '500mg',     'capsule', FALSE, NOW(), 'flyway'),
(gen_random_uuid(), 'DRUG', 'DRG-METF-500', 'Metformin 500mg',         'ميتفورمين 500ملغ',           500, 'IQD', 4, TRUE, 'Metformin',   'Tablet',    '500mg',     'tablet', FALSE, NOW(), 'flyway'),
(gen_random_uuid(), 'DRUG', 'DRG-INS-REG',  'Insulin (Regular)',       'إنسولين عادي',             15000, 'IQD', 5, TRUE, 'Insulin',     'Injection', '100 IU/ml', 'vial',   FALSE, NOW(), 'flyway'),
(gen_random_uuid(), 'DRUG', 'DRG-OMEP-20',  'Omeprazole 20mg',         'أوميبرازول 20ملغ',           750, 'IQD', 6, TRUE, 'Omeprazole',  'Capsule',   '20mg',      'capsule', FALSE, NOW(), 'flyway'),
(gen_random_uuid(), 'DRUG', 'DRG-CIPRO-500','Ciprofloxacin 500mg',     'سيبروفلوكساسين 500ملغ',     1500, 'IQD', 7, TRUE, 'Ciprofloxacin','Tablet',   '500mg',     'tablet', FALSE, NOW(), 'flyway'),
(gen_random_uuid(), 'DRUG', 'DRG-DICLO-50', 'Diclofenac 50mg',         'ديكلوفيناك 50ملغ',           500, 'IQD', 8, TRUE, 'Diclofenac',  'Tablet',    '50mg',      'tablet', FALSE, NOW(), 'flyway');
