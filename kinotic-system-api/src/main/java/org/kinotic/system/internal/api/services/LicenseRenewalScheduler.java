package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.resources.SpringResource;
import org.apache.ignite.services.Service;
import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.domain.api.services.EntitlementService;
import org.kinotic.domain.api.utils.DomainUtil;

/**
 * Renews the cluster's license key with {@link EntitlementService#renew()} as one HA cluster singleton on the Ignite
 * service grid: checks once when it starts and daily after, and the renewal asks the license server only once the key
 * nears its expiry.
 */
@Slf4j
public class LicenseRenewalScheduler implements Service {

    private static final String SINGLETON_NAME = "license-renewal";

    private static final long RENEW_MS = 86_400_000;

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = EntitlementService.class)
    private transient EntitlementService entitlementService;
    @SpringResource(resourceClass = Vertx.class)
    private transient Vertx vertx;

    private transient long timerId;

    /**
     * Deploys the singleton; Ignite elects its host among the nodes hosting system-api.
     *
     * @param ignite the local node
     */
    static void deploy(Ignite ignite) {
        ignite.services(ignite.cluster().forAttribute(ZonePartitioningService.hostsAttribute(DomainUtil.SYSTEM_API_ZONE), true))
              .deployClusterSingleton(SINGLETON_NAME, new LicenseRenewalScheduler());
    }

    @Override
    public void init() {
        renew();
        timerId = vertx.setPeriodic(RENEW_MS, id -> renew());
    }

    @Override
    public void execute() {
        // passive service: all work is driven by the timer started in init
    }

    @Override
    public void cancel() {
        vertx.cancelTimer(timerId);
    }

    private void renew() {
        // composed from a succeeded future so an exception thrown by renew is logged rather than failing the singleton's init
        Future.succeededFuture()
              .compose(v -> entitlementService.renew())
              .onSuccess(license -> {
                  if (license != null) {
                      log.info("Checked the license key with Minds Ignited's license server; the key the cluster runs on expires {}",
                               license.getExpiresAt().toInstant());
                  }
              })
              .onFailure(error -> log.warn("The license key could not be renewed from Minds Ignited's license server;"
                                                   + " it is tried again in a day", error));
    }
}
