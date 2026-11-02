package com.gridpulse.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gridpulse")
public record GridPulseProperties(String apiUrl, String username, String password) {
}
