package com.energymonitor.alert.infrastructure;

import com.energymonitor.alert.adapter.out.messaging.AlertOutboxPublisher;
import com.energymonitor.alert.adapter.out.messaging.AlertOutboxRelay;
import com.energymonitor.alert.adapter.out.messaging.RabbitAlertOutboxPublisher;
import com.energymonitor.alert.adapter.out.persistence.repository.AlertOutboxRepository;
import java.time.Clock;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * The broker side of the alert outbox: the exchange the alert messages go to, and the relay.
 *
 * <p>Only the exchange is declared here. The queues belong to the consumers, which declare and
 * bind their own; until one exists for a message type, {@link RabbitAlertOutboxPublisher} gets
 * the message back and the relay keeps it pending.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AlertOutboxProperties.class)
public class AlertOutboxConfiguration {

    @Bean
    public TopicExchange alertExchange(AlertOutboxProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    /**
     * A template of its own: being mandatory, with a returns callback, is the outbox's business
     * and must not change how anything else in this service sends.
     */
    @Bean
    public AlertOutboxPublisher alertOutboxPublisher(ConnectionFactory connections,
                                                   AlertOutboxProperties properties) {
        return new RabbitAlertOutboxPublisher(new RabbitTemplate(connections), properties.exchange(),
                properties.confirmTimeout());
    }

    @Bean
    public AlertOutboxRelay alertOutboxRelay(AlertOutboxRepository outbox, AlertOutboxPublisher publisher,
                                           AlertOutboxProperties properties, Clock clock) {
        return new AlertOutboxRelay(outbox, publisher, clock, properties.batchSize());
    }

    /** Runs the relay on a fixed delay, so a slow run never overlaps the next one. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "messaging.alert.relay-enabled", havingValue = "true", matchIfMissing = true)
    static class RelaySchedule implements SchedulingConfigurer {

        private final AlertOutboxRelay relay;
        private final AlertOutboxProperties properties;

        RelaySchedule(AlertOutboxRelay relay, AlertOutboxProperties properties) {
            this.relay = relay;
            this.properties = properties;
        }

        @Override
        public void configureTasks(ScheduledTaskRegistrar registrar) {
            registrar.addFixedDelayTask(relay::relay, properties.pollInterval());
        }
    }
}
