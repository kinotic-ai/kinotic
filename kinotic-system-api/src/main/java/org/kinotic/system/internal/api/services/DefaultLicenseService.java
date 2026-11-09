package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.domain.api.model.license.License;
import org.kinotic.domain.api.services.EntitlementService;
import org.kinotic.system.api.services.LicenseService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultLicenseService implements LicenseService {

    private final EntitlementService entitlementService;

    @Override
    public Future<License> findCurrent() {
        return Future.succeededFuture(entitlementService.find());
    }

    @Override
    public Future<License> install(String licenseKey) {
        return entitlementService.install(licenseKey);
    }
}
