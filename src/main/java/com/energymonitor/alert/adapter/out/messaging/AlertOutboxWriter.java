package com.energymonitor.alert.adapter.out.messaging;

import com.energymonitor.alert.adapter.out.persistence.entity.AlertOutboxEntity;
import com.energymonitor.alert.adapter.out.persistence.repository.AlertOutboxRepository;
import com.energymonitor.alert.application.port.out.IdentifierGeneratorPort;
import com.energymonitor.alert.infrastructure.AlertOutboxProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes an alert message to the outbox.
 *
 * <p>Called from inside the use case's transaction, so the row is committed or rolled back
 * together with the change it announces. Publishing to the broker is left to
 * {@link AlertOutboxRelay}.
 */
@Component
public class AlertOutboxWriter {

    private final AlertOutboxRepository outbox;
    private final IdentifierGeneratorPort identifiers;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean discardWritten;

    public AlertOutboxWriter(AlertOutboxRepository outbox, IdentifierGeneratorPort identifiers,
                            ObjectMapper json, Clock clock, AlertOutboxProperties properties) {
        this.outbox = outbox;
        this.identifiers = identifiers;
        this.json = json;
        this.clock = clock;
        this.discardWritten = properties.discardWritten();
    }

    /**
     * @param type       the routing key, such as {@code alert.raised}
     * @param payload    the body, serialized as JSON
     * @param occurredAt when the change happened
     */
    public void write(String type, Object payload, Instant occurredAt) {
        AlertOutboxEntity message = new AlertOutboxEntity();
        message.setIdMessage(identifiers.generate());
        message.setMessageType(type);
        message.setPayload(json.writeValueAsString(payload));
        message.setOccurredAt(occurredAt);
        if (discardWritten) {
            message.setDiscardedAt(clock.instant());
        }
        outbox.save(message);
    }
}
