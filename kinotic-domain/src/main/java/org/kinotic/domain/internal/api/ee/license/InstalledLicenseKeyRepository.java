package org.kinotic.domain.internal.api.ee.license;

import co.elastic.clients.elasticsearch.core.get.GetResult;
import io.vertx.core.Future;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.function.Function;

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
        return saveSync(installed(key)).mapEmpty();
    }

    /**
     * Makes {@code key} the key the cluster runs on in place of {@code expectedKey}.
     *
     * @return true when {@code key} replaced {@code expectedKey}, false when another key is installed by then
     */
    public Future<Boolean> replace(String expectedKey, String key) {
        return crudServiceTemplate.findById(indexName, INSTALLED_ID, type, null, Function.<GetResult<InstalledLicenseKey>>identity())
                .compose(read -> {
                    Future<Boolean> ret;
                    if (!read.found() || read.source() == null || !expectedKey.equals(read.source().getLicenseKey())) {
                        ret = Future.succeededFuture(false);
                    } else {
                        // conditional on the document as read, so an install landing between the read and this write wins
                        ret = crudServiceTemplate.saveSync(indexName, INSTALLED_ID, installed(key),
                                                           b -> b.ifSeqNo(read.seqNo()).ifPrimaryTerm(read.primaryTerm()))
                                                 .map(true)
                                                 .recover(error -> CrudServiceTemplate.isVersionConflict(error)
                                                         ? Future.succeededFuture(false)
                                                         : Future.failedFuture(error));
                    }
                    return ret;
                });
    }

    private static InstalledLicenseKey installed(String key) {
        return new InstalledLicenseKey().setId(INSTALLED_ID).setLicenseKey(key).setInstalled(new Date());
    }
}
