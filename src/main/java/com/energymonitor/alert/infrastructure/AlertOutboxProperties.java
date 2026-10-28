package com.energymonitor.alert.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the alert messages this service publishes ({@code messaging.alert.*}).
 *
 * @param exchange       the topic exchange every alert message goes to
 * @param relayEnabled   whether the outbox relay runs. Off in the test profile, so a test never
 *                       publishes to a broker another process is consuming, and to hold the
 *                       alerts back while the notification service is down
 * @param pollInterval   how often the relay looks for pending messages
 * @param batchSize      the most messages one run publishes
 * @param confirmTimeout how long to wait for the broker to confirm a message
 * @param discardWritten whether a message is stored already discarded; only the test profile
 *                       sets it, because the suite writes to the shared database and its
 *                       messages must never be published by a service started later on it
 */
@ConfigurationProperties("messaging.alert")
public record AlertOutboxProperties(
        @DefaultValue("energy-monitor.alert") String exchange,
        @DefaultValue("true") boolean relayEnabled,
        @DefaultValue("PT1S") Duration pollInterval,
        @DefaultValue("50") int batchSize,
        @DefaultValue("PT5S") Duration confirmTimeout,
        @DefaultValue("false") boolean discardWritten) {
}
