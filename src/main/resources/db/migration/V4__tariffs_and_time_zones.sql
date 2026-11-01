-- Time-of-use tariffs are defined in the grid connection's local time.
ALTER TABLE grid_connection ADD COLUMN time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC';
CREATE INDEX idx_tariff_grid ON tariff_period(tenant_id, grid_connection_id);
CREATE INDEX idx_session_tenant_start ON charging_session(tenant_id, starts_at);
