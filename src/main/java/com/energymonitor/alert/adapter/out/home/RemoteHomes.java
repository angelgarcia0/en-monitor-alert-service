package com.energymonitor.alert.adapter.out.home;

import com.energymonitor.alert.adapter.out.home.HomeServiceClient.RemoteAccess;
import com.energymonitor.alert.adapter.out.home.HomeServiceClient.RemoteLimits;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Optional;

/**
 * What the home service answers about a home: whether a user is a member, which decides who may
 * read its alerts, and its consumption limits, which decide its LIMIT alerts.
 *
 * <p>Both are reused for {@code ttl}. The last limits are also kept for {@code staleTtl} and served
 * while the service does not answer, so an outage does not stop the limit checks. What a user is
 * in a home is never served stale: a member removed while the service is down would otherwise keep
 * reading the alerts of the home, so without a fresh answer the call throws
 * {@link HomeServiceUnavailableException}.
 */
public class RemoteHomes {

    private final HomeServiceClient client;
    private final Cache<String, RemoteAccess> access;
    private final Cache<String, Optional<RemoteLimits>> limits;
    private final Cache<String, Optional<RemoteLimits>> lastKnownLimits;

    /**
     * @param client   the home service
     * @param ttl      how long an answer is reused before asking again
     * @param staleTtl how long the last limits are kept to serve while the service does not answer
     * @param ticker   the clock of the caches
     */
    public RemoteHomes(HomeServiceClient client, Duration ttl, Duration staleTtl, Ticker ticker) {
        this.client = client;
        this.access = cache(ttl, ticker);
        this.limits = cache(ttl, ticker);
        this.lastKnownLimits = cache(staleTtl, ticker);
    }

    private static <V> Cache<String, V> cache(Duration ttl, Ticker ticker) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(10_000).ticker(ticker).build();
    }

    /**
     * @param userId the user, may be null
     * @param homeId the home, may be null
     * @return whether the user is an active member of the home, owners included
     */
    public boolean isMember(String userId, String homeId) {
        if (userId == null || homeId == null) {
            return false;
        }
        // Not served stale: see the class comment.
        return access.get(homeId + "|" + userId, key -> client.access(homeId, userId)).member();
    }

    /**
     * @param homeId the home, may be null
     * @return its consumption limits, when the home exists
     */
    public Optional<RemoteLimits> limits(String homeId) {
        if (homeId == null) {
            return Optional.empty();
        }
        Optional<RemoteLimits> cached = limits.getIfPresent(homeId);
        if (cached != null) {
            return cached;
        }
        try {
            Optional<RemoteLimits> loaded = client.limits(homeId);
            limits.put(homeId, loaded);
            lastKnownLimits.put(homeId, loaded);
            return loaded;
        } catch (HomeServiceUnavailableException unavailable) {
            Optional<RemoteLimits> stale = lastKnownLimits.getIfPresent(homeId);
            if (stale != null) {
                return stale;
            }
            throw unavailable;
        }
    }
}
