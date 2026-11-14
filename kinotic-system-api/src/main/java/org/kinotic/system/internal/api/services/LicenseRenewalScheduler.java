package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.resources.SpringResource;
import org.kinotic.domain.api.services.EntitlementService;

/**
 * Renews the cluster's license key with {@link EntitlementService#renew()} as one HA cluster singleton on the Ignite
 * service grid: checks once when it starts and daily after, and the renewal asks the license server only once the key
 * nears its expiry.
 */
@Slf4j
public class LicenseRenewalScheduler extends PeriodicSystemApiSingleton {

    private static final String SINGLETON_NAME = "license-renewal";

    private static final long RENEW_MS = 86_400_000;

    // Injected by Ignite on the node elected to host the singleton
    @SpringResource(resourceClass = EntitlementService.class)
    private transient EntitlementService entitlementService;

    /**
     * Deploys the singleton; Ignite elects its host among the nodes hosting system-api.
     *
     * @param ignite the local node
     */
    static void deploy(Ignite ignite) {
        systemApiServices(ignite).deployClusterSingleton(SINGLETON_NAME, new LicenseRenewalScheduler());
    }

    @Override
    protected long periodMs() {
        return RENEW_MS;
    }

    @Override
    protected void start() {
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
