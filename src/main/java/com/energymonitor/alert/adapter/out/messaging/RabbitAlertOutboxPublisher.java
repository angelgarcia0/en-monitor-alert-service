package com.energymonitor.alert.adapter.out.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Publishes alert outbox messages to RabbitMQ with correlated publisher confirms.
 *
 * <p>A message counts as published only when the broker confirms it and it was routed to at
 * least one queue. The template is mandatory, so a message no queue is bound for comes back
 * instead of vanishing, and is retried until the notification service has declared its queue.
 * Messages are persistent, so a broker restart does not lose them.
 *
 * <p>The confirmation is correlated rather than simple on purpose: the broker sends the return
 * before the ack, but a simple confirm is waited for on the publishing thread while the return is
 * handled on another, so it may arrive after the wait ended and an unrouted message would pass as
 * published. With correlation data, Spring AMQP sets the returned message on it before it
 * completes the confirmation.
 */
public class RabbitAlertOutboxPublisher implements AlertOutboxPublisher {

    private final RabbitTemplate rabbit;
    private final String exchange;
    private final Duration confirmTimeout;

    /**
     * @param rabbit         a template of its own, so being mandatory affects nobody else
     * @param exchange       the topic exchange of the alert messages
     * @param confirmTimeout how long to wait for the broker's confirmation
     */
    public RabbitAlertOutboxPublisher(RabbitTemplate rabbit, String exchange, Duration confirmTimeout) {
        this.rabbit = rabbit;
        this.exchange = exchange;
        this.confirmTimeout = confirmTimeout;
        rabbit.setMandatory(true);
    }

    @Override
    public void publish(String idMessage, String type, String payload, Instant occurredAt) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(idMessage);
        properties.setType(type);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setTimestamp(Date.from(occurredAt));
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), properties);

        CorrelationData correlation = new CorrelationData(idMessage);
        rabbit.send(exchange, type, message, correlation);
        CorrelationData.Confirm confirm = awaitConfirm(correlation, type);
        if (!confirm.ack()) {
            throw new IllegalStateException("the broker refused " + type + ": " + confirm.reason());
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException("no queue is bound for " + type + " yet");
        }
    }

    private CorrelationData.Confirm awaitConfirm(CorrelationData correlation, String type) {
        try {
            return correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException late) {
            throw new IllegalStateException("the broker did not confirm " + type + " in time", late);
        } catch (ExecutionException failed) {
            throw new IllegalStateException("the confirmation of " + type + " failed", failed.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to confirm " + type, interrupted);
        }
    }
}
