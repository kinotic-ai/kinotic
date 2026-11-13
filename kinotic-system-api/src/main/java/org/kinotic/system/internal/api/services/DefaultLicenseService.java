package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.ignite.Ignite;
import org.kinotic.domain.api.model.license.License;
import org.kinotic.domain.api.services.EntitlementService;
import org.kinotic.system.api.services.LicenseService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultLicenseService implements LicenseService {

    private final EntitlementService entitlementService;
    private final Ignite ignite;

    // Every node requests the deployment; Ignite elects a single host for it
    @EventListener(ApplicationReadyEvent.class)
    public void deployRenewal() {
        LicenseRenewalScheduler.deploy(ignite);
    }

    @Override
    public Future<License> findCurrent() {
        return Future.succeededFuture(entitlementService.find());
    }

    @Override
    public Future<License> install(String licenseKey) {
        return entitlementService.install(licenseKey);
    }

    @Override
    public Future<String> findClusterId() {
        return Future.succeededFuture(entitlementService.findClusterId());
    }
}
