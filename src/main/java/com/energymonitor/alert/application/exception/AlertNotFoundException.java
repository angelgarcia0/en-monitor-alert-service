package com.energymonitor.alert.application.exception;

/**
 * An alert does not exist.
 */
public class AlertNotFoundException extends AlertApplicationException {

    public AlertNotFoundException(String message) {
        super(message);
    }
}
