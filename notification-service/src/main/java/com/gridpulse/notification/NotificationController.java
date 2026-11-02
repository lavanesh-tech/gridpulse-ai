package com.gridpulse.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** The caller's tenant comes from the token, never from the request, exactly as in GridPulse. */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {
    private final JdbcTemplate jdbc;

    NotificationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Notification(long id, UUID sessionId, String severity, String message, Instant createdAt) {
    }

    @GetMapping
    List<Notification> recent(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "50") int limit) {
        UUID tenant = UUID.fromString(jwt.getClaimAsString("tenant_id"));
        return jdbc.query("""
                SELECT id, session_id, severity, message, created_at FROM notification
                WHERE tenant_id = ? ORDER BY created_at DESC, id DESC LIMIT ?
                """, (rs, i) -> new Notification(rs.getLong("id"), rs.getObject("session_id", UUID.class),
                rs.getString("severity"), rs.getString("message"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()), tenant, Math.min(Math.max(limit, 1), 200));
    }
}
