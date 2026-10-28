package com.energymonitor.alert.adapter.out.measurement;

/**
 * The measurement service did not answer in time, or answered with an error, and no earlier answer
 * was kept to fall back on.
 */
public class MeasurementServiceUnavailableException extends RuntimeException {

    public MeasurementServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
