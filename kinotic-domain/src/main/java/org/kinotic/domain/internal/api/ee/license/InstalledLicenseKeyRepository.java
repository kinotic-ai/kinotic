package org.kinotic.domain.internal.api.ee.license;

import io.vertx.core.Future;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Holds the one {@link InstalledLicenseKey} of the cluster.
 */
@Component
public class InstalledLicenseKeyRepository extends AbstractRepository<InstalledLicenseKey> {

    private static final String INSTALLED_ID = "installed";

    public InstalledLicenseKeyRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_installed_license_key", InstalledLicenseKey.class, crudServiceTemplate);
    }

    /** The key the cluster runs on, or {@code null} when none is installed. */
    public Future<String> findInstalledKey() {
        return findById(INSTALLED_ID).map(installed -> installed != null ? installed.getLicenseKey() : null);
    }

    /** Makes {@code key} the key the cluster runs on, replacing any installed before it. */
    public Future<Void> install(String key) {
        return saveSync(new InstalledLicenseKey().setId(INSTALLED_ID).setLicenseKey(key).setInstalled(new Date())).mapEmpty();
    }
}
