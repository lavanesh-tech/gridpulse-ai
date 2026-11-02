package com.gridpulse.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(EventsProperties.class)
class EventsConfig {

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "gridpulse.events.kafka.enabled", havingValue = "true")
    static class KafkaEnabled {
        /** Three partitions keyed by session id: events for one session stay in order, sessions spread out. */
        @Bean
        NewTopic sessionEventsTopic(EventsProperties props) {
            return TopicBuilder.name(props.topic()).partitions(3).replicas(1).build();
        }
    }
}
