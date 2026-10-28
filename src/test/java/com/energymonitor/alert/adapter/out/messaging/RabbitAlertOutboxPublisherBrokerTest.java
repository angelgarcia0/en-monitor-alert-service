package com.energymonitor.alert.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * The publisher against a real RabbitMQ: confirmed and routed, or refused.
 *
 * <p>Needs a broker on {@code RABBITMQ_HOST:RABBITMQ_PORT} (localhost:5672 by default) with the
 * credentials of {@code RABBITMQ_USERNAME} and {@code RABBITMQ_PASSWORD}; without one it is
 * skipped rather than failed, so the suite still runs where no broker exists.
 */
@DisplayName("RabbitMQ alert outbox publisher")
class RabbitAlertOutboxPublisherBrokerTest {

    private static final String HOST = env("RABBITMQ_HOST", "localhost");
    private static final int PORT = Integer.parseInt(env("RABBITMQ_PORT", "5672"));

    private CachingConnectionFactory connections;
    private RabbitAdmin admin;
    private RabbitTemplate rabbit;
    private TopicExchange exchange;
    private RabbitAlertOutboxPublisher publisher;

    @BeforeEach
    void connect() {
        assumeTrue(reachable(), "no RabbitMQ on " + HOST + ":" + PORT);
        connections = new CachingConnectionFactory(HOST, PORT);
        connections.setUsername(env("RABBITMQ_USERNAME", "guest"));
        connections.setPassword(env("RABBITMQ_PASSWORD", "guest"));
        connections.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connections.setPublisherReturns(true);
        admin = new RabbitAdmin(connections);
        exchange = new TopicExchange("test.alert." + UUID.randomUUID(), false, true);
        admin.declareExchange(exchange);
        rabbit = new RabbitTemplate(connections);
        publisher = new RabbitAlertOutboxPublisher(rabbit, exchange.getName(), Duration.ofSeconds(5));
    }

    @AfterEach
    void disconnect() {
        if (admin != null) {
            admin.deleteExchange(exchange.getName());
            connections.destroy();
        }
    }

    @Test
    @DisplayName("delivers a routed message with its id and type, persistent")
    void deliversRoutedMessage() {
        Queue queue = new AnonymousQueue();
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("alert.raised"));

        publisher.publish("amo0000001", "alert.raised",
                "{\"idAlert\":\"alr0000001\"}", Instant.now());

        Message received = rabbit.receive(queue.getName(), 5_000);
        assertThat(received).isNotNull();
        assertThat(new String(received.getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"idAlert\":\"alr0000001\"}");
        assertThat(received.getMessageProperties().getMessageId()).isEqualTo("amo0000001");
        assertThat(received.getMessageProperties().getType()).isEqualTo("alert.raised");
        assertThat(received.getMessageProperties().getReceivedDeliveryMode())
                .isEqualTo(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
    }

    @Test
    @DisplayName("refuses a message no queue is bound for, so the relay keeps it pending")
    void refusesUnroutedMessage() {
        assertThatThrownBy(() -> publisher.publish("amo0000002", "alert.raised", "{}",
                Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alert.raised");
    }

    private static boolean reachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 500);
            return true;
        } catch (IOException unreachable) {
            return false;
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
