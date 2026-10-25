package org.kinotic.system.internal.api.services;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteServices;
import org.apache.ignite.resources.SpringResource;
import org.apache.ignite.services.Service;
import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.domain.api.utils.DomainUtil;

/**
 * An HA cluster singleton on the Ignite service grid, hosted by one of the nodes hosting system-api,
 * that starts its work once when it starts and once every period after. Ignite serializes the
 * singleton to its host, so every field of a subclass is transient and its beans arrive through
 * {@link SpringResource}, injected on the host.
 */
@Slf4j
public abstract class PeriodicSystemApiSingleton implements Service {

    @SpringResource(resourceClass = Vertx.class)
    protected transient Vertx vertx;

    private transient long timerId;

    /**
     * @param ignite the local node
     * @return the service grid of the nodes hosting system-api, the only ones whose context holds the
     *         beans a singleton is injected with
     */
    protected static IgniteServices systemApiServices(Ignite ignite) {
        return ignite.services(ignite.cluster().forAttribute(ZonePartitioningService.hostsAttribute(DomainUtil.SYSTEM_API_ZONE), true));
    }

    /**
     * @return how long after one start of the work the next is, in milliseconds
     */
    protected abstract long periodMs();

    /**
     * Starts one run of the work; called once the singleton is initialized and once a period after.
     */
    protected abstract void start();

    @Override
    public void init() {
        log.info("Starting {}", getClass().getSimpleName());
        start();
        timerId = vertx.setPeriodic(periodMs(), id -> start());
    }

    @Override
    public void execute() {
        // passive service: the work is driven by the timer started in init
    }

    @Override
    public void cancel() {
        log.info("Stopping {}", getClass().getSimpleName());
        vertx.cancelTimer(timerId);
    }

}
