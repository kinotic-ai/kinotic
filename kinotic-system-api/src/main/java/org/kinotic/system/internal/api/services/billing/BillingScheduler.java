package org.kinotic.system.internal.api.services.billing;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.resources.SpringResource;
import org.kinotic.system.internal.api.services.PeriodicSystemApiSingleton;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Runs billing's periodic work as one HA cluster singleton on the Ignite service grid. It checks every minute: once in
 * each hour it polls the hour's usage with {@link UsageMetering#poll(Instant)} and deactivates the organizations whose
 * grace period has run out and reactivates those that have paid with
 * {@link DefaultBillingOperationsService#enforcePayment()}, and it settles each hour that has ended, oldest first.
 * Work that fails is done again at the next check, and an hour that fails to settle is settled again at each check
 * for up to {@link #CATCH_UP}, without holding up the hours after it. A host that takes over from another settles
 * the hours of the past {@link #CATCH_UP} again, which changes nothing for those already settled.
 */
@Slf4j
public class BillingScheduler extends PeriodicSystemApiSingleton {

    private static final String SINGLETON_NAME = "billing";

    private static final long CHECK_MS = 60_000;

    private static final Duration HOUR = Duration.ofHours(1);

    /** How far back a newly started host settles hours from, and how long an hour that fails to settle is retried. */
    static final Duration CATCH_UP = Duration.ofDays(3);

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = UsageMetering.class)
    private transient UsageMetering metering;
    @SpringResource(resourceClass = DefaultBillingOperationsService.class)
    private transient DefaultBillingOperationsService operations;

    // the hour polled, and the hour enforced, last on this host, since the epoch
    private transient long lastPolled;
    private transient long lastEnforced;
    // the start of the next hour to settle, the hours that failed to settle, and whether a settling of hours is under way
    private transient Instant nextToSettle;
    private transient SortedSet<Instant> failedToSettle;
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
            settling = true;
            settle(dueToSettle(now).iterator()).onComplete(done -> settling = false);
        }
    }

    // the hours that failed to settle and have not run out of retries, then each hour ended since the last check
    private List<Instant> dueToSettle(Instant now) {
        Instant oldest = now.truncatedTo(ChronoUnit.HOURS).minus(CATCH_UP);
        if (nextToSettle == null) {
            nextToSettle = oldest;
            failedToSettle = new TreeSet<>();
        }
        SortedSet<Instant> expired = failedToSettle.headSet(oldest);
        if (!expired.isEmpty()) {
            log.error("Gave up settling the hours {}, which kept failing for {} days", expired, CATCH_UP.toDays());
            expired.clear();
        }
        List<Instant> ret = new ArrayList<>(failedToSettle);
        while (!nextToSettle.plus(HOUR).plus(UsageMetering.SETTLE_DELAY).isAfter(now)) {
            ret.add(nextToSettle);
            nextToSettle = nextToSettle.plus(HOUR);
        }
        return ret;
    }

    // settles one hour at a time, so a backlog of hours puts no more load on Elasticsearch and Stripe than one does
    private Future<Void> settle(Iterator<Instant> hours) {
        Future<Void> ret = Future.succeededFuture();
        if (hours.hasNext()) {
            Instant hour = hours.next();
            ret = Future.succeededFuture()
                        .compose(v -> metering.settle(Date.from(hour)))
                        .onSuccess(v -> failedToSettle.remove(hour))
                        .recover(error -> {
                            log.warn("Could not settle the usage of the hour starting {}; the next check tries again", hour, error);
                            failedToSettle.add(hour);
                            return Future.succeededFuture();
                        })
                        .compose(v -> settle(hours));
        }
        return ret;
    }
}
