package org.kinotic.system.internal.api.services.billing;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.resources.SpringResource;
import org.kinotic.system.internal.api.services.PeriodicSystemApiSingleton;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Runs billing's periodic work as one HA cluster singleton on the Ignite service grid. It checks every minute: once in
 * each hour it polls the hour's usage with {@link UsageMetering#poll(Instant)} and deactivates the organizations whose
 * grace period has run out and reactivates those that have paid with
 * {@link DefaultBillingOperationsService#enforcePayment()}, and it settles each hour that has ended, oldest first.
 * Work that fails is done again at the next check. A host that takes over from another settles the hours of the past
 * {@link #CATCH_UP} again, which changes nothing for those already settled.
 */
@Slf4j
public class BillingScheduler extends PeriodicSystemApiSingleton {

    private static final String SINGLETON_NAME = "billing";

    private static final long CHECK_MS = 60_000;

    private static final Duration HOUR = Duration.ofHours(1);

    /** How far back a newly started host settles hours from. */
    static final Duration CATCH_UP = Duration.ofDays(3);

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = UsageMetering.class)
    private transient UsageMetering metering;
    @SpringResource(resourceClass = DefaultBillingOperationsService.class)
    private transient DefaultBillingOperationsService operations;

    // the hour polled, and the hour enforced, last on this host, since the epoch
    private transient long lastPolled;
    private transient long lastEnforced;
    // the start of the next hour to settle, and whether a settling of hours is under way
    private transient Instant nextToSettle;
    private transient boolean settling;

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
        long hour = now.toEpochMilli() / HOUR.toMillis();
        if (hour != lastPolled) {
            // composed from a succeeded future so a synchronous throw fails like any other
            Future.succeededFuture()
                  .compose(v -> metering.poll(now))
                  .onSuccess(v -> lastPolled = hour)
                  .onFailure(error -> log.warn("Could not poll the usage of the hour; the next check tries again", error));
        }
        if (hour != lastEnforced) {
            Future.succeededFuture()
                  .compose(v -> operations.enforcePayment())
                  .onSuccess(v -> lastEnforced = hour)
                  .onFailure(error -> log.warn("Could not enforce payment on every organization; the next check tries again", error));
        }
        if (!settling) {
            if (nextToSettle == null) {
                nextToSettle = now.truncatedTo(ChronoUnit.HOURS).minus(CATCH_UP);
            }
            settling = true;
            settleFrom(now).onComplete(done -> settling = false);
        }
    }

    // settles one hour at a time, so an hour is settled only once every hour before it is
    private Future<Void> settleFrom(Instant now) {
        Future<Void> ret;
        Instant hour = nextToSettle;
        if (!hour.plus(HOUR).plus(UsageMetering.SETTLE_DELAY).isAfter(now)) {
            ret = Future.succeededFuture()
                        .compose(v -> metering.settle(Date.from(hour)))
                        .onFailure(error -> log.warn("Could not settle the usage of the hour starting {}; the next check tries again", hour, error))
                        .compose(v -> {
                            nextToSettle = hour.plus(HOUR);
                            return settleFrom(now);
                        });
        } else {
            ret = Future.succeededFuture();
        }
        return ret;
    }
}
