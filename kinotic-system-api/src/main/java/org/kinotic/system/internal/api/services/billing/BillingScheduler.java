package org.kinotic.system.internal.api.services.billing;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.resources.SpringResource;
import org.kinotic.system.internal.api.services.PeriodicSystemApiSingleton;

import java.time.Duration;
import java.time.Instant;

/**
 * Runs billing's periodic work as one HA cluster singleton on the Ignite service grid. It checks every minute: in each
 * five-minute slot it measures the slot with {@link UsageMetering} and settles the hours that are complete, and in
 * each hour it deactivates the organizations whose grace period has run out and reactivates those that have paid,
 * with {@link DefaultBillingOperationsService#enforcePayment()}. Work that fails is done again at the next check in
 * the same slot or hour; the host that takes over from another does the slot and hour it starts in again, which
 * counts once.
 */
@Slf4j
public class BillingScheduler extends PeriodicSystemApiSingleton {

    private static final String SINGLETON_NAME = "billing";

    private static final long CHECK_MS = 60_000;

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = UsageMetering.class)
    private transient UsageMetering metering;
    @SpringResource(resourceClass = DefaultBillingOperationsService.class)
    private transient DefaultBillingOperationsService operations;

    // the slot measured and the hour enforced last on this host, since the epoch
    private transient long lastSlot;
    private transient long lastHour;

    /**
     * Deploys the singleton; Ignite elects its host among the nodes hosting system-api.
     *
     * @param ignite the local node
     */
    static void deploy(Ignite ignite) {
        systemApiServices(ignite).deployClusterSingleton(SINGLETON_NAME, new BillingScheduler());
    }

    @Override
    protected long periodMs() {
        return CHECK_MS;
    }

    @Override
    protected void start() {
        Instant now = Instant.now();
        long slot = now.toEpochMilli() / UsageMetering.SLOT.toMillis();
        long hour = now.toEpochMilli() / Duration.ofHours(1).toMillis();
        if (slot != lastSlot) {
            metering.measure(now)
                    .onSuccess(v -> lastSlot = slot)
                    .onFailure(error -> log.warn("Could not measure the usage of the slot starting {}; the next check tries again",
                                                 Instant.ofEpochMilli(slot * UsageMetering.SLOT.toMillis()), error))
                    .transform(measured -> metering.settle(now))
                    .onFailure(error -> log.warn("Could not settle every completed hour of usage; the next slot tries again", error));
        }
        if (hour != lastHour) {
            // composed from a succeeded future so a synchronous throw fails like any other
            Future.succeededFuture()
                  .compose(v -> operations.enforcePayment())
                  .onSuccess(v -> lastHour = hour)
                  .onFailure(error -> log.warn("Could not enforce payment on every organization; the next check tries again", error));
        }
    }
}
