-- Idempotent consumer: Kafka delivers at least once, so every event id is recorded exactly once.
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notification (
    id          BIGSERIAL PRIMARY KEY,
    event_id    UUID        NOT NULL UNIQUE REFERENCES processed_event(event_id),
    tenant_id   UUID        NOT NULL,
    session_id  UUID        NOT NULL,
    severity    VARCHAR(10) NOT NULL CHECK (severity IN ('INFO', 'WARNING')),
    message     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_tenant_time ON notification (tenant_id, created_at DESC);
