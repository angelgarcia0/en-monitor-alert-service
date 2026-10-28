package com.energymonitor.alert.adapter.out.home;

/**
 * The home service did not answer in time, or answered with an error, and no earlier answer
 * was kept to fall back on.
 */
public class HomeServiceUnavailableException extends RuntimeException {

    public HomeServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
