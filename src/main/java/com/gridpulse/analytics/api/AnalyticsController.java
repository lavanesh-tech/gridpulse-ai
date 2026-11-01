package com.gridpulse.analytics.api;

import com.gridpulse.analytics.AnalyticsService;
import com.gridpulse.identity.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public AnalyticsService.Summary summary(@RequestParam Instant from, @RequestParam Instant to) {
        return service.summary(CurrentUser.get().tenantId(), from, to);
    }
}
