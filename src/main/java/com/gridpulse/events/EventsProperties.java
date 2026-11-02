package com.gridpulse.events;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** gridpulse.events.*: whether the Kafka relay runs, and the topic it publishes session events to. */
@ConfigurationProperties("gridpulse.events")
record EventsProperties(String topic) {
    EventsProperties {
        if (topic == null || topic.isBlank()) topic = "gridpulse.session-events";
    }
}
