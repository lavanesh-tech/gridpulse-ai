/**
 * Integration events: a transactional outbox and its relay to Kafka. Other modules record events through
 * {@link com.gridpulse.events.Outbox}; nothing outside this module talks to Kafka.
 */
@org.springframework.modulith.ApplicationModule
package com.gridpulse.events;
