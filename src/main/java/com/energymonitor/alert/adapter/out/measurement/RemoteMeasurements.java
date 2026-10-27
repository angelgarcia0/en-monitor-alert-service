package com.energymonitor.alert.adapter.out.measurement;

import com.energymonitor.alert.adapter.out.measurement.MeasurementServiceClient.RemoteHourlyEnergy;
import com.energymonitor.alert.adapter.out.measurement.MeasurementServiceClient.RemoteLevel;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * What the measurement service answers: the consumption level of a reading and the energy of
 * devices over a period.
 *
 * <p>The consumption levels are a catalogue of four rows that rarely changes, and every reading is
 * classified with them, so the list is reused for {@code levelsTtl} and every reading is classified
 * here against it, by the same {@code [minLimit, maxLimit)} rule the service uses. The last list is
 * also kept for {@code levelsStaleTtl}, served while the service does not answer.
 *
 * <p>The energy changes with every reading, so it is always asked.
 */
public class RemoteMeasurements {

    private static final String LEVELS = "levels";

    private final MeasurementServiceClient client;
    private final Cache<String, List<RemoteLevel>> levels;
    private final Cache<String, List<RemoteLevel>> lastKnownLevels;

    /**
     * @param client         the measurement service
     * @param levelsTtl      how long the level catalogue is reused before asking again
     * @param levelsStaleTtl how long the last catalogue is kept to serve while the service does
     *                       not answer
     * @param ticker         the clock of the caches
     */
    public RemoteMeasurements(MeasurementServiceClient client, Duration levelsTtl,
                              Duration levelsStaleTtl, Ticker ticker) {
        this.client = client;
        this.levels = Caffeine.newBuilder().expireAfterWrite(levelsTtl).ticker(ticker).build();
        this.lastKnownLevels = Caffeine.newBuilder().expireAfterWrite(levelsStaleTtl).ticker(ticker).build();
    }

    /**
     * @param activePower the value to classify, W
     * @return the level whose range covers it, empty when none does
     */
    public Optional<RemoteLevel> classify(double activePower) {
        return levels().stream()
                .filter(level -> activePower >= level.minLimit() && activePower < level.maxLimit())
                .findFirst();
    }

    /**
     * @return the energy per device and UTC hour in {@code [from, to)}, oldest first; empty when
     *         no device is given
     */
    public List<RemoteHourlyEnergy> hourlyEnergy(Collection<String> deviceIds, Instant from, Instant to) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return List.of();
        }
        return client.hourlyEnergy(deviceIds, from, to);
    }

    /**
     * The cached catalogue, or a new one, which is also kept as the last known; when the service
     * does not answer, the last known one if there is any.
     */
    private List<RemoteLevel> levels() {
        List<RemoteLevel> cached = levels.getIfPresent(LEVELS);
        if (cached != null) {
            return cached;
        }
        try {
            List<RemoteLevel> loaded = client.levels();
            levels.put(LEVELS, loaded);
            lastKnownLevels.put(LEVELS, loaded);
            return loaded;
        } catch (MeasurementServiceUnavailableException unavailable) {
            List<RemoteLevel> stale = lastKnownLevels.getIfPresent(LEVELS);
            if (stale != null) {
                return stale;
            }
            throw unavailable;
        }
    }
}
