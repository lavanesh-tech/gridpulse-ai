package com.gridpulse.identity.api;

import com.gridpulse.audit.AuditEntry;
import com.gridpulse.audit.AuditLog;
import com.gridpulse.identity.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {
    private final AuditLog audit;

    public AuditController(AuditLog audit) {
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditEntry> recent(@RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return audit.recent(CurrentUser.get().tenantId(), limit);
    }
}
