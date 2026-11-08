package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.internal.api.ee.license.CurrentLicense;
import org.kinotic.domain.internal.api.ee.license.License;
import org.kinotic.system.api.model.LicenseDetails;
import org.kinotic.system.api.services.LicenseService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class DefaultLicenseService implements LicenseService {

    private final CurrentLicense currentLicense;

    @Override
    public Future<LicenseDetails> findCurrent() {
        return Future.succeededFuture(detailsOf(currentLicense.find()));
    }

    @Override
    public Future<LicenseDetails> install(String licenseKey) {
        Validate.notBlank(licenseKey, "licenseKey is required");
        return currentLicense.install(licenseKey).map(DefaultLicenseService::detailsOf);
    }

    private static LicenseDetails detailsOf(License license) {
        LicenseDetails ret = null;
        if (license != null) {
            ret = new LicenseDetails().setLicensee(license.licensee())
                                      .setIssuedAt(license.issuedAt() != null ? Date.from(license.issuedAt()) : null)
                                      .setExpiresAt(Date.from(license.expiresAt()))
                                      .setGraceEndsAt(Date.from(license.graceEndsAt()))
                                      .setStatus(license.status(Instant.now()))
                                      .setFeatures(license.features());
        }
        return ret;
    }
}
