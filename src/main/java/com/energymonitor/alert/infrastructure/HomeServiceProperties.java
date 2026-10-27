package com.energymonitor.alert.infrastructure;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the home service answers, how long to wait for it and how long its answers are reused.
 *
 * @param url            base URL of the service, such as {@code http://home:8086}
 * @param connectTimeout how long to wait for the connection
 * @param readTimeout    how long to wait for the answer
 * @param ttl            how long what a user is in a home and the limits of a home are reused; a
 *                       member removed loses access here within this time
 * @param staleTtl       how long the last limits are kept to serve while the service does not
 *                       answer
 */
@ConfigurationProperties("home.service")
public record HomeServiceProperties(
        @DefaultValue("http://localhost:8086") URI url,
        @DefaultValue("PT1S") Duration connectTimeout,
        @DefaultValue("PT2S") Duration readTimeout,
        @DefaultValue("PT30S") Duration ttl,
        @DefaultValue("PT1H") Duration staleTtl) {
}
