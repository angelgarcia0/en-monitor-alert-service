package com.energymonitor.alert.adapter.out.home;

import java.time.Duration;
import java.util.Optional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the internal API of the home service: what a user is in a home and the consumption limits
 * of a home. Every call carries the shared token in {@code X-Internal-Token}.
 *
 * <p>No answer within the timeouts, or an error, is a {@link HomeServiceUnavailableException};
 * nothing is retried here, so a caller waiting on it fails fast.
 */
public class HomeServiceClient {

    static final String TOKEN_HEADER = "X-Internal-Token";

    private final RestClient homes;

    /**
     * @param homes client with the service's base URL, the timeouts and the token
     */
    public HomeServiceClient(RestClient homes) {
        this.homes = homes;
    }

    /**
     * @param baseUrl        where the home service answers, such as {@code http://home:8086}
     * @param token          the shared internal token
     * @param connectTimeout how long to wait for the connection
     * @param readTimeout    how long to wait for the answer
     * @return a client for that service
     */
    public static HomeServiceClient create(String baseUrl, String token,
                                           Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(connectTimeout);
        requests.setReadTimeout(readTimeout);
        return new HomeServiceClient(RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requests)
                .defaultHeader(TOKEN_HEADER, token)
                .build());
    }

    /**
     * {@code GET /internal/homes/{homeId}/access?userId=}, always 200 when the service answers.
     *
     * @param homeId the home
     * @param userId the user
     * @return whether the home exists and whether the user is a member and an owner of it
     */
    public RemoteAccess access(String homeId, String userId) {
        try {
            RemoteAccess body = homes.get()
                    .uri("/internal/homes/{homeId}/access?userId={userId}", homeId, userId)
                    .retrieve()
                    .body(RemoteAccess.class);
            return body == null ? new RemoteAccess(false, false, false) : body;
        } catch (RestClientException failure) {
            throw unavailable("home access", failure);
        }
    }

    /**
     * {@code GET /internal/homes/{homeId}/thresholds}.
     *
     * @param homeId the home
     * @return its consumption limits; empty when the service answers 404
     */
    public Optional<RemoteLimits> limits(String homeId) {
        try {
            return Optional.ofNullable(homes.get()
                    .uri("/internal/homes/{homeId}/thresholds", homeId)
                    .retrieve()
                    .body(RemoteLimits.class));
        } catch (HttpClientErrorException.NotFound none) {
            return Optional.empty();
        } catch (RestClientException failure) {
            throw unavailable("home limits", failure);
        }
    }

    private static HomeServiceUnavailableException unavailable(String what, RestClientException failure) {
        String status = failure instanceof RestClientResponseException response
                ? " (" + response.getStatusCode().value() + ")" : "";
        return new HomeServiceUnavailableException(
                "home service did not answer the " + what + " request" + status, failure);
    }

    /** What a user is in a home. */
    public record RemoteAccess(boolean exists, boolean member, boolean owner) {
    }

    /** The consumption limits of a home, in kWh, and which of the two the owner set. */
    public record RemoteLimits(double dailyLimit, double monthlyLimit, LimitPeriod limitPeriod) {
    }

    /**
     * Which limit of a home is the one the owner set. Only one is active at a time; the other is
     * derived from it (30 days per month).
     */
    public enum LimitPeriod {
        DAILY,
        MONTHLY
    }
}
