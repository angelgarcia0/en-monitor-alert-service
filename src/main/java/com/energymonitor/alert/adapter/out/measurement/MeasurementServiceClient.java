package com.energymonitor.alert.adapter.out.measurement;

import com.energymonitor.alert.domain.model.RiskConsumption;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

/**
 * Calls the internal API of the measurement service: the consumption levels and the energy of
 * devices hour by hour. Every call carries the shared token in {@code X-Internal-Token}.
 *
 * <p>No answer within the timeouts, or an error, is a {@link MeasurementServiceUnavailableException};
 * nothing is retried here, so a caller waiting on it fails fast.
 */
public class MeasurementServiceClient {

    static final String TOKEN_HEADER = "X-Internal-Token";

    private final RestClient measurements;

    /**
     * @param measurements client with the service's base URL, the timeouts and the token
     */
    public MeasurementServiceClient(RestClient measurements) {
        this.measurements = measurements;
    }

    /**
     * @param baseUrl        where the measurement service answers, such as {@code http://measurement:8085}
     * @param token          the shared internal token
     * @param connectTimeout how long to wait for the connection
     * @param readTimeout    how long to wait for the answer
     * @return a client for that service
     */
    public static MeasurementServiceClient create(String baseUrl, String token,
                                                  Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(connectTimeout);
        requests.setReadTimeout(readTimeout);
        return new MeasurementServiceClient(RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requests)
                .defaultHeader(TOKEN_HEADER, token)
                .build());
    }

    /**
     * @return the consumption level catalogue
     */
    public List<RemoteLevel> levels() {
        try {
            RemoteLevel[] body = measurements.get()
                    .uri("/internal/consumption-levels")
                    .retrieve()
                    .body(RemoteLevel[].class);
            return body == null ? List.of() : List.of(body);
        } catch (RestClientException failure) {
            throw unavailable("consumption levels", failure);
        }
    }

    /**
     * @return the energy per device and UTC hour, oldest first
     */
    public List<RemoteHourlyEnergy> hourlyEnergy(Collection<String> deviceIds, Instant from, Instant to) {
        try {
            RemoteHourlyEnergy[] body = measurements.get()
                    .uri(uri -> withDevices(uri.path("/internal/measurements/hourly-energy"), deviceIds)
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .build())
                    .retrieve()
                    .body(RemoteHourlyEnergy[].class);
            return body == null ? List.of() : List.of(body);
        } catch (RestClientException failure) {
            throw unavailable("hourly energy", failure);
        }
    }

    private static UriBuilder withDevices(UriBuilder uri, Collection<String> deviceIds) {
        for (String deviceId : deviceIds) {
            uri.queryParam("deviceId", deviceId);
        }
        return uri;
    }

    private static MeasurementServiceUnavailableException unavailable(String what, RestClientException failure) {
        String status = failure instanceof RestClientResponseException response
                ? " (" + response.getStatusCode().value() + ")" : "";
        return new MeasurementServiceUnavailableException(
                "measurement service did not answer the " + what + " request" + status, failure);
    }

    /** A consumption level as the measurement service describes it. */
    public record RemoteLevel(String idConsumptionLevel, RiskConsumption name, String description,
                              double minLimit, double maxLimit) {
    }

    /** The energy of one device in one UTC hour. */
    public record RemoteHourlyEnergy(String deviceId, Instant hourStart, double averagePower, double energy) {
    }
}
