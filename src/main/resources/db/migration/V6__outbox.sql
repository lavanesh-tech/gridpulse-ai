-- Transactional outbox: domain events are written in the SAME transaction as the change that caused them,
-- then relayed to Kafka by OutboxRelay. A crash can never lose an event or publish one for a rolled-back change.
CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL REFERENCES tenant(id),
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);
-- the relay only ever scans unpublished rows, oldest first
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
