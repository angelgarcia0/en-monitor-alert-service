package com.energymonitor.alert.application.port.out;

import com.energymonitor.alert.domain.model.AlertRaised;

/**
 * Output port for publishing alert events.
 *
 * <p>Keeps the application layer unaware of the event mechanism: the adapter decides
 * whether the event goes to the Spring context, a message broker or a log.
 */
public interface AlertEventPort {

    /**
     * Publishes that an alert was raised.
     *
     * @param event the event payload
     */
    void publish(AlertRaised event);
}
