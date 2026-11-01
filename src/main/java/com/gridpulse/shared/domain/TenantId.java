package com.gridpulse.shared.domain;

import java.util.Objects;
import java.util.UUID;

/** Identifies the tenant (customer organisation) that owns a piece of data. */
public record TenantId(UUID value) {
    public TenantId {
        Objects.requireNonNull(value, "tenant id must not be null");
    }

    public static TenantId of(String raw) {
        return new TenantId(UUID.fromString(raw));
    }
}
