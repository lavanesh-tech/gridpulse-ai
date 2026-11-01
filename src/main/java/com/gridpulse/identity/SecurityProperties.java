package com.gridpulse.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("gridpulse.security")
public record SecurityProperties(String jwtSecret, String issuer, Duration tokenTtl,
                                 int requestsPerMinute, int loginRequestsPerMinute) {
}
