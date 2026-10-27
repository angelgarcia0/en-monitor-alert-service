package com.energymonitor.alert.application.exception;

/**
 * Base for the application-layer exceptions. Nothing technology-specific lives here.
 */
public class AlertApplicationException extends RuntimeException {

    public AlertApplicationException(String message) {
        super(message);
    }

    public AlertApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
