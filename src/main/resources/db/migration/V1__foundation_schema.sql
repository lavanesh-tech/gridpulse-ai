-- GridPulse AI: foundation schema (Milestone 1)
-- Every business table carries tenant_id for multi-tenant isolation.
-- Power is stored in kW and energy in kWh, NUMERIC(10,3).

CREATE EXTENSION IF NOT EXISTS vector;   -- used from Milestone 5 (RAG)

CREATE TABLE tenant (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE fleet (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenant(id),
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, name)
);

CREATE TABLE vehicle (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL REFERENCES tenant(id),
    fleet_id              UUID NOT NULL REFERENCES fleet(id),
    vin                   VARCHAR(17) NOT NULL,
    model                 VARCHAR(100) NOT NULL,
    battery_capacity_kwh  NUMERIC(10,3) NOT NULL CHECK (battery_capacity_kwh > 0),
    max_charge_rate_kw    NUMERIC(10,3) NOT NULL CHECK (max_charge_rate_kw > 0),
    connector_type        VARCHAR(20) NOT NULL,
    state_of_charge_pct   SMALLINT NOT NULL CHECK (state_of_charge_pct BETWEEN 0 AND 100),
    version               BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, vin)
);
CREATE INDEX idx_vehicle_fleet ON vehicle(tenant_id, fleet_id);

CREATE TABLE grid_connection (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES tenant(id),
    name               VARCHAR(200) NOT NULL,
    capacity_limit_kw  NUMERIC(10,3) NOT NULL CHECK (capacity_limit_kw > 0),
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE charging_station (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenant(id),
    grid_connection_id  UUID NOT NULL REFERENCES grid_connection(id),
    name                VARCHAR(200) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ONLINE'
                        CHECK (status IN ('ONLINE','OFFLINE','MAINTENANCE')),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE charger (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    station_id      UUID NOT NULL REFERENCES charging_station(id),
    label           VARCHAR(50) NOT NULL,
    connector_type  VARCHAR(20) NOT NULL,
    max_power_kw    NUMERIC(10,3) NOT NULL CHECK (max_power_kw > 0),
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                    CHECK (status IN ('AVAILABLE','OUT_OF_SERVICE')),
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (station_id, label)
);

CREATE TABLE tariff_period (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL REFERENCES tenant(id),
    grid_connection_id UUID NOT NULL REFERENCES grid_connection(id),
    starts_at     TIME NOT NULL,
    ends_at       TIME NOT NULL,
    price_per_kwh NUMERIC(10,5) NOT NULL CHECK (price_per_kwh >= 0),
    currency      CHAR(3) NOT NULL DEFAULT 'USD'
);

CREATE TABLE audit_event (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenant(id),
    actor        VARCHAR(200) NOT NULL,
    action       VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    VARCHAR(100),
    details      JSONB,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_tenant_time ON audit_event(tenant_id, occurred_at DESC);
