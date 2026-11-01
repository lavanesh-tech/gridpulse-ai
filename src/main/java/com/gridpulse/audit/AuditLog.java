package com.gridpulse.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plain Spring JDBC (no JPA): audit rows are insert-only and never loaded as entities,
 * so a single parameterised INSERT is simpler and faster than an ORM mapping.
 */
@Service
public class AuditLog {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditLog(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void record(UUID tenantId, String actor, String action, String entityType, String entityId,
                       Map<String, ?> details) {
        String json;
        try {
            json = mapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("audit details not serialisable", e);
        }
        jdbc.update("""
                INSERT INTO audit_event (tenant_id, actor, action, entity_type, entity_id, details)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """, tenantId, actor, action, entityType, entityId, json);
    }

    public List<AuditEntry> recent(UUID tenantId, int limit) {
        return jdbc.query("""
                SELECT id, actor, action, entity_type, entity_id, details::text AS details, occurred_at
                FROM audit_event WHERE tenant_id = ? ORDER BY occurred_at DESC, id DESC LIMIT ?
                """, (rs, i) -> new AuditEntry(rs.getLong("id"), rs.getString("actor"), rs.getString("action"),
                rs.getString("entity_type"), rs.getString("entity_id"), rs.getString("details"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant()), tenantId, limit);
    }
}
