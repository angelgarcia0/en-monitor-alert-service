package com.energymonitor.alert.infrastructure;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the measurement service answers, how long to wait for it and how long its level catalogue
 * is reused.
 *
 * @param url            base URL of the service, such as {@code http://measurement:8085}
 * @param connectTimeout how long to wait for the connection
 * @param readTimeout    how long to wait for the answer; the energy of a home over a month, for a
 *                       monthly limit, is the slowest one
 * @param levelsTtl      how long the consumption levels are reused
 * @param levelsStaleTtl how long the last levels are kept to serve while the service does not
 *                       answer
 */
@ConfigurationProperties("measurement.service")
public record MeasurementServiceProperties(
        @DefaultValue("http://localhost:8085") URI url,
        @DefaultValue("PT1S") Duration connectTimeout,
        @DefaultValue("PT5S") Duration readTimeout,
        @DefaultValue("PT10M") Duration levelsTtl,
        @DefaultValue("PT24H") Duration levelsStaleTtl) {
}
