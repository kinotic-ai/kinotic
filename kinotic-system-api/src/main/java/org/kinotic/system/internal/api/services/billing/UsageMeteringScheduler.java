package org.kinotic.system.internal.api.services.billing;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.resources.SpringResource;
import org.kinotic.system.internal.api.services.PeriodicSystemApiSingleton;

import java.time.Instant;

/**
 * Runs {@link UsageMetering} as one HA cluster singleton on the Ignite service grid: checks every minute, and in each
 * five-minute slot measures the slot, then settles the hours that are complete. A measurement that fails is made again
 * at the next check in the same slot; the host that takes over from another measures the slot it starts in again,
 * which counts once.
 */
@Slf4j
public class UsageMeteringScheduler extends PeriodicSystemApiSingleton {

    private static final String SINGLETON_NAME = "usage-metering";

    private static final long CHECK_MS = 60_000;

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = UsageMetering.class)
    private transient UsageMetering metering;

    // the slot measured last on this host, in slots since the epoch
    private transient long lastSlot;

    /**
     * Deploys the singleton; Ignite elects its host among the nodes hosting system-api.
     *
     * @param ignite the local node
     */
    static void deploy(Ignite ignite) {
        systemApiServices(ignite).deployClusterSingleton(SINGLETON_NAME, new UsageMeteringScheduler());
    }

    @Override
    protected long periodMs() {
        return CHECK_MS;
    }

    @Override
    protected void start() {
        Instant now = Instant.now();
        long slot = now.toEpochMilli() / UsageMetering.SLOT.toMillis();
        if (slot != lastSlot) {
            metering.measure(now)
                    .onSuccess(v -> lastSlot = slot)
                    .onFailure(error -> log.warn("Could not measure the usage of the slot starting {}; the next check tries again",
                                                 Instant.ofEpochMilli(slot * UsageMetering.SLOT.toMillis()), error))
                    .transform(measured -> metering.settle(now))
                    .onFailure(error -> log.warn("Could not settle every completed hour of usage; the next slot tries again", error));
        }
    }
}
