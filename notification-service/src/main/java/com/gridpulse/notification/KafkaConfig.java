package com.gridpulse.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConfig {

    /**
     * A message that still fails after 2 retries one second apart goes to "<topic>-dlt" (Spring Kafka 3.3+ default name) instead of blocking the
     * partition forever. Malformed messages are not retried at all: retrying cannot fix them.
     */
    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template),
                new FixedBackOff(1000L, 2));
        handler.addNotRetryableExceptions(com.fasterxml.jackson.core.JsonProcessingException.class,
                IllegalArgumentException.class);
        return handler;
    }
}
