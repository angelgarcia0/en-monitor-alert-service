package com.energymonitor.alert.application.port.out;

/**
 * Output port for generating {@code VARCHAR(10)} business identifiers.
 */
public interface IdentifierGeneratorPort {

    /**
     * @return a new identifier, at most 10 characters
     */
    String generate();
}
