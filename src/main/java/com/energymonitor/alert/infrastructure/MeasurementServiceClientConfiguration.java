package com.energymonitor.alert.infrastructure;

import com.energymonitor.alert.adapter.out.measurement.MeasurementServiceClient;
import com.energymonitor.alert.adapter.out.measurement.RemoteMeasurements;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connects this service to the measurement service: the client of its internal API, with its
 * address, timeouts and the shared token, and {@link RemoteMeasurements}, the consumption levels,
 * cached, and the energy of devices.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MeasurementServiceProperties.class)
public class MeasurementServiceClientConfiguration {

    @Bean
    public MeasurementServiceClient measurementServiceClient(MeasurementServiceProperties properties,
                                                             @Value("${internal-api.token:}") String token) {
        return MeasurementServiceClient.create(properties.url().toString(), token == null ? "" : token,
                properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RemoteMeasurements remoteMeasurements(MeasurementServiceClient client,
                                                 MeasurementServiceProperties properties) {
        return new RemoteMeasurements(client, properties.levelsTtl(), properties.levelsStaleTtl(),
                Ticker.systemTicker());
    }
}
