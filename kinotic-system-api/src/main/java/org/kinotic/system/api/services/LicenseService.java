package org.kinotic.system.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.model.license.License;

/**
 * The Kinotic Enterprise License key the whole cluster runs on, which system users read and replace without
 * restarting a server. A key bound to the cluster is renewed from Minds Ignited's license server once a day.
 * <p>
 * Published in the system zone, which only SYSTEM participants and the system's own cluster members may address.
 */
@Publish
public interface LicenseService {

    /** The license the cluster runs on, or {@code null} when no valid key is installed. */
    Future<License> findCurrent();

    /**
     * Installs {@code licenseKey} for the whole cluster in place of the installed key. The call completes once the key
     * is stored and the node that took the call runs on it; every other node runs on it shortly after, except a node the
     * notice did not reach or that could not read the key then, which reads it at its next reload, tried every ten
     * minutes.
     * Fails when the key is not one Minds Ignited signed, names no licensee or expiry, is bound to another cluster, or
     * ended its grace period, or when the cluster stores secrets in Azure Key Vault and the key does not turn that
     * feature on.
     *
     * @param licenseKey a Kinotic Enterprise License key, as Minds Ignited issued it
     * @return the license the key holds
     */
    Future<License> install(String licenseKey);

    /**
     * The id of this cluster, the UID of its {@code kube-system} namespace, which a customer gives Minds Ignited to get
     * a key bound to the cluster.
     *
     * @return the id, or {@code null} when the cluster does not run in Kubernetes, or has not been able to read the
     *         namespace yet
     */
    Future<String> findClusterId();
}
