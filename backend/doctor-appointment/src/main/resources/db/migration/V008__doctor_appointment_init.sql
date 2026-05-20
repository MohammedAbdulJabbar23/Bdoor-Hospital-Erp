-- Doctor profiles + their weekly working schedule + days off + appointments.

CREATE TABLE doctor (
    id                 UUID PRIMARY KEY,
    user_id            UUID,
    full_name          VARCHAR(200)  NOT NULL,
    specialty          VARCHAR(200),
    consultation_fee   NUMERIC(12,2),
    currency           VARCHAR(10)   NOT NULL DEFAULT 'IQD',
    phone              VARCHAR(30),
    active             BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ   NOT NULL,
    created_by         VARCHAR(100),
    updated_at         TIMESTAMPTZ,
    updated_by         VARCHAR(100),
    CONSTRAINT fk_doctor_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE SET NULL
);
CREATE INDEX idx_doctor_active ON doctor (active);
CREATE INDEX idx_doctor_user   ON doctor (user_id);

CREATE TABLE doctor_weekly_hour (
    doctor_id     UUID         NOT NULL REFERENCES doctor(id) ON DELETE CASCADE,
    day_of_week   VARCHAR(10)  NOT NULL,
    start_time    TIME         NOT NULL,
    end_time      TIME         NOT NULL,
    slot_minutes  INTEGER      NOT NULL,
    CONSTRAINT chk_dow CHECK (day_of_week IN
        ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    CONSTRAINT chk_hours CHECK (end_time > start_time),
    CONSTRAINT chk_slot CHECK (slot_minutes BETWEEN 1 AND 240)
);
CREATE INDEX idx_doctor_hours_doctor ON doctor_weekly_hour (doctor_id);

CREATE TABLE doctor_day_off (
    doctor_id  UUID         NOT NULL REFERENCES doctor(id) ON DELETE CASCADE,
    date       DATE         NOT NULL,
    reason     VARCHAR(200)
);
CREATE INDEX idx_doctor_day_off_doctor ON doctor_day_off (doctor_id);
CREATE INDEX idx_doctor_day_off_date   ON doctor_day_off (date);

CREATE TABLE appointment (
    id                  UUID PRIMARY KEY,
    doctor_id           UUID         NOT NULL REFERENCES doctor(id),
    doctor_name         VARCHAR(200) NOT NULL,
    patient_id          UUID         NOT NULL REFERENCES patient(id),
    patient_mrn         VARCHAR(30)  NOT NULL,
    patient_name        VARCHAR(300) NOT NULL,
    visit_id            UUID         NOT NULL REFERENCES visit(id),
    scheduled_for       TIMESTAMP    NOT NULL,
    scheduled_date      DATE         NOT NULL,
    duration_minutes    INTEGER      NOT NULL,
    type                VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    notes               VARCHAR(500),
    cancellation_reason VARCHAR(500),
    checked_in_at       TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ,
    updated_by          VARCHAR(100),
    CONSTRAINT chk_appt_type   CHECK (type   IN ('BOOKED','WALKIN')),
    CONSTRAINT chk_appt_status CHECK (status IN ('BOOKED','CHECKED_IN','COMPLETED','CANCELLED','NO_SHOW'))
);
CREATE INDEX idx_appt_doctor_date ON appointment (doctor_id, scheduled_for);
CREATE INDEX idx_appt_patient     ON appointment (patient_id);
CREATE INDEX idx_appt_visit       ON appointment (visit_id);
CREATE INDEX idx_appt_date        ON appointment (scheduled_date);
