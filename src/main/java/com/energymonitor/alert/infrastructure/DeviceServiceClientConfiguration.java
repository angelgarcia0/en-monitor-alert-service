package com.energymonitor.alert.infrastructure;

import com.energymonitor.alert.adapter.out.device.DeviceServiceClient;
import com.energymonitor.alert.adapter.out.device.RemoteDevices;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connects this service to the device service: the client of its internal API, with its address,
 * timeouts and the shared token, and {@link RemoteDevices}, the devices and their homes, cached.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeviceServiceProperties.class)
public class DeviceServiceClientConfiguration {

    @Bean
    public DeviceServiceClient deviceServiceClient(DeviceServiceProperties properties,
                                                   @Value("${internal-api.token:}") String token) {
        return DeviceServiceClient.create(properties.url().toString(), token == null ? "" : token,
                properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RemoteDevices remoteDevices(DeviceServiceClient client, DeviceServiceProperties properties) {
        return new RemoteDevices(client, properties.ttl(), properties.staleTtl(), Ticker.systemTicker());
    }
}
