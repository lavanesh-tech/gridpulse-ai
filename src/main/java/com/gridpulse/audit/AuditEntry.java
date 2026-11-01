package com.gridpulse.audit;

import java.time.Instant;

public record AuditEntry(long id, String actor, String action, String entityType, String entityId,
                         String details, Instant occurredAt) {
}
