package com.energymonitor.alert.adapter.out.messaging;

import com.energymonitor.alert.adapter.out.persistence.entity.AlertOutboxEntity;
import com.energymonitor.alert.adapter.out.persistence.repository.AlertOutboxRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;

/**
 * Moves the alert outbox to the broker.
 *
 * <p>Each run takes the pending messages oldest first and publishes them one by one, waiting for
 * the broker's confirmation before marking a message as published. The first failure stops the
 * run, so a later message never overtakes an earlier one; the failed message is retried on the
 * next run, which is what makes delivery at-least-once. The notification service drops duplicates
 * by message id.
 *
 * <p>Runs on a single instance, the only relay of {@code alert_outbox}.
 * {@code messaging.alert.relay-enabled} turns it off, in the test profile and to hold the alerts
 * back while the notification service is down: they wait in the table meanwhile.
 */
public class AlertOutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(AlertOutboxRelay.class);

    private static final int MAX_ERROR_LENGTH = 255;

    private final AlertOutboxRepository outbox;
    private final AlertOutboxPublisher publisher;
    private final Clock clock;
    private final int batchSize;

    public AlertOutboxRelay(AlertOutboxRepository outbox, AlertOutboxPublisher publisher, Clock clock,
                           int batchSize) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /**
     * Publishes what is pending.
     *
     * @return how many messages were published in this run
     */
    public int relay() {
        int published = 0;
        for (AlertOutboxEntity message : outbox.findPending(PageRequest.of(0, batchSize))) {
            try {
                publisher.publish(message.getIdMessage(), message.getMessageType(),
                        message.getPayload(), message.getOccurredAt());
            } catch (RuntimeException failure) {
                message.setAttempts(message.getAttempts() + 1);
                message.setLastError(truncate(failure.getMessage()));
                outbox.save(message);
                LOG.warn("Alert outbox message {} ({}) not published, attempt {}: {}",
                        message.getIdMessage(), message.getMessageType(), message.getAttempts(),
                        failure.getMessage());
                break;
            }
            message.setPublishedAt(clock.instant());
            outbox.save(message);
            published++;
        }
        return published;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
