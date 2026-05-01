-- Platform tables: outbox + audit log scaffolding.

CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(200)      NOT NULL,
    payload         JSONB             NOT NULL,
    occurred_at     TIMESTAMPTZ       NOT NULL,
    published_at    TIMESTAMPTZ,
    status          VARCHAR(20)       NOT NULL,
    attempt_count   INTEGER,
    last_error      VARCHAR(1000)
);

CREATE INDEX idx_outbox_pending ON outbox_event (status, occurred_at) WHERE status = 'PENDING';
