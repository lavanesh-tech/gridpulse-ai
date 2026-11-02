package com.gridpulse.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification")
public record NotificationProperties(String topic, String jwtSecret, String issuer) {
}
