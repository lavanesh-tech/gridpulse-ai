-- Charging sessions with database-enforced no-overlap rules.
-- btree_gist lets a GiST exclusion constraint combine "=" on a UUID with "&&" (overlaps) on a time range.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE charging_session (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL REFERENCES tenant(id),
    vehicle_id           UUID NOT NULL REFERENCES vehicle(id),
    charger_id           UUID NOT NULL REFERENCES charger(id),
    starts_at            TIMESTAMPTZ NOT NULL,
    ends_at              TIMESTAMPTZ NOT NULL,
    power_kw             NUMERIC(10,3) NOT NULL CHECK (power_kw > 0),
    status               VARCHAR(20) NOT NULL
                         CHECK (status IN ('RESERVED','ACTIVE','COMPLETED','INTERRUPTED','CANCELLED')),
    interruption_reason  VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    CHECK (ends_at > starts_at),
    -- A charger cannot serve two live sessions at the same time ('[)' = half-open, so back-to-back is fine).
    CONSTRAINT no_charger_overlap EXCLUDE USING gist (
        charger_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status IN ('RESERVED', 'ACTIVE')),
    -- A vehicle cannot be on two chargers at the same time.
    CONSTRAINT no_vehicle_overlap EXCLUDE USING gist (
        vehicle_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status IN ('RESERVED', 'ACTIVE'))
);
CREATE INDEX idx_session_tenant_charger ON charging_session(tenant_id, charger_id, starts_at);
