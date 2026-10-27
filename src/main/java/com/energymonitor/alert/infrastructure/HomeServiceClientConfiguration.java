package com.energymonitor.alert.infrastructure;

import com.energymonitor.alert.adapter.out.home.HomeServiceClient;
import com.energymonitor.alert.adapter.out.home.RemoteHomes;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connects this service to the home service: the client of its internal API, with its address,
 * timeouts and the shared token, and {@link RemoteHomes}, the members and the limits of a home,
 * cached.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HomeServiceProperties.class)
public class HomeServiceClientConfiguration {

    @Bean
    public HomeServiceClient homeServiceClient(HomeServiceProperties properties,
                                               @Value("${internal-api.token:}") String token) {
        return HomeServiceClient.create(properties.url().toString(), token == null ? "" : token,
                properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RemoteHomes remoteHomes(HomeServiceClient client, HomeServiceProperties properties) {
        return new RemoteHomes(client, properties.ttl(), properties.staleTtl(), Ticker.systemTicker());
    }
}
